package tech.robd.verzanctuary.utils
/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/utils/SanctuaryPathUtils.kt
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
import java.io.File
import java.nio.file.Path
/**
 * Utility methods for path-based sanctuary exclusion logic.
 */
object SanctuaryPathUtils {
/**
 * Determines if a file (or directory) is inside a "sanctuary-related" directory
 * such as a .git folder, a .sanctuary metadata folder, or a working temp directory.
 *
 * This is used to exclude such files/directories from copy and diff operations,
 * ensuring the sanctuary state does not leak version control metadata or internal working data.
 *
 * @param file The file or directory path to check (relative or absolute).
 * @param sourcePath The base source directory being processed (typically project root).
 * @param sanctuaryDir The main sanctuary directory (excluded from operations).
 * @param standardCheckoutPath The standard sanctuary checkout path (excluded from operations).
 * @return True if the file resides within a sanctuary-related folder; false otherwise.
 *
 * This logic is applied recursively up the file tree, so any parent matching an excluded
 * directory name will cause this to return true.
 */
    fun isInSanctuaryRelatedDirectory(
        file: Path,
        sourcePath: Path,
        sanctuaryDir: File,
        standardCheckoutPath: File
    ): Boolean {
        var parent = file.parent
        while (parent != null && parent != sourcePath.parent) {
            val parentName = parent.fileName?.toString()
            if (parentName == ".git") return true
            if (parentName?.endsWith(Utils.SANCTUARY_FOLDER_SUFFIX) == true) return true
            if (parentName == Utils.SANCTUARY_WORKING_TMP) return true
            // Add any other logic you need
            parent = parent.parent
        }
        return false
    }
}
