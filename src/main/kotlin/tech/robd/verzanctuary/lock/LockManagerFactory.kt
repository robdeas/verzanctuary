/**
 * [File Info]
 * path=main/kotlin/tech/robd/verzanctuary/lock/SanctuaryLockManager.kt
 * description=LockManagerFactory
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

import tech.robd.verzanctuary.lock.LockManager
import tech.robd.verzanctuary.lock.SanctuaryLockManager
import java.io.File

/**
 * Factory for creating lock managers
 */
object LockManagerFactory {
    fun create(sanctuaryDir: File, enableLocking: Boolean = true): LockManager {
        return if (enableLocking) {
            SanctuaryLockManager(sanctuaryDir)
        } else {
            NoOpLockManager()
        }
    }
}
