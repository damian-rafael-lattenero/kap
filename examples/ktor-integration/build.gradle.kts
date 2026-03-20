plugins {
    kotlin("jvm")
    application
    kotlin("plugin.serialization") version "2.0.21"
}

dependencies {
    // Maven: implementation("io.github.damian-rafael-lattenero:kap-core:2.0.3")
    // Maven: implementation("io.github.damian-rafael-lattenero:kap-arrow:2.0.3")
    implementation(project(":kap-core"))
    implementation(project(":kap-arrow"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("io.ktor:ktor-server-status-pages:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

application {
    mainClass.set("MainKt")
}
