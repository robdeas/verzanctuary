/**
 * [File Info]
 * path=main/kotlin/tech/robd/verzanctuary/data/SanctuaryState.kt
 * description=YAML data ProjectFile object
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

package tech.robd.verzanctuary.data

import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class ProjectFile(
    val sanctuaryIdentity: SanctuaryIdentity,
    val sanctuaryState: SanctuaryState
) {
    companion object {
        /**
         * Creates a new ProjectFile object with a default state for initialization.
         *
         * @param projectName The name for the new project.
         * @param projectPath The absolute path to the project's root directory.
         * @return A ProjectFile instance ready to be saved for a new project.
         */
        @OptIn(ExperimentalTime::class)
        fun createNew(projectName: String, projectPath: String): ProjectFile {
            return ProjectFile(
                sanctuaryIdentity = SanctuaryIdentity(
                    projectName = projectName,
                    // Generate a unique ID for the new project
                    sanctuaryId = UUID.randomUUID().toString(),
                    version = "0.1.0",
                    createdUtc = Clock.System.now(),
                    boundProjectPath = projectPath
                ),
                sanctuaryState = SanctuaryState(
                    status = "initialized",
                    // No operations have been performed yet
                    lastOperation = null
                )
            )
        }
    }
}
