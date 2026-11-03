package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

// ── Strategy: which children a failure restarts ──────────────────────────

/**
 * The supervision strategy of a [Supervisor]: what happens to the *other*
 * children when one of them fails.
 *
 * Import the entry directly to read like a value:
 * ```
 * import kap.SupervisionStrategy.OneForOne
 * ```
 */
enum class SupervisionStrategy {
    /**
     * Only the failed child restarts. Siblings keep running untouched: they
     * are never cancelled, never restarted, and their restart counters stay
     * at zero. Use it for independent services that should not be affected by
     * each other's failures.
     */
    OneForOne,

    /**
     * Any failure restarts every child of the group that is still part of it:
     * running siblings are cancelled, their work discarded, and all of them
     * (the failed child included) are relaunched after the policy's delay.
     * Children that had already **completed normally** are not resurrected —
     * normal completion is terminal in the [Process] sense.
     *
     * Use it when the children share an invariant (a cache, a connection
     * pool) and a stale sibling would be inconsistent after one restarts.
     */
    AllForOne,
}

// ── Children: named Kap bodies ───────────────────────────────────────────

/**
 * A named [Kap] body supervised by a [Supervisor]. Build it with
 * [Supervisor.child] — the name is the key of [SupervisorHandle.restarts] and
 * appears in terminal exceptions.
 */
class SupervisorChild internal constructor(
    /** Unique name of the child within its group. */
    val name: String,
    internal val body: Kap<Any?>,
) {
    internal companion object {
        fun <A> of(name: String, body: Kap<A>): SupervisorChild {
            require(name.isNotBlank()) { "supervised child name must not be blank" }
            return SupervisorChild(name, body)
        }
    }
}

// ── Supervisor: a group of supervised children ───────────────────────────

/**
 * A group of named [Kap] bodies supervised together (SPEC-5.0 milestone 5.0):
 * when a child fails, the [strategy] decides whether only that child
 * restarts ([SupervisionStrategy.OneForOne]) or the whole group does
 * ([SupervisionStrategy.AllForOne]); the [restartPolicy] decides the backoff
 * and the restart cap.
 *
 * A `Supervisor` is a description: nothing runs until [startIn] interprets it
 * as a child of the given scope. Each child runs with the terminal-success
 * semantics of [Process] — a body completing normally finishes that child for
 * good — while a failure is retried up to the policy's cap; exhausting the cap
 * fails the group with [SupervisorExhaustedException] whose `cause` is the last
 * failure. The group completes normally exactly when every child has completed
 * normally. `stop()` and the death of the owning scope are never failures and
 * never trigger a restart.
 *
 * The restart policy is a value shared by all children: every child has its
 * own attempt counter, but stateful schedules (`withMaxDuration`) observe the
 * attempts of the whole group. Prefer pure schedules for supervisors.
 *
 * ```
 * val group = Supervisor(
 *     children = listOf(
 *         Supervisor.child("bus-tailer") { bus.tail() },
 *         Supervisor.child("memory-worker") { memory.work() },
 *     ),
 *     strategy = SupervisionStrategy.OneForOne,
 *     restartPolicy = RestartPolicy.exponential(base = 1.seconds, maxRestarts = 5),
 * )
 *
 * coroutineScope {
 *     val handle = group.startIn(this)
 *     awaitShutdownSignal()
 *     handle.stop()
 * }
 * ```
 */
class Supervisor(
    children: List<SupervisorChild>,
    /** What happens to the siblings when a child fails. */
    val strategy: SupervisionStrategy,
    /** Backoff and restart cap applied to each child; the convergence law holds by construction. */
    val restartPolicy: RestartPolicy,
) {
    /** The children of the group, in declaration order. */
    val children: List<SupervisorChild> = children.toList()

    init {
        require(this.children.isNotEmpty()) { "a Supervisor needs at least one child" }
        val duplicates = this.children.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "supervised child names must be unique, duplicated: $duplicates" }
    }

    companion object {
        /** Names a [Kap] body for a [Supervisor]. */
        fun <A> child(name: String, body: Kap<A>): SupervisorChild = SupervisorChild.of(name, body)

        /**
         * Names a suspend block as a supervised child:
         * ```
         * Supervisor.child("poller") { pollOnce() }
         * ```
         */
        fun <A> child(name: String, block: suspend CoroutineScope.() -> A): SupervisorChild =
            SupervisorChild.of(name, Kap(block))
    }

    /**
     * Starts every child as a child of [scope] and returns the group's
     * [SupervisorHandle] immediately. The group lives exactly as long as the
     * scope allows — cancelling the scope ends it, structured-concurrency
     * style; no child escapes its scope.
     */
    fun startIn(scope: CoroutineScope): SupervisorHandle {
        val outcome = CompletableDeferred<Result<Unit>>()
        val stopRequested = MutableStateFlow(false)
        val states = children.map { SupervisedChildState(it) }

        val job = scope.launch {
            try {
                coroutineScope { supervise(states, outcome) }
            } catch (ce: CancellationException) {
                outcome.complete(Result.failure(if (stopRequested.value) SupervisorStoppedException() else ce))
            }
        }
        // The scope may die without stop() and without a child failing;
        // await() must not hang in that case.
        job.invokeOnCompletion { reason ->
            if (!outcome.isCompleted) {
                outcome.complete(Result.failure(reason ?: CancellationException("supervisor ended with its scope")))
            }
        }
        return RunningSupervisor(job, outcome, stopRequested, states.associate { it.child.name to it.restarts })
    }

    /**
     * Runs the children and the supervision loop. Returns when the group
     * completes (all children completed normally) or fails its policy; the
     * `finally` stops whatever is still running, so the enclosing
     * coroutineScope can return.
     */
    private suspend fun CoroutineScope.supervise(
        states: List<SupervisedChildState>,
        outcome: CompletableDeferred<Result<Unit>>,
    ) {
        val events = Channel<Event>(Channel.UNLIMITED)
        states.forEach { launchChild(it, events) }
        var completed = 0
        try {
            for (event in events) {
                val state = states.first { it.child.name == event.child }
                if (state.generation != event.generation) continue   // event from a cancelled generation
                when (event) {
                    is Event.Completed -> {
                        state.completed = true
                        completed += 1
                        if (completed == states.size) {
                            outcome.complete(Result.success(Unit))
                            return
                        }
                    }
                    is Event.Failed -> {
                        val decision = restartPolicy.schedule.decide(state.attempt, event.cause)
                        if (state.attempt >= restartPolicy.maxRestarts || decision is Schedule.Decision.Done) {
                            outcome.complete(
                                Result.failure(
                                    SupervisorExhaustedException(state.child.name, state.attempt, event.cause),
                                ),
                            )
                            return
                        }
                        restart(cohortOf(states, state), (decision as Schedule.Decision.Continue).delay, events)
                    }
                }
            }
        } finally {
            events.close()
            states.forEach { it.job?.cancel() }
        }
    }

    /** The children a failure restarts: just the failed one, or every non-completed child. */
    private fun cohortOf(states: List<SupervisedChildState>, failed: SupervisedChildState): List<SupervisedChildState> =
        when (strategy) {
            SupervisionStrategy.OneForOne -> listOf(failed)
            SupervisionStrategy.AllForOne -> states.filter { !it.completed }
        }

    /**
     * Stops the cohort's running members, waits the policy delay, then
     * relaunches every member with a fresh generation. The delay is serialized
     * with the rest of the supervision loop (one restart cycle at a time);
     * `RestartPolicy` guarantees it is positive for real policies.
     */
    private suspend fun CoroutineScope.restart(
        cohort: List<SupervisedChildState>,
        wait: Duration,
        events: Channel<Event>,
    ) {
        cohort.forEach { it.job?.cancelAndJoin() }
        if (wait > ZERO) delay(wait)
        cohort.forEach {
            it.attempt += 1
            it.restarts.value = it.attempt   // counted when the restart runs, not when scheduled
        }
        cohort.forEach { launchChild(it, events) }
    }

    // The supervisor sees every child failure by definition; cancellation is rethrown.
    @Suppress("TooGenericExceptionCaught")
    private fun CoroutineScope.launchChild(state: SupervisedChildState, events: Channel<Event>) {
        val generation = ++state.generation
        state.job = launch {
            try {
                with(state.child.body.isolated()) { execute() }
                events.send(Event.Completed(state.child.name, generation))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                events.send(Event.Failed(state.child.name, generation, e))
            }
        }
    }
}

// ── Events and child state (private to the supervisor) ───────────────────

private sealed interface Event {
    val child: String
    val generation: Int

    data class Failed(override val child: String, override val generation: Int, val cause: Throwable) : Event
    data class Completed(override val child: String, override val generation: Int) : Event
}

private class SupervisedChildState(val child: SupervisorChild) {
    val restarts = MutableStateFlow(0)
    var attempt = 0
    var generation = 0
    var completed = false
    var job: Job? = null
}

// ── Handle: restart counters, stop, await ────────────────────────────────

/**
 * The live handle of a started [Supervisor]. Obtained from
 * [Supervisor.startIn].
 */
interface SupervisorHandle {
    /**
     * Restart counters, one per child, keyed by child name and live-updated —
     * the honest surface of what the supervisor actually did. A child that
     * never restarted stays at zero; under [SupervisionStrategy.AllForOne]
     * every restarted child's counter advances together.
     */
    val restarts: Map<String, StateFlow<Int>>

    /** True while the group exists (children running or waiting out a restart delay). */
    val isAlive: Boolean

    /**
     * Stops the group and every child. Idempotent. Not a failure: no restart
     * is triggered, exhaustion never fires, and a pending [await] throws
     * [SupervisorStoppedException].
     */
    fun stop()

    /**
     * Suspends until the group finishes: normally when every child completed
     * normally; [SupervisorExhaustedException] when a child exceeded the
     * restart policy (cause = that child's last failure); [SupervisorStoppedException]
     * after [stop]; the cancellation of the owning scope otherwise.
     */
    suspend fun await()
}

private class RunningSupervisor(
    private val job: Job,
    private val outcome: CompletableDeferred<Result<Unit>>,
    private val stopRequested: MutableStateFlow<Boolean>,
    override val restarts: Map<String, StateFlow<Int>>,
) : SupervisorHandle {

    override val isAlive: Boolean get() = job.isActive

    override fun stop() {
        stopRequested.value = true
        job.cancel(CancellationException("supervisor stopped"))
    }

    override suspend fun await() {
        outcome.await().fold(
            onSuccess = { },
            onFailure = { throw it },
        )
    }
}

// ── Terminal exceptions ──────────────────────────────────────────────────

/**
 * Thrown by [SupervisorHandle.await] when a child failed and the
 * [RestartPolicy] allowed no further restart. [cause] is that child's last
 * failure.
 */
class SupervisorExhaustedException(
    /** Name of the child whose restart budget ran out. */
    val child: String,
    /** Restarts performed for that child before giving up. */
    val restarts: Int,
    cause: Throwable,
) : RuntimeException(
    "supervised child '$child' exhausted its restart policy after $restarts restarts",
    cause,
)

/** Thrown by [SupervisorHandle.await] after [SupervisorHandle.stop]. */
class SupervisorStoppedException : RuntimeException("supervisor stopped")
