package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The Supervisor laws (SPEC-5.0 milestone 5.0), property-checked under
 * virtual time:
 *
 * 1. **OneForOne isolates**: a failure restarts the failed child only; every
 *    sibling's counter stays at zero and its body never re-runs.
 * 2. **AllForOne is total**: any failure restarts every child of the group
 *    (the failed one included), and exactly one group restart happens per
 *    failure cycle — simultaneous failures do not multiply restarts.
 * 3. **Stop and cancellation never restart**: neither counts as a failure,
 *    whatever the strategy and the restart budget.
 * 4. **Exhaustion names the truth**: the group fails with the last failure as
 *    cause after exactly `maxRestarts` restarts.
 * 5. **Liveness is honest**: `isAlive` is true exactly while the group exists
 *    (including during the backoff wait) and false after any terminal outcome.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SupervisorLawsTest {

    // ── 1. OneForOne isolation ───────────────────────────────────────────

    @Test
    fun `OneForOne leaves every sibling restart counter at zero`() = runTest {
        checkAll(Arb.int(1..3), Arb.int(1..3)) { siblings, restarts ->
            var flakyRuns = 0
            var siblingRuns = 0
            val policy = RestartPolicy.constant(100.milliseconds, maxRestarts = 20)
            val children = buildList {
                add(
                    Supervisor.child<Unit>("flaky") {
                        flakyRuns += 1
                        delay(1.milliseconds)
                        throw IllegalStateException("boom-$flakyRuns")
                    },
                )
                repeat(siblings) { index ->
                    add(
                        Supervisor.child("steady-$index") {
                            siblingRuns += 1
                            while (isActive) delay(20.milliseconds)
                        },
                    )
                }
            }
            val handle = Supervisor(children, SupervisionStrategy.OneForOne, policy).startIn(this@runTest)

            // Body delay 1 ms + policy delay 100 ms per cycle: n*101 ms is exactly n restarts.
            advanceTimeBy((restarts * 101).milliseconds)
            runCurrent()

            assertEquals(restarts, handle.restarts.getValue("flaky").value)
            children.drop(1).forEach { child ->
                assertEquals(0, handle.restarts.getValue(child.name).value, "sibling ${child.name} restarted")
            }
            assertEquals(siblings, siblingRuns, "each sibling body ran exactly once")
            assertEquals(restarts + 1, flakyRuns)

            handle.stop()
            assertFailsWith<SupervisorStoppedException> { handle.await() }
        }
    }

    // ── 2. AllForOne totality ────────────────────────────────────────────

    @Test
    fun `AllForOne restarts every child of the group together`() = runTest {
        checkAll(Arb.int(2..4)) { size ->
            var groupRuns = 0
            val bodyRuns = IntArray(size)
            val children = List(size) { index ->
                Supervisor.child<Unit>("c$index") {
                    bodyRuns[index] += 1
                    if (index == 0 && bodyRuns[index] == 1) {
                        delay(10.milliseconds)
                        throw IllegalStateException("first cycle failure")
                    }
                    groupRuns += 1
                    while (isActive) delay(50.milliseconds)
                }
            }
            val handle = Supervisor(
                children,
                SupervisionStrategy.AllForOne,
                RestartPolicy.constant(40.milliseconds, maxRestarts = 5),
            ).startIn(this@runTest)

            advanceTimeBy(150.milliseconds)
            runCurrent()

            children.forEach { child ->
                assertEquals(1, handle.restarts.getValue(child.name).value, "${child.name} restarted")
            }
            bodyRuns.forEachIndexed { index, runs ->
                assertEquals(2, runs, "child c$index re-ran after the group restart")
            }
            assertTrue(groupRuns >= size, "every child re-entered its body")

            handle.stop()
            assertFailsWith<SupervisorStoppedException> { handle.await() }
        }
    }

    // ── 3. Stop and cancellation never restart ───────────────────────────

    @Test
    fun `stop never restarts, whatever the strategy or the budget`() = runTest {
        checkAll(Arb.int(0..1), Arb.int(1..3)) { strategyIndex, maxRestarts ->
            val strategy =
                if (strategyIndex == 0) SupervisionStrategy.OneForOne else SupervisionStrategy.AllForOne
            var runs = 0
            val handle = Supervisor(
                listOf(
                    Supervisor.child<Unit>("flaky") {
                        runs += 1
                        throw IllegalStateException("x")
                    },
                ),
                strategy,
                RestartPolicy.constant(1.seconds, maxRestarts = maxRestarts),
            ).startIn(this@runTest)

            delay(1.milliseconds)   // the failure happened; the group is inside its backoff
            handle.stop()
            handle.stop()
            delay(5.seconds)         // nothing may restart now

            assertEquals(1, runs)
            assertEquals(0, handle.restarts.getValue("flaky").value)
            assertFalse(handle.isAlive)
            assertFailsWith<SupervisorStoppedException> { handle.await() }
        }
    }

    @Test
    fun `scope cancellation never restarts, whatever the strategy`() = runTest {
        checkAll(Arb.int(0..1)) { strategyIndex ->
            val strategy =
                if (strategyIndex == 0) SupervisionStrategy.OneForOne else SupervisionStrategy.AllForOne
            var runs = 0
            val scopeJob = Job()
            val groupScope = CoroutineScope(coroutineContext + scopeJob)
            val handle = Supervisor(
                listOf(
                    Supervisor.child<Unit>("flaky") {
                        runs += 1
                        throw IllegalStateException("x")
                    },
                ),
                strategy,
                RestartPolicy.constant(1.seconds, maxRestarts = 5),
            ).startIn(groupScope)

            delay(1.milliseconds)
            scopeJob.cancel()
            delay(5.seconds)

            assertEquals(1, runs)
            assertEquals(0, handle.restarts.getValue("flaky").value)
            assertFailsWith<CancellationException> { handle.await() }
        }
    }

    // ── 4. Exhaustion names the truth ────────────────────────────────────

    @Test
    fun `exhaustion reports exactly maxRestarts restarts and the last failure`() = runTest {
        checkAll(Arb.int(0..3)) { maxRestarts ->
            var runs = 0
            val handle = Supervisor(
                listOf(
                    Supervisor.child<Unit>("always-fails") {
                        runs += 1
                        throw IllegalStateException("boom-$runs")
                    },
                ),
                SupervisionStrategy.OneForOne,
                RestartPolicy.constant(10.milliseconds, maxRestarts = maxRestarts),
            ).startIn(this@runTest)

            val exhausted = assertFailsWith<SupervisorExhaustedException> { handle.await() }

            assertEquals(maxRestarts + 1, runs)
            assertEquals(maxRestarts, handle.restarts.getValue("always-fails").value)
            assertEquals("always-fails", exhausted.child)
            assertEquals(maxRestarts, exhausted.restarts)
            assertEquals("boom-${maxRestarts + 1}", exhausted.cause?.message)
        }
    }

    // ── 5. Liveness is honest ────────────────────────────────────────────

    @Test
    fun `isAlive is true during the backoff and false after every terminal outcome`() = runTest {
        checkAll(Arb.int(1..3)) { maxRestarts ->
            var runs = 0
            val handle = Supervisor(
                listOf(
                    Supervisor.child<Unit>("flaky") {
                        runs += 1
                        delay(1.milliseconds)
                        throw IllegalStateException("x")
                    },
                ),
                SupervisionStrategy.OneForOne,
                RestartPolicy.constant(100.milliseconds, maxRestarts = maxRestarts),
            ).startIn(this@runTest)

            delay(50.milliseconds)   // failed at 1 ms, backoff until 101 ms
            assertTrue(handle.isAlive, "the group exists while it waits to restart")
            assertEquals(0, handle.restarts.getValue("flaky").value, "no restart has run yet")

            assertFailsWith<SupervisorExhaustedException> { handle.await() }
            assertFalse(handle.isAlive)
        }
    }
}
