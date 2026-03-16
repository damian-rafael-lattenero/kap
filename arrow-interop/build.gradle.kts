plugins {
    kotlin("jvm")
    `maven-publish`
    signing
}

group = "org.applicative.coroutines"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":"))
    api("io.arrow-kt:arrow-core:1.2.4")
    implementation("io.arrow-kt:arrow-fx-coroutines:1.2.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "coroutines-applicatives-arrow"
            from(components["java"])

            pom {
                name.set("coroutines-applicatives-arrow")
                description.set("Arrow interop for coroutines-applicatives")
                url.set("https://github.com/dlattenero/coroutines-applicatives")

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("dlattenero")
                        name.set("D. Lattenero")
                    }
                }

                scm {
                    url.set("https://github.com/dlattenero/coroutines-applicatives")
                }
            }
        }
    }
}
