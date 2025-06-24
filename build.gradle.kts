plugins {
    kotlin("jvm") version "2.1.20"
    `maven-publish`
    signing
    application
    id("org.graalvm.buildtools.native") version "0.10.1"
}

group = "tech.robd"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")

    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    // optional support for rendering markdown in help messages
    implementation("com.github.ajalt.clikt:clikt-markdown:5.0.1")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.0")
    testImplementation("org.junit.platform:junit-platform-suite:1.10.2")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.slf4j:slf4j-simple:2.0.9")

}

application {
    mainClass.set("tech.robd.verzanctuary.cli.VerZanctuaryCliKt")
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()

    // Enable preview features for testing (useful for FFI experimentation)
    jvmArgs("--enable-preview")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "tech.robd.verzanctuary.cli.VerZanctuaryCliKt",
            "Implementation-Title" to "VerZanctuary",
            "Implementation-Version" to project.version,
            "Multi-Release" to "true"
        )
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Remove all possible signature files from META-INF, including provider files and manifest-only jars
    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/versions/**",
        "META-INF/*.EC",
        "META-INF/*.LIST",
        "META-INF/LICENSE*",
        "META-INF/NOTICE*"
    )
    // For extra certainty, remove all signature files recursively (older Gradle requires the above style, recent Gradle is fine with wildcards)
    exclude({ fileTreeElement ->
        val path = fileTreeElement.path
        path.startsWith("META-INF/") && (
                path.endsWith(".SF") ||
                        path.endsWith(".DSA") ||
                        path.endsWith(".RSA") ||
                        path.endsWith(".EC") ||
                        path.endsWith(".LIST")
                )
    })
}


// Enable preview features for runtime (Java 21 FFI, etc.)
tasks.withType<JavaExec> {
    jvmArgs("--enable-preview")
}

tasks.named<JavaExec>("run") {
    jvmArgs("--enable-preview")
}


graalvmNative {
    binaries {
        named("main") {
            imageName.set("verz")
            mainClass.set("tech.robd.verzanctuary.cli.VerZanctuaryCliKt")

            val os = org.gradle.internal.os.OperatingSystem.current()
            if (os.isLinux) {
                // Linux: use musl for portability
                val forceMusl = System.getenv("FORCE_MUSL") == "1" || System.getProperty("forceMusl", "false") == "true"

                if ( file("/etc/alpine-release").exists() || forceMusl) {
                    // On Alpine (musl) or if forced
                    buildArgs.addAll(
                        "--no-fallback",
                        "--install-exit-handlers",
                        "--enable-preview",
                        "-H:+ReportExceptionStackTraces",
                        "-H:IncludeResources=.*\\.properties",
                        "--static",
                        "--libc=musl"
                    )
                } else {
                    // On Ubuntu, Fedora, Debian, etc. (glibc)
                    buildArgs.addAll(
                        "--no-fallback",
                        "--install-exit-handlers",
                        "--enable-preview",
                        "-H:+ReportExceptionStackTraces",
                        "-H:IncludeResources=.*\\.properties"
                    )
                }
            } else if (os.isWindows) {
                // Windows: default args
                buildArgs.addAll(
                    "--no-fallback",
                    "--install-exit-handlers",
                    "--enable-preview",
                    "-H:+ReportExceptionStackTraces",
                    "-H:IncludeResources=.*\\.properties"
                )
            } else {
                // macOS or others: safe defaults (adjust if you want)
                buildArgs.addAll(
                    "--no-fallback",
                    "--install-exit-handlers",
                    "--enable-preview",
                    "-H:+ReportExceptionStackTraces",
                    "-H:IncludeResources=.*\\.properties"
                )
            }
        }
    }
}

// Custom tasks for development
tasks.register("createSanctuary") {
    group = "sanctuary"
    description = "Create a sanctuary of current project state"

    doLast {
        exec {
            commandLine("java", "-jar", "build/libs/verzanctuary-${version}.jar", "create", "--scenario", "WORKING_STATE")
        }
    }
}

tasks.register("listSanctuaries") {
    group = "sanctuary"
    description = "List all project sanctuaries"

    doLast {
        exec {
            commandLine("java", "-jar", "build/libs/verzanctuary-${version}.jar", "list")
        }
    }
}

// Distribution tasks
tasks.register<Zip>("createDistribution") {
    group = "distribution"
    description = "Create distribution package"

    from("build/libs/verzanctuary-${version}.jar")
    from("README.md")
    from("LICENSE")
    from("examples/")

    archiveFileName.set("verzanctuary-${version}.zip")
    destinationDirectory.set(file("build/distributions"))
}

// GitHub Actions friendly tasks
tasks.register("githubRelease") {
    group = "release"
    description = "Prepare artifacts for GitHub release"
    dependsOn("jar", "createDistribution")

    doLast {
        println("Release artifacts ready in build/distributions/")
        println("- verzanctuary-${version}.jar")
        println("- verzanctuary-${version}.zip")
    }
}