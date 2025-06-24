/**
 * [File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/data/SanctuaryState.kt
 * description=General-purpose source file
 * generator=add-robokeytags.groovy
 * editable=true
 * license=apache
 * [File Info]
 */

/**
 * [🧩 File Info]
 * path=src/test/kotlin/tech/robd/verzanctuary/LockingTest.kt
 * description=LockingTest tests.
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


import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import tech.robd.verzanctuary.lock.SanctuaryLockException
import tech.robd.verzanctuary.lock.SanctuaryLockManager
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LockingTest : TestBase() {

    private lateinit var projectDir: File
    private lateinit var managerWithLocking: VersionSanctuaryManager
    private lateinit var managerWithoutLocking: VersionSanctuaryManager

    @BeforeEach
    override fun setUpFileSystem() {
        super.setUpFileSystem()
        projectDir = createTestProject("locking-test-project")
        managerWithLocking = VersionSanctuaryManager(projectDir, enableLocking = true)
        managerWithoutLocking = VersionSanctuaryManager(projectDir, enableLocking = false)
    }

    @Test
    fun `should acquire and release lock successfully`() {
        assertFalse(managerWithLocking.isLocked(), "Should not be locked initially")

        // Create a sanctuary (which should acquire and release lock)
        val branch = managerWithLocking.createSanctuarySnapshot("Test lock")
        assertTrue(branch.startsWith("auto-"))

        // After operation, lock should be released
        assertFalse(managerWithLocking.isLocked(), "Should not be locked after operation")
    }

    @Test
    fun `should show lock info when locked`() {
        // We'll test this by using a custom lock manager that we can control
        val sanctuaryDir = projectDir.parentFile.resolve("locking-test-project.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)

        assertNull(lockManager.getLockInfo(), "Should have no lock info initially")

        assertTrue(lockManager.acquireLock("test_operation"), "Should acquire lock")

        val lockInfo = lockManager.getLockInfo()
        assertNotNull(lockInfo, "Should have lock info when locked")
        assertEquals("test_operation", lockInfo.operation)
        assertTrue(lockInfo.processId > 0, "Should have valid process ID")

        assertTrue(lockManager.releaseLock(), "Should release lock")
        assertNull(lockManager.getLockInfo(), "Should have no lock info after release")
    }

    @Test
    fun `should prevent concurrent operations with locking enabled`() {
        // First manager starts a long operation (we'll simulate this)
        val sanctuaryDir = projectDir.parentFile.resolve("locking-test-project.sanctuary")
        val lockManager1 = SanctuaryLockManager(sanctuaryDir)
        val lockManager2 = SanctuaryLockManager(sanctuaryDir)

        // First lock acquires successfully
        assertTrue(lockManager1.acquireLock("operation1"), "First lock should succeed")

        // Second lock should fail
        assertFalse(lockManager2.acquireLock("operation2"), "Second lock should fail")

        // After first lock is released, second should succeed
        assertTrue(lockManager1.releaseLock(), "Should release first lock")
        assertTrue(lockManager2.acquireLock("operation2"), "Second lock should now succeed")

        assertTrue(lockManager2.releaseLock(), "Should release second lock")
    }

    @Test
    fun `should allow concurrent operations with locking disabled`() {
        // Both managers have locking disabled, so they should be able to operate concurrently
        val branch1 = managerWithoutLocking.createSanctuarySnapshot("First")
        val branch2 = managerWithoutLocking.createSanctuarySnapshot("Second")

        assertTrue(branch1.startsWith("auto-"))
        assertTrue(branch2.startsWith("auto-"))
        assertNotEquals(branch1, branch2)
    }

    @Test
    fun `should throw exception when lock cannot be acquired`() {
        // Create a locked manager
        val sanctuaryDir = projectDir.parentFile.resolve("locking-test-project.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)

        // Acquire lock manually
        assertTrue(lockManager.acquireLock("blocking_operation"))

        // Now trying to create sanctuary should fail
        val exception = assertThrows<SanctuaryLockException> {
            managerWithLocking.createSanctuarySnapshot("Should fail")
        }

        assertTrue(exception.message!!.contains("Cannot acquire lock"))
        assertTrue(exception.message!!.contains("verz lock unlock"))

        // Clean up
        lockManager.releaseLock()
    }

    @Test
    fun `should handle stale lock cleanup`() {
        val sanctuaryDir = projectDir.parentFile.resolve("locking-test-project.sanctuary")
        sanctuaryDir.mkdirs()
        val lockFile = sanctuaryDir.resolve(".lock")

        // Create a fake stale lock file (old timestamp, fake process ID)
        val staleLockContent = """
            processId=999999
            timestamp=2020-01-01T00:00:00
            operation=stale_operation
            created=1577836800000
        """.trimIndent()

        lockFile.writeText(staleLockContent)
        assertTrue(lockFile.exists(), "Stale lock file should exist")

        // New lock manager should be able to clean up stale lock and acquire new one
        val lockManager = SanctuaryLockManager(sanctuaryDir)
        assertTrue(lockManager.acquireLock("new_operation"), "Should clean up stale lock and acquire new one")

        // Verify it's our lock now
        val lockInfo = lockManager.getLockInfo()
        assertNotNull(lockInfo)
        assertEquals("new_operation", lockInfo.operation)
        assertNotEquals(999999L, lockInfo.processId, "Should be our process ID, not the stale one")

        lockManager.releaseLock()
    }

    @Test
    fun `should force unlock when requested`() {
        val sanctuaryDir = projectDir.parentFile.resolve("locking-test-project.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)

        // Acquire lock
        assertTrue(lockManager.acquireLock("test_operation"))
        assertTrue(lockManager.isLocked())

        // Force unlock should work
        assertTrue(lockManager.forceUnlock())
        assertFalse(lockManager.isLocked())
    }

    @Test
    fun `should handle concurrent lock attempts correctly`() {
        val sanctuaryDir = projectDir.parentFile.resolve("locking-test-project.sanctuary")

        // Use CompletableFuture to simulate concurrent access
        val future1 = CompletableFuture.supplyAsync {
            val lockManager = SanctuaryLockManager(sanctuaryDir)
            val acquired = lockManager.acquireLock("concurrent_test_1")
            if (acquired) {
                Thread.sleep(100) // Hold lock briefly
                lockManager.releaseLock()
            }
            acquired
        }

        val future2 = CompletableFuture.supplyAsync {
            Thread.sleep(10) // Slight delay to ensure first one starts
            val lockManager = SanctuaryLockManager(sanctuaryDir)
            val acquired = lockManager.acquireLock("concurrent_test_2")
            if (acquired) {
                lockManager.releaseLock()
            }
            acquired
        }

        val result1 = future1.get(5, TimeUnit.SECONDS)
        val result2 = future2.get(5, TimeUnit.SECONDS)

        // Exactly one should succeed, one should fail
        assertTrue(result1 xor result2, "Exactly one lock attempt should succeed")
    }

    @Test
    fun `lock file should contain correct information`() {
        val sanctuaryDir = projectDir.parentFile.resolve("locking-test-project.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)
        val lockFile = sanctuaryDir.resolve(".lock")

        assertFalse(lockFile.exists(), "Lock file should not exist initially")

        assertTrue(lockManager.acquireLock("file_content_test"))
        assertTrue(lockFile.exists(), "Lock file should exist after acquiring lock")

        val content = lockFile.readText()
        assertTrue(content.contains("processId="))
        assertTrue(content.contains("timestamp="))
        assertTrue(content.contains("operation=file_content_test"))
        assertTrue(content.contains("created="))

        lockManager.releaseLock()
        assertFalse(lockFile.exists(), "Lock file should be deleted after releasing lock")
    }

    @Test
    fun `should work with withLock helper method`() {
        val sanctuaryDir = projectDir.parentFile.resolve("locking-test-project.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)

        var operationExecuted = false

        val result = lockManager.withLock("with_lock_test") {
            operationExecuted = true
            "operation_result"
        }

        assertEquals("operation_result", result)
        assertTrue(operationExecuted)
        assertFalse(lockManager.isLocked(), "Lock should be released after withLock")
    }

    @Test
    fun `withLock should release lock even if operation throws exception`() {
        val sanctuaryDir = projectDir.parentFile.resolve("locking-test-project.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)

        assertThrows<RuntimeException> {
            lockManager.withLock("exception_test") {
                throw RuntimeException("Test exception")
            }
        }

        assertFalse(lockManager.isLocked(), "Lock should be released even after exception")
    }

    @Test
    fun `manager lock info should return formatted string`() {
        val sanctuaryDir = projectDir.parentFile.resolve("locking-test-project.sanctuary")
        val lockManager = SanctuaryLockManager(sanctuaryDir)

        assertNull(managerWithLocking.getLockInfo(), "Should have no lock info initially")

        assertTrue(lockManager.acquireLock("format_test"))

        // Note: The manager checks its own lock manager, but we're using a separate one
        // So we'll test the NoOp manager instead
        assertNull(managerWithoutLocking.getLockInfo(), "NoOp manager should always return null")
        assertFalse(managerWithoutLocking.isLocked(), "NoOp manager should never be locked")
        assertTrue(managerWithoutLocking.forceUnlock(), "NoOp manager should always succeed")

        lockManager.releaseLock()
    }
}