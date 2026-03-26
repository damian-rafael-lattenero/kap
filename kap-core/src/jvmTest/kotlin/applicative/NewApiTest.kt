package applicative

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
            Effect { delay(100); "slow" }
                .raceAgainst(Effect { delay(10); "fast" })
        }
        assertEquals("fast", result)
    }

    @Test
    fun `raceAgainst is equivalent to race top-level function`() = runTest {
        val primary = Effect { delay(10); "primary" }
        val secondary = Effect { delay(100); "secondary" }

        val viaExtension = Async { primary.raceAgainst(secondary) }
        val viaTopLevel = Async { race(primary, secondary) }
        assertEquals(viaTopLevel, viaExtension)
    }

    @Test
    fun `raceAgainst falls back when primary fails`() = runTest {
        val result = Async {
            Effect<String> { error("primary failed") }
                .raceAgainst(Effect { delay(10); "fallback" })
        }
        assertEquals("fallback", result)
    }

    @Test
    fun `raceAgainst chains with other combinators`() = runTest {
        val result = Async {
            Effect { delay(200); "slow-primary" }
                .raceAgainst(Effect { delay(10); "fast-replica" })
                .map { it.uppercase() }
        }
        assertEquals("FAST-REPLICA", result)
    }
}
