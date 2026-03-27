plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
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

    api(libs.ktor.server.core)
    implementation(libs.coroutines.core)
    compileOnly(libs.ktor.server.status.pages)

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.json)
    testImplementation(libs.ktor.server.status.pages)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.serialization.json)
    testImplementation(project(":kap-arrow"))
    testImplementation(libs.arrow.core)
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    coordinates(group.toString(), "kap-ktor", version.toString())

    pom {
        name.set("kap-ktor")
        description.set("Ktor integration plugin for KAP — Kotlin Async Parallelism")
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
