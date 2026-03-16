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

// ── Code generation tasks ────────────────────────────────────────────────

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

tasks.register("generateLift") {
    description = "Regenerates src/commonMain/kotlin/applicative/Lift.kt"
    group = "codegen"

    val maxArity = 22
    val outputFile = file("src/commonMain/kotlin/applicative/Lift.kt")
    outputs.file(outputFile)

    doLast {
        fun generateLift(n: Int): String {
            val typeParams = (1..n).joinToString(", ") { "P$it" }
            val paramType = (1..n).joinToString(", ") { "P$it" }
            val curriedType = (1..n).joinToString(" -> ") { "(P$it)" } + " -> R"
            return "fun <$typeParams, R> lift$n(f: ($paramType) -> R): Computation<$curriedType> = pure(f.curried())"
        }

        val header = buildString {
            appendLine("// ┌──────────────────────────────────────────────────────────────────────┐")
            appendLine("// │  AUTO-GENERATED — do not edit by hand.                               │")
            appendLine("// │  Run: ./gradlew generateLift                                         │")
            appendLine("// └──────────────────────────────────────────────────────────────────────┘")
            appendLine("package applicative")
            appendLine()
            appendLine("import applicative.internal.curried")
            appendLine()
            appendLine("// ── lift: pure . curry ──────────────────────────────────────────────────")
            appendLine()
            appendLine("/**")
            appendLine(" * Curries [f] and wraps it as a pure [Computation], ready for [ap] chains.")
            appendLine(" *")
            appendLine(" * ```")
            appendLine(" * lift2 { a: String, b: Int -> \"\$a=\$b\" }")
            appendLine(" *     .ap { fetchName() }")
            appendLine(" *     .ap { fetchAge() }")
            appendLine(" * ```")
            appendLine(" *")
            appendLine(" * Overloads [lift2] through [lift22] cover arities 2-22.")
            appendLine(" */")
        }

        val body = (2..maxArity).joinToString("\n\n") { generateLift(it) }
        outputFile.writeText("$header\n$body\n")
        println("Generated ${outputFile.path} (arities 2..$maxArity)")
    }
}

tasks.register("generateValidatedOverloads") {
    description = "Regenerates src/commonMain/kotlin/applicative/ValidatedOverloads.kt (zipV + liftV arities 2-22)"
    group = "codegen"

    val maxArity = 22
    val outputFile = file("src/commonMain/kotlin/applicative/ValidatedOverloads.kt")
    outputs.file(outputFile)

    doLast {
        // Type parameter letters: A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W, X
        // (skips E which is the error type, and R which is the result type — 22 letters total)
        val letters = listOf("A","B","C","D","F","G","H","I","J","K","L","M","N","O","P","Q","S","T","U","V","W","X")
        // Variable suffixes: lowercase, except 'O' uses uppercase to avoid 'do' keyword clash
        val varSuffixes = letters.map { if (it == "O") "O" else it.lowercase() }

        fun generateZipV(n: Int): String {
            val types = letters.take(n)
            val lowerVars = varSuffixes.take(n)

            // Function parameters: fa, fb, fc, ...
            val params = types.mapIndexed { i, t ->
                "    f${lowerVars[i]}: suspend () -> Either<Nel<E>, $t>"
            }.joinToString(",\n")

            // Combine parameter: combine: (A, B, C, ...) -> R
            val combineParams = types.joinToString(", ")

            // Async launches: val da = async { fa() }
            val asyncLaunches = lowerVars.joinToString("\n    ") { v ->
                "val d$v = async { f$v() }"
            }

            // Awaits: val ea = da.await()
            val awaits = lowerVars.joinToString("\n    ") { v ->
                "val e$v = d$v.await()"
            }

            // Right check: ea is Either.Right && eb is Either.Right && ...
            val rightCheck = lowerVars.joinToString(" && ") { v ->
                "e$v is Either.Right"
            }

            // Combine values: ea.value, eb.value, ...
            val combineValues = lowerVars.joinToString(", ") { v ->
                "e$v.value"
            }

            // Error accumulation: if (ea is Either.Left) add(ea.value)
            val errorChecks = lowerVars.joinToString("\n            ") { v ->
                "if (e$v is Either.Left) add(e$v.value)"
            }

            // Type params for the function signature
            val typeParamsList = "E, ${types.joinToString(", ")}, R"

            return """fun <$typeParamsList> zipV(
$params,
    combine: ($combineParams) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    $asyncLaunches
    $awaits
    if ($rightCheck)
        Either.Right(combine($combineValues))
    else {
        val errors = buildList {
            $errorChecks
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}"""
        }

        fun generateLiftV(n: Int): String {
            val typeParams = (1..n).joinToString(", ") { "P$it" }
            val paramType = (1..n).joinToString(", ") { "P$it" }
            val curriedType = (1..n).joinToString(" -> ") { "(P$it)" } + " -> R"
            return "fun <E, $typeParams, R> liftV$n(f: ($paramType) -> R): Computation<Either<Nel<E>, $curriedType>> =\n    pure(Either.Right(f.curried()))"
        }

        val header = buildString {
            appendLine("// ┌──────────────────────────────────────────────────────────────────────┐")
            appendLine("// │  AUTO-GENERATED — do not edit by hand.                               │")
            appendLine("// │  Run: ./gradlew generateValidatedOverloads                           │")
            appendLine("// └──────────────────────────────────────────────────────────────────────┘")
            appendLine("package applicative")
            appendLine()
            appendLine("import kotlinx.coroutines.async")
            appendLine("import applicative.internal.curried")
        }

        // zipV goes up to arity 21 (limited by 21 available type-param letters: A-W skipping E,R)
        val maxZipVArity = minOf(maxArity, letters.size)
        val zipVBody = (2..maxZipVArity).joinToString("\n\n") { generateZipV(it) }
        val liftVBody = (2..maxArity).joinToString("\n\n") { generateLiftV(it) }

        val content = buildString {
            append(header)
            appendLine()
            appendLine("// ── zipV: parallel validation with full type inference ───────────────────")
            appendLine()
            appendLine(zipVBody)
            appendLine()
            appendLine("// ── liftV: curried validated lift ────────────────────────────────────────")
            appendLine()
            appendLine(liftVBody)
        }

        outputFile.writeText(content)
        println("Generated ${outputFile.path} (arities 2..$maxArity)")
    }
}

tasks.register("generateZipMapN") {
    description = "Regenerates src/commonMain/kotlin/applicative/ZipOverloads.kt (zip + mapN arities 3-22)"
    group = "codegen"

    val maxArity = 22
    val outputFile = file("src/commonMain/kotlin/applicative/ZipOverloads.kt")
    outputs.file(outputFile)

    doLast {
        // Type parameter letters: A-W skipping R (22 letters)
        val typeLetters = listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","S","T","U","V","W")

        fun generateZip(n: Int): String {
            val types = typeLetters.take(n)
            val typeParams = types.joinToString(", ") + ", R"
            val params = (1..n).joinToString(",\n") { i ->
                "    c$i: Computation<${types[i - 1]}>"
            }
            val asyncLaunches = (1..n).joinToString("\n    ") { i ->
                "val d$i = async { with(c$i) { execute() } }"
            }
            val awaits = (1..n).joinToString(", ") { "d$it.await()" }
            val combineTypes = types.joinToString(", ")

            return """/** Runs $n computations in parallel, combining results with [combine]. */
fun <$typeParams> zip(
$params,
    combine: ($combineTypes) -> R,
): Computation<R> = Computation {
    $asyncLaunches
    combine($awaits)
}"""
        }

        fun generateMapN(n: Int): String {
            val types = typeLetters.take(n)
            val typeParams = types.joinToString(", ") + ", R"
            val params = (1..n).joinToString(",\n") { i ->
                "    c$i: Computation<${types[i - 1]}>"
            }
            val combineTypes = types.joinToString(", ")

            // Arity 2 delegates to instance method; 3+ delegates to top-level zip
            val delegation = if (n == 2) "c1.zip(c2, combine)" else {
                val zipArgs = (1..n).joinToString(", ") { "c$it" }
                "zip($zipArgs, combine)"
            }

            return """/** Runs $n computations in parallel, combining results with [combine]. Alias for [zip]. */
fun <$typeParams> mapN(
$params,
    combine: ($combineTypes) -> R,
): Computation<R> = $delegation"""
        }

        val header = buildString {
            appendLine("// ┌──────────────────────────────────────────────────────────────────────┐")
            appendLine("// │  AUTO-GENERATED — do not edit by hand.                               │")
            appendLine("// │  Run: ./gradlew generateZipMapN                                      │")
            appendLine("// └──────────────────────────────────────────────────────────────────────┘")
            appendLine("package applicative")
            appendLine()
            appendLine("import kotlinx.coroutines.async")
        }

        val zipBody = (3..maxArity).joinToString("\n\n") { generateZip(it) }
        val mapNBody = (2..maxArity).joinToString("\n\n") { generateMapN(it) }

        val content = buildString {
            append(header)
            appendLine()
            appendLine("// ── zip: parallel combination (3-$maxArity arity) ──────────────────────────────")
            appendLine()
            appendLine(zipBody)
            appendLine()
            appendLine("// ── mapN: alias for zip (2-$maxArity arity) ────────────────────────────────────")
            appendLine()
            appendLine(mapNBody)
        }

        outputFile.writeText(content)
        println("Generated ${outputFile.path} (zip 3..$maxArity, mapN 2..$maxArity)")
    }
}

tasks.register("generateResourceZip") {
    description = "Regenerates src/commonMain/kotlin/applicative/ResourceZip.kt (Resource.zip arities 2-22)"
    group = "codegen"

    val maxArity = 22
    val outputFile = file("src/commonMain/kotlin/applicative/ResourceZip.kt")
    outputs.file(outputFile)

    doLast {
        val typeLetters = listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","S","T","U","V","W")

        fun generateResourceZip(n: Int): String {
            val types = typeLetters.take(n)
            val typeParams = types.joinToString(", ") + ", R"
            val params = (1..n).joinToString(",\n") { i ->
                "    r$i: Resource<${types[i - 1]}>"
            }
            val combineTypes = types.joinToString(", ")

            // Build nested bind chain: r1.bind { v1 -> r2.bind { v2 -> ... use(combine(v1, v2, ...)) } }
            val opens = (1..n).joinToString(" ") { i -> "r$i.bind { v$i ->" }
            val closesBraces = " }".repeat(n)
            val combineArgs = (1..n).joinToString(", ") { "v$it" }

            return """/** Combines $n resources. Release runs in reverse order, even on failure. */
fun <$typeParams> Resource.Companion.zip(
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
            appendLine("// │  Run: ./gradlew generateResourceZip                                  │")
            appendLine("// └──────────────────────────────────────────────────────────────────────┘")
            appendLine("package applicative")
        }

        val body = (2..maxArity).joinToString("\n\n") { generateResourceZip(it) }

        val content = buildString {
            append(header)
            appendLine()
            appendLine("// ── Resource.zip: composable resource combination (2-$maxArity arity) ────────")
            appendLine()
            appendLine(body)
        }

        outputFile.writeText(content)
        println("Generated ${outputFile.path} (arities 2..$maxArity)")
    }
}

tasks.register("generateAll") {
    dependsOn("generateCurry", "generateLift", "generateValidatedOverloads", "generateZipMapN", "generateResourceZip")
    description = "Regenerates all codegen files (curry, lift, validated overloads, zip/mapN, resource zip)"
    group = "codegen"
}

