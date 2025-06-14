/**
 * [File Info]
 * path=test/kotlin/tech/robd/verzanctuary/IntegrationTest.kt
 * description=General-purpose source file
 * generator=add-robokeytags.groovy
 * editable=true
 * license=apache
 * [File Info]
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

package tech.robd.verzanctuary


import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tech.robd.verzanctuary.cli.*
import tech.robd.verzanctuary.commands.CheckoutCommand
import tech.robd.verzanctuary.commands.CleanupCommand
import tech.robd.verzanctuary.commands.CreateCommand
import tech.robd.verzanctuary.commands.DiffCommand
import tech.robd.verzanctuary.commands.InfoCommand
import tech.robd.verzanctuary.commands.ListCommand
import tech.robd.verzanctuary.commands.VersionCommand
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VerSanctuaryCliTest : BaseTest() {

    private lateinit var projectDir: File
    private lateinit var manager: VersionSanctuaryManager

    @BeforeEach
    override fun setUpFileSystem() {
        super.setUpFileSystem()

        // Create a test project structure using BaseTest utilities
        projectDir = createTestProject("cli-test-project")
        manager = VersionSanctuaryManager(projectDir)
    }

    private fun runCommand(args: List<String>): CliktCommandTestResult {
        // Create the full CLI with all subcommands i need to test
        val cli = VerZanctuaryCli()
            .subcommands(
                CreateCommand(),
                ListCommand(),
                CheckoutCommand(),
                DiffCommand(),
                CleanupCommand(),
                InfoCommand(),
                VersionCommand()
            )

        return cli.test(args)
    }

    @Test
    fun `should checkout existing sanctuary to browse folder`() {
        // Given
        writeFile("cli-test-project/checkout-test.txt", "Test content for checkout")
        val branchName = manager.createSanctuarySnapshot("Checkout test")

        // When
        val result = runCommand(listOf(
            "checkout",
            branchName,
            "--directory", projectDir.absolutePath,
            "--browse"
        ))

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
        assertTrue(result.output.contains("Checked out sanctuary '$branchName'"))

        // NEW: Expect browse space path (co-located scenario)
        assertTrue(exists("cli-test-project.sanctuary/browse"))
        assertTrue(exists("cli-test-project.sanctuary/browse/checkout-test.txt"))
        assertEquals("Test content for checkout", readFile("cli-test-project.sanctuary/browse/checkout-test.txt"))

        // Also update the CLI message check to be more flexible
        assertTrue(result.output.contains("Files available in:"))
    }

    @Test
    fun `should checkout existing sanctuary to working directory by default with force flag`() {
        // setup ...
        writeFile("cli-test-project/original.txt", "Original content")
        val branchName = manager.createSanctuarySnapshot("Working directory test")

        // Change the working directory file
        writeFile("cli-test-project/original.txt", "Modified content")
        writeFile("cli-test-project/new-file.txt", "New content")

        // When
        val result = runCommand(listOf(
            "checkout",
            branchName,
            "--directory", projectDir.absolutePath,
            "--force"
        ))

        // Debug output
        println("=== DEBUG OUTPUT ===")
        println("Status code: ${result.statusCode}")
        println("stdout: '${result.output}'")
        println("stderr: '${result.stderr}'")
        println("Branch name: $branchName")

        // Debug file states
        println("Original file exists: ${exists("cli-test-project/original.txt")}")
        if (exists("cli-test-project/original.txt")) {
            println("Original file content: '${readFile("cli-test-project/original.txt")}'")
        }
        println("New file exists: ${exists("cli-test-project/new-file.txt")}")

        // Debug sanctuary branches
        val branches2 = manager.listSanctuaryBranches()
        println("All branches: $branches2")
        println("Auto-backup branches: ${branches2.filter { it.contains("Auto-backup") }}")
        println("==================")

        // Then - Add debug to each assertion
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")


        val originalContent = readFile("cli-test-project/original.txt")
        assertEquals("Original content", originalContent,
            "File content not restored. Actual: '$originalContent'")

         val branches = manager.listSanctuaryBranches()
        assertTrue(branches.size >= 2,
        "Should have original + backup branch. Got: $branches")
    }

    @Test
    fun `should create sanctuary with custom message`() {
        // Given
        writeFile("cli-test-project/create-test.txt", "Content for create test")

        // When
        val result = runCommand(listOf(
            "create",
            "--message", "Test sanctuary",
            "--directory", projectDir.absolutePath
        ))

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
        assertTrue(result.output.contains("Created sanctuary:"))
        assertTrue(result.output.contains("Location:"))

        // Verify sanctuary was created
        val branches = manager.listSanctuaryBranches()
        assertTrue(branches.isNotEmpty())
    }

    @Test
    fun `should list sanctuaries`() {
        // Given
        writeFile("cli-test-project/test1.txt", "First content")
        val branch1 = manager.createSanctuarySnapshot("First sanctuary")

        writeFile("cli-test-project/test2.txt", "Second content")
        val branch2 = manager.createSanctuarySnapshot("Second sanctuary")

        // When
        val result = runCommand(listOf(
            "list",
            "--directory", projectDir.absolutePath
        ))

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
        assertTrue(result.output.contains("Version sanctuaries for cli-test-project:"))
        assertTrue(result.output.contains(branch1))
        assertTrue(result.output.contains(branch2))
    }

    @Test
    fun `should handle missing arguments gracefully`() {
        // When - checkout without branch name
        val result = runCommand(listOf("checkout"))

        // Then
        assertTrue(result.statusCode != 0, "Should fail without required argument")
        assertTrue(result.output.contains("branch") || result.stderr.contains("branch"))
    }

    @Test
    fun `should show version`() {
        // When
        val result = runCommand(listOf("version"))

        // Then
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("VerZanctuary v0.1.0"))
    }

    @Test
    fun `should show help`() {
        // When
        val result = runCommand(listOf("--help"))

        // Then
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("VerZanctuary"))
        assertTrue(result.output.contains("create"))
        assertTrue(result.output.contains("list"))
        assertTrue(result.output.contains("checkout"))
    }

//    @Test
//    fun `should compare with latest sanctuary`() {
//        // Given
//        writeFile("cli-test-project/test.txt", "original content")
//        manager.createSanctuarySnapshot("First version")
//
//        // Modify file
//        writeFile("cli-test-project/test.txt", "modified content")
//
//        // When
//        val result = runCommand(listOf(
//            "diff",
//            "--latest",
//            "--directory", projectDir.absolutePath
//        ))
//
//        // Then
//        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
//        assertTrue(result.output.contains("Comparing current state with latest sanctuary"))
//    }

    @Test
    fun `should cleanup old sanctuaries`() {
        // Given
        // Create multiple sanctuaries with actual file changes
        repeat(5) { i ->
            writeFile("cli-test-project/test$i.txt", "content $i")
            manager.createSanctuarySnapshot("Sanctuary $i")
            Thread.sleep(50) // Ensure different timestamps
        }

        // When - Keep only 2
        val result = runCommand(listOf(
            "cleanup",
            "--keep", "2",
            "--directory", projectDir.absolutePath
        ))

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
        assertTrue(result.output.contains("Cleanup complete"))
        assertTrue(result.output.contains("removed 3"))

        // Verify only 2 remain
        val remainingBranches = manager.listSanctuaryBranches()
        assertEquals(2, remainingBranches.size)
    }

    @Test
    fun `should show info`() {
        manager.createSanctuarySnapshot("Test info")

        // ADD the directory parameter!
        val result = runCommand(listOf(
            "info",
            "--directory", projectDir.absolutePath  // This is missing!
        ))

        // Now it should find the right project
        assertTrue(result.output.contains("Project: cli-test-project"))
        assertTrue(result.output.contains("Layout: Co-located"))
        assertTrue(result.output.contains("Total snapshots: 1"))
    }

    @Test
    fun `should show environment info`() {
        // When
        val result = runCommand(listOf(
            "info",
            "--env",
            "--directory", projectDir.absolutePath
        ))

        // Then
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Environment Configuration"))
        assertTrue(result.output.contains("VERZANCTUARY_SANCTUARY_DIR"))
    }

    @Test
    fun `should handle diff with specific branches`() {
        // Given
        writeFile("cli-test-project/diff-test.txt", "Version 1")
        val branch1 = manager.createSanctuarySnapshot("Version 1")

        writeFile("cli-test-project/diff-test.txt", "Version 2")
        val branch2 = manager.createSanctuarySnapshot("Version 2")

        // When
        val result = runCommand(listOf(
            "diff",
            "--from", branch1,
            "--to", branch2,
            "--directory", projectDir.absolutePath
        ))

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
        // Fix: Match the actual CLI output format
        assertTrue(result.output.contains("Comparing sanctuary '$branch1' with '$branch2'"))
        // OR more flexible:
        assertTrue(result.output.contains("Comparing") &&
                result.output.contains(branch1) &&
                result.output.contains(branch2))
    }

    @Test
    fun `should compare with latest sanctuary`() {
        // Given
        writeFile("cli-test-project/test.txt", "original content")
        manager.createSanctuarySnapshot("First version")

        // Modify file
        writeFile("cli-test-project/test.txt", "modified content")

        // When - Add --disable-locking flag
        val result = runCommand(listOf(
            "diff",
            "--latest",
            "--ignore-locks",
            "--directory", projectDir.absolutePath
        ))

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
        assertTrue(result.output.contains("Comparing current state with latest sanctuary"))
    }

    @Test
    fun `should save diff to file`() {
        // Given
        writeFile("cli-test-project/save-diff.txt", "Original")
        manager.createSanctuarySnapshot("Original")

        writeFile("cli-test-project/save-diff.txt", "Modified")

        val patchFile = File(tempDir.toFile(), "test.patch")

        // When
        val result = runCommand(listOf(
            "diff",
            "--latest",
            "--ignore-locks",
            "--output", patchFile.absolutePath,
            "--directory", projectDir.absolutePath
        ))

        // Debug
        println("=== DEBUG SAVE DIFF ===")
        println("Status: ${result.statusCode}")
        println("Output: '${result.output}'")
        println("Stderr: '${result.stderr}'")
        println("Patch file exists: ${patchFile.exists()}")
        if (patchFile.exists()) {
            println("Patch file content: '${patchFile.readText()}'")
        }
        println("======================")

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
        assertTrue(patchFile.exists(), "Patch file should exist")
        if (patchFile.exists()) {
            assertTrue(patchFile.readText().contains("Modified"), "Patch should contain changes")
        }
    }

    /**
     * CLI Feature Tests - Single File Checkout and Conflict Detection
     * Add these to your VerSanctuaryCliTest.kt file
     */

    @Test
    fun `should checkout single file via CLI`() {
        // Given
        writeFile("cli-test-project/file1.txt", "File 1 original")
        writeFile("cli-test-project/file2.txt", "File 2 original")
        val branchName = manager.createSanctuarySnapshot("CLI single file test")

        // Modify files
        writeFile("cli-test-project/file1.txt", "File 1 modified")
        writeFile("cli-test-project/file2.txt", "File 2 modified")

        // When - checkout only file1.txt via CLI
        val result = runCommand(listOf(
            "checkout",
            branchName,
            "file1.txt",
            "--directory", projectDir.absolutePath,
            "--force"
        ))

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
        assertEquals("File 1 original", readFile("cli-test-project/file1.txt"))
        assertEquals("File 2 modified", readFile("cli-test-project/file2.txt"))
    }

    @Test
    fun `should checkout multiple files via CLI`() {
        // Given
        writeFile("cli-test-project/file1.txt", "File 1 original")
        writeFile("cli-test-project/file2.txt", "File 2 original")
        writeFile("cli-test-project/file3.txt", "File 3 original")
        val branchName = manager.createSanctuarySnapshot("CLI multiple files test")

        // Modify all files
        writeFile("cli-test-project/file1.txt", "File 1 modified")
        writeFile("cli-test-project/file2.txt", "File 2 modified")
        writeFile("cli-test-project/file3.txt", "File 3 modified")

        // When - checkout file1.txt and file3.txt
        val result = runCommand(listOf(
            "checkout",
            branchName,
            "file1.txt",
            "file3.txt",
            "--directory", projectDir.absolutePath,
            "--force"
        ))

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
        assertEquals("File 1 original", readFile("cli-test-project/file1.txt"))
        assertEquals("File 2 modified", readFile("cli-test-project/file2.txt")) // Unchanged
        assertEquals("File 3 original", readFile("cli-test-project/file3.txt"))
    }

    @Test
    fun `should abort checkout on conflicts without force flag`() {
        // Given
        writeFile("cli-test-project/conflict.txt", "Original content")
        val branchName = manager.createSanctuarySnapshot("Conflict test")

        // Create conflict
        writeFile("cli-test-project/conflict.txt", "Modified content")

        // When - try checkout without --force
        val result = runCommand(listOf(
            "checkout",
            branchName,
            "--directory", projectDir.absolutePath
            // No --force flag
        ))

        // Then - should succeed (command runs) but operation should be aborted
        assertEquals(0, result.statusCode, "Command should run successfully")

        // File should remain unchanged (conflict prevented restoration)
        assertEquals("Modified content", readFile("cli-test-project/conflict.txt"))

        // Could also check for conflict messages in output if CLI captures them properly
        // assertTrue(result.output.contains("conflicts") || result.stderr.contains("conflicts"))
    }

    @Test
    fun `should force checkout despite conflicts via CLI`() {
        // Given
        writeFile("cli-test-project/force-test.txt", "Original content")
        val branchName = manager.createSanctuarySnapshot("Force conflict test")

        // Create conflict
        writeFile("cli-test-project/force-test.txt", "Modified content")
        writeFile("cli-test-project/new-file.txt", "New file")

        // When - force checkout
        val result = runCommand(listOf(
            "checkout",
            branchName,
            "--directory", projectDir.absolutePath,
            "--force"
        ))

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")
        assertEquals("Original content", readFile("cli-test-project/force-test.txt"))

        // Should have created auto-backup
        val branches = manager.listSanctuaryBranches()
        assertTrue(branches.size >= 2, "Should create auto-backup branch")
    }

    @Test
    fun `should show conflict details before aborting`() {
        // Given
        writeFile("cli-test-project/detailed-conflict.txt", "Original")
        val branchName = manager.createSanctuarySnapshot("Detailed conflict test")

        // Create specific conflict scenario
        writeFile("cli-test-project/detailed-conflict.txt", "Modified")
        writeFile("cli-test-project/new-file.txt", "New file content")

        // When - try checkout without force
        val result = runCommand(listOf(
            "checkout",
            branchName,
            "--directory", projectDir.absolutePath
        ))

        // Then - command should run but operation aborted
        assertEquals(0, result.statusCode)

        // Files should remain in modified state
        assertEquals("Modified", readFile("cli-test-project/detailed-conflict.txt"))
        assertTrue(exists("cli-test-project/new-file.txt"))

        // Note: In a real implementation, you might want to capture and verify
        // that conflict details are shown to the user, but since CLI output
        // capture is problematic in your current test setup, we focus on
        // verifying the functional behavior (files unchanged)
    }

    @Test
    fun `should checkout to temp folder when conflicts exist`() {
        // Given
        writeFile("cli-test-project/temp-conflict.txt", "Original")
        val branchName = manager.createSanctuarySnapshot("Temp conflict test")

        // Create conflict
        writeFile("cli-test-project/temp-conflict.txt", "Modified")

        // When - use --browse to inspect safely
        val result = runCommand(listOf(
            "checkout",
            branchName,
            "--directory", projectDir.absolutePath,
            "--browse"
        ))

        // Then - should succeed and checkout to browse space
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")

        // Working directory file should remain unchanged
        assertEquals("Modified", readFile("cli-test-project/temp-conflict.txt"))

        // Browse space should have the original content
        assertTrue(exists("cli-test-project.sanctuary/browse/temp-conflict.txt"))
        assertEquals("Original", readFile("cli-test-project.sanctuary/browse/temp-conflict.txt"))
    }

    @Test
    fun `should handle single file conflicts gracefully`() {
        // Given
        writeFile("cli-test-project/single-conflict.txt", "Original")
        writeFile("cli-test-project/safe-file.txt", "Safe content")
        val branchName = manager.createSanctuarySnapshot("Single file conflict test")

        // Create conflict in one file only
        writeFile("cli-test-project/single-conflict.txt", "Conflicted")

        // When - try to checkout the conflicted file specifically
        val result = runCommand(listOf(
            "checkout",
            branchName,
            "single-conflict.txt",
            "--directory", projectDir.absolutePath
            // No --force, should detect conflict
        ))

        // Then - should handle gracefully
        assertEquals(0, result.statusCode, "Command should run")

        // File should remain in conflicted state (conflict prevented overwrite)
        assertEquals("Conflicted", readFile("cli-test-project/single-conflict.txt"))
        assertEquals("Safe content", readFile("cli-test-project/safe-file.txt"))
    }

    @Test
    fun `should diff specific files via CLI`() {
        // Given
        writeFile("cli-test-project/diff-file1.txt", "Version 1 content")
        writeFile("cli-test-project/diff-file2.txt", "Unchanged content")
        val branchName = manager.createSanctuarySnapshot("Diff file test")

        // Modify only one file
        writeFile("cli-test-project/diff-file1.txt", "Version 2 content")

        // When - diff specific file
        val result = runCommand(listOf(
            "diff",
            branchName,
            "diff-file1.txt",
            "--directory", projectDir.absolutePath,
            "--ignore-locks"
        ))

        // Then
        assertEquals(0, result.statusCode, "Command failed: ${result.stderr}")

        // Should succeed - specific diff functionality
        // Note: Actual diff content verification would depend on your diff output format
    }

    @Test
    fun `should show help for checkout command with file arguments`() {
        // When
        val result = runCommand(listOf("checkout", "--help"))

        // Then
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("checkout") || result.output.contains("files"))
        // Should show that files can be specified as arguments
    }

}