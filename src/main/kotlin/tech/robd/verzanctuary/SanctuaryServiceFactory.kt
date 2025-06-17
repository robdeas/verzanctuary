package tech.robd.verzanctuary

import tech.robd.verzanctuary.lock.LockManager
import tech.robd.verzanctuary.path.SanctuaryPathResolver
import tech.robd.verzanctuary.services.SanctuaryGitService
import tech.robd.verzanctuary.services.SanctuaryLogService
import java.io.File

object SanctuaryServiceFactory {

    fun gitService(workingDirectory: File, lockManager: LockManager,sanctuaryLogService: SanctuaryLogService, isGitRepository: Boolean): SanctuaryGitService {
        val pathResolver = SanctuaryPathResolver(workingDirectory)
        val paths = pathResolver.resolvePaths()
        val parser = SanctuaryMetadataParser()
        return SanctuaryGitService(
            sanctuaryGitDir = paths.sanctuaryGitDir,
            standardCheckoutPath = paths.browseSpaceDir,
            projectName = workingDirectory.name,
            sanctuaryDir = paths.sanctuaryDir,
            projectRoot = workingDirectory,
            metadataFile = paths.metadataFile,
            parser = parser,
            lockManager = lockManager,
            sanctuaryLogService = sanctuaryLogService,
            isGitRepository = isGitRepository
        )
    }
}