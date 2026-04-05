plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.binary.compat)
    alias(libs.plugins.ksp) apply false
}

group = "io.github.damian-rafael-lattenero"
version = "2.7.0"

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

apiValidation {
    ignoredProjects.addAll(listOf("benchmarks", "ecommerce-checkout", "dashboard-aggregator",
        "validated-registration", "resilient-fetcher", "full-stack-order", "ktor-integration", "readme-examples",
        "kap-kotest", "kap-ktor", "kap-ksp-annotations", "kap-ksp", "ksp-demo"))
}
