/**
 * [File Info]
 * path=test/kotlin/tech/robd/verzanctuary/IntegrationTest.kt
 * description=NoOpLockManager does nothing
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

/**
 * No-operation lock manager for tests and --ignore-locks mode
 */
class NoOpLockManager: LockManager {
    override fun <T> withLock(operation: String, action: () -> T): T = action()
    override fun acquireLock(operation: String): Boolean {
        // No Op
        return true;
    }

    override fun releaseLock(): Boolean {
        // No Op
        return true;
    }

    override fun isLocked(): Boolean = false
    override fun getLockInfo(): LockManager.LockInfo? = null
    override fun forceUnlock(): Boolean = true
}