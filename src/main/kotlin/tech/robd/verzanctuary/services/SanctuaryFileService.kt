package tech.robd.verzanctuary.services

import java.io.File


class SanctuaryFileService(
    private val projectRoot: File,
    private val standardCheckoutPath: File
) {
    //copyFilesToSanctuary,
// copyFromSanctuaryToWorkingDirectory,
// and copyFileFromSanctuaryToWorking
// are generic file move/copy utilities with some sanctuary-specific exclusions.

    //These could become a
// SanctuaryFileService that encapsulates all exclusion logic (e.g., skip .git, skip .sanctuary, etc.).
}