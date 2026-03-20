plugins {
    kotlin("jvm")
    id("me.champeau.jmh") version "0.7.3"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-resilience"))
    implementation(project(":kap-arrow"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.arrow-kt:arrow-fx-coroutines:2.2.2.1")
    implementation("io.arrow-kt:arrow-core:2.2.2.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

jmh {
    warmupIterations.set((findProperty("jmh.warmup") as? String)?.toIntOrNull() ?: 3)
    iterations.set((findProperty("jmh.iterations") as? String)?.toIntOrNull() ?: 5)
    fork.set((findProperty("jmh.fork") as? String)?.toIntOrNull() ?: 2)
    resultFormat.set("JSON")
    includes.set(listOf("applicative.benchmarks.*"))
    jmhVersion.set("1.37")
}
