/**
 * [File Info]
 * path=main/kotlin/tech/robd/verzanctuary/data/SanctuaryState.kt
 * description=Main lick manager for the sanctuary.
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

package tech.robd.verzanctuary.lock

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Manages file-based locking for sanctuary operations
 */
class SanctuaryLockManager(
    private val sanctuaryDir: File
) : LockManager {
    private val lockFile = sanctuaryDir.resolve(".lock")
    private val maxLockAge = TimeUnit.MINUTES.toMillis(5) // 5 minute stale lock timeout



    /**
     * Acquire an exclusive lock for the sanctuary
     * @param operation Description of what operation needs the lock
     * @return true if lock acquired, false if already locked
     */
    override fun acquireLock(operation: String): Boolean {
        // Check if there's an existing lock
        if (lockFile.exists()) {
            if (isLockStale()) {
                // Try to remove stale lock (may fail on Windows if process still running)
                return cleanupStalelock() && acquireLock(operation)
            }
            return false // Lock is active
        }

        return try {
            // Ensure sanctuary directory exists
            sanctuaryDir.mkdirs()

            // Create lock file with process info
            val lockInfo = createLockInfo(operation)

            // Windows-safe atomic write
            val tempLockFile = File(lockFile.parentFile, "${lockFile.name}.tmp")
            tempLockFile.writeText(lockInfo)

            // Atomic rename (works on both platforms)
            val success = tempLockFile.renameTo(lockFile)

            if (!success) {
                tempLockFile.delete() // Cleanup temp file
                return false
            }

            // Verify lock was created correctly
            lockFile.exists() && isOurLock(lockInfo)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Release the lock if we own it
     */
    override fun releaseLock(): Boolean {
        return try {
            if (lockFile.exists()) {
                lockFile.delete()
            } else {
                true // No lock to release
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Execute an operation with automatic lock management
     */
    override fun <T> withLock(operation: String, action: () -> T): T {
        if (!acquireLock(operation)) {
            throw SanctuaryLockException(
                "Cannot acquire lock for operation: $operation. " +
                        "Sanctuary may be in use by another process. " +
                        "Run 'verz lock status' to check lock details or 'verz lock unlock --force' to force remove."
            )
        }

        try {
            return action()
        } finally {
            releaseLock()
        }
    }

    /**
     * Check if the current lock is stale (too old or process dead)
     */
    private fun isLockStale(): Boolean {
        return try {
            if (!lockFile.exists()) return false

            val lockInfo = getLockInfo()
            if (lockInfo == null) return true // Can't parse = stale

            // Check age
            val lockAge = System.currentTimeMillis() - lockInfo.created
            if (lockAge > maxLockAge) return true

            // Check if process still exists (cross-platform)
            return !isProcessAlive(lockInfo.processId)
        } catch (e: Exception) {
            true // If we can't determine, consider it stale
        }
    }

    /**
     * Check if a process is still running (cross-platform)
     */
    private fun isProcessAlive(pid: Long): Boolean {
        return try {
            ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
        } catch (e: Exception) {
            // If we can't check, assume it's dead
            false
        }
    }

    /**
     * Safely cleanup a stale lock (handles Windows file locking)
     */
    private fun cleanupStalelock(): Boolean {
        return try {
            // Try direct deletion first
            if (lockFile.delete()) {
                return true
            }

            // If direct deletion fails (Windows), try overwrite approach
            val tempContent = "STALE_LOCK_CLEANUP_${System.currentTimeMillis()}"
            lockFile.writeText(tempContent)
            Thread.sleep(10) // Brief pause
            lockFile.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verify this lock file was created by us
     */
    private fun isOurLock(expectedContent: String): Boolean {
        return try {
            lockFile.readText().trim() == expectedContent.trim()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get information about the current lock
     */
    override fun getLockInfo(): LockManager.LockInfo? {
        return try {
            if (!lockFile.exists()) return null

            val content = lockFile.readText()
            parseLockInfo(content)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if sanctuary is currently locked
     */
    override fun isLocked(): Boolean {
        return lockFile.exists() && !isLockStale()
    }

    // Using the interface's LockInfo class instead of defining our own

    private fun createLockInfo(operation: String): String {
        val processId = ProcessHandle.current().pid()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        return """
            processId=$processId
            timestamp=$timestamp
            operation=$operation
            created=${System.currentTimeMillis()}
        """.trimIndent()
    }

    private fun parseLockInfo(content: String): LockManager.LockInfo? {
        return try {
            val lines = content.lines().associate { line ->
                val parts = line.split("=", limit = 2)
                parts[0] to parts.getOrNull(1).orEmpty()
            }

            LockManager.LockInfo(
                processId = lines["processId"]?.toLongOrNull() ?: 0,
                timestamp = lines["timestamp"].orEmpty(),
                operation = lines["operation"].orEmpty(),
                created = lines["created"]?.toLongOrNull() ?: 0
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Force remove a lock (use with caution)
     */
    override fun forceUnlock(): Boolean {
        return try {
            if (lockFile.exists()) {
                lockFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

