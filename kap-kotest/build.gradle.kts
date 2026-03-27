plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":kap-core"))
    api(project(":kap-resilience"))
    compileOnly(project(":kap-arrow"))
    compileOnly(libs.arrow.core)

    api(libs.coroutines.test)
    api(kotlin("test"))

    testImplementation(project(":kap-arrow"))
    testImplementation(libs.arrow.core)
    testImplementation(libs.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    coordinates(group.toString(), "kap-kotest", version.toString())

    pom {
        name.set("kap-kotest")
        description.set("Test matchers and utilities for KAP — Kotlin Async Parallelism")
        url.set("https://github.com/damian-rafael-lattenero/kap")
        inceptionYear.set("2025")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("damian-rafael-lattenero")
                name.set("Damian Rafael Lattenero")
            }
        }
        scm {
            url.set("https://github.com/damian-rafael-lattenero/kap")
        }
    }
}
