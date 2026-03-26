plugins {
    kotlin("jvm")
    alias(libs.plugins.jmh)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-resilience"))
    implementation(project(":kap-arrow"))
    implementation(libs.coroutines.core)
    implementation(libs.arrow.fx)
    implementation(libs.arrow.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
}

jmh {
    warmupIterations.set((findProperty("jmh.warmup") as? String)?.toIntOrNull() ?: 3)
    iterations.set((findProperty("jmh.iterations") as? String)?.toIntOrNull() ?: 5)
    fork.set((findProperty("jmh.fork") as? String)?.toIntOrNull() ?: 2)
    resultFormat.set("JSON")
    includes.set(listOf("kap.benchmarks.*"))
    jmhVersion.set("1.37")
}
