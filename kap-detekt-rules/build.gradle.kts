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
    // Supplied by the detekt runtime that loads the ruleset — the jar must not
    // bundle the API it implements.
    compileOnly(libs.detekt.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.detekt.test)
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral()
    if (!project.hasProperty("skipSigning")) signAllPublications()
    coordinates(group.toString(), "kap-detekt-rules", version.toString())

    pom {
        name.set("kap-detekt-rules")
        description.set("Detekt rules for KAP — Kotlin Async Parallelism: bans manual wait loops and hand-rolled concurrency in kap-using code (the kap doctrine as a mechanical gate).")
        url.set("https://github.com/damian-rafael-lattenero/kap")
        inceptionYear.set("2026")

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
