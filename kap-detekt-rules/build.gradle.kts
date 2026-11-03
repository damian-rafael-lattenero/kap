plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    // Supplied by the detekt runtime that loads the ruleset — the jar must not
    // bundle the API it implements.
    compileOnly(libs.detekt.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.detekt.test)
}

tasks.test {
    useJUnitPlatform()
}
