plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(project(":kap-ksp-annotations"))

    // Self-processing: the fixtures in src/test/kotlin are processed by this
    // module's own processor (kspTest), and GoldenTest.kt diffs the generated
    // output against the committed snapshots in src/test/resources/golden/.
    add("kspTest", project(":kap-ksp"))
    testImplementation(project(":kap-ksp-annotations"))
    testImplementation(project(":kap-core"))
    // arrow on the test classpath turns on validated-builder generation,
    // so goldens cover both the plain and the Validated code paths.
    testImplementation(project(":kap-arrow"))
    testImplementation(libs.arrow.core)
    testImplementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
    testImplementation(kotlin("test"))
}

mavenPublishing {
    publishToMavenCentral()
    if (!project.hasProperty("skipSigning")) signAllPublications()
    coordinates(group.toString(), "kap-ksp", version.toString())

    pom {
        name.set("kap-ksp")
        description.set("KSP processor for KAP — generates type-safe wrappers to prevent same-type parameter swaps")
        inceptionYear.set("2025")
        url.set("https://github.com/damian-rafael-lattenero/kap")

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
