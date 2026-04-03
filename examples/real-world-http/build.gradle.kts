plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-resilience"))
    implementation(project(":kap-ksp-annotations"))
    implementation(libs.coroutines.core)
    ksp(project(":kap-ksp"))

    implementation(libs.ktor.server.core)
    implementation("io.ktor:ktor-client-core:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-client-content-negotiation:${libs.versions.ktor.get()}")
    implementation(libs.ktor.serialization.json)
    implementation(libs.serialization.json)
}

application {
    mainClass.set("MainKt")
}
