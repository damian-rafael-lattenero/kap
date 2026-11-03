package kap

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
 * `Supervisor`: named supervision (SPEC-5.0 milestone 5.0), proven under
 * virtual time. Bodies use `delay` only, so every schedule below is exact.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SupervisorTest {

    @Test
    fun `OneForOne restarts only the failed child and leaves siblings running`() = runTest {
        var failingRuns = 0
        var siblingInvocations = 0
        var siblingTicks = 0
        val group = Supervisor(
            children = listOf(
                Supervisor.child<Unit>("flaky") {
                    failingRuns += 1
                    delay(10.milliseconds)
                    throw IllegalStateException("boom-$failingRuns")
                },
                Supervisor.child("steady") {
                    siblingInvocations += 1
                    while (isActive) {
                        siblingTicks += 1
                        delay(20.milliseconds)
                    }
                },
            ),
            strategy = SupervisionStrategy.OneForOne,
            restartPolicy = RestartPolicy.constant(50.milliseconds, maxRestarts = 10),
        )
        val handle = group.startIn(this)

        // Failures at t=10, 70, 130, 190 restart at +50 ms each: by t=200, three restarts ran.
        advanceTimeBy(200.milliseconds)
        runCurrent()

        assertEquals(3, handle.restarts["flaky"]!!.value)
        assertEquals(0, handle.restarts["steady"]!!.value, "OneForOne must never restart a sibling")
        assertEquals(1, siblingInvocations, "the sibling body must not re-run")
        assertTrue(siblingTicks > 5, "the sibling kept running while the other child restarted")
        assertEquals(4, failingRuns, "initial run plus three restarts")
        assertTrue(handle.isAlive)

        handle.stop()
        assertFailsWith<SupervisorStoppedException> { handle.await() }
        assertFalse(handle.isAlive)
    }

    @Test
    fun `AllForOne restarts every child of the group`() = runTest {
        var aRuns = 0
        var bRuns = 0
        val group = Supervisor(
            children = listOf(
                Supervisor.child<Unit>("a") {
                    aRuns += 1
                    if (aRuns == 1) {
                        delay(10.milliseconds)
                        throw IllegalStateException("first-run")
                    }
                    while (isActive) delay(50.milliseconds)
                },
                Supervisor.child("b") {
                    bRuns += 1
                    while (isActive) delay(50.milliseconds)
                },
            ),
            strategy = SupervisionStrategy.AllForOne,
            restartPolicy = RestartPolicy.constant(40.milliseconds, maxRestarts = 5),
        )
        val handle = group.startIn(this)

        advanceTimeBy(100.milliseconds)   // failure at t=10, group delay 40, relaunch at t=50
        runCurrent()

        assertEquals(2, aRuns, "the failed child re-ran")
        assertEquals(2, bRuns, "the sibling re-ran as part of the group restart")
        assertEquals(1, handle.restarts["a"]!!.value)
        assertEquals(1, handle.restarts["b"]!!.value, "AllForOne counts a restart for every child")

        handle.stop()
        assertFailsWith<SupervisorStoppedException> { handle.await() }
    }

    @Test
    fun `AllForOne does not resurrect a child that already completed normally`() = runTest {
        var flakyRuns = 0
        var doneRuns = 0
        val group = Supervisor(
            children = listOf(
                Supervisor.child<Unit>("flaky") {
                    flakyRuns += 1
                    delay(10.milliseconds)
                    throw IllegalStateException("always")
                },
                Supervisor.child("done") {
                    doneRuns += 1
                    "finished"
                },
            ),
            strategy = SupervisionStrategy.AllForOne,
            restartPolicy = RestartPolicy.constant(20.milliseconds, maxRestarts = 10),
        )
        val handle = group.startIn(this)

        advanceTimeBy(100.milliseconds)
        runCurrent()

        assertTrue(flakyRuns > 1, "the running child restarted")
        assertEquals(1, doneRuns, "normal completion is terminal: the completed child is not resurrected")
        assertEquals(0, handle.restarts["done"]!!.value)
        assertTrue(handle.restarts["flaky"]!!.value > 0)

        handle.stop()
        assertFailsWith<SupervisorStoppedException> { handle.await() }
    }

    @Test
    fun `exhaustion fails the group with the child's last cause`() = runTest {
        var runs = 0
        val handle = Supervisor(
            children = listOf(
                Supervisor.child<Unit>("always-fails") {
                    runs += 1
                    throw IllegalStateException("boom-$runs")
                },
            ),
            strategy = SupervisionStrategy.OneForOne,
            restartPolicy = RestartPolicy.constant(20.milliseconds, maxRestarts = 2),
        ).startIn(this)

        val exhausted = assertFailsWith<SupervisorExhaustedException> { handle.await() }
        assertEquals(3, runs, "initial run plus two restarts")
        assertEquals(2, handle.restarts["always-fails"]!!.value)
        assertEquals("always-fails", exhausted.child)
        assertEquals(2, exhausted.restarts)
        assertEquals("boom-3", exhausted.cause?.message, "the last failure is the cause")
        assertFalse(handle.isAlive)
    }

    @Test
    fun `the group completes when every child completes normally`() = runTest {
        var aRuns = 0
        var bRuns = 0
        val handle = Supervisor(
            children = listOf(
                Supervisor.child("a") { aRuns += 1; delay(10.milliseconds); "a" },
                Supervisor.child("b") { bRuns += 1; delay(20.milliseconds); "b" },
            ),
            strategy = SupervisionStrategy.OneForOne,
            restartPolicy = RestartPolicy.constant(1.seconds),
        ).startIn(this)

        handle.await()   // returns, no exception
        assertEquals(1, aRuns)
        assertEquals(1, bRuns)
        assertEquals(0, handle.restarts.getValue("a").value)
        assertEquals(0, handle.restarts.getValue("b").value)
        assertFalse(handle.isAlive)
    }

    @Test
    fun `stop is idempotent, cancels every child and never restarts`() = runTest {
        var finallyRan = 0
        fun body(name: String) = Supervisor.child<Unit>(name) {
            try {
                while (isActive) delay(1.seconds)
            } finally {
                finallyRan += 1
            }
        }
        val handle = Supervisor(
            children = listOf(body("a"), body("b")),
            strategy = SupervisionStrategy.AllForOne,
            restartPolicy = RestartPolicy.constant(1.seconds, maxRestarts = 5),
        ).startIn(this)

        delay(100.milliseconds)
        handle.stop()
        handle.stop()
        assertFailsWith<SupervisorStoppedException> { handle.await() }
        assertEquals(2, finallyRan, "both bodies were cancelled, not abandoned")
        assertEquals(0, handle.restarts.getValue("a").value)
        assertEquals(0, handle.restarts.getValue("b").value)
        assertFalse(handle.isAlive)
    }

    @Test
    fun `stop during the restart delay prevents the next run`() = runTest {
        var runs = 0
        val handle = Supervisor(
            children = listOf(
                Supervisor.child<Unit>("flaky") {
                    runs += 1
                    throw IllegalStateException("x")
                },
            ),
            strategy = SupervisionStrategy.OneForOne,
            restartPolicy = RestartPolicy.constant(1.seconds, maxRestarts = 10),
        ).startIn(this)

        delay(500.milliseconds)   // the failure happened at t=0; the restart is due at t=1 s
        handle.stop()
        delay(5.seconds)          // nothing may restart now
        assertEquals(1, runs)
        assertEquals(0, handle.restarts.getValue("flaky").value)
        assertFailsWith<SupervisorStoppedException> { handle.await() }
    }

    @Test
    fun `cancellation of the owning scope ends the group without restarts`() = runTest {
        var runs = 0
        val scopeJob = Job()
        val groupScope = CoroutineScope(coroutineContext + scopeJob)
        val handle = Supervisor(
            children = listOf(
                Supervisor.child<Unit>("flaky") {
                    runs += 1
                    throw IllegalStateException("x")
                },
            ),
            strategy = SupervisionStrategy.OneForOne,
            restartPolicy = RestartPolicy.constant(1.seconds, maxRestarts = 10),
        ).startIn(groupScope)

        delay(100.milliseconds)   // failure at t=0, inside the restart delay
        scopeJob.cancel()
        delay(2.seconds)
        assertEquals(1, runs)
        assertEquals(0, handle.restarts.getValue("flaky").value)
        assertFailsWith<CancellationException> { handle.await() }
    }

    @Test
    fun `isAlive is honest - true during a restart delay, false once the group ends`() = runTest {
        val handle = Supervisor(
            children = listOf(
                Supervisor.child<Unit>("flaky") {
                    delay(10.milliseconds)
                    throw IllegalStateException("x")
                },
            ),
            strategy = SupervisionStrategy.OneForOne,
            restartPolicy = RestartPolicy.constant(1.seconds, maxRestarts = 1),
        ).startIn(this)

        delay(500.milliseconds)   // the child failed at t=10; the group waits out its backoff
        assertTrue(handle.isAlive, "the group exists while it waits to restart")

        advanceTimeBy(2.seconds)  // restart, fail again, exhaust
        runCurrent()
        assertFailsWith<SupervisorExhaustedException> { handle.await() }
        assertFalse(handle.isAlive)
    }

    @Test
    fun `AllForOne coalesces simultaneous failures into a single group restart`() = runTest {
        var aRuns = 0
        var bRuns = 0
        val handle = Supervisor(
            children = listOf(
                Supervisor.child<Unit>("a") { aRuns += 1; delay(10.milliseconds); throw IllegalStateException("a") },
                Supervisor.child<Unit>("b") { bRuns += 1; delay(10.milliseconds); throw IllegalStateException("b") },
            ),
            strategy = SupervisionStrategy.AllForOne,
            restartPolicy = RestartPolicy.constant(50.milliseconds, maxRestarts = 1),
        ).startIn(this)

        advanceTimeBy(65.milliseconds)   // both fail at t=10, one group restart at t=60
        runCurrent()

        assertEquals(2, aRuns)
        assertEquals(2, bRuns)
        assertEquals(1, handle.restarts.getValue("a").value, "one group restart, not one per failure")
        assertEquals(1, handle.restarts.getValue("b").value)

        assertFailsWith<SupervisorExhaustedException> { handle.await() }
    }

    @Test
    fun `child names are unique and non-blank`() {
        val policy = RestartPolicy.constant(1.milliseconds)
        assertFailsWith<IllegalArgumentException> {
            Supervisor(listOf(Supervisor.child("a") { 1 }, Supervisor.child("a") { 2 }), SupervisionStrategy.OneForOne, policy)
        }
        assertFailsWith<IllegalArgumentException> {
            Supervisor(listOf(Supervisor.child(" ") { 1 }), SupervisionStrategy.OneForOne, policy)
        }
        assertFailsWith<IllegalArgumentException> {
            Supervisor(emptyList(), SupervisionStrategy.OneForOne, policy)
        }
    }
}
