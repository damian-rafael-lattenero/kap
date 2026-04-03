plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    application
}

dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-resilience"))
    implementation(project(":kap-arrow"))
    implementation(project(":kap-ksp-annotations"))
    implementation(libs.coroutines.core)
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx)
    ksp(project(":kap-ksp"))
}

application {
    mainClass.set("MainKt")
}
