plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":kap-core"))
    api(libs.arrow.core)
    implementation(libs.arrow.fx)
    implementation(libs.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

// ── Code generation tasks ────────────────────────────────────────────────

tasks.register("generateValidatedOverloads") {
    description = "Regenerates src/main/kotlin/kap/ValidatedOverloads.kt (zipV + kapV arities 2-22)"
    group = "codegen"

    val maxArity = 22
    val outputFile = file("src/main/kotlin/kap/ValidatedOverloads.kt")
    outputs.file(outputFile)

    doLast {
        val letters = listOf("A","B","C","D","F","G","H","I","J","K","L","M","N","O","P","Q","S","T","U","V","W","X")
        val varSuffixes = letters.map { if (it == "O") "O" else it.lowercase() }

        fun generateZipV(n: Int): String {
            val types = letters.take(n)
            val lowerVars = varSuffixes.take(n)
            val params = types.mapIndexed { i, t ->
                "    f${lowerVars[i]}: suspend () -> Either<NonEmptyList<E>, $t>"
            }.joinToString(",\n")
            val combineParams = types.joinToString(", ")
            val asyncLaunches = lowerVars.joinToString("\n    ") { v -> "val d$v = async { f$v() }" }
            val awaits = lowerVars.joinToString("\n    ") { v -> "val e$v = d$v.await()" }
            val rightCheck = lowerVars.joinToString(" && ") { v -> "e$v is Either.Right" }
            val combineValues = lowerVars.joinToString(", ") { v -> "e$v.value" }
            val errorChecks = lowerVars.joinToString("\n            ") { v -> "if (e$v is Either.Left) add(e$v.value)" }
            val typeParamsList = "E, ${types.joinToString(", ")}, R"

            return """fun <$typeParamsList> zipV(
$params,
    combine: ($combineParams) -> R,
): Kap<Either<NonEmptyList<E>, R>> = Kap {
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
            return "fun <E, $typeParams, R> kapV(f: ($paramType) -> R): Kap<Either<NonEmptyList<E>, $curriedType>> =\n    Kap.of(Either.Right(f.curried()))"
        }

        val header = buildString {
            appendLine("// ┌──────────────────────────────────────────────────────────────────────┐")
            appendLine("// │  AUTO-GENERATED — do not edit by hand.                               │")
            appendLine("// │  Run: ./gradlew :kap-arrow:generateValidatedOverloads                │")
            appendLine("// └──────────────────────────────────────────────────────────────────────┘")
            appendLine("package kap")
            appendLine()
            appendLine("import arrow.core.Either")
            appendLine("import arrow.core.NonEmptyList")
            appendLine("import kotlinx.coroutines.async")
            appendLine("import kap.internal.curried")
        }

        val maxZipVArity = minOf(maxArity, letters.size)
        val zipVBody = (2..maxZipVArity).joinToString("\n\n") { generateZipV(it) }
        val liftVBody = (2..maxArity).joinToString("\n\n") { generateLiftV(it) }

        val content = buildString {
            append(header)
            appendLine()
            appendLine(zipVBody)
            appendLine()
            appendLine(liftVBody)
        }
        outputFile.writeText(content)
        println("Generated ${outputFile.path}")
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (!project.hasProperty("skipSigning")) signAllPublications()
    coordinates(group.toString(), "kap-arrow", version.toString())

    pom {
        name.set("kap-arrow")
        description.set("Kotlin Applicative Parallelism — Arrow integration: validated DSL, Either/Nel bridges, raceEither")
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
