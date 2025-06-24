package tech.robd.verzanctuary.services
/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/services/SanctuaryMetadataService.kt
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
import tech.robd.verzanctuary.SanctuaryMetadataParser
import java.io.File
import kotlin.time.ExperimentalTime

/**
 * SanctuaryMetadataService
 *
 * Provides safe, validated access to a sanctuary’s core metadata file (`sanctuary.yaml`).
 *
 * # Purpose
 * - Reads and interprets sanctuary metadata for status reporting, health checks, and project binding validation.
 * - Ensures that sanctuary operations act on the intended project directory.
 * - Defends against mis-binding (e.g., when a .sanctuary is copied/moved between projects).
 *
 */
class SanctuaryMetadataService(val parser: SanctuaryMetadataParser, val metadataFile: File) {
    /**
     * Reads the current sanctuary metadata and returns a human-readable summary of its status.
     *
     * @param projectName Name of the project (used for reporting).
     * @return String summary of the sanctuary's current status, or error information if metadata is missing or invalid.
     */
    @OptIn(ExperimentalTime::class)
    fun getSanctuaryStatus(projectName: String): String {
        return try {
            // Assume you have a parser instance to read the file
            val metadata = parser.read(metadataFile)
            """
        Project: $projectName
        Status: ${metadata.state.status}
        Last Operation: ${metadata.state.lastOperation?.type}
        Last Success: ${metadata.state.lastOperation?.success}
        Completed: ${metadata.state.lastOperation?.completedUtc}
        """.trimIndent()

        } catch (e: Exception) {
            "Status: Unknown (${e.message})"
        }
    }

    /**
     * Validates that the sanctuary is bound to the current project root.
     *
     * This defends against using a sanctuary in the wrong directory by checking that the
     * `boundProjectPath` in the metadata matches the absolute path of `projectRoot`.
     *
     * @param projectRoot The directory the service expects to be bound to.
     * @return true if binding is valid or metadata does not exist; false if binding is invalid.
     */
    fun validateBinding(projectRoot: File): Boolean {
        return try {
            if (!metadataFile.exists()) {
                true // No metadata yet is valid
            } else {
                val metadata = parser.read(metadataFile)
                metadata.identity.boundProjectPath == projectRoot.absolutePath
            }
        } catch (e: Exception) {
            true // If can't read metadata, assume valid
        }
    }
}