plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser()
        nodejs()
    }
    wasmJs {
        browser()
        nodejs()
    }
    linuxX64()

    val xcodeAvailable = try {
        val selectProc = ProcessBuilder("xcode-select", "-p").start()
        val selectOk = selectProc.waitFor() == 0
        if (!selectOk) false
        else {
            val xcrunProc = ProcessBuilder("xcrun", "xcodebuild", "-version").start()
            xcrunProc.waitFor() == 0
        }
    } catch (_: Exception) { false }

    if (xcodeAvailable) {
        macosX64()
        macosArm64()
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kap-core"))
            // `Schedule` (the polling cadence of `emitted(...).every(...)`) lives in resilience.
            api(project(":kap-resilience"))
            implementation(libs.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.kotest.property)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (!project.hasProperty("skipSigning")) signAllPublications()
    coordinates(group.toString(), "kap-stream", version.toString())

    pom {
        name.set("kap-stream")
        description.set("KAP — backpressured streams: named strategies, Kap stages, explicit cancellation")
        inceptionYear.set("2025")
        url.set("https://github.com/damian-rafael-lattenero/kap")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("damian-rafael-lattenero")
                name.set("Damian Rafael Lattenero")
                url.set("https://github.com/damian-rafael-lattenero")
            }
        }
        scm {
            url.set("https://github.com/damian-rafael-lattenero/kap")
            connection.set("scm:git:git://github.com/damian-rafael-lattenero/kap.git")
            developerConnection.set("scm:git:ssh://git@github.com/damian-rafael-lattenero/kap.git")
        }
    }
}
