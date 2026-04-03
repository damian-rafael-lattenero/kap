plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    application
}

dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-arrow"))
    implementation(project(":kap-ksp-annotations"))
    implementation(libs.coroutines.core)
    ksp(project(":kap-ksp"))
}

application {
    mainClass.set("MainKt")
}
