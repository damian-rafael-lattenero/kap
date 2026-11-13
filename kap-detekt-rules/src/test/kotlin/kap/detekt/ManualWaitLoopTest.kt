package kap.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleSetProvider
import dev.detekt.test.lint
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualWaitLoopTest {

    private val rule = ManualWaitLoop(Config.empty)

    @Test
    fun `flags while true with an imported coroutines delay`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.delay

            suspend fun poll() {
                while (true) {
                    delay(100)
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `flags while true with a fully qualified coroutines delay`() {
        val findings = rule.lint(
            """
            suspend fun poll() {
                while (true) {
                    kotlinx.coroutines.delay(100)
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `flags while true when delay comes from the star import`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.*

            suspend fun poll() {
                while (true) {
                    delay(100)
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `flags while true with Thread sleep`() {
        val findings = rule.lint(
            """
            fun poll() {
                while (true) {
                    Thread.sleep(100)
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `flags a wait nested inside the loop body`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.delay

            suspend fun poll() {
                while (true) {
                    if (ready()) {
                        delay(10)
                    }
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports every wait loop in the file`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.delay

            suspend fun first() {
                while (true) {
                    delay(10)
                }
            }

            fun second() {
                while (true) {
                    Thread.sleep(10)
                }
            }
            """.trimIndent(),
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun `does not flag a bounded loop that waits`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.delay

            suspend fun poll() {
                while (running) {
                    delay(100)
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not flag an unbounded loop without a wait call`() {
        val findings = rule.lint(
            """
            fun poll() {
                while (true) {
                    if (ready()) break
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not flag a same-named local delay`() {
        val findings = rule.lint(
            """
            fun delay(x: Long) = x

            fun poll() {
                while (true) {
                    delay(100)
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not flag Thread sleep outside an unbounded loop`() {
        val findings = rule.lint(
            """
            fun waitABit() {
                Thread.sleep(10)
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `provider exposes the kap ruleset with ManualWaitLoop`() {
        val provider = KapRuleSetProvider()

        assertEquals("kap", provider.ruleSetId.value)
        assertEquals(
            setOf("ManualWaitLoop", "CancellationArmBeforeCatchAll", "VolatileReadModifyWrite"),
            provider.instance().rules.keys.map { it.value }.toSet(),
        )
    }

    @Test
    fun `provider is registered for ServiceLoader`() {
        val providers = ServiceLoader.load(RuleSetProvider::class.java).toList()

        assertTrue(providers.any { it is KapRuleSetProvider })
    }
}
