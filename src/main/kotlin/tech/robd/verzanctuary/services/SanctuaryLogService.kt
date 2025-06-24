package tech.robd.verzanctuary.services
/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/services/SanctuaryLogService.kt
 * description=VersionSanctuaryManager has most of the logic
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

import tech.robd.verzanctuary.utils.Utils
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Structured audit logging service for sanctuary operations.
 *
 * ## Purpose
 * Provides consistent, machine-readable logging of all sanctuary operations for
 * troubleshooting, auditing, and usage analysis. Each log entry captures the
 * operation context, outcome, and relevant metadata in JSON format.
 *
 * ## Log Format
 * Uses a line-delimited JSON (JSONL) format where each line contains a complete
 * JSON object representing one operation. This format enables easy parsing by
 * log analysis tools while remaining human-readable.
 *
 * ## Thread Safety
 * File append operations are atomic at the OS level for reasonably sized log
 * entries, making this service naturally thread-safe for concurrent logging
 * from multiple sanctuary operations.
 *
 * @param logFile File where sanctuary operations are logged
 */
class SanctuaryLogService(
    private val logFile: File
) {
    /**
     * Returns the log file for direct access by external tools.
     *
     * @return Log file handle for analysis tools or backup operations
     */
    fun getProjectLogFile(): File = logFile

    /**
     * Records a sanctuary operation with structured metadata.
     *
     * ## Log Entry Structure
     * Each entry contains:
     * - **timestamp**: ISO-8601 formatted timestamp with timezone
     * - **type**: Operation category (e.g., "snapshot", "checkout", "diff")
     * - **message**: Human-readable operation description
     * - **result**: Operation outcome ("created", "error", "nochange", etc.)
     * - **branch**: Associated sanctuary branch (optional)
     * - **details**: Additional context data (optional)
     *
     * ## Usage Patterns
     * - Log before and after risky operations
     * - Include error details for failed operations
     * - Record timing information for performance analysis
     * - Capture user-provided messages for audit trails
     *
     * @param type Operation category for filtering and analysis
     * @param message Human-readable description of the operation
     * @param result Operation outcome indicator
     * @param branch Sanctuary branch involved in operation (if applicable)
     * @param details Additional structured data relevant to operation
     */
    fun logSanctuaryEvent(
        type: String,
        message: String,
        result: String,
        branch: String? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val map = mutableMapOf<String, Any?>(
            "timestamp" to timestamp,
            "type" to type,
            "message" to message,
            "result" to result
        )
        if (branch != null) map["branch"] = branch
        if (details.isNotEmpty()) map["details"] = details

        val json = map.entries.joinToString(
            prefix = "{", postfix = "}"
        ) { (k, v) ->
            "\"$k\":${Utils.toJsonValue(v)}"
        }
        getProjectLogFile().appendText(json + "\n")
    }

    /**
     * Retrieves recent sanctuary operations for display or analysis.
     *
     * ## Use Cases
     * - Display recent activity in CLI commands
     * - Troubleshoot failed operations by examining log context
     * - Generate activity reports for team usage tracking
     * - Debug timing issues by analyzing operation sequences
     *
     * ## Performance Note
     * Reads the entire log file to get last N entries. For very large log files,
     * consider external log rotation or more sophisticated log management.
     *
     * @param lastN Number of recent entries to return (default: 10)
     * @return List of JSON log entries, oldest first
     */
    fun showSanctuaryLog(lastN: Int = 10): List<String> {
        val logFile = getProjectLogFile()
        if (!logFile.exists()) return emptyList()
        return logFile.readLines().takeLast(lastN)
    }

}