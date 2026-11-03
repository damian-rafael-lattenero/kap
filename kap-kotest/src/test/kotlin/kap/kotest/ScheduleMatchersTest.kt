package kap.kotest

import kap.Schedule
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ScheduleMatchersTest {

    private val schedule = Schedule.times<String>(3)

    @Test
    fun `shouldContinue passes on Continue without expected delay`() {
        schedule.decideAt(2, "input").shouldContinue()
    }

    @Test
    fun `shouldContinue passes on exact delay`() {
        Schedule.spaced<String>(100.milliseconds)
            .decideAt(0, "input")
            .shouldContinue(100.milliseconds)
    }

    @Test
    fun `shouldContinue accepts any delay when null`() {
        Schedule.exponential<String>(2.seconds)
            .decideAt(3, "input")
            .shouldContinue(null)
    }

    @Test
    fun `shouldContinue fails on delay mismatch`() {
        assertFailsWith<AssertionError> {
            Schedule.spaced<String>(100.milliseconds)
                .decideAt(0, "input")
                .shouldContinue(500.milliseconds)
        }
    }

    @Test
    fun `shouldContinue fails on Done`() {
        assertFailsWith<AssertionError> {
            schedule.decideAt(3, "input").shouldContinue()
        }
    }

    @Test
    fun `shouldStop passes on Done`() {
        schedule.decideAt(3, "input").shouldStop()
    }

    @Test
    fun `shouldStop fails on Continue`() {
        assertFailsWith<AssertionError> {
            schedule.decideAt(2, "input").shouldStop()
        }
    }

    @Test
    fun `shouldStop is strict about an infinite Continue`() {
        val decision: Schedule.Decision = Schedule.Decision.Continue(Duration.INFINITE)
        assertFailsWith<AssertionError> {
            decision.shouldStop()
        }
    }

    @Test
    fun `shouldContinue matches an infinite delay explicitly`() {
        val decision: Schedule.Decision = Schedule.Decision.Continue(Duration.INFINITE)
        decision.shouldContinue(Duration.INFINITE)
    }

    @Test
    fun `semiring composition decisions can be matched`() {
        val combined = Schedule.times<String>(3) and Schedule.spaced(100.milliseconds)
        combined.decideAt(2, "input").shouldContinue(100.milliseconds)
        combined.decideAt(3, "input").shouldStop()
    }
}
