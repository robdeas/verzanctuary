package tech.robd.verzanctuary.services
/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/services/SanctuaryConflictService.kt
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

import tech.robd.verzanctuary.path.ConflictInfo
import tech.robd.verzanctuary.utils.Utils
import java.io.File
/**
 * **SanctuaryConflictService**
 *
 * Provides logic to detect file-level conflicts between the current working directory
 * and the checked-out sanctuary snapshot (safe zone) during checkout or restore operations.
 *
 * # Purpose
 * - Determines which files in the current project would be overwritten, changed, or lost
 *   if a sanctuary snapshot is restored.
 * - Used to prevent accidental overwrites, warn users, and support safe/forced checkout strategies.
 */
class SanctuaryConflictService(private val projectRoot: File,private val standardCheckoutPath: File) {
    /**
     * Detect potential conflicts between sanctuary and current working directory
     */
    fun detectAllPotentialConflicts(branchName: String, filesInGitRepo: List<String>  ): List<ConflictInfo> {
        val conflicts = mutableListOf<ConflictInfo>()

        filesInGitRepo.forEach { filePath ->
                val conflict = checkFileForConflict(filePath)
                if (conflict != null) {
                    conflicts.add(conflict)
                }
            }
        return conflicts
    }

    /**
     * Check if a specific file would cause a conflict
     */
    fun checkFileForConflict(filePath: String): ConflictInfo? {
        val workingFile = File(projectRoot, filePath)
        val sanctuaryFile = File(standardCheckoutPath, filePath)

        val workingExists = workingFile.exists()
        val sanctuaryExists = sanctuaryFile.exists()

        return when {
            !workingExists && !sanctuaryExists -> null // No conflict
            !workingExists && sanctuaryExists -> {
                // New file from sanctuary
                ConflictInfo(filePath, "missing", "new file")
            }

            workingExists && !sanctuaryExists -> {
                // File exists in working but not in sanctuary
                ConflictInfo(filePath, "new file", "missing")
            }

            true -> {
                // Both exist - check if different
                if (!Utils.filesAreIdentical(workingFile, sanctuaryFile)) {
                    ConflictInfo(filePath, "modified", "different content")
                } else {
                    null // Same content, no conflict
                }
            }

            else -> null
        }
    }
}