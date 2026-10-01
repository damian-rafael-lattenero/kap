import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.binary.compat)
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.versions.plugin)
}

group = "io.github.damian-rafael-lattenero"
version = "3.0.0"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

allprojects {
    repositories {
        mavenCentral()
    }
}

dependencies {
    dokka(project(":kap-core"))
    dokka(project(":kap-resilience"))
    dokka(project(":kap-arrow"))
}

dokka {
    moduleName.set("KAP")
    dokkaPublications.html {
        outputDirectory.set(layout.projectDirectory.dir("docs/api"))
    }
}

// Root alias so `./gradlew benchmarks` works from the repo root.
tasks.register("benchmarks") {
    group = "verification"
    description = "Runs the JMH benchmark suite (see benchmarks/README for smoke-run flags)."
    dependsOn(":benchmarks:jmh")
}

// ── Static analysis: detekt on library modules ─────────────────────────────
// Existing findings live in each module's detekt-baseline.xml (tolerated until
// Fase 1 cleanup); any NEW violation fails the build.
val detektModules = listOf(
    "kap-core", "kap-resilience", "kap-arrow", "kap-ktor", "kap-kotest",
    "kap-ksp", "kap-ksp-annotations", "benchmarks",
)

subprojects {
    if (name in detektModules) {
        apply(plugin = "io.gitlab.arturbosch.detekt")
        configure<DetektExtension> {
            buildUponDefaultConfig = true
            // KMP: analyze common + platform main sources; JVM-only: src/main (+jmh for benchmarks).
            val kotlinDirs = listOf(
                "src/commonMain/kotlin", "src/jsMain/kotlin", "src/wasmJsMain/kotlin",
                "src/nativeMain/kotlin", "src/jvmMain/kotlin", "src/main/kotlin",
                "src/jmh/kotlin",
            ).filter { file(it).exists() }
            source.setFrom(kotlinDirs)
            // Tests are out of scope for now (Fase 1 decides).
        }
    }
}

apiValidation {
    ignoredProjects.addAll(listOf("benchmarks", "ecommerce-checkout", "dashboard-aggregator",
        "validated-registration", "resilient-fetcher", "full-stack-order", "ktor-integration", "readme-examples",
        "kap-kotest", "kap-ktor", "kap-ksp-annotations", "kap-ksp", "ksp-demo"))
}
