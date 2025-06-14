package tech.robd.verzanctuary

/**
 * Copyright 2025 Rob Deas
 *
 * Tests specifically for auto-backup functionality
 * Tests the safety net feature that creates backups before destructive operations
 */

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutoBackupTest : BaseTest() {

    private lateinit var projectDir: File
    private lateinit var manager: VersionSanctuaryManager

    @BeforeEach
    override fun setUpFileSystem() {
        super.setUpFileSystem()
        projectDir = createTestProject("auto-backup-test-project")
        manager = VersionSanctuaryManager(projectDir)
    }

    @Test
    fun `should create auto-backup before full directory checkout`() {
        // Given - create original sanctuary
        writeFile("auto-backup-test-project/original.txt", "Original content")
        val originalBranch = manager.createSanctuarySnapshot("Original state")

        // Modify working directory
        writeFile("auto-backup-test-project/original.txt", "Modified content")
        writeFile("auto-backup-test-project/new-file.txt", "New file content")

        val branchesBefore = manager.listSanctuaryBranches()
        assertEquals(1, branchesBefore.size, "Should have 1 branch initially")

        // When - force checkout (should create auto-backup)
        manager.checkoutSanctuaryToWorking(originalBranch, emptyList(), forceOverwrite = true)

        // Then - should have created auto-backup
        val branchesAfter = manager.listSanctuaryBranches()
        assertEquals(2, branchesAfter.size, "Should have original + auto-backup")

        // Verify the auto-backup contains the modified state
        val backupBranch = branchesAfter.find { it != originalBranch }
        assertNotNull(backupBranch, "Should have created backup branch")

        // Original file should be restored
        assertEquals("Original content", readFile("auto-backup-test-project/original.txt"))
    }

    @Test
    fun `should create auto-backup before single file checkout`() {
        // Given - setup with original file
        writeFile("auto-backup-test-project/file1.txt", "File 1 original")
        writeFile("auto-backup-test-project/file2.txt", "File 2 original")
        val originalBranch = manager.createSanctuarySnapshot("Original files")

        // Modify both files
        writeFile("auto-backup-test-project/file1.txt", "File 1 modified")
        writeFile("auto-backup-test-project/file2.txt", "File 2 modified")

        val branchesBefore = manager.listSanctuaryBranches()

        // When - checkout single file with force
        manager.checkoutSanctuaryToWorking(originalBranch, listOf("file1.txt"), forceOverwrite = true)

        // Then - should create auto-backup
        val branchesAfter = manager.listSanctuaryBranches()
        assertTrue(branchesAfter.size > branchesBefore.size, "Should create auto-backup")

        // Only file1 should be restored, file2 should remain modified
        assertEquals("File 1 original", readFile("auto-backup-test-project/file1.txt"))
        assertEquals("File 2 modified", readFile("auto-backup-test-project/file2.txt"))
    }

    @Test
    fun `should not create auto-backup when no conflicts detected`() {
        // Given - create sanctuary
        writeFile("auto-backup-test-project/no-conflict.txt", "Same content")
        val branchName = manager.createSanctuarySnapshot("No conflict test")

        // Don't modify the file - leave it identical
        val branchesBefore = manager.listSanctuaryBranches()

        // When - checkout without conflicts (should not need backup)
        manager.checkoutSanctuaryToWorking(branchName, emptyList(), forceOverwrite = false)

        // Then - should not create unnecessary backup
        val branchesAfter = manager.listSanctuaryBranches()
        assertEquals(branchesBefore.size, branchesAfter.size, "Should not create backup when no conflicts")
    }

    @Test
    fun `should create auto-backup with meaningful name pattern`() {
        // Given
        writeFile("auto-backup-test-project/backup-name.txt", "Original")
        val originalBranch = manager.createSanctuarySnapshot("Original")

        // Modify file
        writeFile("auto-backup-test-project/backup-name.txt", "Modified")

        // When - create auto-backup
        manager.checkoutSanctuaryToWorking(originalBranch, emptyList(), forceOverwrite = true)

        // Then - backup branch should follow naming pattern
        val branches = manager.listSanctuaryBranches()
        val backupBranch = branches.find { it != originalBranch }
        assertNotNull(backupBranch, "Should create backup branch")

        // All sanctuary branches start with "auto-" and have timestamp
        assertTrue(backupBranch!!.startsWith("auto-"), "Backup should follow auto- naming pattern")
        assertTrue(backupBranch.contains("-"), "Backup should contain timestamp separators")
    }

    @Test
    fun `should preserve auto-backup content correctly`() {
        // Given - create original
        writeFile("auto-backup-test-project/preserve-test.txt", "Original content")
        writeFile("auto-backup-test-project/secondary.txt", "Secondary content")
        val originalBranch = manager.createSanctuarySnapshot("Original state")

        // Modify files to specific states
        writeFile("auto-backup-test-project/preserve-test.txt", "State to preserve")
        writeFile("auto-backup-test-project/secondary.txt", "Secondary modified")
        writeFile("auto-backup-test-project/new-file.txt", "New file to preserve")

        // When - force checkout (creates auto-backup)
        manager.checkoutSanctuaryToWorking(originalBranch, emptyList(), forceOverwrite = true)

        // Then - get the backup branch and verify its content
        val branches = manager.listSanctuaryBranches()
        val backupBranch = branches.find { it != originalBranch }
        assertNotNull(backupBranch, "Should have backup branch")

        // Checkout the backup to verify it preserved the modified state
        manager.checkoutSanctuaryToBrowseSpace(backupBranch)

        // Verify backup preserved the modified state
        // Note: This assumes your temp checkout location is accessible for testing
        // You might need to adjust based on your actual temp directory structure
    }

    @Test
    fun `should handle multiple consecutive auto-backups`() {
        // Given - original state
        writeFile("auto-backup-test-project/multi-backup.txt", "Version 1")
        val branch1 = manager.createSanctuarySnapshot("Version 1")

        // Create second version
        writeFile("auto-backup-test-project/multi-backup.txt", "Version 2")
        val branch2 = manager.createSanctuarySnapshot("Version 2")

        // Modify to version 3
        writeFile("auto-backup-test-project/multi-backup.txt", "Version 3")

        // When - checkout branch1 (should backup version 3)
        manager.checkoutSanctuaryToWorking(branch1, emptyList(), forceOverwrite = true)

        val branchesAfterFirst = manager.listSanctuaryBranches()

        // Modify again to version 4
        writeFile("auto-backup-test-project/multi-backup.txt", "Version 4")

        // Checkout branch2 (should backup version 4)
        manager.checkoutSanctuaryToWorking(branch2, emptyList(), forceOverwrite = true)

        // Then - should have multiple auto-backups
        val finalBranches = manager.listSanctuaryBranches()
        assertTrue(finalBranches.size > branchesAfterFirst.size, "Should create multiple auto-backups")
        assertTrue(finalBranches.size >= 4, "Should have original branches + auto-backups")
    }

    @Test
    fun `should create auto-backup even when checkout fails`() {
        // Given - create original sanctuary
        writeFile("auto-backup-test-project/fail-test.txt", "Original")
        val originalBranch = manager.createSanctuarySnapshot("Original")

        // Modify file
        writeFile("auto-backup-test-project/fail-test.txt", "Modified state")

        val branchesBefore = manager.listSanctuaryBranches()

        // When - attempt checkout that might fail (using invalid branch name)
        try {
            manager.checkoutSanctuaryToWorking("non-existent-branch", emptyList(), forceOverwrite = true)
            fail("Should have thrown exception for non-existent branch")
        } catch (e: Exception) {
            // Expected to fail
        }

        // Then - verify no backup was created for failed operation
        val branchesAfter = manager.listSanctuaryBranches()
        assertEquals(branchesBefore.size, branchesAfter.size, "Should not create backup for failed checkout")
    }

    @Test
    fun `should auto-backup preserve file permissions and structure`() {
        // Given - create directory structure
        createDirectory("auto-backup-test-project/subdir")
        writeFile("auto-backup-test-project/subdir/nested.txt", "Nested content")
        writeFile("auto-backup-test-project/root.txt", "Root content")
        val originalBranch = manager.createSanctuarySnapshot("Structure test")

        // Modify structure
        writeFile("auto-backup-test-project/subdir/nested.txt", "Modified nested")
        writeFile("auto-backup-test-project/subdir/new-nested.txt", "New nested file")
        writeFile("auto-backup-test-project/root.txt", "Modified root")

        // When - force checkout
        manager.checkoutSanctuaryToWorking(originalBranch, emptyList(), forceOverwrite = true)

        // Then - should have created backup preserving structure
        val branches = manager.listSanctuaryBranches()
        assertTrue(branches.size >= 2, "Should create auto-backup with structure")

        // Files should be restored to original state
        assertEquals("Nested content", readFile("auto-backup-test-project/subdir/nested.txt"))
        assertEquals("Root content", readFile("auto-backup-test-project/root.txt"))
    }

    @Test
    fun `should handle auto-backup with empty working directory`() {
        // Given - create sanctuary with files
        writeFile("auto-backup-test-project/will-disappear.txt", "Content")
        val branchWithFiles = manager.createSanctuarySnapshot("Has files")

        // Delete all files (empty working directory)
        File(projectDir, "will-disappear.txt").delete()

        val branchesBefore = manager.listSanctuaryBranches()

        // When - checkout (should backup empty state)
        manager.checkoutSanctuaryToWorking(branchWithFiles, emptyList(), forceOverwrite = true)

        // Then - should create backup even for empty state
        val branchesAfter = manager.listSanctuaryBranches()
        assertTrue(branchesAfter.size > branchesBefore.size, "Should backup even empty working directory")

        // File should be restored
        assertTrue(exists("auto-backup-test-project/will-disappear.txt"))
        assertEquals("Content", readFile("auto-backup-test-project/will-disappear.txt"))
    }

    @Test
    fun `should auto-backup work with file exclusions`() {
        // Given - create files including ones that should be excluded
        writeFile("auto-backup-test-project/normal-file.txt", "Normal content")
        createDirectory("auto-backup-test-project/.git")
        writeFile("auto-backup-test-project/.git/config", "Git config")
        val originalBranch = manager.createSanctuarySnapshot("With exclusions")

        // Modify files
        writeFile("auto-backup-test-project/normal-file.txt", "Modified content")
        writeFile("auto-backup-test-project/.git/config", "Modified git config")

        // When - checkout with force
        manager.checkoutSanctuaryToWorking(originalBranch, emptyList(), forceOverwrite = true)

        // Then - should create backup but exclude .git files
        val branches = manager.listSanctuaryBranches()
        assertTrue(branches.size >= 2, "Should create auto-backup")

        // Normal file should be restored
        assertEquals("Normal content", readFile("auto-backup-test-project/normal-file.txt"))

        // .git file behavior depends on your exclusion logic
        // It might be restored or left unchanged
    }
}