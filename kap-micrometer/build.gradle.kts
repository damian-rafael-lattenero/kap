plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    api(project(":kap-core"))
    // The public API exposes MeterRegistry: consumers must see the Micrometer types.
    api(libs.micrometer.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.micrometer.registry.prometheus)
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral()
    if (!project.hasProperty("skipSigning")) signAllPublications()
    coordinates(group.toString(), "kap-micrometer", version.toString())

    pom {
        name.set("kap-micrometer")
        description.set("Micrometer MeterRegistry implementation of KAP's Meter SPI — Kotlin Async Parallelism")
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
            connection.set("scm:git:git://github.com/damian-rafael-lattenero/kap.git")
            developerConnection.set("scm:git:ssh://git@github.com/damian-rafael-lattenero/kap.git")
        }
    }
}
