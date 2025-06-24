/**
 * VerZanctuary Utilities Package - Core utility functions and helpers.
 *
 * This package provides low-level utility functions that support sanctuary operations
 * but don't belong to any specific service or domain. These utilities handle common
 * tasks like Git repository detection, file operations, path manipulation, and
 * data formatting that are used throughout the sanctuary system.
 */
package tech.robd.verzanctuary.utils

/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/utils/Utils.kt
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
import java.time.format.DateTimeFormatter

/**
 * Miscellaneous Utility methods
 */
object Utils {

    const val SANCTUARY_WORKING_TMP = "sanctuary.workingTmp"
    const val SANCTUARY_FOLDER_SUFFIX = ".sanctuary"

    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm-ss-SSS")

    /**
     * Check if two files have identical content
     */
    fun filesAreIdentical(file1: File, file2: File): Boolean {
        if (file1.length() != file2.length()) return false

        return file1.readBytes().contentEquals(file2.readBytes())
    }

    /**
     * Utility for simple JSON encoding (does NOT handle lists, but fine for this use)
     */
    fun toJsonValue(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> v.entries.joinToString(
            prefix = "{", postfix = "}"
        ) { (k, v2) -> "\"$k\":${toJsonValue(v2)}" }

        is String -> "\"" + v.replace("\"", "\\\"") + "\""
        else -> "\"" + v.toString().replace("\"", "\\\"") + "\""
    }

}

/**
 * Extension function to make file operations easier
 */
fun File.deleteRecursively(): Boolean {
    return if (isDirectory) {
        listFiles()?.all { it.deleteRecursively() } == true && delete()
    } else {
        delete()
    }
}