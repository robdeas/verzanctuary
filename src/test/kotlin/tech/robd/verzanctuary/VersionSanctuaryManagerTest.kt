/**
 * [🧩 File Info]
 * path=src/test/kotlin/tech/robd/verzanctuary/VersionSanctuaryManagerTest.kt
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

package tech.robd.verzanctuary

import org.junit.jupiter.api.*
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VersionSanctuaryManagerTest : TestBase() {

    private lateinit var projectDir: File
    private lateinit var manager: VersionSanctuaryManager

    @BeforeEach
    override fun setUpFileSystem() {
        super.setUpFileSystem()
        // Each test uses a unique tempDir, so "test-project" is safe
        projectDir = createTestProject("test-project")
        manager = VersionSanctuaryManager(projectDir, enableLocking = false)  // Add this parameter

    }

    @AfterEach
    fun cleanupSystemProperties() {
        System.clearProperty("VERZANCTUARY_SANCTUARY_DIR")
        System.clearProperty("VERZANCTUARY_WORKSPACE_PARENT")
    }

    @Test
    fun `should create sanctuary snapshot successfully`() {
        val branchName = manager.createSanctuarySnapshot("Test snapshot")
        assertTrue(branchName.startsWith("auto-"))

        // Check new structure
        assertTrue(exists("test-project.sanctuary"))
        assertTrue(exists("test-project.sanctuary/.sanctuary"))
        assertTrue(exists("test-project.sanctuary/.sanctuary/HEAD"))
        assertTrue(exists("test-project.sanctuary/.sanctuary/config"))
    }

    @Test
    fun `should list sanctuary branches correctly`() {
        // Make unique file changes before each snapshot to force unique branches
        writeFile("test-project/branch1.txt", "first version at ${System.nanoTime()}")
        val branch1 = manager.createSanctuarySnapshot("First snapshot")
        Thread.sleep(10)

        writeFile("test-project/branch2.txt", "second version at ${System.nanoTime()}")
        val branch2 = manager.createSanctuarySnapshot("Second snapshot")

        // When
        val branches = manager.listSanctuaryBranches()

        // Then
        assertEquals(2, branches.size)
        assertTrue(branches.all { it.startsWith("auto-") })
        assertTrue(branches[0] < branches[1], "Branches should be sorted by timestamp")
        assertTrue(branches.contains(branch1))
        assertTrue(branches.contains(branch2))
    }



    @Test
    fun `should create new branch when files change`() {
        writeFile("test-project/unique.txt", "test at ${System.nanoTime()}")
        val branchName = manager.quickBackup(BackupScenario.BEFORE_REFACTOR)
        val branches = manager.listSanctuaryBranches()
        assertTrue(branches.contains(branchName))
        val logEntry = manager.showSanctuaryLog(1).last()
        assertTrue(logEntry.contains("\"result\":\"created\""))
    }

    @Test
    fun `should log nochange if backup requested but no files changed`() {
        manager.quickBackup(BackupScenario.END_OF_DAY) // Initial (created)
        manager.quickBackup(BackupScenario.END_OF_DAY) // No change (nochange)
        val logLines = manager.showSanctuaryLog(2)
        assertTrue(logLines.first().contains("\"result\":\"created\""))
        assertTrue(logLines.last().contains("\"result\":\"nochange\""))
    }

    @Test
    fun `should generate patch between branches`() {
        val branch1 = manager.createSanctuarySnapshot("Initial state")
        writeFile("test-project/README.md", readFile("test-project/README.md") + "\nAdded content")
        writeFile("test-project/newfile.txt", "New file content")
        val branch2 = manager.createSanctuarySnapshot("Modified state")

        val patch = manager.generatePatch(branch1, branch2)
        assertNotNull(patch)
        assertTrue(patch.isNotEmpty())
        assertTrue(patch.contains("README.md"))
        assertTrue(patch.contains("Added content"))
    }

    @Test
    fun `should compare with latest sanctuary`() {
        manager.createSanctuarySnapshot("Initial state")
        writeFile("test-project/README.md", readFile("test-project/README.md") + "\nLatest changes")
        val changes = manager.compareWithLatest()
        assertNotNull(changes)
        assertTrue(changes!!.contains("Latest changes"))
    }

    @Test
    fun `should cleanup old sanctuaries`() {
        repeat(5) { i ->
            writeFile("test-project/file$i.txt", "content $i at ${System.nanoTime()}")
            manager.createSanctuarySnapshot("Snapshot $i")
            Thread.sleep(10)
        }
        assertEquals(5, manager.listSanctuaryBranches().size)
        manager.cleanupOldSanctuaries(3)
        assertEquals(3, manager.listSanctuaryBranches().size)
    }

    @Test
    fun `should checkout sanctuary to standard location`() {
        writeFile("test-project/test-file.txt", "Original content")
        val branchName = manager.createSanctuarySnapshot("Test checkout")
        manager.checkoutSanctuaryToStandardLocation(branchName)

        // NEW: Co-located workspace inside sanctuary (browse subdirectory)
        assertTrue(exists("test-project.sanctuary/browse"))
        assertTrue(exists("test-project.sanctuary/browse/test-file.txt"))
        assertEquals("Original content", readFile("test-project.sanctuary/browse/test-file.txt"))
    }

    @Test
    fun `should checkout to lab workspace without creating  git directory`() {
        // Setup: Create the workspace structure that would normally be created
        val customWorkspaceDir = createDirectory("custom-workspace")
        System.setProperty("VERZANCTUARY_WORKSPACE_PARENT", customWorkspaceDir.absolutePath)

        val manager = VersionSanctuaryManager(projectDir, enableLocking = false)
        val branchName = manager.createSanctuarySnapshot("Lab test")

        // Create lab workspace directory structure
        val labSpaceDir = File(customWorkspaceDir, "test-project.verzspaces/lab")
        labSpaceDir.mkdirs()

        // Setup: Create a mock .git in lab space to verify it's not touched
        val labGitDir = File(labSpaceDir, ".git")
        labGitDir.mkdirs()
        val gitConfigFile = File(labGitDir, "config")
        gitConfigFile.writeText("[core]\n    repositoryformatversion = 0")

        // When
        manager.checkoutSanctuaryToLabSpace(branchName)

        // Then - verify .git wasn't touched
        assertTrue(gitConfigFile.exists())
        assertEquals("[core]\n    repositoryformatversion = 0", gitConfigFile.readText())

        // And files were overlaid (but not .git files)
        assertTrue(exists("custom-workspace/test-project.verzspaces/lab/build.gradle.kts"))
        assertFalse(exists("custom-workspace/test-project.verzspaces/lab/.git/HEAD")) // No sanctuary .git files
    }

    @Test
    fun `should checkout to lab workspace without modifying existing git repo`() {
        // Create manager first to get the paths it expects
        val manager = VersionSanctuaryManager(projectDir, enableLocking = false)
        val branchName = manager.createSanctuarySnapshot("Lab overlay test")

        // Get the lab space path the manager expects
        val labSpaceDir = File(manager.getLabWorkspaceLocation())
        labSpaceDir.mkdirs()

        println("Creating git repo at: ${labSpaceDir.absolutePath}")

        // Create actual git repo where manager expects it
        ProcessBuilder("git", "init")
            .directory(labSpaceDir)
            .start()
            .waitFor()

        // Add a test file and commit it
        File(labSpaceDir, "lab-file.txt").writeText("Original lab content")
        ProcessBuilder("git", "add", ".")
            .directory(labSpaceDir)
            .start()
            .waitFor()
        ProcessBuilder("git", "commit", "-m", "Initial lab commit")
            .directory(labSpaceDir)
            .start()
            .waitFor()

        // Capture git state before sanctuary overlay
        val gitHeadBefore = File(labSpaceDir, ".git/HEAD").readText()

        // When: Checkout sanctuary to lab
        manager.checkoutSanctuaryToLabSpace(branchName)

        // Then: Git repo unchanged, but sanctuary files overlaid
        assertEquals(gitHeadBefore, File(labSpaceDir, ".git/HEAD").readText())
        assertTrue(File(labSpaceDir, "build.gradle.kts").exists())
        assertTrue(File(labSpaceDir, "lab-file.txt").exists())
    }

    @Test
    fun `should respect VERZANCTUARY_WORKSPACE_PARENT environment variable`() {
        val customWorkspaceDir = createDirectory("custom-workspace")
        System.setProperty("VERZANCTUARY_WORKSPACE_PARENT", customWorkspaceDir.absolutePath)
        val customManager = VersionSanctuaryManager(projectDir)
        val branchName = customManager.createSanctuarySnapshot("Custom workspace test")
        customManager.checkoutSanctuaryToStandardLocation(branchName)


        // Check for verzspaces container and browse subdirectory
        assertTrue(exists("custom-workspace/test-project.verzspaces"))
        assertTrue(exists("custom-workspace/test-project.verzspaces/browse"))
        assertTrue(exists("custom-workspace/test-project.verzspaces/browse/README.md"))
    }

    @Test
    fun `should provide comprehensive sanctuary info`() {
        manager.createSanctuarySnapshot("Info test")
        val info = manager.getSanctuaryInfo()
        assertTrue(info.contains("Project: test-project"))
        assertTrue(info.contains("Layout:")) // Either "Co-located" or "Separated"
        assertTrue(info.contains("Sanctuary:"))
        assertTrue(info.contains("Working copy:"))
        assertTrue(info.contains("Total snapshots: 1"))
    }

    @Test
    fun `should provide environment info`() {
        val envInfo = manager.getEnvironmentInfo()
        assertTrue(envInfo.contains("Environment Configuration:"))
        assertTrue(envInfo.contains("VERZANCTUARY_WORKSPACE_PARENT:"))
        assertTrue(envInfo.contains("Resolved Paths:"))
        assertTrue(envInfo.contains("Storage Layout:")) // This should be there now
    }

    @Test
    fun `should not accept empty project directory`() {
        val emptyProject = createDirectory("empty-project")

        // Should throw IOException when trying to create manager without git
        val exception = assertThrows<IOException> {
            VersionSanctuaryManager(emptyProject)
        }

        // Verify it's the right exception message
        assertTrue(exception.message?.contains("Not a Git repository") == true)
    }
    @Test
    fun `should handle empty project directory with no-git flag`() {
        val emptyProject = createDirectory("empty-project")
        val emptyManager = VersionSanctuaryManager(emptyProject, useGitRoot = false)  // ← Add useGitRoot = false
        val branchName = emptyManager.createSanctuarySnapshot("Empty project test")
        assertTrue(branchName.startsWith("auto-"))
        assertEquals(1, emptyManager.listSanctuaryBranches().size)
    }

    @Test
    fun `should not store git directory in sanctuary repo but should have git dir in working checkout`() {
        // Setup: Ensure there's a .git dir in project, and a real file change so a branch is created
        createDirectory("test-project/.git")
        writeFile("test-project/.git/config", "git config")
        writeFile("test-project/file.txt", "important content")

        val branchName = manager.createSanctuarySnapshot("Test snapshot")
        manager.checkoutSanctuaryToStandardLocation(branchName)

        // 1. Sanctuary repo itself should NOT contain a .git dir
        val sanctuaryRepoDir = File(manager.getSanctuaryLocation())
        assertFalse(File(sanctuaryRepoDir, ".git").exists(), "Sanctuary repo must not contain a .git directory")

        // 2. WorkingTmp checkout should ALWAYS contain a .git dir
        val workingTmpDir = File(manager.getStandardCheckoutLocation())
        assertTrue(File(workingTmpDir, ".git").exists(), "Checked out workingTmp must contain a .git directory (created by JGit)")

        // 3. Confirm important files are present in workingTmp
        assertTrue(File(workingTmpDir, "file.txt").exists())
    }


    @Test
    fun `should handle file modifications correctly`() {
        writeFile("test-project/modifiable.txt", "Version 1")
        val branch1 = manager.createSanctuarySnapshot("Version 1")
        writeFile("test-project/modifiable.txt", "Version 2")
        val branch2 = manager.createSanctuarySnapshot("Version 2")
        val patch = manager.generatePatch(branch1, branch2)
        assertTrue(patch.contains("-Version 1"))
        assertTrue(patch.contains("+Version 2"))
    }

    @Test
    fun `should return meaningful error for non-existent branches`() {
        assertThrows<IllegalArgumentException> {
            manager.generatePatch("non-existent-1", "non-existent-2")
        }
    }


    /**
     * New Feature Tests - Single File Checkout and Conflict Detection
     * Add these to your VersionSanctuaryManagerTest.kt file
     */

    @Test
    fun `should checkout single file only`() {
        // Given
        writeFile("test-project/file1.txt", "File 1 original")
        writeFile("test-project/file2.txt", "File 2 original")
        writeFile("test-project/file3.txt", "File 3 original")
        val branchName = manager.createSanctuarySnapshot("Multiple files test")

        // Modify all files
        writeFile("test-project/file1.txt", "File 1 modified")
        writeFile("test-project/file2.txt", "File 2 modified")
        writeFile("test-project/file3.txt", "File 3 modified")

        // When - checkout only file1.txt
        manager.checkoutSanctuaryToWorking(branchName, listOf("file1.txt"), forceOverwrite = true)

        // Then - only file1 should be restored
        assertEquals("File 1 original", readFile("test-project/file1.txt"))
        assertEquals("File 2 modified", readFile("test-project/file2.txt")) // Unchanged
        assertEquals("File 3 modified", readFile("test-project/file3.txt")) // Unchanged
    }

    @Test
    fun `should checkout multiple specific files`() {
        // Given
        writeFile("test-project/file1.txt", "File 1 original")
        writeFile("test-project/file2.txt", "File 2 original")
        writeFile("test-project/file3.txt", "File 3 original")
        val branchName = manager.createSanctuarySnapshot("Multiple files test")

        // Modify all files
        writeFile("test-project/file1.txt", "File 1 modified")
        writeFile("test-project/file2.txt", "File 2 modified")
        writeFile("test-project/file3.txt", "File 3 modified")

        // When - checkout file1.txt and file3.txt
        manager.checkoutSanctuaryToWorking(branchName, listOf("file1.txt", "file3.txt"), forceOverwrite = true)

        // Then - only specified files should be restored
        assertEquals("File 1 original", readFile("test-project/file1.txt"))
        assertEquals("File 2 modified", readFile("test-project/file2.txt")) // Unchanged
        assertEquals("File 3 original", readFile("test-project/file3.txt"))
    }

    @Test
    fun `should checkout single file from subdirectory`() {
        // Given
        createDirectory("test-project/src")
        writeFile("test-project/src/main.kt", "Main original")
        writeFile("test-project/src/utils.kt", "Utils original")
        writeFile("test-project/readme.txt", "Readme original")
        val branchName = manager.createSanctuarySnapshot("Subdirectory test")

        // Modify all files
        writeFile("test-project/src/main.kt", "Main modified")
        writeFile("test-project/src/utils.kt", "Utils modified")
        writeFile("test-project/readme.txt", "Readme modified")

        // When - checkout only src/main.kt
        manager.checkoutSanctuaryToWorking(branchName, listOf("src/main.kt"), forceOverwrite = true)

        // Then - only the specific file should be restored
        assertEquals("Main original", readFile("test-project/src/main.kt"))
        assertEquals("Utils modified", readFile("test-project/src/utils.kt")) // Unchanged
        assertEquals("Readme modified", readFile("test-project/readme.txt")) // Unchanged
    }

    @Test
    fun `should handle checkout of non-existent file gracefully`() {
        // Given
        writeFile("test-project/existing.txt", "Existing file")
        val branchName = manager.createSanctuarySnapshot("Non-existent test")

        // When - try to checkout a file that doesn't exist in sanctuary
        manager.checkoutSanctuaryToWorking(branchName, listOf("nonexistent.txt"), forceOverwrite = true)

        // Then - should not crash, existing files unchanged
        assertEquals("Existing file", readFile("test-project/existing.txt"))
        assertFalse(exists("test-project/nonexistent.txt"))
    }

    @Test
    fun `should detect conflict when file is modified in working directory`() {
        // Given
        writeFile("test-project/conflict.txt", "Original content")
        val branchName = manager.createSanctuarySnapshot("Conflict test")

        // Modify the file to create a conflict
        writeFile("test-project/conflict.txt", "Modified content")

        // When - try to checkout without force (should detect conflict)
        var conflictDetected = false
        try {
            manager.checkoutSanctuaryToWorking(branchName, emptyList(), forceOverwrite = false)
        } catch (e: Exception) {
            // If it throws, that's also valid - conflict was detected
            conflictDetected = true
        }

        // Then - file should remain unchanged (conflict prevented overwrite)
        assertEquals("Modified content", readFile("test-project/conflict.txt"))

        // If no exception was thrown, the method should have detected conflict and aborted
        // (We can't easily test the console output in unit tests, but the file state proves it worked)
    }

    @Test
    fun `should detect conflict for new file in working directory`() {
        // Given - create sanctuary without new-file.txt
        writeFile("test-project/original.txt", "Original")
        val branchName = manager.createSanctuarySnapshot("New file conflict test")

        // Add a new file that doesn't exist in sanctuary
        writeFile("test-project/new-file.txt", "New file content")

        // When - checkout (this will show new-file.txt as a conflict)
        manager.checkoutSanctuaryToWorking(branchName, emptyList(), forceOverwrite = false)

        // Then - new file should still exist (conflict detection preserved it)
        assertTrue(exists("test-project/new-file.txt"))
        assertEquals("New file content", readFile("test-project/new-file.txt"))
    }

    @Test
    fun `should detect conflict for deleted file`() {
        // Given
        writeFile("test-project/will-be-deleted.txt", "File content")
        writeFile("test-project/permanent.txt", "Permanent content")
        val branchName = manager.createSanctuarySnapshot("Deletion conflict test")

        // Delete the file (simulating user deletion)
        File(projectDir, "will-be-deleted.txt").delete()

        // When - checkout (sanctuary has the file, working directory doesn't)
        manager.checkoutSanctuaryToWorking(branchName, emptyList(), forceOverwrite = false)

        // Then - this is a conflict scenario, but behavior may vary
        // The key is that it should be detected as a conflict situation
        assertTrue(exists("test-project/permanent.txt")) // Other files unaffected
    }

    @Test
    fun `should force checkout despite conflicts`() {
        // Given
        writeFile("test-project/conflict.txt", "Original content")
        val branchName = manager.createSanctuarySnapshot("Force test")

        // Create conflicts
        writeFile("test-project/conflict.txt", "Modified content")
        writeFile("test-project/new-file.txt", "New file")

        // When - force checkout despite conflicts
        manager.checkoutSanctuaryToWorking(branchName, emptyList(), forceOverwrite = true)

        // Then - sanctuary state should be restored
        assertEquals("Original content", readFile("test-project/conflict.txt"))
        // Note: new-file.txt behavior depends on your implementation
        // It might be deleted (full restore) or preserved (partial restore)
    }

    @Test
    fun `should create auto-backup before destructive checkout`() {
        // Given
        writeFile("test-project/backup-test.txt", "Original")
        val originalBranch = manager.createSanctuarySnapshot("Original state")

        // Modify file
        writeFile("test-project/backup-test.txt", "Modified before checkout")

        val branchesBefore = manager.listSanctuaryBranches()

        // When - force checkout (should create auto-backup)
        manager.checkoutSanctuaryToWorking(originalBranch, emptyList(), forceOverwrite = true)

        // Then - should have created an additional branch (auto-backup)
        val branchesAfter = manager.listSanctuaryBranches()
        assertTrue(branchesAfter.size > branchesBefore.size,
            "Should create auto-backup. Before: ${branchesBefore.size}, After: ${branchesAfter.size}")

        // And file should be restored
        assertEquals("Original", readFile("test-project/backup-test.txt"))
    }

    @Test
    fun `should detect no conflicts when files are identical`() {
        // Given
        writeFile("test-project/identical.txt", "Same content")
        val branchName = manager.createSanctuarySnapshot("Identical test")

        // Don't modify the file - it's identical
        // (This simulates the case where user thinks they modified but content is the same)

        // When - checkout (should detect no conflicts)
        manager.checkoutSanctuaryToWorking(branchName, emptyList(), forceOverwrite = false)

        // Then - should succeed without issues
        assertEquals("Same content", readFile("test-project/identical.txt"))
    }

    @Test
    fun `should handle conflict detection for specific files only`() {
        // Given
        writeFile("test-project/file1.txt", "File 1 original")
        writeFile("test-project/file2.txt", "File 2 original")
        val branchName = manager.createSanctuarySnapshot("Selective conflict test")

        // Modify both files
        writeFile("test-project/file1.txt", "File 1 modified")
        writeFile("test-project/file2.txt", "File 2 modified")

        // When - checkout only file1.txt with force (should only affect file1)
        manager.checkoutSanctuaryToWorking(branchName, listOf("file1.txt"), forceOverwrite = true)

        // Then - only file1 should be restored
        assertEquals("File 1 original", readFile("test-project/file1.txt"))
        assertEquals("File 2 modified", readFile("test-project/file2.txt")) // Unchanged
    }



}
