/**
 * [File Info]
 * path=main/kotlin/tech/robd/verzanctuary/lock/LockManager.kt
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

import tech.robd.verzanctuary.VersionSanctuaryManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.use


/**
 * Factory function for creating VersionSanctuaryManager instances in tests
 *
 * This function provides a clean way to create sanctuary managers
 * with proper test isolation and setup.
 */

fun createSanctuaryManager(projectDirectory: File): VersionSanctuaryManager {
    // The VersionSanctuaryManager constructor now takes a parser instance.
    return VersionSanctuaryManager(projectDirectory, SanctuaryMetadataParser())
}


/**
 * Create a sanctuary manager for testing with environment variable setup
 */
fun createSanctuaryManagerWithEnv(
    projectDirectory: File,
    sanctuaryDir: String? = null,
    workspaceDir: String? = null
): VersionSanctuaryManager {
    // Set environment variables if provided
    sanctuaryDir?.let { System.setProperty("VERZANCTUARY_SANCTUARY_DIR", it) }
    workspaceDir?.let { System.setProperty("VERZANCTUARY_WORKSPACE_DIR", it) }

    return VersionSanctuaryManager(projectDirectory)
}


