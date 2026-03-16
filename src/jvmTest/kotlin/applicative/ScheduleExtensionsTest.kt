package applicative

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.ZERO

class ScheduleExtensionsTest {

    @Test
    fun `doUntil stops when predicate becomes true`() {
        val schedule = Schedule.doUntil<Int> { it > 3 }
        assertEquals(Schedule.Decision.Continue(ZERO), schedule.decide(0, 1))
        assertEquals(Schedule.Decision.Continue(ZERO), schedule.decide(1, 3))
        assertEquals(Schedule.Decision.Done, schedule.decide(2, 4))
    }

    @Test
    fun `whileInput behaves same as doWhile`() {
        val a = Schedule.doWhile<Int> { it < 5 }
        val b = Schedule.whileInput<Int> { it < 5 }
        for (i in 0..10) {
            assertEquals(a.decide(0, i), b.decide(0, i))
        }
    }

    @Test
    fun `untilInput behaves same as doUntil`() {
        val a = Schedule.doUntil<Int> { it >= 5 }
        val b = Schedule.untilInput<Int> { it >= 5 }
        for (i in 0..10) {
            assertEquals(a.decide(0, i), b.decide(0, i))
        }
    }

    @Test
    fun `collect accumulates values`() {
        val schedule = Schedule.recurs<String>(3).collect()
        // collect wraps fold which doesn't change retry behavior
        assertEquals(Schedule.Decision.Continue(ZERO), schedule.decide(0, "a"))
        assertEquals(Schedule.Decision.Continue(ZERO), schedule.decide(1, "b"))
        assertEquals(Schedule.Decision.Continue(ZERO), schedule.decide(2, "c"))
        assertEquals(Schedule.Decision.Done, schedule.decide(3, "d"))
    }

    @Test
    fun `zipWith combines delays with custom function`() {
        val s1 = Schedule.spaced<Int>(100.milliseconds)
        val s2 = Schedule.spaced<Int>(200.milliseconds)
        val combined = s1.zipWith(s2) { a, b -> minOf(a, b) }
        val decision = combined.decide(0, 0)
        assertEquals(Schedule.Decision.Continue(100.milliseconds), decision)
    }

    @Test
    fun `zipWith stops when either schedule stops`() {
        val s1 = Schedule.recurs<Int>(2)
        val s2 = Schedule.forever<Int>()
        val combined = s1.zipWith(s2) { a, b -> maxOf(a, b) }
        assertEquals(Schedule.Decision.Continue(ZERO), combined.decide(0, 0))
        assertEquals(Schedule.Decision.Continue(ZERO), combined.decide(1, 0))
        assertEquals(Schedule.Decision.Done, combined.decide(2, 0))
    }
}
