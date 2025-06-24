/**
 * [🧩 File Info]
 * path=src/test/kotlin/tech/robd/verzanctuary/IntegrationTest.kt
 * description=IntegrationTest tests.
 * editable=true
 * license=apache
 * [/🧩 File Info]
 */

/**
 * Copyright 2025 Rob Deas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.robd.verzanctuary.integration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tech.robd.verzanctuary.BackupScenario
import tech.robd.verzanctuary.VersionSanctuaryManager
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests that simulate real-world usage scenarios
 */
class IntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.resolve("real-project").toFile()
        setupRealisticProject()
    }

    private fun setupRealisticProject() {
        projectDir.mkdirs()

        // Initialize real git repository
        val gitDir = File(projectDir, ".git")
        if (gitDir.exists() && !gitDir.isDirectory) {
            println("WARNING: .git exists as a file. Deleting...")
            gitDir.delete()
        }
        gitDir.mkdirs()
        File(gitDir, "HEAD").writeText("ref: refs/heads/main\n")
        File(gitDir, "refs/heads").mkdirs()


        // Create a realistic Kotlin project structure
        val srcDir = File(projectDir, "src/main/kotlin/com/example")
        srcDir.mkdirs()

        val testDir = File(projectDir, "src/test/kotlin/com/example")
        testDir.mkdirs()

        val resourcesDir = File(projectDir, "src/main/resources")
        resourcesDir.mkdirs()

        // Project files
        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                kotlin("jvm") version "2.1.20"
                application
            }
            
            group = "com.example"
            version = "1.0.0"
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
                testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
            }
            
            application {
                mainClass.set("com.example.MainKt")
            }
        """.trimIndent())

        File(projectDir, "README.md").writeText("""
            # Example Project
            
            This is a realistic test project for VerZanctuary integration testing.
            
            ## Features
            - Kotlin application
            - JUnit tests
            - Gradle build
            
            ## Usage
            ```bash
            ./gradlew run
            ```
        """.trimIndent())

        File(projectDir, "gradle.properties").writeText("""
            kotlin.code.style=official
            org.gradle.parallel=true
            org.gradle.caching=true
        """.trimIndent())

        // Source files
        File(srcDir, "Main.kt").writeText("""
            package com.example
            
            import kotlin.random.Random
            
            fun main() {
                println("Hello from Example Project!")
                
                val service = ExampleService()
                val result = service.processData("integration-test")
                println("Result: ${'$'}result")
            }
        """.trimIndent())

        File(srcDir, "ExampleService.kt").writeText("""
            package com.example
            
            class ExampleService {
                fun processData(input: String): String {
                    return "Processed: ${'$'}input"
                }
                
                fun calculateScore(data: List<Int>): Double {
                    return data.average()
                }
            }
        """.trimIndent())

        File(srcDir, "DataModel.kt").writeText("""
            package com.example
            
            data class User(
                val id: Long,
                val name: String,
                val email: String
            )
            
            data class Project(
                val name: String,
                val description: String,
                val users: List<User>
            )
        """.trimIndent())

        // Test files
        File(testDir, "ExampleServiceTest.kt").writeText("""
            package com.example
            
            import org.junit.jupiter.api.Test
            import org.junit.jupiter.api.Assertions.*
            
            class ExampleServiceTest {
                private val service = ExampleService()
                
                @Test
                fun `should process data correctly`() {
                    val result = service.processData("test")
                    assertEquals("Processed: test", result)
                }
                
                @Test
                fun `should calculate average score`() {
                    val scores = listOf(1, 2, 3, 4, 5)
                    val average = service.calculateScore(scores)
                    assertEquals(3.0, average)
                }
            }
        """.trimIndent())

        // Resources
        File(resourcesDir, "application.properties").writeText("""
            app.name=Example Project
            app.version=1.0.0
            debug.enabled=false
        """.trimIndent())

        File(resourcesDir, "data.json").writeText("""
            {
                "users": [
                    {"id": 1, "name": "Alice", "email": "alice@example.com"},
                    {"id": 2, "name": "Bob", "email": "bob@example.com"}
                ]
            }
        """.trimIndent())

        // Configuration files
        File(projectDir, ".gitignore").writeText("""
            .gradle/
            build/
            *.log
            .DS_Store
            *.sanctuary/
            sanctuary.workingTmp/
        """.trimIndent())
    }

    @Test
    fun `should handle end of day workflow with cleanup`() {
        val manager = VersionSanctuaryManager(projectDir, enableLocking = false)

        // Simulate a full day of development
        val sanctuaries = mutableListOf<String>()

        // Morning: initial state
        sanctuaries.add(manager.quickBackup(BackupScenario.WORKING_STATE))
        Thread.sleep(100) // Increased from 50ms for more reliable timing

        // Multiple development iterations
        repeat(8) { iteration ->
            // Make some changes - ensure each change is unique and meaningful
            val iterationFile = File(projectDir, "iteration-$iteration.txt")
            iterationFile.writeText("Development iteration $iteration at ${System.currentTimeMillis()}")

            // Also modify existing files to ensure Git sees changes
            val mainFile = File(projectDir, "src/main/kotlin/com/example/Main.kt")
            val currentContent = mainFile.readText()
            mainFile.writeText(currentContent + "\n// Iteration $iteration change")

            // Various scenarios throughout the day
            val scenario = when (iteration % 4) {
                0 -> BackupScenario.BEFORE_REFACTOR
                1 -> BackupScenario.BEFORE_AI_CONSULT
                2 -> BackupScenario.BEFORE_EXPERIMENT
                else -> BackupScenario.WORKING_STATE
            }

            sanctuaries.add(manager.quickBackup(scenario))
            Thread.sleep(100) // Increased timing to ensure different timestamps
        }

        // End of day - make one final change to ensure it's different
        val endOfDayFile = File(projectDir, "end-of-day-summary.md")
        endOfDayFile.writeText("""
        # End of Day Summary
        
        - Completed 8 development iterations
        - Created ${sanctuaries.size} sanctuaries
        - Ready for tomorrow
        
        Generated at: ${System.currentTimeMillis()}
    """.trimIndent())

        sanctuaries.add(manager.quickBackup(BackupScenario.END_OF_DAY))

        // Debug: Print sanctuary info
        println("Created sanctuaries: ${sanctuaries.size}")
        sanctuaries.forEachIndexed { index, sanctuary ->
            println("  $index: $sanctuary")
        }

        // Verify we have all sanctuaries
        val allBranches = manager.listSanctuaryBranches()
        println("Total branches found: ${allBranches.size}")
        assertEquals(10, allBranches.size, "Expected 10 sanctuaries (1 initial + 8 iterations + 1 end of day), but got ${allBranches.size}")

        // Verify all our created sanctuaries are in the branch list
        sanctuaries.forEach { sanctuary ->
            assertTrue(allBranches.contains(sanctuary), "Sanctuary $sanctuary not found in branch list")
        }

        // Cleanup - keep only last 5
        val beforeCleanupCount = allBranches.size
        manager.cleanupOldSanctuaries(5)

        val afterCleanup = manager.listSanctuaryBranches()
        assertEquals(5, afterCleanup.size, "Expected 5 sanctuaries after cleanup, but got ${afterCleanup.size}")

        // Verify the most recent ones were kept (should be the last 5 from our list)
        val expectedKept = sanctuaries.takeLast(5)
        expectedKept.forEach { sanctuary ->
            assertTrue(afterCleanup.contains(sanctuary), "Recent sanctuary $sanctuary should have been kept after cleanup")
        }

        // Verify the older ones were removed
        val expectedRemoved = sanctuaries.dropLast(5)
        expectedRemoved.forEach { sanctuary ->
            assertFalse(afterCleanup.contains(sanctuary), "Old sanctuary $sanctuary should have been removed during cleanup")
        }

        // Final verification: end of day sanctuary should definitely be kept
        assertTrue(afterCleanup.contains(sanctuaries.last()), "End of day sanctuary should be kept")

        println("Cleanup successful: removed ${beforeCleanupCount - afterCleanup.size} old sanctuaries")
    }


    @Test
    fun `should handle environment variable configuration`() {
        // Set up custom directories
        val customSanctuaryDir = tempDir.resolve("custom-sanctuaries").toFile()
        val customWorkspaceDir = tempDir.resolve("custom-workspace").toFile()

        customSanctuaryDir.mkdirs()
        customWorkspaceDir.mkdirs()

        System.setProperty("VERZANCTUARY_SANCTUARY_DIR", customSanctuaryDir.absolutePath)
        System.setProperty("VERZANCTUARY_WORKSPACE_PARENT", customWorkspaceDir.absolutePath)

        try {
            val manager = VersionSanctuaryManager(projectDir, enableLocking = false)

            // Create sanctuary
            val sanctuary = manager.createSanctuarySnapshot("Environment test")

            // Verify sanctuary metadata is in custom location
            val sanctuaryMetadata = customSanctuaryDir.resolve("real-project.sanctuary")
            assertTrue(sanctuaryMetadata.exists())
            assertTrue(sanctuaryMetadata.resolve(".sanctuary").exists())
            assertTrue(sanctuaryMetadata.resolve(".sanctuary/HEAD").exists())

            // Test checkout to custom workspace
            manager.checkoutSanctuaryToStandardLocation(sanctuary)

            // NEW: Use verzspaces container structure for separated workspace
            val verzspacesContainer = customWorkspaceDir.resolve("real-project.verzspaces")
            val browseWorkspace = verzspacesContainer.resolve("browse")

            assertTrue(verzspacesContainer.exists())
            assertTrue(browseWorkspace.exists())
            assertTrue(browseWorkspace.resolve("build.gradle.kts").exists())

            // Verify environment info shows custom paths
            val envInfo = manager.getEnvironmentInfo()
            assertTrue(envInfo.contains(customSanctuaryDir.absolutePath))
            assertTrue(envInfo.contains(customWorkspaceDir.absolutePath))

        } finally {
            // Clean up environment variables
            System.clearProperty("VERZANCTUARY_SANCTUARY_DIR")
            System.clearProperty("VERZANCTUARY_WORKSPACE_PARENT")
        }
    }

    @Test
    fun `should handle large project with many files`() {
        // Create a larger project structure
        val deepDir = File(projectDir, "src/main/kotlin/com/example/deep/nested/package")
        deepDir.mkdirs()

        // Create many files
        repeat(20) { i ->
            val file = File(deepDir, "File$i.kt")
            file.writeText("""
                package com.example.deep.nested.package
                
                class File$i {
                    fun method$i(): String = "result$i"
                    
                    companion object {
                        const val CONSTANT$i = $i
                    }
                }
            """.trimIndent())
        }

        // Create binary files
        val resourcesDir = File(projectDir, "src/main/resources")
        val binaryFile = File(resourcesDir, "test.bin")
        binaryFile.writeBytes(ByteArray(1024) { it.toByte() })

        val manager = VersionSanctuaryManager(projectDir, enableLocking = false)

        // Create sanctuary with many files
        val sanctuary = manager.createSanctuarySnapshot("Large project test")

        // Verify sanctuary was created successfully
        assertTrue(sanctuary.startsWith("auto-"))

        val branches = manager.listSanctuaryBranches()
        assertEquals(1, branches.size)

        // Test checkout with many files
        manager.checkoutSanctuaryToStandardLocation(sanctuary)


        // FIXED: Use co-located workspace path
        val workspaceDir = tempDir.resolve("real-project.sanctuary/browse").toFile()
        assertTrue(workspaceDir.exists())

        // Verify all files were copied
        val workspaceDeepDir = File(workspaceDir, "src/main/kotlin/com/example/deep/nested/package")
        assertTrue(workspaceDeepDir.exists())
        assertEquals(20, workspaceDeepDir.listFiles()?.size ?: 0)

        // Verify binary file was copied correctly
        val workspaceBinaryFile = File(workspaceDir, "src/main/resources/test.bin")
        assertTrue(workspaceBinaryFile.exists())
        assertEquals(1024, workspaceBinaryFile.length())
    }

    @Test
    fun `should preserve file permissions and attributes`() {
        // Create executable script
        val scriptFile = File(projectDir, "build.sh")
        scriptFile.writeText("""
            #!/bin/bash
            echo "Building project..."
            ./gradlew build
        """.trimIndent())

        // Make executable (if on Unix-like system)
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            scriptFile.setExecutable(true)
        }

        val manager = VersionSanctuaryManager(projectDir, enableLocking = false)
        val sanctuary = manager.createSanctuarySnapshot("Permission test")

        // Checkout and verify permissions
        manager.checkoutSanctuaryToStandardLocation(sanctuary)

        // FIXED: Use co-located workspace path
        val workspaceDir = tempDir.resolve("real-project.sanctuary/browse").toFile()
        val workspaceScript = File(workspaceDir, "build.sh")

        assertTrue(workspaceScript.exists())
        assertEquals(scriptFile.readText(), workspaceScript.readText())

        // Check executable permission (if on Unix-like system)
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            assertEquals(scriptFile.canExecute(), workspaceScript.canExecute())
        }
    }
}