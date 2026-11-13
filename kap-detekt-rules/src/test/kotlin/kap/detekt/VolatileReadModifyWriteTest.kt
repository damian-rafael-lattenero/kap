package kap.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class VolatileReadModifyWriteTest {

    private val rule = VolatileReadModifyWrite(Config.empty)

    @Test
    fun `flags postfix increment on a volatile`() {
        val findings = rule.lint(
            """
            import kotlin.concurrent.Volatile

            class Counter {
                @Volatile var events = 0

                fun onEvent() { events++ }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `flags prefix increment and decrement`() {
        val findings = rule.lint(
            """
            import kotlin.concurrent.Volatile

            class Counter {
                @Volatile var up = 0
                @Volatile var down = 0

                fun tick() { ++up; --down }
            }
            """.trimIndent(),
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun `flags compound assignment`() {
        val findings = rule.lint(
            """
            import kotlin.concurrent.Volatile

            class Totals {
                @Volatile var bytes = 0L

                fun add(n: Long) { bytes += n }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `flags a self-referential assignment`() {
        val findings = rule.lint(
            """
            import kotlin.concurrent.Volatile

            class Totals {
                @Volatile var bytes = 0L

                fun add(n: Long) { bytes = bytes + n }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `flags an assignment through a function call`() {
        val findings = rule.lint(
            """
            import kotlin.concurrent.Volatile

            class Cache {
                @Volatile var offset = 0L

                fun advance() { offset = next(offset) }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `does not flag a plain store or a plain read`() {
        val findings = rule.lint(
            """
            import kotlin.concurrent.Volatile

            class Gate {
                @Volatile var running = false
                @Volatile var offset = 0L

                fun start() { running = true }
                fun reset() { offset = 0L }
                fun current(): Long = offset
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not flag a plain property doing the same RMW`() {
        val findings = rule.lint(
            """
            class Counter {
                var events = 0

                fun onEvent() { events++ }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports every offending site`() {
        val findings = rule.lint(
            """
            import kotlin.concurrent.Volatile

            class Counter {
                @Volatile var events = 0

                fun bump() { events++ }
                fun add(n: Int) { events += n }
                fun double() { events = events * 2 }
            }
            """.trimIndent(),
        )

        assertEquals(3, findings.size)
    }

    @Test
    fun `detects a top-level volatile property`() {
        val findings = rule.lint(
            """
            import kotlin.concurrent.Volatile

            @Volatile var hits = 0

            fun record() { hits++ }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `a plain read inside an expression is not flagged`() {
        val findings = rule.lint(
            """
            import kotlin.concurrent.Volatile

            class Registry {
                @Volatile var version = 0

                fun describe(): String = "v" + version
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }
}
