plugins {
    kotlin("jvm")
    application
}

dependencies {
    // Maven: implementation("io.github.damian-rafael-lattenero:kap-core:2.0.3")
    implementation(project(":kap-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application {
    mainClass.set("MainKt")
}
