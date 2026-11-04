package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

/**
 * The `Schedule.debounce` laws — the quiet-window stop condition (the lawful
 * home of agent-brain's interim `drainUntilQuiet` fold).
 *
 * For a fresh instance and the input sequence `a0, a1, ...`:
 *
 * 1. **Stop at the filled window.** The decision at attempt `i` is
 *    [Schedule.Decision.Done] exactly when the run of consecutive quiet inputs
 *    ending at `ai` is `>= n`.
 * 2. **Reset.** A non-quiet input resets the run to zero.
 * 3. **Delay neutrality.** Every [Schedule.Decision.Continue] carries
 *    [ZERO] — `cadence and debounce(n)` keeps the cadence's delays exactly.
 * 4. **Boundaries.** `debounce(0)` decides like [Schedule.never];
 *    `debounce(1)` decides like [Schedule.doUntil].
 * 5. **Statefulness.** The run belongs to the instance: two fresh instances
 *    decide identically on the same sequence, and a used instance carries its
 *    run across calls (the documented caveat, pinned).
 *
 * Every check compares the actual decisions with an independent oracle
 * (a plain fold over the inputs), so the law is stated, not the implementation.
 * No sleeping: schedules are pure values and decisions are data.
 */
class ScheduleDebounceLawsTest {

    /** The oracle: the run of consecutive empty counts and its threshold. */
    private fun expectedDecisions(inputs: List<Int>, n: Int): List<Schedule.Decision> {
        var run = 0
        return inputs.map { count ->
            run = if (count == 0) run + 1 else 0
            if (run >= n) Schedule.Decision.Done else Schedule.Decision.Continue(ZERO)
        }
    }

    private fun Schedule<Int>.decisionsFor(inputs: List<Int>): List<Schedule.Decision> =
        inputs.mapIndexed { attempt, value -> decide(attempt, value) }

    // ── 1. Stop at the filled window ────────────────────────────────────

    @Test
    fun `the stop lands exactly on the n-th consecutive quiet poll`() = runTest {
        checkAll(Arb.int(1..6)) { n ->
            // n - 1 quiet polls are not enough; the n-th is the decision.
            val inputs = List(n - 1) { 0 } + listOf(0, 0)
            val decisions = Schedule.debounce(n).decisionsFor(inputs)

            assertEquals(
                List(n - 1) { Schedule.Decision.Continue(ZERO) } + Schedule.Decision.Done,
                decisions.take(n),
                "a window of $n must not close before the n-th quiet poll",
            )
        }
    }

    @Test
    fun `the decisions match the oracle for every window and sequence`() = runTest {
        checkAll(Arb.list(Arb.int(0..3), 0..20), Arb.int(0..5)) { counts, n ->
            val actual = Schedule.debounce(n).decisionsFor(counts)
            assertEquals(expectedDecisions(counts, n), actual, "window=$n counts=$counts")
        }
    }

    // ── 2. Reset ────────────────────────────────────────────────────────

    @Test
    fun `a non-quiet poll resets the run`() = runTest {
        val inputs = listOf(0, 0, 5, 0, 0, 0)
        val decisions = Schedule.debounce(3).decisionsFor(inputs)

        assertEquals(
            listOf(
                Schedule.Decision.Continue(ZERO),
                Schedule.Decision.Continue(ZERO),
                Schedule.Decision.Continue(ZERO), // the work poll restarts the window
                Schedule.Decision.Continue(ZERO),
                Schedule.Decision.Continue(ZERO),
                Schedule.Decision.Done, // the window fills only here
            ),
            decisions,
        )
    }

    // ── 3. Delay neutrality ─────────────────────────────────────────────

    @Test
    fun `every continue carries a zero delay`() = runTest {
        checkAll(Arb.list(Arb.int(0..3), 0..20), Arb.int(0..5)) { counts, n ->
            Schedule.debounce(n).decisionsFor(counts).forEach { decision ->
                if (decision is Schedule.Decision.Continue) {
                    assertEquals(ZERO, decision.delay, "the window is a stop condition, not a clock")
                }
            }
        }
    }

    @Test
    fun `a cadence composed with the window keeps the cadence delays exactly`() = runTest {
        val cadence = Schedule.spaced<Int>(500.milliseconds) and Schedule.debounce(3)
        val inputs = listOf(2, 0, 0, 0) // work, quiet, quiet, quiet: the window fills at attempt 3

        assertEquals(
            listOf(
                Schedule.Decision.Continue(500.milliseconds),
                Schedule.Decision.Continue(500.milliseconds),
                Schedule.Decision.Continue(500.milliseconds),
                Schedule.Decision.Done,
            ),
            cadence.decisionsFor(inputs),
            "and must keep max(cadence, ZERO) == cadence and stop with the window",
        )
    }

    // ── 4. Boundaries ───────────────────────────────────────────────────

    @Test
    fun `a window of zero decides exactly like never`() = runTest {
        checkAll(Arb.list(Arb.int(0..3), 0..10)) { counts ->
            assertEquals(
                Schedule.never<Int>().decisionsFor(counts),
                Schedule.debounce(0).decisionsFor(counts),
                "an empty window stops immediately: counts=$counts",
            )
        }
    }

    @Test
    fun `a window of one decides exactly like doUntil`() = runTest {
        checkAll(Arb.list(Arb.int(0..3), 0..10)) { counts ->
            assertEquals(
                Schedule.doUntil<Int> { it == 0 }.decisionsFor(counts),
                Schedule.debounce(1).decisionsFor(counts),
                "a singleton window is the first quiet poll: counts=$counts",
            )
        }
    }

    @Test
    fun `the shorthand reads a zero count as quiet and composes with an Int cadence`() = runTest {
        val schedule: Schedule<Int> = Schedule.debounce(2)
        val composed = Schedule.spaced<Int>(50.milliseconds) and schedule

        assertEquals(Schedule.Decision.Continue(50.milliseconds), composed.decide(0, 7))
        assertEquals(Schedule.Decision.Continue(50.milliseconds), composed.decide(1, 0))
        assertEquals(Schedule.Decision.Done, composed.decide(2, 0))
    }

    @Test
    fun `a negative window is rejected`() {
        assertFailsWith<IllegalArgumentException> { Schedule.debounce(-1) }
        assertFailsWith<IllegalArgumentException> { Schedule.debounce<Int>(-1) { it == 0 } }
    }

    // ── 5. Statefulness ─────────────────────────────────────────────────

    @Test
    fun `fresh instances decide identically and a used instance carries its run`() = runTest {
        val inputs = listOf(0, 0, 1, 0, 0, 0, 0)

        assertEquals(
            Schedule.debounce(3).decisionsFor(inputs),
            Schedule.debounce(3).decisionsFor(inputs),
            "the state belongs to the instance, never to the factory",
        )

        val used = Schedule.debounce(3)
        used.decisionsFor(inputs) // fills the window
        assertEquals(
            Schedule.Decision.Done,
            used.decide(inputs.size, 0),
            "a quiet input keeps the filled window",
        )
        assertEquals(
            Schedule.Decision.Continue(ZERO),
            used.decide(inputs.size + 1, 7),
            "but a work input restarts it — the state is carried: build one fresh instance per run",
        )
    }

    // ── The D2 unlock: the window ends a repeat loop ────────────────────

    @Test
    fun `the quiet window ends a repeat loop exactly like the hand-rolled drain`() = runTest {
        // The agent-brain drain scenario: polls [3, 4, 0, 2, 0, 0, 0, 99] with
        // a window of 3 stop after the 7th poll; the 99 is never seen.
        val script = ArrayDeque(listOf(3, 4, 0, 2, 0, 0, 0, 99))
        var calls = 0
        var total = 0

        Kap {
            val count = script.removeFirst()
            calls += 1
            total += count
            count
        }.repeat(Schedule.spaced<Int>(ZERO) and Schedule.debounce(3)).evalGraph()

        assertEquals(7, calls, "the loop stops at the filled quiet window")
        assertEquals(9, total, "the total is what the polls handed over")
    }
}
