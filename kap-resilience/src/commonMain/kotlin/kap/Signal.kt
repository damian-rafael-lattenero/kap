package kap

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.ZERO

/**
 * The outcome of one [Signal.awaitOr] window, as a value: no exception for the
 * timeout path.
 */
sealed interface Beat {
    /** A wake was pending, or arrived inside the window. */
    data object Signalled : Beat

    /** The window elapsed with no wake: the schedule's delay, exactly. */
    data object TimedOut : Beat
}

/**
 * A **hot, conflated wake-up signal** — the primitive a supervised publisher
 * needs to say "wake NOW, or after `every`" (the bot's `LiveHub.requestTick` /
 * `awaitBeat`, SPEC-5.0). Unlike a cold [KapStream], a [Signal] carries no
 * elements: it is one pending wake at most, and [send] is non-suspending and
 * callable from any thread, callback or coroutine.
 *
 * ```
 * val beat = Signal()
 *
 * // any producer, any thread: a tap, a frame, a shutdown hook
 * fun onCardMoved() = beat.send()
 *
 * // the publisher's loop sleeps on schedule algebra, never on a raw channel
 * while (true) {
 *     redraw()
 *     when (beat.awaitOr(Schedule.spaced(3.seconds))) {
 *         Beat.Signalled -> Unit          // a tap asked for the draw NOW
 *         Beat.TimedOut -> Unit           // the cadence elapsed: the next draw is due
 *     }
 * }
 * ```
 *
 * ## Module placement
 *
 * [Signal] lives in **kap-resilience**, the home of [Schedule] and [Process].
 * It needs no [Backpressure] (a wake is conflated by definition, the one point
 * of the algebra), so it is not a kap-stream type; and it cannot live in
 * kap-core because its window is a [Schedule], which kap-core cannot see
 * (kap-resilience depends on kap-core, not the other way round). The smallest
 * lawful home is therefore the module that owns the schedule algebra.
 *
 * ## Laws
 *
 * 1. **A pending wake wakes immediately.** A [send] that happened-before the
 *    await is never lost: [awaitOr] returns [Beat.Signalled] at zero virtual
 *    elapsed time, whatever the schedule says.
 * 2. **No wake waits exactly the schedule's delay.** With nothing pending,
 *    `Continue(d)` returns [Beat.TimedOut] after exactly virtual `d`; it never
 *    polls early. `Done` means "no window": [Beat.TimedOut] immediately.
 * 3. **Conflation.** `N` sends with no await between them collapse to one
 *    pending wake: one [Beat.Signalled], never `N` wake-ups. Once that wake is
 *    consumed, the next window waits its full delay (no ghost wake remains).
 * 4. **Cancellation propagates.** Cancelling an awaiting coroutine cancels the
 *    wait and leaves the pending state untouched: a wake sent before or during
 *    the cancelled await is still there for the next await.
 * 5. **One wake per await.** Each [Beat.Signalled] consumes exactly one pending
 *    wake; the API can never report two.
 */
class Signal {
    /**
     * The one pending wake — a conflated channel, which is exactly the
     * "keep the latest, never queue" algebra: `send` replaces a pending wake
     * instead of appending one.
     */
    private val wakes = Channel<Unit>(Channel.CONFLATED)

    /**
     * Wakes the awaiting consumer now, if there is one; otherwise leaves one
     * pending wake (superseding any earlier pending one). Non-suspending and
     * callable from any thread, callback or coroutine.
     */
    fun send() {
        wakes.trySend(Unit)
    }

    /**
     * Waits for a wake or for the schedule's window, whichever comes first.
     *
     * The decision is taken once, at attempt `0` with [Unit] (a schedule is a
     * pointwise policy): `Continue(d)` is a `d`-long window; `Done` is no
     * window at all. A pending wake is checked **before** the schedule, so
     * "a pending wake wakes immediately" holds even for a `Done` or zero-delay
     * schedule.
     *
     * ```
     * beat.awaitOr(Schedule.spaced(3.seconds))                  // the cadence
     * beat.awaitOr(Schedule.exponential<Unit>(100.milliseconds)) // retrying publisher
     * ```
     *
     * @param schedule the window policy: its attempt-0 `Continue` delay is the
     *   maximum wait; `Done` waits nothing.
     * @return [Beat.Signalled] for a wake, [Beat.TimedOut] once the window
     *   elapses; no exception for the timeout path.
     */
    suspend fun awaitOr(schedule: Schedule<Unit>): Beat {
        if (wakes.tryReceive().isSuccess) return Beat.Signalled
        return when (val decision = schedule.decideAt(0, Unit)) {
            is Schedule.Decision.Done -> Beat.TimedOut
            is Schedule.Decision.Continue ->
                if (decision.delay <= ZERO) {
                    Beat.TimedOut
                } else {
                    val woken = withTimeoutOrNull(decision.delay) { wakes.receive() }
                    if (woken != null) Beat.Signalled else Beat.TimedOut
                }
        }
    }
}
