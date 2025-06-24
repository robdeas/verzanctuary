/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/lock/LockManager.kt
 * description=LockManager
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

package tech.robd.verzanctuary.lock

/**
 * Common interface for sanctuary lock management
 */
interface LockManager {
    data class LockInfo(
        val processId: Long,
        val timestamp: String,
        val operation: String,
        val created: Long = System.currentTimeMillis()
    )

    /**
     * Execute an operation with automatic lock management
     */
    fun <T> withLock(operation: String, action: () -> T): T

    /**
     * Acquire an exclusive lock for the sanctuary
     */
    fun acquireLock(operation: String): Boolean

    /**
     * Release the current lock
     */
    fun releaseLock(): Boolean

    /**
     * Check if sanctuary is currently locked
     */
    fun isLocked(): Boolean

    /**
     * Get information about the current lock
     */
    fun getLockInfo(): LockInfo?

    /**
     * Force remove a lock (use with caution)
     */
    fun forceUnlock(): Boolean
}
