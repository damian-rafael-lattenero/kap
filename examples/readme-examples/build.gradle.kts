plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-resilience"))
    implementation(project(":kap-arrow"))
    implementation(libs.coroutines.core)
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx)
}

application {
    mainClass.set("MainKt")
}
