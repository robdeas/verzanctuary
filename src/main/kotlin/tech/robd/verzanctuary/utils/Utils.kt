package tech.robd.verzanctuary.utils

import java.io.File
import java.time.format.DateTimeFormatter

object Utils {

     const val SANCTUARY_WORKING_TMP = "sanctuary.workingTmp"
    const val SANCTUARY_FOLDER_SUFFIX = ".sanctuary"

    val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm-ss-SSS")

    /**
     * Check if two files have identical content
     */
    fun filesAreIdentical(file1: File, file2: File): Boolean {
        if (file1.length() != file2.length()) return false

        return file1.readBytes().contentEquals(file2.readBytes())
    }

    // Utility for simple JSON encoding (does NOT handle lists, but fine for this use)
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