import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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
version = "4.2.0"

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

// ── Built with the newest LTS, runs on 21+ ──────────────────────────────────
// Every module declares jvmToolchain(25) (the compiler JDK — and, since the
// detekt 2.0 upgrade, the Gradle daemon's JDK too: there is a single JVM in
// the build now). The BYTECODE target stays 21 so the published artifacts keep
// working for JDK 21 consumers, the examples resolve the library variants, and
// the ABI validator keeps reading the class files.
val bytecodeTarget = 21
subprojects {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(bytecodeTarget.toString()))
            freeCompilerArgs.add("-Xjdk-release=$bytecodeTarget")
        }
    }
    tasks.withType<JavaCompile>().configureEach { options.release.set(bytecodeTarget) }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.toVersion(bytecodeTarget)
            targetCompatibility = JavaVersion.toVersion(bytecodeTarget)
        }
    }
}

// ── Static analysis: detekt on library modules ─────────────────────────────
// Existing findings live in each module's detekt-baseline.xml (tolerated until
// Fase 1 cleanup); any NEW violation fails the build. detekt 2.0 runs its
// analysis in isolated workers on any JDK >= 17 — no daemon-version dance —
// and creates type-resolved tasks per compilation by default, so the old
// custom commonMain TR task is gone.
val detektModules = listOf(
    "kap-core", "kap-resilience", "kap-arrow", "kap-ktor", "kap-kotest",
    "kap-ksp", "kap-ksp-annotations", "benchmarks",
)

subprojects {
    if (name in detektModules) {
        apply(plugin = "dev.detekt")
        configure<DetektExtension> {
            buildUponDefaultConfig = true
            val baselineFile = file("detekt-baseline.xml")
            if (baselineFile.exists()) baseline.set(baselineFile)
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

// Aggregates every detekt flavour: the plain `detekt` pass (extension sources)
// plus the type-resolved per-compilation tasks detekt 2.0 generates —
// detektMain for JVM modules, detektMainJvm for KMP ones (the jvm compilation
// includes the common sources, superseding the old custom commonMain TR task).
// CI gate entry point.
val detektAll = tasks.register("detektAll") {
    group = "verification"
    description = "All detekt checks: plain + per-compilation type-resolved."
    val detektTaskNames = listOf("detekt", "detektMain", "detektMainJvm")
    detektModules.forEach { moduleName ->
        val sp = subprojects.find { it.name == moduleName } ?: return@forEach
        detektTaskNames.forEach { taskName ->
            if (taskName in sp.tasks.names) dependsOn("${sp.path}:$taskName")
        }
    }
}

apiValidation {
    ignoredProjects.addAll(listOf("benchmarks", "ecommerce-checkout", "dashboard-aggregator",
        "validated-registration", "resilient-fetcher", "full-stack-order", "ktor-integration", "readme-examples",
        "kap-kotest", "kap-ktor", "kap-ksp-annotations", "kap-ksp", "ksp-demo"))
}
