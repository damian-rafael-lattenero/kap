plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser()
        nodejs()
    }
    wasmJs {
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
            api(project(":kap-core"))
            implementation(libs.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.kotest.property)
        }
        nativeTest.dependencies {
             implementation(kotlin("test"))
             implementation(libs.coroutines.test)
        }     
    }
}

// ── Code generation tasks ────────────────────────────────────────────────

tasks.register("generateResourceZip") {
    description = "Regenerates src/commonMain/kotlin/kap/ResourceZip.kt"
    group = "codegen"

    val maxArity = 22
    val outputFile = file("src/commonMain/kotlin/kap/ResourceZip.kt")
    outputs.file(outputFile)

    doLast {
        val typeLetters = listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","S","T","U","V","W")

        fun generateResourceZip(n: Int): String {
            val types = typeLetters.take(n)
            val typeParams = types.joinToString(", ") + ", R"
            val params = (1..n).joinToString(",\n") { i -> "    r$i: Resource<${types[i - 1]}>" }
            val combineTypes = types.joinToString(", ")
            val opens = (1..n).joinToString(" ") { i -> "r$i.bind { v$i ->" }
            val closesBraces = " }".repeat(n)
            val combineArgs = (1..n).joinToString(", ") { "v$it" }

            return """fun <$typeParams> Resource.Companion.zip(
$params,
    combine: ($combineTypes) -> R,
): Resource<R> = Resource { use ->
    $opens
        use(combine($combineArgs))
    $closesBraces
}"""
        }

        val header = buildString {
            appendLine("// ┌──────────────────────────────────────────────────────────────────────┐")
            appendLine("// │  AUTO-GENERATED — do not edit by hand.                               │")
            appendLine("// │  Run: ./gradlew :kap-resilience:generateResourceZip                  │")
            appendLine("// └──────────────────────────────────────────────────────────────────────┘")
            appendLine("package kap")
        }

        val body = (2..maxArity).joinToString("\n\n") { generateResourceZip(it) }
        val content = buildString {
            append(header)
            appendLine()
            appendLine(body)
        }
        outputFile.writeText(content)
        println("Generated ${outputFile.path}")
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (!project.hasProperty("skipSigning")) signAllPublications()
    coordinates(group.toString(), "kap-resilience", version.toString())

    pom {
        name.set("kap-resilience")
        description.set("Kotlin Applicative Parallelism — resilience patterns: Schedule, Resource, CircuitBreaker, bracket")
        inceptionYear.set("2025")
        url.set("https://github.com/damian-rafael-lattenero/kap")

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
            url.set("https://github.com/damian-rafael-lattenero/kap")
            connection.set("scm:git:git://github.com/damian-rafael-lattenero/kap.git")
            developerConnection.set("scm:git:ssh://git@github.com/damian-rafael-lattenero/kap.git")
        }
    }
}
