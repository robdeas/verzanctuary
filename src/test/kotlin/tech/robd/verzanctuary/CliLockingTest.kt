/**
 * [File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/data/SanctuaryState.kt
 * description=General-purpose source file
 * editable=true
 * license=apache
 * [File Info]
 */

/**
 * [🧩 File Info]
 * path=src/test/kotlin/tech/robd/verzanctuary/CliLockingTest.kt
 * description=CliLockingTest tests.
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

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import tech.robd.verzanctuary.cli.*
import tech.robd.verzanctuary.commands.CheckoutCommand
import tech.robd.verzanctuary.commands.CleanupCommand
import tech.robd.verzanctuary.commands.CreateCommand
import tech.robd.verzanctuary.commands.DiffCommand
import tech.robd.verzanctuary.commands.InfoCommand
import tech.robd.verzanctuary.commands.ListCommand
import tech.robd.verzanctuary.commands.LockCommand
import tech.robd.verzanctuary.commands.LockStatusCommand
import tech.robd.verzanctuary.commands.StatusCommand
import tech.robd.verzanctuary.commands.UnlockCommand
import tech.robd.verzanctuary.commands.VersionCommand
import tech.robd.verzanctuary.lock.SanctuaryLockException
import tech.robd.verzanctuary.lock.SanctuaryLockManager
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CliLockingTest : TestBase() {

    private lateinit var projectDir: File
    private lateinit var manager: VersionSanctuaryManager

    @BeforeEach
    override fun setUpFileSystem() {
        super.setUpFileSystem()
        projectDir = createTestProject("cli-locking-test")
        manager = VersionSanctuaryManager(projectDir, enableLocking = false) // For setup only
    }

    private fun runCLI(args: List<String>) = VerZanctuaryCli()
        .subcommands(
            CreateCommand(),
            ListCommand(),
            CheckoutCommand(),
            DiffCommand(),
            CleanupCommand(),
            InfoCommand(),
            StatusCommand(),
            VersionCommand(),
            LockCommand().subcommands(
                UnlockCommand(),
                LockStatusCommand()
            )
        ).test(args)


    @Test
    fun `CLI should fail when sanctuary is locked`() {
        // Setup: Create a sanctuary first
        writeFile("cli-locking-test/test.txt", "test content")
        manager.createSanctuarySnapshot("Setup snapshot")

        // Manually acquire lock
        val sanctuaryDir = projectDir.parentFile.resolve("cli-locking-test.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)
        assertTrue(lockManager.acquireLock("manual_lock"))

        try {
            // CLI operation should fail due to lock
            val exception = assertThrows<SanctuaryLockException> {
                runCLI(listOf(
                    "create",
                    "-m", "Should fail",
                    "--directory", projectDir.absolutePath
                ))
            }

            // Verify the exception contains the expected message
            assertTrue(exception.message!!.contains("Cannot acquire lock"))
            assertTrue(exception.message!!.contains("verz lock unlock"))

        } finally {
            lockManager.releaseLock()
        }
    }


    @Test
    fun `CLI should work with ignore-locks flag`() {
        // Setup: Create a sanctuary first
        writeFile("cli-locking-test/test.txt", "test content")
        manager.createSanctuarySnapshot("Setup snapshot")

        // Manually acquire lock
        val sanctuaryDir = projectDir.parentFile.resolve("cli-locking-test.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)
        assertTrue(lockManager.acquireLock("manual_lock"))

        try {
            // CLI operation should succeed with --ignore-locks
            val result = runCLI(listOf(
                "create",
                "-m", "Should work",
                "--ignore-locks",
                "--directory", projectDir.absolutePath
            ))

            assertEquals(0, result.statusCode, "Command should succeed with --ignore-locks: ${result.stderr}")
            assertTrue(result.output.contains("Created sanctuary:"), "Should create sanctuary successfully")
        } finally {
            lockManager.releaseLock()
        }
    }

    @Test
    fun `lock status command should show correct status`() {
        val result = runCLI(listOf(
            "lock", "status",
            "--directory", projectDir.absolutePath
        ))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Not locked"), "Should show not locked initially")
    }

    @Test
    fun `lock status should show lock info when locked`() {
        // Manually acquire lock
        val sanctuaryDir = projectDir.parentFile.resolve("cli-locking-test.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)
        assertTrue(lockManager.acquireLock("test_lock_status"))

        try {
            val result = runCLI(listOf(
                "lock", "status",
                "--directory", projectDir.absolutePath
            ))

            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains("LOCKED"), "Should show locked status")
            assertTrue(result.output.contains("test_lock_status"), "Should show operation name")
        } finally {
            lockManager.releaseLock()
        }
    }

    @Test
    fun `unlock command should remove lock`() {
        // Manually acquire lock
        val sanctuaryDir = projectDir.parentFile.resolve("cli-locking-test.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)
        assertTrue(lockManager.acquireLock("test_unlock"))
        assertTrue(lockManager.isLocked())

        // Use CLI to unlock
        val result = runCLI(listOf(
            "lock", "unlock", "--force",
            "--directory", projectDir.absolutePath
        ))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Lock removed successfully"), "Should confirm lock removal")

        // Verify lock is actually removed
        assertFalse(lockManager.isLocked(), "Lock should be removed")
    }

    @Test
    fun `unlock command should handle no lock gracefully`() {
        val result = runCLI(listOf(
            "lock", "unlock", "--force",
            "--directory", projectDir.absolutePath
        ))

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("not locked"), "Should handle no lock case")
    }

    @Test
    fun `unlock without force should require confirmation`() {
        // Manually acquire lock
        val sanctuaryDir = projectDir.parentFile.resolve("cli-locking-test.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)
        assertTrue(lockManager.acquireLock("test_no_force"))

        try {
            val result = runCLI(listOf(
                "lock", "unlock",  // No --force flag
                "--directory", projectDir.absolutePath
            ))

            assertEquals(0, result.statusCode) // Command succeeds but doesn't unlock
            assertTrue(result.output.contains("Use --force"), "Should require --force flag")
            assertTrue(lockManager.isLocked(), "Lock should still exist without --force")
        } finally {
            lockManager.releaseLock()
        }
    }

    @Test
    fun `read-only commands should work even when locked`() {
        // Setup: Create a sanctuary first
        writeFile("cli-locking-test/test.txt", "test content")
        manager.createSanctuarySnapshot("Setup snapshot")

        // Manually acquire lock
        val sanctuaryDir = projectDir.parentFile.resolve("cli-locking-test.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)
        assertTrue(lockManager.acquireLock("test_readonly"))

        try {
            // List command should work (read-only)
            val listResult = runCLI(listOf(
                "list",
                "--directory", projectDir.absolutePath
            ))
            assertEquals(0, listResult.statusCode, "List command should work when locked")

            // Info command should work (read-only)
            val infoResult = runCLI(listOf(
                "info",
                "--directory", projectDir.absolutePath
            ))
            assertEquals(0, infoResult.statusCode, "Info command should work when locked")

            // Status command should work (read-only)
            val statusResult = runCLI(listOf(
                "status",
                "--directory", projectDir.absolutePath
            ))
            assertEquals(0, statusResult.statusCode, "Status command should work when locked")

        } finally {
            lockManager.releaseLock()
        }
    }
}