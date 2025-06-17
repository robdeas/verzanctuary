package tech.robd.verzanctuary.services

import tech.robd.verzanctuary.utils.Utils
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


class SanctuaryLogService(
    private val logFile: File
) {
     fun getProjectLogFile(): File = logFile

    // ...more methods
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

    fun showSanctuaryLog(lastN: Int = 10): List<String> {
        val logFile = getProjectLogFile()
        if (!logFile.exists()) return emptyList()
        return logFile.readLines().takeLast(lastN)
    }

}