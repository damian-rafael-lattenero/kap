package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The Signal laws (SPEC-5.0 — the hot wake-up primitive), proven under virtual
 * time:
 *
 * 1. A pending wake wakes immediately, even under a long window.
 * 2. No wake waits exactly the schedule's `Continue` delay, never early.
 * 3. `N` sends with no await between them collapse to one pending wake; a
 *    burst during an await wakes the waiter once and leaves at most one
 *    pending wake (never `N` wake-ups).
 * 4. Cancellation propagates and does not consume a future wake.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SignalLawsTest {

    @Test
    fun `a pending wake returns Signalled immediately whatever the window`() = runTest {
        checkAll(Arb.long(1L..10_000L)) { windowMs ->
            val signal = Signal()
            signal.send()
            val start = currentTime
            assertEquals(Beat.Signalled, signal.awaitOr(Schedule.spaced(windowMs.milliseconds)))
            assertEquals(start, currentTime, "a pending wake must not wait (window=$windowMs)")
        }
    }

    @Test
    fun `no wake returns TimedOut after exactly the schedule delay`() = runTest {
        checkAll(Arb.long(1L..10_000L)) { windowMs ->
            val signal = Signal()
            val start = currentTime
            assertEquals(Beat.TimedOut, signal.awaitOr(Schedule.spaced(windowMs.milliseconds)))
            assertEquals(
                start + windowMs,
                currentTime,
                "the window must be the schedule's delay, exactly (window=$windowMs)",
            )
        }
    }

    @Test
    fun `N sends with no await between them collapse to one pending wake`() = runTest {
        checkAll(Arb.int(1..100)) { sends ->
            val signal = Signal()
            repeat(sends) { signal.send() }
            val start = currentTime
            assertEquals(Beat.Signalled, signal.awaitOr(Schedule.spaced(1.seconds)))
            assertEquals(start, currentTime, "$sends sends must wake immediately")
            assertEquals(
                Beat.TimedOut,
                signal.awaitOr(Schedule.spaced(1.seconds)),
                "$sends sends must leave at most one wake",
            )
            assertEquals(start + 1_000, currentTime, "the ghost wake must not shorten the next window")
        }
    }

    @Test
    fun `a burst while a waiter is suspended wakes it once and leaves at most one pending wake`() = runTest {
        val signal = Signal()
        val sender = launch { repeat(100) { signal.send() } }

        assertEquals(Beat.Signalled, signal.awaitOr(Schedule.spaced(1.seconds)))
        sender.join()
        // one in-flight wake plus at most one conflated pending wake — never 100
        assertEquals(Beat.Signalled, signal.awaitOr(Schedule.spaced(1.seconds)))
        assertEquals(Beat.TimedOut, signal.awaitOr(Schedule.spaced(1.seconds)))
    }

    @Test
    fun `a cancelled await propagates and leaves the pending state untouched`() = runTest {
        val signal = Signal()
        var outcome: Beat? = null
        val awaiting = launch { outcome = signal.awaitOr(Schedule.spaced(10.seconds)) }

        delay(1.seconds)   // the await is mid-window
        awaiting.cancel()
        awaiting.join()
        assertNull(outcome, "cancellation must not produce a Beat")

        signal.send()
        assertEquals(
            Beat.Signalled,
            signal.awaitOr(Schedule.spaced(1.seconds)),
            "the wake sent after the cancelled await must not be lost",
        )
    }
}
