plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser()
        nodejs()
    }
    linuxX64()

    val xcodeAvailable = try {
        val selectProc = ProcessBuilder("xcode-select", "-p").start()
        val selectOk = selectProc.waitFor() == 0
        if (!selectOk) false
        else {
            val xcrunProc = ProcessBuilder("xcrun", "xcodebuild", "-version").start()
            xcrunProc.waitFor() == 0
        }
    } catch (_: Exception) { false }

    if (xcodeAvailable) {
        macosX64()
        macosArm64()
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.kotest.property)
        }
    }
}

// ── Code generation tasks ────────────────────────────────────────────────

tasks.register("generateCurry") {
    description = "Regenerates src/commonMain/kotlin/kap/internal/curry.kt"
    group = "codegen"

    val maxArity = 22
    val outputFile = file("src/commonMain/kotlin/kap/internal/curry.kt")
    outputs.file(outputFile)

    doLast {
        fun generateCurried(arity: Int): String {
            val typeParams = (1..arity).joinToString(", ") { "P$it" }
            val receiverParams = (1..arity).joinToString(", ") { "P$it" }
            val returnType = (1..arity).joinToString(" -> ") { "(P$it)" } + " -> R"
            val params = (1..arity).map { "p$it" to "P$it" }
            val callArgs = params.joinToString(", ") { it.first }
            val paramStr = params.joinToString(" -> { ") { (name, type) -> "$name: $type" }

            if (arity <= 5) {
                val nested = "{ $paramStr -> this($callArgs)" + " }".repeat(arity)
                return "fun <$typeParams, R> (($receiverParams) -> R).curried(): $returnType =\n    $nested"
            }

            val chunks = params.chunked(3)
            val lines = mutableListOf<String>()
            for ((idx, chunk) in chunks.withIndex()) {
                val chunkStr = chunk.joinToString(" -> { ") { (name, type) -> "$name: $type" }
                val prefix = "{ "
                if (idx == chunks.lastIndex) {
                    lines.add("    $prefix$chunkStr ->")
                    lines.add("        this($callArgs)")
                    lines.add("    " + " }".repeat(arity))
                } else {
                    lines.add("    $prefix$chunkStr ->")
                }
            }
            return "fun <$typeParams, R> (($receiverParams) -> R).curried(): $returnType =\n${lines.joinToString("\n")}"
        }

        val header = buildString {
            appendLine("// ┌──────────────────────────────────────────────────────────────────────┐")
            appendLine("// │  AUTO-GENERATED — do not edit by hand.                               │")
            appendLine("// │  Run: ./gradlew :kap-core:generateCurry                              │")
            appendLine("// └──────────────────────────────────────────────────────────────────────┘")
            appendLine("package kap.internal")
        }

        val body = (2..maxArity).joinToString("\n\n") { generateCurried(it) }
        outputFile.writeText("$header\n$body\n")
        println("Generated ${outputFile.path} (arities 2..$maxArity)")
    }
}

tasks.register("generateKap") {
    description = "Regenerates src/commonMain/kotlin/kap/KapOverloads.kt"
    group = "codegen"

    val maxArity = 22
    val outputFile = file("src/commonMain/kotlin/kap/KapOverloads.kt")
    outputs.file(outputFile)

    doLast {
        fun generateKap(n: Int): String {
            val typeParams = (1..n).joinToString(", ") { "P$it" }
            val paramType = (1..n).joinToString(", ") { "P$it" }
            val curriedType = (1..n).joinToString(" -> ") { "(P$it)" } + " -> R"
            return "fun <$typeParams, R> kap(f: ($paramType) -> R): Kap<$curriedType> = Kap.of(f.curried())"
        }

        val header = buildString {
            appendLine("// ┌──────────────────────────────────────────────────────────────────────┐")
            appendLine("// │  AUTO-GENERATED — do not edit by hand.                               │")
            appendLine("// │  Run: ./gradlew :kap-core:generateKap                                │")
            appendLine("// └──────────────────────────────────────────────────────────────────────┘")
            appendLine("package kap")
            appendLine()
            appendLine("import kap.internal.curried")
            appendLine()
            appendLine("// ── kap: curry and wrap for .with chains ────────────────────────────────")
            appendLine()
            appendLine("/** Curries [f] and wraps it as a [Kap], ready for [with] chains. */")
        }

        val body = (2..maxArity).joinToString("\n\n") { generateKap(it) }
        outputFile.writeText("$header\n$body\n")
        println("Generated ${outputFile.path} (arities 2..$maxArity)")
    }
}

tasks.register("generateZipCombine") {
    description = "Regenerates src/commonMain/kotlin/kap/ZipCombineOverloads.kt"
    group = "codegen"

    val maxArity = 22
    val outputFile = file("src/commonMain/kotlin/kap/ZipCombineOverloads.kt")
    outputs.file(outputFile)

    doLast {
        val typeLetters = listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","S","T","U","V","W")

        fun generateZip(n: Int): String {
            val types = typeLetters.take(n)
            val typeParams = types.joinToString(", ") + ", R"
            val params = (1..n).joinToString(",\n") { i -> "    c$i: Kap<${types[i - 1]}>" }
            val asyncLaunches = (1..n).joinToString("\n    ") { i -> "val d$i = async { with(c$i) { execute() } }" }
            val awaits = (1..n).joinToString(", ") { "d$it.await()" }
            val combineTypes = types.joinToString(", ")
            return """fun <$typeParams> zip(
$params,
    combine: ($combineTypes) -> R,
): Kap<R> = Kap {
    $asyncLaunches
    combine($awaits)
}"""
        }

        fun generateMapN(n: Int): String {
            val types = typeLetters.take(n)
            val typeParams = types.joinToString(", ") + ", R"
            val params = (1..n).joinToString(",\n") { i -> "    c$i: Kap<${types[i - 1]}>" }
            val combineTypes = types.joinToString(", ")
            val delegation = if (n == 2) "c1.zip(c2, f)" else {
                val zipArgs = (1..n).joinToString(", ") { "c$it" }
                "zip($zipArgs, f)"
            }
            return """fun <$typeParams> combine(
$params,
    f: ($combineTypes) -> R,
): Kap<R> = $delegation"""
        }

        val header = buildString {
            appendLine("// ┌──────────────────────────────────────────────────────────────────────┐")
            appendLine("// │  AUTO-GENERATED — do not edit by hand.                               │")
            appendLine("// │  Run: ./gradlew :kap-core:generateZipCombine                          │")
            appendLine("// └──────────────────────────────────────────────────────────────────────┘")
            appendLine("package kap")
            appendLine()
            appendLine("import kotlinx.coroutines.async")
        }

        val zipBody = (3..maxArity).joinToString("\n\n") { generateZip(it) }
        val mapNBody = (2..maxArity).joinToString("\n\n") { generateMapN(it) }

        val content = buildString {
            append(header)
            appendLine()
            appendLine(zipBody)
            appendLine()
            appendLine(mapNBody)
        }
        outputFile.writeText(content)
        println("Generated ${outputFile.path}")
    }
}

tasks.register("generateAll") {
    dependsOn("generateCurry", "generateKap", "generateZipCombine")
    description = "Regenerates all codegen files for kap-core"
    group = "codegen"
}

mavenPublishing {
    publishToMavenCentral()
    if (!project.hasProperty("skipSigning")) signAllPublications()
    coordinates(group.toString(), "kap-core", version.toString())

    pom {
        name.set("kap-core")
        description.set("KAP — lean DSL for parallel orchestration with Kotlin coroutines")
        inceptionYear.set("2025")
        url.set("https://github.com/damian-rafael-lattenero/coroutines-applicatives")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("damian-rafael-lattenero")
                name.set("Damian Rafael Lattenero")
                url.set("https://github.com/damian-rafael-lattenero")
            }
        }
        scm {
            url.set("https://github.com/damian-rafael-lattenero/coroutines-applicatives")
            connection.set("scm:git:git://github.com/damian-rafael-lattenero/coroutines-applicatives.git")
            developerConnection.set("scm:git:ssh://git@github.com/damian-rafael-lattenero/coroutines-applicatives.git")
        }
    }
}
