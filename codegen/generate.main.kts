#!/usr/bin/env kotlin

/**
 * Generates `curry.kt` — curried extension functions for (P1, …, PN) -> R
 * for arities 2..22 (matching Kotlin's max function type arity).
 *
 * Usage:
 *   kotlin codegen/generate.main.kts
 *   — or —
 *   ./gradlew generateCurry
 *
 * Output is written to src/main/kotlin/applicative/internal/curry.kt
 */

import java.io.File

val maxArity = 22
val outputFile = File("src/main/kotlin/applicative/internal/curry.kt")

fun generateCurried(arity: Int): String {
    val typeParams = (1..arity).joinToString(", ") { "P$it" }
    val receiverParams = (1..arity).joinToString(", ") { "P$it" }
    val returnType = (1..arity).joinToString(" -> ") { "(P$it)" } + " -> R"
    val params = (1..arity).map { "p$it" to "P$it" }
    val callArgs = params.joinToString(", ") { it.first }

    // Build: { p1: P1 -> { p2: P2 -> ... -> this(p1, ..., pN) } ... }
    val opens = params.joinToString(" -> { ") { (name, type) -> "{ $name: $type" }

    if (arity <= 5) {
        val nested = "$opens -> this($callArgs)" + " }".repeat(arity)
        return "fun <$typeParams, R> (($receiverParams) -> R).curried(): $returnType =\n    $nested"
    }

    // Multi-line with rows of 3 for readability
    val chunks = params.chunked(3)
    val lines = mutableListOf<String>()
    for ((idx, chunk) in chunks.withIndex()) {
        val chunkOpens = chunk.joinToString(" -> { ") { (name, type) -> "{ $name: $type" }
        if (idx == chunks.lastIndex) {
            lines.add("    $chunkOpens ->")
            lines.add("        this($callArgs)")
            lines.add("    " + " }".repeat(arity))
        } else {
            lines.add("    $chunkOpens ->")
        }
    }
    return "fun <$typeParams, R> (($receiverParams) -> R).curried(): $returnType =\n${lines.joinToString("\n")}"
}

val header = buildString {
    appendLine("// ┌──────────────────────────────────────────────────────────────────────┐")
    appendLine("// │  AUTO-GENERATED — do not edit by hand.                               │")
    appendLine("// │  Run: ./gradlew generateCurry                                        │")
    appendLine("// └──────────────────────────────────────────────────────────────────────┘")
    appendLine("package applicative.internal")
}

val body = (2..maxArity).joinToString("\n\n") { generateCurried(it) }
outputFile.writeText("$header\n$body\n")
println("Generated ${outputFile.path} (arities 2..$maxArity)")
