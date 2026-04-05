package kap

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for newly added API: [raceAgainst] extension.
 */
class NewApiTest {

    // ════════════════════════════════════════════════════════════════════════
    // raceAgainst extension
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `raceAgainst returns faster computation result`() = runTest {
        val result = Kap { delay(100); "slow" }
                .raceAgainst(Kap { delay(10); "fast" }).evalGraph()
        assertEquals("fast", result)
    }

    @Test
    fun `raceAgainst is equivalent to race top-level function`() = runTest {
        val primary = Kap { delay(10); "primary" }
        val secondary = Kap { delay(100); "secondary" }

        val viaExtension = primary.raceAgainst(secondary).evalGraph()
        val viaTopLevel = race(primary, secondary).evalGraph()
        assertEquals(viaTopLevel, viaExtension)
    }

    @Test
    fun `raceAgainst falls back when primary fails`() = runTest {
        val result = Kap<String> { error("primary failed") }
                .raceAgainst(Kap { delay(10); "fallback" }).evalGraph()
        assertEquals("fallback", result)
    }

    @Test
    fun `raceAgainst chains with other combinators`() = runTest {
        val result = Kap { delay(200); "slow-primary" }
                .raceAgainst(Kap { delay(10); "fast-replica" })
                .map { it.uppercase() }.evalGraph()
        assertEquals("FAST-REPLICA", result)
    }
}
