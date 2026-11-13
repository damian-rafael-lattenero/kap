package kap.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class CancellationArmBeforeCatchAllTest {

    private val rule = CancellationArmBeforeCatchAll(Config.empty)

    @Test
    fun `flags a bare broad catch`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.CancellationException

            fun run() {
                try {
                    work()
                } catch (e: Exception) {
                    log(e)
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `flags catch Throwable and catch RuntimeException`() {
        val findings = rule.lint(
            """
            fun run() {
                try {
                    work()
                } catch (e: Throwable) {
                    log(e)
                }
                try {
                    work()
                } catch (e: RuntimeException) {
                    log(e)
                }
            }
            """.trimIndent(),
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun `flags a fully qualified broad catch`() {
        val findings = rule.lint(
            """
            fun run() {
                try {
                    work()
                } catch (e: java.lang.Exception) {
                    log(e)
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `compliant with an earlier CancellationException clause`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.CancellationException

            fun run() {
                try {
                    work()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(e)
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `compliant with an inline is-check and rethrow`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.CancellationException

            fun run() {
                try {
                    work()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    log(e)
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `compliant with a when is-arm and rethrow`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.CancellationException

            fun run() {
                try {
                    work()
                } catch (e: Exception) {
                    when (e) {
                        is CancellationException -> throw e
                        else -> log(e)
                    }
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `compliant with an unconditional rethrow`() {
        val findings = rule.lint(
            """
            fun run() {
                try {
                    work()
                } catch (e: Exception) {
                    throw IllegalStateException("wrap", e)
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `an is-check without a throw still flags`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.CancellationException

            fun run() {
                try {
                    work()
                } catch (e: Exception) {
                    if (e is CancellationException) log("cancelled")
                    log(e)
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `narrow catches never flag`() {
        val findings = rule.lint(
            """
            fun run() {
                try {
                    work()
                } catch (e: IllegalStateException) {
                    log(e)
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports every offender in the file`() {
        val findings = rule.lint(
            """
            fun run() {
                try {
                    work()
                } catch (e: Exception) {
                    log(e)
                }
                try {
                    work()
                } catch (e: Exception) {
                    throw e
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `the arm in an unrelated try does not excuse this one`() {
        val findings = rule.lint(
            """
            import kotlinx.coroutines.CancellationException

            fun run() {
                try {
                    try {
                        work()
                    } catch (e: CancellationException) {
                        throw e
                    }
                } catch (e: Exception) {
                    log(e)
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }
}
