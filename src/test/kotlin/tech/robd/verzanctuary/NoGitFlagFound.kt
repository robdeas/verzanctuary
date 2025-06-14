package tech.robd.verzanctuary

/**
 * Copyright 2025 Rob Deas
 *
 * Tests specifically for --no-git flag functionality
 * Separated to avoid large file issues and improve organization
 */
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.io.IOException
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NoGitFlagTest : BaseTest() {

    private lateinit var projectDir: File

    @BeforeEach
    override fun setUpFileSystem() {
        super.setUpFileSystem()
        projectDir = createTestProject("no-git-test-project")
    }

    @Test
    fun `should use current directory with useGitRoot false`() {
        // Given - create a subdirectory within the git project
        val subDir = File(projectDir, "subdirectory")
        subDir.mkdirs()

        // When - create manager with useGitRoot = false from subdirectory
        val managerNoGit = VersionSanctuaryManager(
            workingDirectory = subDir,
            useGitRoot = false
        )

        // Then - should use the subdirectory as project root
        assertEquals(subDir.absolutePath, managerNoGit.projectRoot.absolutePath)
        assertFalse(managerNoGit.isGitRepository)
    }

    @Test
    fun `should work in non-git directory with useGitRoot false`() {
        // Given - create a temporary directory outside git
        val nonGitDir = File(tempDir.toFile(), "non-git-test")
        nonGitDir.mkdirs()

        try {
            // When - create manager with useGitRoot = false
            val manager = VersionSanctuaryManager(
                workingDirectory = nonGitDir,
                useGitRoot = false
            )

            // Then - should work fine
            assertFalse(manager.isGitRepository)
            assertEquals(nonGitDir.absolutePath, manager.projectRoot.absolutePath)

        } finally {
            nonGitDir.deleteRecursively()
        }
    }

    @Test
    fun `should throw exception in non-git directory with useGitRoot true`() {
        // Given - create a temporary directory outside git
        val nonGitDir = File(tempDir.toFile(), "non-git-fail")
        nonGitDir.mkdirs()

        try {
            // When/Then - should throw IOException
            assertFailsWith<IOException> {
                VersionSanctuaryManager(
                    workingDirectory = nonGitDir,
                    useGitRoot = true
                )
            }
        } finally {
            nonGitDir.deleteRecursively()
        }
    }

    @Test
    fun `should detect git repository correctly in subdirectory`() {
        // Given - create nested directory structure in git project
        val deepDir = File(projectDir, "deep/nested/directory")
        deepDir.mkdirs()

        // When - create manager with git root detection from deep directory
        val manager = VersionSanctuaryManager(
            workingDirectory = deepDir,
            useGitRoot = true
        )

        // Then - should find the git root correctly
        assertTrue(manager.isGitRepository)
        assertEquals(projectDir.absolutePath, manager.projectRoot.absolutePath)
    }

    @Test
    fun `should use subdirectory name when useGitRoot is false`() {
        // Given - create a subdirectory with specific name
        val subDir = File(projectDir, "my-special-subdir")
        subDir.mkdirs()

        // When - create manager without git root detection
        val manager = VersionSanctuaryManager(
            workingDirectory = subDir,
            useGitRoot = false
        )

        // Then - should use subdirectory as project root
        assertEquals("my-special-subdir", manager.projectRoot.name)
        assertEquals(subDir.absolutePath, manager.projectRoot.absolutePath)
    }
}