package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The Process laws (SPEC-5.0 milestone 4.2). The convergence law — restart
 * policies never hot-loop — is property-checked here on the pure decision
 * function; the lifecycle laws (terminal success, stop/cancellation never
 * failures, bounded restarts) are proven under virtual time in
 * [ProcessTest] and the bounded-restart law below.
 */
class ProcessLawsTest {

    private val failure = IllegalStateException("law")

    @Test
    fun `convergence - every restart waits a strictly positive delay`() = runTest {
        checkAll(Arb.int(1..10_000), Arb.int(0..50)) { baseMs, maxRestarts ->
            val policy = RestartPolicy.exponential(baseMs.milliseconds, maxRestarts = maxRestarts)
            (0 until policy.maxRestarts).forEach { n ->
                val d = policy.schedule.decide(n, failure)
                assertTrue(
                    d is Schedule.Decision.Continue && d.delay > ZERO,
                    "exponential restart delay must be strictly positive (base=$baseMs, n=$n)",
                )
            }
        }
        checkAll(Arb.int(1..10_000), Arb.int(0..50)) { ms, maxRestarts ->
            val policy = RestartPolicy.constant(ms.milliseconds, maxRestarts = maxRestarts)
            (0 until policy.maxRestarts).forEach { n ->
                val d = policy.schedule.decide(n, failure)
                assertTrue(
                    d is Schedule.Decision.Continue && d.delay > ZERO,
                    "constant restart delay must be strictly positive (delay=$ms, n=$n)",
                )
            }
        }
    }

    @Test
    fun `convergence - unlawful policies cannot be expressed`() {
        assertFailsWith<IllegalArgumentException> { RestartPolicy.constant(ZERO) }
        assertFailsWith<IllegalArgumentException> { RestartPolicy.exponential(ZERO) }
        assertFailsWith<IllegalArgumentException> { RestartPolicy.constant(1.milliseconds, maxRestarts = -1) }
    }

    @Test
    fun `exponential backoff is non-decreasing for factor of at least 1`() = runTest {
        checkAll(Arb.int(1..5_000), Arb.int(0..30)) { baseMs, maxRestarts ->
            val policy = RestartPolicy.exponential(baseMs.milliseconds, maxRestarts = maxRestarts)
            var previous = ZERO
            (0 until policy.maxRestarts).forEach { n ->
                val d = policy.schedule.decide(n, failure) as Schedule.Decision.Continue
                assertTrue(d.delay >= previous, "exponential delay must not decrease (n=$n)")
                previous = d.delay
            }
        }
    }

    @Test
    fun `exponential backoff respects its max cap`() = runTest {
        checkAll(Arb.int(1..1_000), Arb.int(0..40)) { baseMs, n ->
            val policy = RestartPolicy.exponential(baseMs.milliseconds, max = 1.seconds, maxRestarts = 100)
            val d = policy.schedule.decide(n, failure) as Schedule.Decision.Continue
            assertTrue(d.delay <= 1.seconds, "delay must respect the cap (n=$n)")
        }
    }

    @Test
    fun `constant policy waits the same delay every restart`() = runTest {
        checkAll(Arb.int(1..5_000), Arb.int(0..40)) { ms, n ->
            val policy = RestartPolicy.constant(ms.milliseconds, maxRestarts = n + 1)
            val d = policy.schedule.decide(n, failure) as Schedule.Decision.Continue
            assertEquals(ms.milliseconds, d.delay)
        }
    }

    @Test
    fun `bounded - an instantly failing body runs exactly maxRestarts plus one times`() = runTest {
        listOf(0, 1, 3, 7).forEach { cap ->
            var runs = 0
            val handle = Kap { runs += 1; throw failure }
                .process(
                    "bounded-$cap",
                    restart = RestartPolicy.constant(1.milliseconds, maxRestarts = cap),
                    heartbeatEvery = ZERO,
                )
                .startIn(this)
            assertFailsWith<ProcessExhaustedException> { handle.await() }
            assertEquals(cap + 1, runs, "executions must equal cap+1 (cap=$cap)")
            assertEquals(cap, handle.restarts.value, "restarts must equal the cap (cap=$cap)")
        }
    }
}
