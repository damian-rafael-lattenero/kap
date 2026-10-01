package kap.ksp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden-file tests for the KSP processor.
 *
 * The fixtures in `src/test/kotlin/Fixtures.kt` are processed by this module's
 * own processor (self-application via `kspTest(project(":kap-ksp"))`). This
 * test diffs the generated output against the committed snapshots in
 * `src/test/resources/golden/` — any change to the generated code must be
 * intentional and committed together with the processor change.
 *
 * To regenerate after an intentional change:
 *   1. ./gradlew :kap-ksp:kspTest
 *   2. copy every generated .kt file from build/generated/ksp/test/kotlin
 *      into src/test/resources/golden
 */
class GoldenTest {

    private val projectDir = File(System.getProperty("user.dir"))
    private val generatedDir = File(projectDir, "build/generated/ksp/test/kotlin")
    private val goldenDir = File(projectDir, "src/test/resources/golden")

    @Test
    fun `generated builders match committed golden snapshots`() {
        val goldens = goldenDir.listFiles { f -> f.isFile && f.extension == "kt" }
            ?.map { it.name }?.sorted()
            ?: error("No golden files found in $goldenDir")
        val generated = generatedDir.listFiles { f -> f.isFile && f.extension == "kt" }
            ?.map { it.name }?.sorted()
            ?: error("No generated files found in $generatedDir — did kspTest run?")

        assertEquals(goldens, generated, """
            Golden file set drifted from generated output.
            Missing goldens: ${(goldens - generated.toSet())}
            Untracked generated: ${(generated - goldens.toSet())}
            If intentional: regenerate and copy (see GoldenTest KDoc).
        """.trimIndent())

        for (name in goldens) {
            val expected = File(goldenDir, name).readText().trimEnd()
            val actual = File(generatedDir, name).readText().trimEnd()
            assertEquals(
                expected, actual,
                "Golden mismatch in $name — if intentional, regenerate and commit both files.",
            )
        }
    }
}
