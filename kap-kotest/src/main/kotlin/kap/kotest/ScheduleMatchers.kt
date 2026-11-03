package kap.kotest

import kap.Schedule
import kotlin.time.Duration

/**
 * Assert that a [Schedule.Decision] is [Schedule.Decision.Continue], optionally
 * with the expected delay.
 *
 * ```
 * val policy = Schedule.times<String>(3)
 * policy.decideAt(2, "input").shouldContinue()          // any delay
 * policy.decideAt(2, "input").shouldContinue(ZERO)      // exact delay
 * ```
 *
 * The delay check is exact ([Duration] equality): `shouldContinue(100.milliseconds)`
 * fails on a Continue carrying 99 ms. Pass `null` (the default) to accept any delay.
 *
 * @param expectedDelay the delay the decision must carry, or `null` to accept any.
 */
fun Schedule.Decision.shouldContinue(expectedDelay: Duration? = null) {
    val delay = (this as? Schedule.Decision.Continue)?.delay
    assert(delay != null) {
        "Expected Decision.Continue" +
                (expectedDelay?.let { "($it)" }.orEmpty()) +
                " but was $this"
    }
    if (expectedDelay != null) {
        assert(delay == expectedDelay) {
            "Expected Decision.Continue($expectedDelay) but was Decision.Continue($delay)"
        }
    }
}

/**
 * Assert that a [Schedule.Decision] is [Schedule.Decision.Done] — the schedule
 * stops retrying.
 *
 * ```
 * val policy = Schedule.times<String>(3)
 * policy.decideAt(3, "input").shouldStop()
 * ```
 *
 * Strict by design: a [Schedule.Decision.Continue] carrying [Duration.INFINITE]
 * is *observationally* a stop (a retry that waits forever stops — see LAWS.md),
 * but this matcher still fails on it. For the observational reading, assert the
 * delay explicitly: `decision.shouldContinue(Duration.INFINITE)`.
 */
fun Schedule.Decision.shouldStop() {
    assert(this is Schedule.Decision.Done) {
        "Expected Decision.Done but was $this"
    }
}
