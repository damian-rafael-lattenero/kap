package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// ── RestartPolicy: the convergence law as a value ────────────────────────

/**
 * The restart half of a [Process]: the delay schedule between restarts plus
 * the hard cap on restart count.
 *
 * **Convergence law.** Every restart waits a strictly positive delay and the
 * restart count is finite: a body that fails instantly can never hot-loop.
 * The factories enforce this — [constant] requires `delay > 0`, [exponential]
 * requires `base > 0`, and both take a bounded `maxRestarts`. There is no
 * public constructor precisely so an unlawful policy (zero delays, or no
 * cap) cannot be expressed.
 *
 * ```
 * val policy = RestartPolicy.exponential(base = 500.milliseconds, maxRestarts = 4)
 * // A failing body restarts at +0.5s, +1.5s, +3.5s, then the 4th failure exhausts.
 * ```
 */
class RestartPolicy private constructor(
    internal val schedule: Schedule<Throwable>,
    /** Maximum restarts before the process is declared exhausted. */
    val maxRestarts: Int,
) {
    init {
        require(maxRestarts >= 0) { "maxRestarts must be >= 0, was $maxRestarts" }
    }

    companion object {
        /** Default cap: eight restarts under exponential backoff is minutes of trying. */
        const val DEFAULT_MAX_RESTARTS: Int = 8

        /** Restarts with a fixed [delay] between attempts. [delay] must be positive. */
        fun constant(
            delay: Duration,
            maxRestarts: Int = DEFAULT_MAX_RESTARTS,
        ): RestartPolicy {
            require(delay > ZERO) {
                "constant restart delay must be strictly positive (convergence law), was $delay"
            }
            return RestartPolicy(Schedule.spaced(delay), maxRestarts)
        }

        /**
         * Restarts with exponential backoff starting from [base], multiplied by
         * [factor] each restart, capped at [max]. [base] must be positive.
         */
        fun exponential(
            base: Duration = 100.milliseconds,
            factor: Double = 2.0,
            max: Duration = Duration.INFINITE,
            maxRestarts: Int = DEFAULT_MAX_RESTARTS,
        ): RestartPolicy {
            require(base > ZERO) {
                "exponential base must be strictly positive (convergence law), was $base"
            }
            return RestartPolicy(Schedule.exponential(base, factor, max), maxRestarts)
        }
    }
}

// ── Process: a supervised loop with a heartbeat ──────────────────────────

/**
 * A long-lived supervised computation: the body runs; **normal completion
 * finishes the process**, a failure restarts it according to a
 * [RestartPolicy], and exhausting the policy fails [ProcessHandle.await]
 * with [ProcessExhaustedException]. While the process is alive a heartbeat
 * beats at its own cadence, independent of the body's progress — a body
 * that is stuck mid-cycle still has a live heartbeat, so the beat answers
 * "is the process there", never "did the cycle finish" (the supervisor
 * that killed a healthy-but-slow poller taught exactly this lesson).
 *
 * A [Process] is a description; nothing runs until [startIn] interprets it
 * in a scope. Cancellation is sacred: `stop()`, or the death of the owning
 * scope, ends the process without counting as a failure and without
 * triggering a restart. Observability callbacks ([onRestart], [onHeartbeat]
 * of [process]) are transparent — they cannot change the lifecycle.
 *
 * ```
 * val poller = Kap { pollOnce() }
 *     .repeat(Schedule.spaced(500.milliseconds))
 *     .process(
 *         name = "tg-poller",
 *         restart = RestartPolicy.exponential(base = 1.seconds, maxRestarts = 5),
 *         heartbeatEvery = 5.seconds,
 *         onHeartbeat = { touchHbFile() },
 *     )
 *
 * coroutineScope {
 *     val handle = poller.startIn(this)
 *     awaitShutdownSignal()
 *     handle.stop()
 * }
 * ```
 */
// The parameter list IS the API (SPEC-5.0 names every one of them); no config object.
@Suppress("LongParameterList")
class Process<A> internal constructor(
    /** The process name, surfaced by the [ProcessHandle] and every terminal exception. */
    val name: String,
    private val body: Kap<A>,
    private val restart: RestartPolicy,
    private val heartbeatEvery: Duration,
    private val onRestart: (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit,
    private val onHeartbeat: () -> Unit,
    private val timeSource: TimeSource,
) {
    init {
        require(heartbeatEvery >= ZERO) {
            "heartbeatEvery must be >= ZERO (ZERO disables the heartbeat), was $heartbeatEvery"
        }
    }

    /**
     * Starts the process as a child of [scope] and returns its handle
     * immediately. The process lives exactly as long as the scope allows:
     * cancelling the scope ends it, structured-concurrency style. The
     * returned computation never escapes its scope — there is no daemon.
     *
     * The heartbeat beats at [Process.heartbeatEvery] (immediately at start,
     * then every period) while the process is alive, including during the
     * body's execution and during restart delays. `heartbeatEvery == ZERO`
     * disables it and [ProcessHandle.heartbeatAge] reports
     * [Duration.INFINITE].
     */
    fun startIn(scope: CoroutineScope): ProcessHandle<A> {
        val outcome = CompletableDeferred<Result<A>>()
        val restarts = MutableStateFlow(0)
        val lastBeat = MutableStateFlow<TimeMark?>(null)
        val stopRequested = MutableStateFlow(false)

        val job = scope.launch {
            val beatJob = startHeartbeat(lastBeat)
            try {
                supervise(outcome, restarts)
            } catch (ce: CancellationException) {
                val terminal: Throwable =
                    if (stopRequested.value) ProcessStoppedException(name) else ce
                outcome.complete(Result.failure(terminal))
            } finally {
                beatJob?.cancel()
            }
        }
        // The scope may die without stop() and without the body failing;
        // await() must not hang in that case.
        job.invokeOnCompletion { reason ->
            if (!outcome.isCompleted) {
                outcome.complete(
                    Result.failure(
                        reason ?: CancellationException("process '$name' ended with its scope"),
                    ),
                )
            }
        }
        return RunningProcess(name, job, outcome, restarts, lastBeat, stopRequested)
    }

    private fun CoroutineScope.startHeartbeat(lastBeat: MutableStateFlow<TimeMark?>): Job? =
        if (heartbeatEvery > ZERO) {
            launch {
                while (isActive) {
                    lastBeat.value = timeSource.markNow()
                    tryBeat()
                    delay(heartbeatEvery)
                }
            }
        } else {
            null
        }

    /** Runs the body, restarts it per policy, completes [outcome] with the terminal result. */
    // The supervisor sees every failure by definition — same contract as retry's catch.
    // delay/execute suspend inside inline lambdas; the TR pass cannot see it (CircuitBreaker precedent).
    @Suppress("TooGenericExceptionCaught", "RedundantSuspendModifier")
    private suspend fun CoroutineScope.supervise(
        outcome: CompletableDeferred<Result<A>>,
        restarts: MutableStateFlow<Int>,
    ) {
        var attempt = 0
        while (true) {
            try {
                val value = with(body.isolated()) { execute() }
                outcome.complete(Result.success(value))
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val decision = restart.schedule.decide(attempt, e)
                val terminal = terminalOf(attempt, decision, e)
                if (terminal != null) {
                    outcome.complete(Result.failure(terminal))
                    return
                }
                val nextDelay = (decision as Schedule.Decision.Continue).delay
                notifyRestart(attempt + 1, e, nextDelay)
                if (nextDelay > ZERO) delay(nextDelay)
                attempt += 1
                restarts.value = attempt   // counted when the restart runs, not when scheduled
            }
        }
    }

    /** Exhaustion or a Done schedule ends the process; null means "restart after the delay". */
    private fun terminalOf(attempt: Int, decision: Schedule.Decision, e: Throwable): Throwable? =
        when {
            attempt >= restart.maxRestarts -> ProcessExhaustedException(name, restart.maxRestarts, e)
            decision is Schedule.Decision.Done -> e
            else -> null
        }

    // Transparency law: observability hooks never change the lifecycle.
    @Suppress("SwallowedException")
    private fun tryBeat() {
        try {
            onHeartbeat()
        } catch (_: RuntimeException) {
            // the beat never kills anything
        }
    }

    @Suppress("SwallowedException")
    private fun notifyRestart(attempt: Int, error: Throwable, nextDelay: Duration) {
        try {
            onRestart(attempt, error, nextDelay)
        } catch (_: RuntimeException) {
            // observability only: the restart proceeds
        }
    }
}

// ── Handle: stop, await, health ──────────────────────────────────────────

/**
 * The live handle of a started [Process]. Obtained from [Process.startIn].
 */
interface ProcessHandle<out A> {
    /** The process name. */
    val name: String

    /** Restarts performed so far; collect the flow for live updates. */
    val restarts: StateFlow<Int>

    /** True while the process exists (running the body or waiting out a restart delay). */
    val isAlive: Boolean

    /**
     * Stops the process. Idempotent. Not a failure: no restart is triggered,
     * exhaustion never fires, and a pending [ProcessHandle.await] throws
     * [ProcessStoppedException].
     */
    fun stop()

    /**
     * Suspends until the process finishes: its value on normal completion,
     * [ProcessExhaustedException] when the restart policy gave up (cause =
     * the last failure), [ProcessStoppedException] after [stop], the
     * cancellation of the owning scope otherwise.
     */
    suspend fun await(): A

    /**
     * Age of the last heartbeat beat, per the [Process]'s time source.
     * [Duration.INFINITE] when the heartbeat is disabled or no beat has
     * landed yet.
     */
    fun heartbeatAge(): Duration
}

private class RunningProcess<out A>(
    override val name: String,
    private val job: Job,
    private val outcome: CompletableDeferred<Result<A>>,
    override val restarts: StateFlow<Int>,
    private val lastBeat: StateFlow<TimeMark?>,
    private val stopRequested: MutableStateFlow<Boolean>,
) : ProcessHandle<A> {

    override val isAlive: Boolean get() = job.isActive

    override fun stop() {
        stopRequested.value = true
        job.cancel(CancellationException("process '$name' stopped"))
    }

    override suspend fun await(): A =
        outcome.await().fold(
            onSuccess = { it },
            onFailure = { throw it },
        )

    override fun heartbeatAge(): Duration =
        lastBeat.value?.elapsedNow() ?: Duration.INFINITE
}

// ── Terminal exceptions ──────────────────────────────────────────────────

/**
 * Thrown by [ProcessHandle.await] when the body failed and the
 * [RestartPolicy] allowed no further restart. [cause] is the last failure.
 */
class ProcessExhaustedException(
    name: String,
    val restarts: Int,
    cause: Throwable,
) : RuntimeException("process '$name' exhausted its restart policy after $restarts restarts", cause)

/** Thrown by [ProcessHandle.await] after [ProcessHandle.stop]. */
class ProcessStoppedException(
    name: String,
) : RuntimeException("process '$name' stopped")

// ── Builder ──────────────────────────────────────────────────────────────

/**
 * Lifts this computation into a supervised, named [Process] — the SPEC-5.0
 * milestone 4.2 first cut: a repeated `Kap` with a restart policy, health,
 * and a handle with `stop()`. Nothing runs until [Process.startIn].
 *
 * Semantics:
 * - the body **completing normally finishes the process** (transient
 *   semantics: a long-lived service body should not return);
 * - a failure restarts after the policy's delay, up to its cap, then
 *   [ProcessHandle.await] throws [ProcessExhaustedException];
 * - `stop()` and scope cancellation are never failures (no restart, no
 *   exhaustion);
 * - the heartbeat beats at [heartbeatEvery] independent of body progress
 *   (`ZERO` disables it);
 * - [onRestart]/[onHeartbeat] are transparent observers.
 *
 * @param name process name, surfaced in the handle and terminal exceptions.
 * @param restart delays between restarts and the restart cap; the factories
 *   enforce the convergence law (positive delays, finite cap).
 * @param heartbeatEvery beat period while the process is alive; `ZERO` disables.
 * @param onRestart observer invoked before each restart delay (attempt
 *   number, the failure, the delay about to be waited).
 * @param onHeartbeat observer invoked on every beat.
 * @param timeSource injectable time source for [ProcessHandle.heartbeatAge].
 */
// The parameter list IS the API (SPEC-5.0 names every one of them); no config object.
@Suppress("LongParameterList")
fun <A> Kap<A>.process(
    name: String,
    restart: RestartPolicy = RestartPolicy.exponential(),
    heartbeatEvery: Duration = 1.seconds,
    onRestart: (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
    onHeartbeat: () -> Unit = {},
    timeSource: TimeSource = TimeSource.Monotonic,
): Process<A> = Process(name, this, restart, heartbeatEvery, onRestart, onHeartbeat, timeSource)
