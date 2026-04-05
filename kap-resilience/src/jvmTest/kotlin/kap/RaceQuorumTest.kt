package kap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class RaceQuorumTest {

    @Test
    fun `quorum of 1 from 3 returns first success`() = runTest {
        val result = raceQuorum(
            required = 1,
            Kap { delay(100); "slow" },
            Kap { delay(10); "fast" },
            Kap { delay(50); "medium" },
        ).evalGraph()

        assertEquals(1, result.size)
        assertEquals("fast", result[0])
        assertEquals(10, currentTime)
    }

    @Test
    fun `quorum of 2 from 3 returns two fastest successes`() = runTest {
        val result = raceQuorum(
            required = 2,
            Kap { delay(100); "slow" },
            Kap { delay(10); "fast" },
            Kap { delay(50); "medium" },
        ).evalGraph()

        assertEquals(2, result.size)
        assertTrue(result.contains("fast"))
        assertTrue(result.contains("medium"))
        assertEquals(50, currentTime)
    }

    @Test
    fun `quorum of 3 from 3 waits for all — same as sequence`() = runTest {
        val result = raceQuorum(
            required = 3,
            Kap { delay(10); "A" },
            Kap { delay(50); "B" },
            Kap { delay(30); "C" },
        ).evalGraph()

        assertEquals(3, result.size)
        assertTrue(result.containsAll(listOf("A", "B", "C")))
        assertEquals(50, currentTime, "required==size must wait for slowest (equivalent to sequence)")
    }

    @Test
    fun `quorum with required equals size and one failure throws`() = runTest {
        val error = assertFailsWith<RuntimeException> {
            val r: List<String> =             raceQuorum(
                required = 3,
                Kap { delay(10); "A" },
                Kap<String> { delay(20); throw RuntimeException("fail") },
                Kap { delay(30); "C" },
            ).evalGraph()
        }
        assertTrue(error.message == "fail")
    }

    @Test
    fun `quorum tolerates failures up to allowed limit`() = runTest {
        val result = raceQuorum(
            required = 2,
            Kap { delay(10); throw RuntimeException("fail-1") },
            Kap { delay(20); "B" },
            Kap { delay(30); "C" },
            Kap { delay(40); "D" },
        ).evalGraph()

        assertEquals(2, result.size)
        assertEquals("B", result[0])
        assertEquals("C", result[1])
        assertEquals(30, currentTime)
    }

    @Test
    fun `quorum fails when too many computations fail`() = runTest {
        val error = assertFailsWith<RuntimeException> {
            val r: List<String> =             raceQuorum(
                required = 2,
                Kap { delay(10); throw RuntimeException("fail-1") },
                Kap { delay(20); throw RuntimeException("fail-2") },
                Kap { delay(30); "C" },
            ).evalGraph()
        }

        // The last failure is thrown. Prior failures may be suppressed.
        assertTrue(error.message == "fail-1" || error.message == "fail-2",
            "Expected one of the failure messages, got: ${error.message}")
    }

    @Test
    fun `quorum of 1 from 1 returns the single result`() = runTest {
        val result = raceQuorum(
            required = 1,
            Kap { delay(10); "only" },
        ).evalGraph()

        assertEquals(listOf("only"), result)
    }

    @Test
    fun `quorum validates required parameter`() {
        assertFailsWith<IllegalArgumentException> {
            raceQuorum(required = 0, Kap { "a" })
        }
        assertFailsWith<IllegalArgumentException> {
            raceQuorum(required = 3, Kap { "a" }, Kap { "b" })
        }
    }

    @Test
    fun `quorum cancels remaining after reaching required count`() = runTest {
        var slowCancelled = false
        val result = raceQuorum(
            required = 2,
            Kap { delay(10); "fast" },
            Kap { delay(20); "medium" },
            Kap {
                try {
                    delay(1000); "slow"
                } catch (e: kotlinx.coroutines.CancellationException) {
                    slowCancelled = true
                    throw e
                }
            },
        ).evalGraph()

        assertEquals(2, result.size)
        assertTrue(slowCancelled, "Slow computation should have been cancelled")
        assertEquals(20, currentTime, "Should complete at 20ms when quorum of 2 is met")
    }

    @Test
    fun `iterable extension raceQuorum works`() = runTest {
        val computations = listOf(
            Kap { delay(10); "A" },
            Kap { delay(20); "B" },
            Kap { delay(30); "C" },
        )

        val result = computations.raceQuorum(2).evalGraph()

        assertEquals(2, result.size)
        assertEquals("A", result[0])
        assertEquals("B", result[1])
    }

    @Test
    fun `quorum virtual time proof — 2 of 5 at different speeds`() = runTest {
        val result = raceQuorum(
            required = 2,
            Kap { delay(50); "A" },
            Kap { delay(30); "B" },
            Kap { delay(10); "C" },
            Kap { delay(40); "D" },
            Kap { delay(20); "E" },
        ).evalGraph()

        assertEquals(2, result.size)
        // Fastest two: C@10ms, E@20ms
        assertEquals("C", result[0])
        assertEquals("E", result[1])
        assertEquals(20, currentTime, "Quorum of 2 from 5 should complete at 20ms")
    }
}
