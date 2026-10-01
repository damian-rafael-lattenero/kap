plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    application
}

dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-ksp-annotations"))
    implementation(libs.coroutines.core)
    ksp(project(":kap-ksp"))

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    kspTest(project(":kap-ksp"))
}

application {
    mainClass.set("MainKt")
}

// Dry-run: `./gradlew :examples:ksp-demo:kspKotlin -PkapDump` logs the
// generated builders instead of writing them — inspect before compiling.
ksp {
    if (project.hasProperty("kapDump")) arg("kap.dump", "true")
}
