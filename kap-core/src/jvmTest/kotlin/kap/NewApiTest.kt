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
        val result = Async {
            Kap { delay(100); "slow" }
                .raceAgainst(Kap { delay(10); "fast" })
        }
        assertEquals("fast", result)
    }

    @Test
    fun `raceAgainst is equivalent to race top-level function`() = runTest {
        val primary = Kap { delay(10); "primary" }
        val secondary = Kap { delay(100); "secondary" }

        val viaExtension = Async { primary.raceAgainst(secondary) }
        val viaTopLevel = Async { race(primary, secondary) }
        assertEquals(viaTopLevel, viaExtension)
    }

    @Test
    fun `raceAgainst falls back when primary fails`() = runTest {
        val result = Async {
            Kap<String> { error("primary failed") }
                .raceAgainst(Kap { delay(10); "fallback" })
        }
        assertEquals("fallback", result)
    }

    @Test
    fun `raceAgainst chains with other combinators`() = runTest {
        val result = Async {
            Kap { delay(200); "slow-primary" }
                .raceAgainst(Kap { delay(10); "fast-replica" })
                .map { it.uppercase() }
        }
        assertEquals("FAST-REPLICA", result)
    }
}
