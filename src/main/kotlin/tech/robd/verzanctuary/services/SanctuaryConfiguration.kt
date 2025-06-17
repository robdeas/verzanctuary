package tech.robd.verzanctuary.services

import tech.robd.verzanctuary.path.SanctuaryPaths
import java.io.File


class SanctuaryConfiguration(
    private val workingDirectory: File,
    private val projectName: String
) {
    fun resolvePaths(): SanctuaryPaths { TODO("Not yet implemented") }
    fun getEnvironmentInfo(): String { TODO("Not yet implemented") }
    // ...more methods
}
