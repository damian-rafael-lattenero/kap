pluginManagement {
    repositories {
        gradlePluginPortal()
        // Plugin-classpath transitive deps live on Central (e.g. detekt 2.0's
        // io.github.detekt.sarif4k:sarif4k) — the portal only mirrors it, and its
        // redirect/CDN has flaked on CI. Central removes the single point of failure.
        mavenCentral()
    }
}

rootProject.name = "kap"
include(":kap-core")
include(":kap-resilience")
include(":kap-arrow")
include(":kap-kotest")
include(":kap-ktor")
include(":kap-ksp-annotations")
include(":kap-ksp")
include(":benchmarks")

include(":examples:ecommerce-checkout")
include(":examples:dashboard-aggregator")
include(":examples:validated-registration")
include(":examples:resilient-fetcher")
include(":examples:full-stack-order")
include(":examples:ktor-integration")
include(":examples:readme-examples")
include(":examples:ksp-demo")
include(":examples:real-world-http")
