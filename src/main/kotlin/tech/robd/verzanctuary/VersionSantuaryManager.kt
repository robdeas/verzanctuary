/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/VersionSanctuaryManager.kt
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

package tech.robd.verzanctuary

import org.eclipse.jgit.api.Git
import tech.robd.verzanctuary.data.SanctuaryMetadata
import tech.robd.verzanctuary.lock.LockManager
import tech.robd.verzanctuary.lock.LockManagerFactory
import tech.robd.verzanctuary.path.SanctuaryPathResolver
import tech.robd.verzanctuary.path.SanctuaryPaths
import tech.robd.verzanctuary.services.SanctuaryCheckoutService
import tech.robd.verzanctuary.services.SanctuaryConflictService
import tech.robd.verzanctuary.services.SanctuaryDiffService
import tech.robd.verzanctuary.services.SanctuaryFileService
import tech.robd.verzanctuary.services.SanctuaryGitService
import tech.robd.verzanctuary.services.SanctuaryLogService
import tech.robd.verzanctuary.services.SanctuaryMetadataService
import tech.robd.verzanctuary.utils.GitUtils
import java.io.File
import java.nio.file.Path

/**
 * VerZanctuary - A safe sanctuary for your code versions
 *
 * VersionSanctuaryManager coordinates versioned code "sanctuaries" for a project,
 * without interfering with your main Git workflow.
 * which enables timestamped, isolated snapshots outside your main Git workflow.
 *
 * - Snapshots are stored in a parallel .sanctuary directory as a "hidden" independent Git repos.
 * - Allows restoring or inspecting any saved state, protecting your working tree.
 * - Not tied to Git history: ideal for "savepoints" before risky AI changes, refactoring, or experiments.
 *
 * Entry point for all high-level sanctuary operations: snapshot, restore, diff, cleanup, etc.
 *
 * Typical usage:
 *   val manager = VersionSanctuaryManager(File(".")) // from your project root
 *   manager.createSanctuarySnapshot("Before big refactor")
 *   // ... make changes ...
 *   manager.checkoutSanctuaryToWorking("auto-20250621-2345-01-123")
 *
 * This class should remain focused on orchestration; implementation details are delegated to services.
 *
 *  * Environment Variables:
 *  * - VERZANCTUARY_SANCTUARY_DIR: Custom location for sanctuary metadata (defaults to parent directory)
 *  * - VERZANCTUARY_WORKSPACE_DIR: Custom location for working tmp directory (defaults to VERZANCTUARY_SANCTUARY_DIR or parent directory)
 * *
*/
class VersionSanctuaryManager(
    private val workingDirectory: File,
    private val parser: SanctuaryMetadataParser = SanctuaryMetadataParser(),
    private val enableLocking: Boolean = true,
    private val useGitRoot: Boolean = true
) {

    val sanctuaryFileService: SanctuaryFileService
    val sanctuaryConflictService: SanctuaryConflictService
    val sanctuaryGitService: SanctuaryGitService
    val sanctuaryDiffService: SanctuaryDiffService
    val sanctuaryCheckoutService: SanctuaryCheckoutService
    val sanctuaryMetadataService: SanctuaryMetadataService
    val sanctuaryLogService: SanctuaryLogService
    val lockManager: LockManager
    val sanctuaryDir: File
    private val logFile: File
    private val paths: SanctuaryPaths

    // Find the true project root, not just the current working directory
    val projectRoot: File
    val isGitRepository: Boolean
    private val projectName: String
    private val standardCheckoutPath: File
    private val pathResolver: SanctuaryPathResolver

    // Metadata file: project.sanctuary/sanctuary.yaml
    private val metadataFile: File

    init {

        if (useGitRoot) {
            projectRoot = GitUtils.findGitRoot(workingDirectory) // This throws if no git found
            isGitRepository = true
        } else {
            projectRoot = workingDirectory
            isGitRepository = File(workingDirectory, ".git").exists()
        }
        projectName = projectRoot.name
        pathResolver = SanctuaryPathResolver(workingDirectory, projectName)
        paths = pathResolver.resolvePaths()
        metadataFile = paths.metadataFile
        sanctuaryDir = paths.sanctuaryDir
        lockManager = LockManagerFactory.create(sanctuaryDir, enableLocking)
        logFile = paths.logFile
        standardCheckoutPath = paths.browseSpaceDir
        // now services
        sanctuaryLogService = SanctuaryLogService(logFile)
        sanctuaryFileService = SanctuaryServiceFactory.fileService(
            projectRoot,           // <--- source
            standardCheckoutPath,  // <--- target (where sanctuary snapshots go)
            sanctuaryDir,
            isGitRepository
        )

        sanctuaryConflictService = SanctuaryServiceFactory.conflictService(
            projectRoot,
            standardCheckoutPath
        )

        sanctuaryGitService =
            SanctuaryServiceFactory.gitService(
                sanctuaryFileService,
                sanctuaryConflictService,
                workingDirectory,
                lockManager,
                sanctuaryLogService,
                isGitRepository
            )

        sanctuaryDiffService = SanctuaryServiceFactory.diffService(lockManager, sanctuaryGitService)

        sanctuaryCheckoutService = SanctuaryServiceFactory.checkoutService(
            lockManager,
            sanctuaryLogService,
            sanctuaryGitService,
            sanctuaryFileService
        )

        sanctuaryMetadataService = SanctuaryServiceFactory.metadataService(parser, metadataFile)

    }

    private val sanctuaryGitDir = paths.sanctuaryGitDir
    private val labWorkspacePath = paths.labSpaceDir

    /**
     * Returns the absolute path to the "lab workspace" for safe, isolated experimentation.
     * Useful for testing or ephemeral state without touching the user's active working directory.
     */
    fun getLabWorkspaceLocation(): String = labWorkspacePath.absolutePath

    private fun saveMetadata(metadata: SanctuaryMetadata) {
        parser.write(metadataFile, metadata)
    }

    /**
     * Initializes (or opens) the sanctuary Git repository.
     * Creates metadata and structure if missing.
     */
    fun initializeRepo(): Git {
        return sanctuaryGitService.initializeRepo()
    }

    /**
     * Prints an overview of the current sanctuary layout, snapshot count, and key locations.
     */
    fun getSanctuaryInfo(): String {
        return """
        ${pathResolver.getPathSummary()}
        Total snapshots: ${listSanctuaryBranches().size}
    """.trimIndent()
    }

    /**
     * Shows all relevant environment variables and their effects on this instance.
     */
    fun getEnvironmentInfo(): String = pathResolver.getEnvironmentInfo()

    /**
     * Copies the current working directory files into the sanctuary's managed snapshot area.
     * Excludes .git, .sanctuary, and temp directories by default.
     */
    fun copyFilesToSanctuary() {
        sanctuaryGitService.copyFilesToSanctuary()
    }

    /**
     * Lists the last N log events for this sanctuary (snapshots, checkouts, etc.).
     */
    fun showSanctuaryLog(lastN: Int = 10): List<String> {
        return sanctuaryLogService.showSanctuaryLog()
    }


    /**
     * Restores files from a given sanctuary snapshot branch to the working directory.
     * If conflicts are detected, the restore is aborted unless forceOverwrite is true.
     *
     * @param branchName   Sanctuary branch to restore from
     * @param files        List of file paths to restore (all files if empty)
     * @param forceOverwrite  If true, current files are overwritten and an auto-backup is created
     */
    fun checkoutSanctuaryToWorking(
        branchName: String,
        files: List<String> = emptyList(),
        forceOverwrite: Boolean = false
    ) {
        sanctuaryGitService.checkoutSanctuaryToWorking(
            branchName,
            files,
            forceOverwrite
        )
    }

    /**
     * Restores a sanctuary snapshot to a temporary browse folder for inspection,
     * leaving the working directory untouched.
     */
    fun checkoutSanctuaryToBrowseSpace(branchName: String) {
        checkoutSanctuaryToStandardLocation(branchName)
    }


    /**
     * Diff a sanctuary against current working directory
     */
    fun diffSanctuaryWithWorking(branchName: String): String {
        return sanctuaryDiffService.diffSanctuaryWithWorking(branchName, projectRoot, standardCheckoutPath)
    }

    /**
     * Generate diff between two directories
     */
    private fun generateDiffBetweenDirectories(
        fromDir: Path,
        toDir: Path,
        fromLabel: String = "a",
        toLabel: String = "b"
    ): String {
        return sanctuaryDiffService.generateDiffBetweenDirectories(
            fromDir,
            toDir,
            fromLabel,
            toLabel
        )
    }

    /**
     * Diff a specific file between sanctuary and working directory
     */
    fun diffFileWithWorking(branchName: String, filePath: String): String {
        return sanctuaryDiffService.diffFileWithWorking(branchName, filePath, projectRoot, standardCheckoutPath)
    }

    /**
     * Checks out a sanctuary snapshot to an isolated "lab" workspace (not the user's working dir).
     * For safe, disposable experiments.
     */
    fun checkoutSanctuaryToLabSpace(branchName: String) {


        sanctuaryCheckoutService.checkoutSanctuaryToLabSpace(branchName, labWorkspacePath)
    }

    private fun copyFilesToLabWorkspace() {
        sanctuaryFileService.copyFilesToLabWorkspace(labWorkspacePath)
    }

    private fun generateFileDiff(
        fromFile: File,
        toFile: File,
        fromLabel: String = "a",
        toLabel: String = "b",
        filePath: String? = null
    ): String {
        return sanctuaryDiffService.generateFileDiff(
            fromFile,
            toFile,
            fromLabel,
            toLabel,
            filePath
        )
    }


    /**
     * Create a sanctuary snapshot with timestamped branch
     * Public method that wraps with locking
     */
    fun createSanctuarySnapshot(message: String): String {
        return sanctuaryGitService.createSanctuarySnapshot(message)
    }

    /**
     * Ensure hooksDir is a directory, deleting any file or symlink at that path.
     * Retries a couple of times if necessary, but doesn't hang forever.
     */
    fun ensureHooksDirectory(hooksDir: File, maxTries: Int = 3, retryDelayMs: Long = 50) {
        sanctuaryCheckoutService.ensureHooksDirectory(hooksDir, maxTries, retryDelayMs)
    }

    private fun setupBrowseSpaceProtection(branchName: String) {
        sanctuaryCheckoutService.setupBrowseSpaceProtection(branchName, standardCheckoutPath)
    }

    /**
     * List all sanctuary branches
     */
    fun listSanctuaryBranches(): List<String> {
        return sanctuaryGitService.listBranches()
    }

    /**
     * Checkout a specific sanctuary branch
     */
    fun checkoutSanctuary(branchName: String) {
        sanctuaryGitService.checkoutSanctuary(branchName)
    }

    fun checkoutSanctuaryToStandardLocation(branchName: String) {

        sanctuaryCheckoutService.checkoutSanctuaryToStandardLocation(branchName, standardCheckoutPath)
    }

    /**
     * Generate a patch file showing differences between two sanctuary branches
     */
    fun generatePatch(fromBranch: String, toBranch: String, outputFile: File? = null): String {
        return sanctuaryGitService.generatePatch(fromBranch, toBranch, outputFile)
    }

    /**
     * Quick sanctuary with predefined scenarios
     */
    fun quickBackup(scenario: BackupScenario): String {
        val message = when (scenario) {
            BackupScenario.BEFORE_AI_CONSULT -> "Before AI consultation"
            BackupScenario.BEFORE_REFACTOR -> "Before refactoring"
            BackupScenario.END_OF_DAY -> "End of day sanctuary"
            BackupScenario.BEFORE_EXPERIMENT -> "Before experimental changes"
            BackupScenario.WORKING_STATE -> "Stable working state"
        }

        return createSanctuarySnapshot(message)
    }

    /**
     * Compare current working directory with latest sanctuary
     */
    fun compareWithLatest(): String? {
        return sanctuaryDiffService.compareWithLatest()
    }

    /**
     * Delete old sanctuary branches (keep only the most recent N)
     */
    fun cleanupOldSanctuaries(keepCount: Int = 10) {
        sanctuaryGitService.cleanupOldSanctuaries(keepCount)
    }

    /**
     * Get sanctuary location for external tools
     */
    fun getSanctuaryLocation(): String = sanctuaryDir.absolutePath

    /**
     * Get standard checkout location for external tools
     */
    fun getStandardCheckoutLocation(): String = standardCheckoutPath.absolutePath

    /**
     * Returns true if a valid sanctuary repository exists for this project.
     * Checks for the presence of the sanctuary directory, its .git subdirectory, and a HEAD file.
     *
     * This is a lightweight check to quickly determine if the sanctuary is initialized and usable.
     */
    fun sanctuaryExists(): Boolean {
        return sanctuaryDir.exists() &&
                sanctuaryGitDir.exists() &&
                sanctuaryGitDir.resolve("HEAD").exists()
    }

    /**
    * Verifies that the sanctuary is correctly "bound" to this project root.
    * Ensures the sanctuary metadata references the expected working directory.
    *
    * Returns true if the sanctuary appears valid for this project, false if not.
    * Useful to guard against accidental use of stale or misaligned sanctuaries.
    */
    fun validateBinding(): Boolean {
        return sanctuaryMetadataService.validateBinding(projectRoot)
    }

    /**
     * Provides a summary of the current sanctuary's status.
     * Includes details such as binding status, snapshot count, and key locations.
     *
     * Intended for display in CLI or diagnostics to help the user understand the current state.
     */
    fun getSanctuaryStatus(): String {
        return sanctuaryMetadataService.getSanctuaryStatus(projectName)
    }

    companion object {
        // Name of the sanctuary configuration file (it is not user-facing but an internal reference).
        private const val CONFIG_FILE_NAME = "sanctuary.yaml"
    }

    /**
     * Returns true if the sanctuary is currently locked by any process.
     * Used to guard against concurrent or conflicting operations (e.g., snapshot and checkout).
     */
    fun isLocked(): Boolean = lockManager.isLocked()

    /**
     * Returns human-readable lock information if the sanctuary is currently locked.
     * Includes process ID, timestamp, and operation description if locked; otherwise, null.
     *
     * Useful for diagnostics or displaying to the user when a lock is blocking an action.
     */
    fun getLockInfo(): String? {
        val info = lockManager.getLockInfo()
        return info?.let {
            "Locked by process ${it.processId} at ${it.timestamp} for operation: ${it.operation}"
        }
    }

    /**
     * Forcibly releases the current sanctuary lock, if present.
     * Use with care: only intended for recovery from stuck or orphaned locks.
     *
     * Returns true if a lock was present and cleared, false if not.
     */
    fun forceUnlock(): Boolean = lockManager.forceUnlock()

}
