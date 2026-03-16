plugins {
    kotlin("multiplatform") version "2.0.21"
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version "1.9.20"
}

group = "org.applicative.coroutines"
version = "1.0.0"

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
    linuxX64()
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            implementation("io.arrow-kt:arrow-fx-coroutines:1.2.4")
            implementation("io.arrow-kt:arrow-core:1.2.4")
            implementation("io.kotest:kotest-property:5.9.1")
        }
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaHtml"))
}

publishing {
    repositories {
        maven {
            name = "sonatype"
            val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
            credentials {
                username = findProperty("ossrhUsername") as String? ?: ""
                password = findProperty("ossrhPassword") as String? ?: ""
            }
        }
    }

    publications.withType<MavenPublication> {
        artifact(javadocJar)

        pom {
            name.set("coroutines-applicatives")
            description.set("Applicative functor DSL for declarative parallel composition of Kotlin coroutines")
            url.set("https://github.com/dlattenero/coroutines-applicatives")

            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }

            developers {
                developer {
                    id.set("dlattenero")
                    name.set("D. Lattenero")
                }
            }

            scm {
                url.set("https://github.com/dlattenero/coroutines-applicatives")
                connection.set("scm:git:git://github.com/dlattenero/coroutines-applicatives.git")
                developerConnection.set("scm:git:ssh://github.com/dlattenero/coroutines-applicatives.git")
            }
        }
    }
}

signing {
    // Configure GPG signing for Maven Central publishing.
    // Requires GPG key and Sonatype credentials.
    // Set these in ~/.gradle/gradle.properties:
    //   signing.gnupg.keyName=<KEY_ID>
    //   signing.gnupg.passphrase=<PASSPHRASE>
    //   ossrhUsername=<SONATYPE_USERNAME>
    //   ossrhPassword=<SONATYPE_PASSWORD>
    isRequired = gradle.taskGraph.hasTask("publish")
    useGpgCmd()
    sign(publishing.publications)
}

tasks.register("generateCurry") {
    description = "Regenerates src/commonMain/kotlin/applicative/internal/curry.kt"
    group = "codegen"

    val maxArity = 22
    val outputFile = file("src/commonMain/kotlin/applicative/internal/curry.kt")

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
                val prefix = if (idx == 0) "{ " else "{ "
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
            appendLine("// │  Run: ./gradlew generateCurry                                        │")
            appendLine("// └──────────────────────────────────────────────────────────────────────┘")
            appendLine("package applicative.internal")
        }

        val body = (2..maxArity).joinToString("\n\n") { generateCurried(it) }
        outputFile.writeText("$header\n$body\n")
        println("Generated ${outputFile.path} (arities 2..$maxArity)")
    }
}

