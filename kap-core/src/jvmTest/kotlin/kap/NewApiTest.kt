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
                .raceAgainst(Kap { delay(10); "fast" }).executeGraph()
        assertEquals("fast", result)
    }

    @Test
    fun `raceAgainst is equivalent to race top-level function`() = runTest {
        val primary = Kap { delay(10); "primary" }
        val secondary = Kap { delay(100); "secondary" }

        val viaExtension = primary.raceAgainst(secondary).executeGraph()
        val viaTopLevel = race(primary, secondary).executeGraph()
        assertEquals(viaTopLevel, viaExtension)
    }

    @Test
    fun `raceAgainst falls back when primary fails`() = runTest {
        val result = Kap<String> { error("primary failed") }
                .raceAgainst(Kap { delay(10); "fallback" }).executeGraph()
        assertEquals("fallback", result)
    }

    @Test
    fun `raceAgainst chains with other combinators`() = runTest {
        val result = Kap { delay(200); "slow-primary" }
                .raceAgainst(Kap { delay(10); "fast-replica" })
                .map { it.uppercase() }.executeGraph()
        assertEquals("FAST-REPLICA", result)
    }
}
