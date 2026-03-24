package applicative

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Property-based tests verifying algebraic laws for [Schedule] composition.
 *
 * These laws ensure that combining schedules with [and], [or], and [jittered]
 * behaves predictably — enabling safe refactoring of retry policies.
 */
class ScheduleLawsTest {

    // ════════════════════════════════════════════════════════════════════════
    // AND LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `and is associative — (s1 and s2) and s3 == s1 and (s2 and s3)`() = runTest {
        val s1 = Schedule.times<Int>(5)
        val s2 = Schedule.spaced<Int>(100.milliseconds)
        val s3 = Schedule.spaced<Int>(200.milliseconds)

        checkAll(Arb.int(0..10), Arb.int()) { attempt, value ->
            val left = ((s1 and s2) and s3).decide(attempt, value)
            val right = (s1 and (s2 and s3)).decide(attempt, value)
            assertEquals(left, right, "and should be associative at attempt=$attempt")
        }
    }

    @Test
    fun `and is commutative for delay selection — s1 and s2 delay == s2 and s1 delay`() = runTest {
        val s1 = Schedule.spaced<Int>(100.milliseconds)
        val s2 = Schedule.spaced<Int>(200.milliseconds)

        checkAll(Arb.int(0..10), Arb.int()) { attempt, value ->
            val left = (s1 and s2).decide(attempt, value)
            val right = (s2 and s1).decide(attempt, value)
            assertEquals(left, right, "and should be commutative at attempt=$attempt")
        }
    }

    @Test
    fun `and with forever is identity for delay — s and forever == s (delays match)`() = runTest {
        val s = Schedule.exponential<Int>(100.milliseconds)
        val forever = Schedule.forever<Int>()

        checkAll(Arb.int(0..10), Arb.int()) { attempt, value ->
            val withForever = (s and forever).decide(attempt, value)
            val alone = s.decide(attempt, value)
            assertEquals(alone, withForever, "forever should not change and-composed schedule at attempt=$attempt")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // OR LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `or is associative — (s1 or s2) or s3 == s1 or (s2 or s3)`() = runTest {
        val s1 = Schedule.times<Int>(2)
        val s2 = Schedule.times<Int>(4)
        val s3 = Schedule.times<Int>(6)

        checkAll(Arb.int(0..10), Arb.int()) { attempt, value ->
            val left = ((s1 or s2) or s3).decide(attempt, value)
            val right = (s1 or (s2 or s3)).decide(attempt, value)
            assertEquals(left, right, "or should be associative at attempt=$attempt")
        }
    }

    @Test
    fun `or is commutative for delay selection — s1 or s2 delay == s2 or s1 delay`() = runTest {
        val s1 = Schedule.spaced<Int>(100.milliseconds)
        val s2 = Schedule.spaced<Int>(200.milliseconds)

        checkAll(Arb.int(0..10), Arb.int()) { attempt, value ->
            val left = (s1 or s2).decide(attempt, value)
            val right = (s2 or s1).decide(attempt, value)
            assertEquals(left, right, "or should be commutative at attempt=$attempt")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AND/OR INTERACTION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `and is more restrictive than or — if and says Done, or may still Continue`() {
        val short = Schedule.times<Int>(2)
        val long = Schedule.times<Int>(5)

        // At attempt=3: short says Done, long says Continue
        val andResult = (short and long).decide(3, 0)
        val orResult = (short or long).decide(3, 0)

        assertEquals(Schedule.Decision.Done, andResult, "and should stop when either stops")
        assertIsContinue(orResult, "or should continue when either continues")
    }

    @Test
    fun `and uses max delay, or uses min delay — verified with property`() = runTest {
        val s1 = Schedule.spaced<Int>(100.milliseconds)
        val s2 = Schedule.spaced<Int>(300.milliseconds)

        checkAll(Arb.int(0..10), Arb.int()) { attempt, value ->
            val andDecision = (s1 and s2).decide(attempt, value)
            val orDecision = (s1 or s2).decide(attempt, value)

            assertIsContinue(andDecision, "both spaced should always Continue")
            assertIsContinue(orDecision, "both spaced should always Continue")

            val andDelay = (andDecision as Schedule.Decision.Continue).delay
            val orDelay = (orDecision as Schedule.Decision.Continue).delay

            assertEquals(300.milliseconds, andDelay, "and should pick max delay")
            assertEquals(100.milliseconds, orDelay, "or should pick min delay")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // JITTERED LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `jittered preserves Continue vs Done decisions`() = runTest {
        val s = Schedule.times<Int>(3)

        checkAll(Arb.int(0..10), Arb.int()) { attempt, value ->
            val original = s.decide(attempt, value)
            val jittered = s.jittered().decide(attempt, value)

            when (original) {
                is Schedule.Decision.Done ->
                    assertEquals(Schedule.Decision.Done, jittered, "jittered should preserve Done at attempt=$attempt")
                is Schedule.Decision.Continue ->
                    assertIsContinue(jittered, "jittered should preserve Continue at attempt=$attempt")
            }
        }
    }

    @Test
    fun `jittered with factor 0 is identity`() = runTest {
        val s = Schedule.exponential<Int>(100.milliseconds)

        checkAll(Arb.int(0..8), Arb.int()) { attempt, value ->
            val original = s.decide(attempt, value)
            val jittered = s.jittered(factor = 0.0).decide(attempt, value)
            assertEquals(original, jittered, "jittered(0.0) should be identity at attempt=$attempt")
        }
    }

    @Test
    fun `jittered delays stay within bounds`() = runTest {
        val base = 100.milliseconds
        val factor = 0.5
        val s = Schedule.spaced<Int>(base).jittered(factor)

        checkAll(Arb.int(0..20), Arb.int()) { attempt, value ->
            val decision = s.decide(attempt, value)
            assertIsContinue(decision, "spaced.jittered should always Continue")
            val delay = (decision as Schedule.Decision.Continue).delay

            val min = base * (1.0 - factor)
            val max = base * (1.0 + factor)
            assert(delay >= min) { "delay $delay < min $min at attempt=$attempt" }
            assert(delay <= max) { "delay $delay > max $max at attempt=$attempt" }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // EXPONENTIAL LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `exponential delays grow monotonically`() {
        val s = Schedule.exponential<Int>(50.milliseconds)
        val err = 0

        var prev = 0.milliseconds
        repeat(10) { attempt ->
            val decision = s.decide(attempt, err)
            assertIsContinue(decision, "exponential should always Continue")
            val delay = (decision as Schedule.Decision.Continue).delay
            assert(delay >= prev) { "delay $delay < previous $prev at attempt=$attempt" }
            prev = delay
        }
    }

    @Test
    fun `exponential with max cap never exceeds max`() = runTest {
        val max = 500.milliseconds
        val s = Schedule.exponential<Int>(50.milliseconds, max = max)

        checkAll(Arb.int(0..20), Arb.int()) { attempt, value ->
            val decision = s.decide(attempt, value)
            assertIsContinue(decision, "exponential should always Continue")
            val delay = (decision as Schedule.Decision.Continue).delay
            assert(delay <= max) { "delay $delay > max $max at attempt=$attempt" }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // FIBONACCI LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fibonacci delays grow monotonically`() {
        val s = Schedule.fibonacci<Int>(50.milliseconds)
        val err = 0

        var prev = 0.milliseconds
        repeat(10) { attempt ->
            val decision = s.decide(attempt, err)
            assertIsContinue(decision, "fibonacci should always Continue")
            val delay = (decision as Schedule.Decision.Continue).delay
            assert(delay >= prev) { "delay $delay < previous $prev at attempt=$attempt" }
            prev = delay
        }
    }

    @Test
    fun `linear delays grow linearly`() = runTest {
        val base = 50.milliseconds
        val s = Schedule.linear<Int>(base)

        checkAll(Arb.int(0..20)) { attempt ->
            val decision = s.decide(attempt, 0)
            assertIsContinue(decision, "linear should always Continue")
            val delay = (decision as Schedule.Decision.Continue).delay
            val expected = base * (attempt + 1)
            assertEquals(expected, delay, "linear delay at attempt=$attempt")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // RECURS BOUNDARY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recurs(0) never retries`() = runTest {
        val s = Schedule.times<Int>(0)
        checkAll(Arb.int()) { value ->
            assertEquals(Schedule.Decision.Done, s.decide(0, value))
        }
    }

    @Test
    fun `recurs(n) continues for exactly n attempts then stops`() = runTest {
        checkAll(Arb.int(1..20)) { n ->
            val s = Schedule.times<Int>(n)
            repeat(n) { attempt ->
                assertIsContinue(s.decide(attempt, 0), "should Continue at attempt=$attempt for recurs($n)")
            }
            assertEquals(Schedule.Decision.Done, s.decide(n, 0), "should Done at attempt=$n for recurs($n)")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════

    private fun assertIsContinue(decision: Schedule.Decision, message: String = "") {
        assert(decision is Schedule.Decision.Continue) {
            "$message — expected Continue, got $decision"
        }
    }
}
