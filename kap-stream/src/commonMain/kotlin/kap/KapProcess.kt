package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * One thing that happened to an item the mailbox did not hand to the consumer
 * as a plain call: it was dropped by the named strategy (or by the closed
 * mailbox), it failed inside the handler, or it exceeded its budget. Reported
 * through the injected `observe` callback — a typed event, not a log line, so
 * the caller decides what to record.
 *
 * Observation is **transparent** (the Process laws apply): a `RuntimeException`
 * thrown by the observer is swallowed and the mailbox proceeds. A
 * `CancellationException` always propagates — cancellation is never swallowed
 * anywhere in KAP — and a fatal `Error` is not caught either (it fails the
 * drain and the surrounding [Process] handles it per its restart policy).
 */
sealed interface MailboxEvent<out A> {
    /**
     * The item did not enter the mailbox (a lossy strategy made it the victim)
     * or was offered after the mailbox closed. [drops] is the running total of
     * lost items, the value [KapProcess.dropped] holds after this event.
     */
    data class Dropped<out A>(val item: A, val drops: Long) : MailboxEvent<A>

    /** The handler threw: the item is abandoned, the drain continues. */
    data class Failed<out A>(val item: A, val error: Throwable) : MailboxEvent<A>

    /** The handler exceeded [budget]: it was cancelled and abandoned, the drain continues. */
    data class TimedOut<out A>(val item: A, val budget: Duration) : MailboxEvent<A>
}

/**
 * A typed mailbox process — "actors yes, Akka no" (SPEC-5.0): a bounded queue of
 * a user ADT [A], a **named** backpressure policy ([Backpressure], the same
 * algebra [KapStream] uses), a single-consumer drain with per-item failure
 * isolation and a per-item time budget, and a lifecycle that is a [Process]
 * (kap-resilience) with all of its laws: normal completion, stop and scope
 * cancellation are never failures, a failure restarts under the policy, and
 * the heartbeat beats while the drain runs.
 *
 * ```
 * sealed interface Command
 * data class EnforceMode(val taskId: String) : Command
 * data class RefreshMenu(val taskId: String, val options: List<Option>) : Command
 *
 * val mailbox = KapProcess<Command>(
 *     name = "stream-effects",
 *     backpressure = Backpressure.Buffer(256),   // exact: the producer waits, nothing is lost
 *     itemBudget = 45.seconds,                    // one stuck effect can never stall the loop
 *     observe = { event -> log(event) },          // drops, failures and timeouts as data
 * )
 *
 * coroutineScope {
 *     val worker = mailbox.asProcess(handler = { command -> runEffect(command) }).startIn(this)
 *     mailbox.offer(EnforceMode(taskId))          // from the pump: suspends only if the buffer is full
 *     awaitShutdownSignal()
 *     worker.stop()                               // idempotent, not a failure
 * }
 * ```
 *
 * ## Module placement
 *
 * `KapProcess` lives in **kap-stream** on purpose. The minimal-code law asks
 * for the smallest home that already owns the required pieces: kap-stream owns
 * [Backpressure] (the mailbox's policy value) and already depends on
 * kap-resilience (the [Process] lifecycle). A dedicated `kap-actors` module
 * would add a Gradle module, a publication coordinate and a new dependency
 * edge for ~250 lines that reuse exactly these two algebras. The mailbox is
 * the same backpressure algebra with one consumer instead of a stream of
 * stages; it belongs next to it.
 *
 * ## Laws
 *
 * 1. **Exact strategies never drop.** [Backpressure.Suspend] and
 *    [Backpressure.Buffer] hand every offered item to the handler, in offer
 *    order, and [dropped] stays `0`. The producer suspends exactly where the
 *    strategy says (always for `Suspend`, only when full for `Buffer`).
 * 2. **Lossy strategies drop only what they say — and count it.** `Conflate`,
 *    `Latest(n)` and `DropOldest(n)` keep the newest `n` (`Conflate` = one),
 *    `DropLatest(n)` keeps the oldest `n` and drops the incoming element.
 *    Survivors keep offer order and are never duplicated, and every lost item
 *    increments [dropped] exactly once (offers after [close] included).
 *    `delivered + dropped == offered` once a closed mailbox has drained.
 * 3. **At most one handler at a time.** The drain is a single sequential loop;
 *    starting a second [consume] on the same mailbox is rejected, so two
 *    handlers can never overlap.
 * 4. **Failure isolation.** A handler throwing an `Exception` is reported as
 *    [MailboxEvent.Failed] and the drain continues with the next item; the
 *    surrounding [Process] does not restart. `CancellationException` is never
 *    an item failure: it propagates (cancellation is sacred).
 * 5. **Budget, not abandonment by silence.** An item whose handler exceeds
 *    [itemBudget] is cancelled and reported as [MailboxEvent.TimedOut]; the
 *    next item starts immediately. The handler's `finally` blocks still run.
 * 6. **Process lifecycle.** Normal completion is [close] followed by a drained
 *    mailbox; `stop()` and scope cancellation are never failures; a fatal
 *    failure of the drain (an `Error`, or an error in the observer) restarts
 *    it under the [RestartPolicy]. The heartbeat beats while the drain runs,
 *    independent of handler progress.
 */
// The constructor IS the policy surface (name, strategy, budget, observer); no config object.
// `isClosedForSend` is a best-effort fast path; the ClosedSendChannelException catch
// covers the check-then-send race — the Delicate opt-in is read and intended.
@OptIn(DelicateCoroutinesApi::class)
@Suppress("LongParameterList", "TooGenericExceptionCaught")
class KapProcess<A>(
    /** The mailbox name, surfaced by the [Process] handle and its terminal exceptions. */
    val name: String,
    /** The named strategy deciding what happens when the consumer cannot take an item yet. */
    val backpressure: Backpressure = Backpressure.Suspend,
    /**
     * The per-item time budget: a handler running longer is cancelled and
     * reported as [MailboxEvent.TimedOut]. [Duration.INFINITE] (the default)
     * disables the budget.
     */
    val itemBudget: Duration = Duration.INFINITE,
    /**
     * The observer of everything that did not reach the handler as a plain
     * call: drops (with the running total), handler failures and timeouts.
     * Transparent: its own `RuntimeException`s cannot break the drain.
     */
    private val observe: (MailboxEvent<A>) -> Unit = {},
) {
    init {
        require(itemBudget > ZERO) {
            "itemBudget must be strictly positive (use Duration.INFINITE to disable), was $itemBudget"
        }
    }

    private val policy: Policy = when (backpressure) {
        Backpressure.Suspend -> Policy.Exact
        is Backpressure.Buffer -> Policy.Exact
        Backpressure.Conflate -> Policy.KeepNewest
        is Backpressure.DropOldest -> Policy.KeepNewest
        is Backpressure.Latest -> Policy.KeepNewest
        is Backpressure.DropLatest -> Policy.KeepOldest
    }

    /**
     * The mailbox itself. Capacity 0 (rendezvous) for [Backpressure.Suspend],
     * the strategy's own slot count otherwise; overflow is always `SUSPEND` and
     * the lossy policies are implemented at [offer], where they can be counted.
     */
    private val channel: Channel<A> = when (backpressure) {
        Backpressure.Suspend -> Channel(Channel.RENDEZVOUS)
        is Backpressure.Buffer -> Channel(backpressure.capacity)
        Backpressure.Conflate -> Channel(1)
        is Backpressure.DropOldest -> Channel(backpressure.capacity)
        is Backpressure.DropLatest -> Channel(backpressure.capacity)
        is Backpressure.Latest -> Channel(backpressure.n)
    }

    private val drops = MutableStateFlow(0L)

    /** Items the mailbox never handed to the handler — the readable drop counter. */
    val dropped: StateFlow<Long> = drops.asStateFlow()

    private val consuming = MutableStateFlow(false)

    /**
     * Offers [item] to the mailbox. Never throws a failure: under the exact
     * strategies it suspends exactly as the strategy prescribes (always for
     * [Backpressure.Suspend], only when full for [Backpressure.Buffer]); under
     * the lossy strategies it never suspends, drops per policy and increments
     * [dropped]. An offer after [close] is dropped and counted too, so no
     * caller ever observes a closed-mailbox exception.
     *
     * Only structured cancellation propagates (`CancellationException`), as
     * everywhere in KAP.
     */
    suspend fun offer(item: A) {
        if (channel.isClosedForSend) {
            recordDrop(item)
            return
        }
        when (policy) {
            Policy.Exact -> sendExact(item)
            Policy.KeepOldest -> if (!channel.trySend(item).isSuccess) recordDrop(item)
            Policy.KeepNewest -> offerKeepingNewest(item)
        }
    }

    /**
     * Runs the drain: every item, in order, through [handler] — at most one
     * handler at a time. Per-item failures are reported and isolated
     * ([MailboxEvent.Failed]); an item exceeding [itemBudget] is cancelled and
     * reported ([MailboxEvent.TimedOut]); cancellation of the caller is never
     * swallowed. Completes normally when the mailbox is [close]d and drained.
     *
     * @throws IllegalStateException when the mailbox already has a consumer.
     */
    suspend fun consume(handler: suspend (A) -> Unit) {
        check(consuming.compareAndSet(expect = false, update = true)) {
            "KapProcess '$name' already has a consumer: one mailbox, one drain"
        }
        try {
            for (item in channel) handle(item, handler)
        } finally {
            consuming.value = false
        }
    }

    /**
     * Closes the mailbox: no further item is accepted (offers are dropped and
     * counted) and the drain completes normally after the buffered items are
     * handled. Idempotent.
     */
    fun close() {
        channel.close()
    }

    /**
     * The mailbox as a supervised [Process] — the SPEC-5.0 actor with the
     * Process laws. The body is [consume]; a normally closed mailbox finishes
     * the process, `stop()`/scope cancellation end it without a restart, and a
     * fatal drain failure restarts it under [restart]. The heartbeat comes from
     * [Kap.process] and beats while the drain runs, including mid-handler.
     *
     * @param handler the per-item effect.
     * @param restart delays and cap applied to a fatal drain failure.
     * @param heartbeatEvery beat period; `Duration.ZERO` disables it.
     * @param onRestart observer invoked before each restart delay.
     * @param onHeartbeat observer invoked on every beat.
     * @param timeSource injectable time source for the handle's heartbeat age.
     */
    // The parameter list IS the API (the same shape as Kap.process); no config object.
    @Suppress("LongParameterList")
    fun asProcess(
        handler: suspend (A) -> Unit,
        restart: RestartPolicy = RestartPolicy.exponential(),
        heartbeatEvery: Duration = 1.seconds,
        onRestart: (attempt: Int, error: Throwable, nextDelay: Duration) -> Unit = { _, _, _ -> },
        onHeartbeat: () -> Unit = {},
        timeSource: TimeSource = TimeSource.Monotonic,
    ): Process<Unit> = Kap { consume(handler) }.process(
        name = name,
        restart = restart,
        heartbeatEvery = heartbeatEvery,
        onRestart = onRestart,
        onHeartbeat = onHeartbeat,
        timeSource = timeSource,
    )

    // ── the drain's per-item step ────────────────────────────────────────

    private suspend fun handle(item: A, handler: suspend (A) -> Unit) {
        try {
            val completed: Unit? = if (itemBudget == Duration.INFINITE) {
                handler(item)
                Unit
            } else {
                withTimeoutOrNull(itemBudget) { handler(item) }
            }
            if (completed == null) report(MailboxEvent.TimedOut(item, itemBudget))
        } catch (e: CancellationException) {
            throw e   // cancellation is never an item failure
        } catch (e: Exception) {
            report(MailboxEvent.Failed(item, e))
        }
    }

    // ── offer policies ───────────────────────────────────────────────────

    private suspend fun sendExact(item: A) {
        try {
            channel.send(item)
        } catch (_: ClosedSendChannelException) {
            recordDrop(item)   // the mailbox closed between the check and the send
        }
    }

    /**
     * Keeps the newest item: on a full buffer the oldest element is taken out
     * (and counted), then the offer retries. If the consumer wins the race for
     * that element, the retry finds the freed slot — either way exactly one
     * item leaves the buffer and the offered item enters.
     */
    // Three exits are the policy's shape: sent, mailbox closed, or victim taken and retried.
    @Suppress("ReturnCount")
    private fun offerKeepingNewest(item: A) {
        while (true) {
            if (channel.trySend(item).isSuccess) return
            if (channel.isClosedForSend) {
                recordDrop(item)
                return
            }
            val victim = channel.tryReceive()
            when {
                victim.isSuccess -> recordDrop(victim.getOrThrow())
                victim.isClosed -> {
                    recordDrop(item)
                    return
                }
                // empty again: the consumer freed the slot between the two calls; retry the send
            }
        }
    }

    // ── reporting ────────────────────────────────────────────────────────

    private fun recordDrop(item: A) {
        report(MailboxEvent.Dropped(item, drops.updateAndGet { it + 1L }))
    }

    // Transparency law (Process law 5): observation cannot change the drain.
    // Only a RuntimeException is swallowed; a CancellationException is the
    // caller's cancellation speaking through the hook and always propagates,
    // and a fatal Error fails the drain (the Process restarts it per policy).
    private fun report(event: MailboxEvent<A>) {
        try {
            observe(event)
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            // observability only: the mailbox proceeds
        }
    }

    private enum class Policy {
        /** Nothing is dropped: the producer suspends when the consumer cannot take. */
        Exact,

        /** The oldest buffered item is the victim on overflow (Conflate/Latest/DropOldest). */
        KeepNewest,

        /** The incoming item is the victim on overflow (DropLatest). */
        KeepOldest,
    }
}
