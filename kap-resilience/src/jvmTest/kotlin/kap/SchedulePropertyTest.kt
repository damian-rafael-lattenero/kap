package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Property-based tests verifying Schedule composition invariants.
 *
 * These tests use random inputs via Kotest to ensure schedule combinators
 * satisfy their algebraic contracts under all conditions.
 */
class SchedulePropertyTest {

    private val err = RuntimeException("test")

    // ════════════════════════════════════════════════════════════════════════
    // recurs: produces exactly n Continue decisions followed by Done
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recurs(n) produces exactly n Continue then Done`() = runTest {
        checkAll(Arb.int(0..20)) { n ->
            val schedule = Schedule.times<Throwable>(n)
            repeat(n) { attempt ->
                assertIs<Schedule.Decision.Continue>(schedule.decide(attempt, err),
                    "attempt $attempt of recurs($n) should Continue")
            }
            assertIs<Schedule.Decision.Done>(schedule.decide(n, err),
                "attempt $n of recurs($n) should be Done")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // and: both must agree to continue, uses max delay
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `and stops when either schedule says Done`() = runTest {
        checkAll(Arb.int(0..10), Arb.int(0..10)) { n1, n2 ->
            val s1 = Schedule.times<Throwable>(n1)
            val s2 = Schedule.times<Throwable>(n2)
            val combined = s1 and s2
            val minN = minOf(n1, n2)

            // Should continue for min(n1, n2) attempts
            repeat(minN) { attempt ->
                assertIs<Schedule.Decision.Continue>(combined.decide(attempt, err),
                    "and($n1,$n2) at attempt $attempt should Continue")
            }
            // Should stop at min(n1, n2)
            assertIs<Schedule.Decision.Done>(combined.decide(minN, err),
                "and($n1,$n2) at attempt $minN should be Done")
        }
    }

    @Test
    fun `and uses max delay of both schedules`() = runTest {
        checkAll(Arb.long(1L..1000L), Arb.long(1L..1000L)) { d1, d2 ->
            val s1 = Schedule.spaced<Throwable>(d1.milliseconds)
            val s2 = Schedule.spaced<Throwable>(d2.milliseconds)
            val combined = s1 and s2

            val decision = combined.decide(0, err)
            assertIs<Schedule.Decision.Continue>(decision)
            assertEquals(maxOf(d1, d2).milliseconds, decision.delay,
                "and should use max delay of $d1 and $d2")
        }
    }

    @Test
    fun `and is commutative for delay`() = runTest {
        checkAll(Arb.long(1L..500L), Arb.long(1L..500L), Arb.int(0..5)) { d1, d2, attempt ->
            val s1 = Schedule.spaced<Throwable>(d1.milliseconds) and Schedule.times(10)
            val s2 = Schedule.spaced<Throwable>(d2.milliseconds) and Schedule.times(10)

            val forward = (s1 and s2).decide(attempt, err)
            val reverse = (s2 and s1).decide(attempt, err)

            // Both should produce the same decision type and delay
            assertEquals(forward::class, reverse::class)
            if (forward is Schedule.Decision.Continue && reverse is Schedule.Decision.Continue) {
                assertEquals(forward.delay, reverse.delay)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // or: either can continue, uses min delay
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `or continues when either schedule says Continue`() = runTest {
        checkAll(Arb.int(0..10), Arb.int(0..10)) { n1, n2 ->
            val s1 = Schedule.times<Throwable>(n1)
            val s2 = Schedule.times<Throwable>(n2)
            val combined = s1 or s2
            val maxN = maxOf(n1, n2)

            // Should continue for max(n1, n2) attempts
            repeat(maxN) { attempt ->
                assertIs<Schedule.Decision.Continue>(combined.decide(attempt, err),
                    "or($n1,$n2) at attempt $attempt should Continue")
            }
            // Should stop at max(n1, n2)
            assertIs<Schedule.Decision.Done>(combined.decide(maxN, err),
                "or($n1,$n2) at attempt $maxN should be Done")
        }
    }

    @Test
    fun `or uses min delay of both schedules`() = runTest {
        checkAll(Arb.long(1L..1000L), Arb.long(1L..1000L)) { d1, d2 ->
            val s1 = Schedule.spaced<Throwable>(d1.milliseconds)
            val s2 = Schedule.spaced<Throwable>(d2.milliseconds)
            val combined = s1 or s2

            val decision = combined.decide(0, err)
            assertIs<Schedule.Decision.Continue>(decision)
            assertEquals(minOf(d1, d2).milliseconds, decision.delay,
                "or should use min delay of $d1 and $d2")
        }
    }

    @Test
    fun `or is commutative for delay`() = runTest {
        checkAll(Arb.long(1L..500L), Arb.long(1L..500L), Arb.int(0..5)) { d1, d2, attempt ->
            val s1 = Schedule.spaced<Throwable>(d1.milliseconds)
            val s2 = Schedule.spaced<Throwable>(d2.milliseconds)

            val forward = (s1 or s2).decide(attempt, err)
            val reverse = (s2 or s1).decide(attempt, err)

            assertEquals(forward::class, reverse::class)
            if (forward is Schedule.Decision.Continue && reverse is Schedule.Decision.Continue) {
                assertEquals(forward.delay, reverse.delay)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // jittered: delay is within bounds
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `jittered delay is within factor bounds`() = runTest {
        checkAll(Arb.long(10L..1000L), Arb.int(0..10)) { baseMs, attempt ->
            val base = baseMs.milliseconds
            val factor = 0.5
            val seeded = Random(attempt.toLong() * 31 + baseMs)
            val schedule = Schedule.spaced<Throwable>(base).jittered(factor, seeded)

            val decision = schedule.decide(attempt, err)
            assertIs<Schedule.Decision.Continue>(decision)
            val minExpected = base * (1.0 - factor)
            val maxExpected = base * (1.0 + factor)
            assertTrue(decision.delay >= minExpected,
                "jittered delay ${decision.delay} should be >= $minExpected (base=$base, factor=$factor)")
            assertTrue(decision.delay <= maxExpected,
                "jittered delay ${decision.delay} should be <= $maxExpected (base=$base, factor=$factor)")
        }
    }

    @Test
    fun `jittered preserves Done from underlying schedule`() = runTest {
        checkAll(Arb.int(0..5)) { n ->
            val schedule = Schedule.times<Throwable>(n).jittered()
            // After n attempts, should be Done regardless of jitter
            assertIs<Schedule.Decision.Done>(schedule.decide(n, err))
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // fold: does not change retry behavior
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fold does not change retry decisions`() = runTest {
        checkAll(Arb.int(0..10), Arb.int(0..15)) { n, attempt ->
            val base = Schedule.times<Throwable>(n)
            val folded = base.fold(0) { acc, _ -> acc + 1 }

            val baseDecision = base.decide(attempt, err)
            val foldedDecision = folded.decide(attempt, err)

            assertEquals(baseDecision::class, foldedDecision::class,
                "fold should not change decision type at attempt $attempt for recurs($n)")
            if (baseDecision is Schedule.Decision.Continue && foldedDecision is Schedule.Decision.Continue) {
                assertEquals(baseDecision.delay, foldedDecision.delay,
                    "fold should not change delay")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // exponential: delays grow correctly
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `exponential delays double each attempt`() = runTest {
        checkAll(Arb.long(1L..100L)) { baseMs ->
            val base = baseMs.milliseconds
            val schedule = Schedule.exponential<Throwable>(base)

            var expected = base
            repeat(5) { attempt ->
                val decision = schedule.decide(attempt, err)
                assertIs<Schedule.Decision.Continue>(decision)
                assertEquals(expected, decision.delay,
                    "exponential at attempt $attempt with base $base")
                expected *= 2
            }
        }
    }

    @Test
    fun `exponential respects max cap`() = runTest {
        checkAll(Arb.long(1L..50L), Arb.long(100L..500L)) { baseMs, maxMs ->
            val base = baseMs.milliseconds
            val max = maxMs.milliseconds
            val schedule = Schedule.exponential<Throwable>(base, max = max)

            repeat(20) { attempt ->
                val decision = schedule.decide(attempt, err)
                assertIs<Schedule.Decision.Continue>(decision)
                assertTrue(decision.delay <= max,
                    "exponential delay ${decision.delay} should be <= max $max at attempt $attempt")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // fibonacci: delays follow fibonacci sequence
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fibonacci produces non-decreasing delays`() = runTest {
        checkAll(Arb.long(1L..100L)) { baseMs ->
            val base = baseMs.milliseconds
            val schedule = Schedule.fibonacci<Throwable>(base)

            var prevDelay = 0.milliseconds
            repeat(10) { attempt ->
                val decision = schedule.decide(attempt, err)
                assertIs<Schedule.Decision.Continue>(decision)
                assertTrue(decision.delay >= prevDelay,
                    "fibonacci delay at attempt $attempt should be >= previous ($prevDelay)")
                prevDelay = decision.delay
            }
        }
    }

    @Test
    fun `fibonacci respects max cap`() = runTest {
        checkAll(Arb.long(1L..50L), Arb.long(100L..500L)) { baseMs, maxMs ->
            val base = baseMs.milliseconds
            val max = maxMs.milliseconds
            val schedule = Schedule.fibonacci<Throwable>(base, max = max)

            repeat(20) { attempt ->
                val decision = schedule.decide(attempt, err)
                assertIs<Schedule.Decision.Continue>(decision)
                assertTrue(decision.delay <= max,
                    "fibonacci delay ${decision.delay} should be <= max $max at attempt $attempt")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // linear: delays grow linearly
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `linear delays grow by base each attempt`() = runTest {
        checkAll(Arb.long(1L..100L)) { baseMs ->
            val base = baseMs.milliseconds
            val schedule = Schedule.linear<Throwable>(base)

            repeat(5) { attempt ->
                val decision = schedule.decide(attempt, err)
                assertIs<Schedule.Decision.Continue>(decision)
                val expected = base * (attempt + 1)
                assertEquals(expected, decision.delay,
                    "linear at attempt $attempt with base $base")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // zipWith: custom delay merge
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zipWith applies custom delay merge`() = runTest {
        checkAll(Arb.long(1L..500L), Arb.long(1L..500L)) { d1, d2 ->
            val s1 = Schedule.spaced<Throwable>(d1.milliseconds)
            val s2 = Schedule.spaced<Throwable>(d2.milliseconds)
            // Sum delays instead of max/min
            val combined = s1.zipWith(s2) { a, b -> a + b }

            val decision = combined.decide(0, err)
            assertIs<Schedule.Decision.Continue>(decision)
            assertEquals((d1 + d2).milliseconds, decision.delay)
        }
    }
}
