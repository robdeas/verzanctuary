/**
 * [File Info]
 * path=main/kotlin/tech/robd/verzanctuary/data/SanctuaryState.kt
 * description=VersionSanctuaryManager has most of the logic
 * editable=true
 * license=apache
 * [File Info]
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
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import tech.robd.verzanctuary.data.SanctuaryMetadata
import tech.robd.verzanctuary.lock.LockManagerFactory
import tech.robd.verzanctuary.path.ConflictInfo
import tech.robd.verzanctuary.path.SanctuaryPathResolver
import tech.robd.verzanctuary.services.SanctuaryLogService
import tech.robd.verzanctuary.utils.Utils.SANCTUARY_FOLDER_SUFFIX
import tech.robd.verzanctuary.utils.Utils.SANCTUARY_WORKING_TMP
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime

/**
 * VerZanctuary - A safe sanctuary for your code versions
 *
 * NOTE: This file is now huge (~1400+ lines) and will be released in v0.1.0 to show functionality.
 * The concepts here are interesting, so I will release with a flawed architecture.
 * I will then immediately after that refactor it to be more modular with smaller files in v0.1.1.
 *
 * Planned refactoring:
 * - SanctuaryGitService (Git operations)
 * - SanctuaryFileService (File copying/filtering)
 * - SanctuaryConflictService (Conflict detection)
 * - SanctuaryDiffService (Diff generation)
 * - SanctuaryCheckoutService (Checkout operations)
 * - SanctuaryLogService (Event logging)
 * - SanctuaryConfiguration (Path resolution)
 *
 * No API breaking changes are planned for the refactor.
 *
 * Creates timestamped snapshots of your projects without interfering with your main Git workflow.
 * Uses .sanctuary extension for metadata and sanctuary.workingTmp for checkout operations.
 *
 * Environment Variables:
 * - VERZANCTUARY_SANCTUARY_DIR: Custom location for sanctuary metadata (defaults to parent directory)
 * - VERZANCTUARY_WORKSPACE_DIR: Custom location for working tmp directory (defaults to VERZANCTUARY_SANCTUARY_DIR or parent directory)
 */
class VersionSanctuaryManager(
    private val workingDirectory: File,
    private val parser: SanctuaryMetadataParser = SanctuaryMetadataParser(),
    private val enableLocking: Boolean = true,
    private val useGitRoot: Boolean = true
) {
    // Find the true project root, not just the current working directory
    val projectRoot: File
    val isGitRepository: Boolean

    init {
        if (useGitRoot) {
            projectRoot = findGitRoot(workingDirectory) // This throws if no git found
            isGitRepository = true
        } else {
            projectRoot = workingDirectory
            isGitRepository = File(workingDirectory, ".git").exists()
        }
    }

    private val projectName = projectRoot.name
    private val pathResolver = SanctuaryPathResolver(workingDirectory, projectName)
    private val paths = pathResolver.resolvePaths()
    private val sanctuaryDir = paths.sanctuaryDir
    private val sanctuaryGitDir = paths.sanctuaryGitDir
    private val standardCheckoutPath = paths.browseSpaceDir

    // Metadata file: project.sanctuary/sanctuary.yaml
    private val metadataFile = paths.metadataFile
    private val logFile = paths.logFile

    private val lockManager = LockManagerFactory.create(sanctuaryDir, enableLocking)

    // Working directory: project.sanctuary.workingTmp/
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm-ss-SSS")

    // Add lab workspace path
    private val labWorkspacePath = paths.labSpaceDir

    private val sanctuaryLogService = SanctuaryLogService(logFile)

    val sanctuaryGitService = SanctuaryServiceFactory.gitService(workingDirectory, lockManager, sanctuaryLogService, isGitRepository)

    /**
     * export the absolute path of the lab workspace, we probably need this for testability
     */
    fun getLabWorkspaceLocation(): String = labWorkspacePath.absolutePath

    private fun saveMetadata(metadata: SanctuaryMetadata) {
        parser.write(metadataFile, metadata)
    }


    fun initializeRepo(): Git {
        return sanctuaryGitService.initializeRepo()
    }

    /**
     * Get info about this sanctuary manager
     */
    fun getSanctuaryInfo(): String {
        return """
        ${pathResolver.getPathSummary()}
        Total snapshots: ${listSanctuaryBranches().size}
    """.trimIndent()
    }

    fun getEnvironmentInfo(): String = pathResolver.getEnvironmentInfo()

    /**
     * Copy all files from working directory to sanctuary repository
     */
    fun copyFilesToSanctuary() {
        sanctuaryGitService.copyFilesToSanctuary()
    }

    fun showSanctuaryLog(lastN: Int = 10): List<String> {
        return sanctuaryLogService.showSanctuaryLog()
    }

    private fun Path.isGitDir(): Boolean {
        val name = this.fileName?.toString() ?: return false
        return name == ".git"
    }
    /**
     * Checkout sanctuary to working directory (default behavior)
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
     * Checkout sanctuary to working directory (default behavior)
     */

    /**
     * Checkout sanctuary to browse folder (safe inspection)
     */
    fun checkoutSanctuaryToBrowseSpace(branchName: String) {
        // This is the existing method, just renamed for clarity
        checkoutSanctuaryToStandardLocation(branchName)
    }


    /**
     * Diff a sanctuary against current working directory
     */
    fun diffSanctuaryWithWorking(branchName: String): String {
        return lockManager.withLock("diff_sanctuary") {
            val git = initializeRepo()
            try {
                // Checkout sanctuary to temp
                git.checkout().setName(branchName).call()

                // Generate diff between temp and working directory
                generateDiffBetweenDirectories(
                    standardCheckoutPath.toPath(),
                    projectRoot.toPath(),
                    "sanctuary/$branchName",
                    "working directory"
                )
            } finally {
                git.close()
            }
        }
    }

    /**
     * Diff a specific file between sanctuary and working directory
     */
    fun diffFileWithWorking(branchName: String, filePath: String): String {
        return lockManager.withLock("diff_file") {
            val git = initializeRepo()
            try {
                git.checkout().setName(branchName).call()

                val sanctuaryFile = File(standardCheckoutPath, filePath)
                val workingFile = File(projectRoot, filePath)

                generateFileDiff(sanctuaryFile, workingFile, "sanctuary/$branchName", "working directory")
            } finally {
                git.close()
            }
        }
    }




    // New checkout method for lab
    fun checkoutSanctuaryToLabSpace(branchName: String) {
        lockManager.withLock("checkout_lab") {
            try {
                // 1. Checkout sanctuary to temp location first
                val git = initializeRepo()
                try {
                    git.checkout().setName(branchName).call()
                } finally {
                    git.close()
                }

                // 2. Copy sanctuary files over the lab workspace (preserving .git)
                copyFilesToLabWorkspace()

                println("✅ Checked out sanctuary '$branchName' to lab workspace")
                println("🔬 Lab workspace ready - Git sees these as local changes")

            } catch (e: Exception) {
                sanctuaryLogService.logSanctuaryEvent(
                    type = "checkout_lab",
                    message = "Checkout to lab",
                    result = "error",
                    branch = branchName,
                    details = mapOf("exception" to e.toString())
                )
                throw e
            }
        }
    }

    private fun copyFilesToLabWorkspace() {
        val sourcePath = standardCheckoutPath.toPath()  // From sanctuary checkout
        val targetPath = labWorkspacePath.toPath()      // To lab workspace

        Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relativePath = sourcePath.relativize(dir)

                // SKIP .git directories entirely - don't touch lab's real git repo
                if (relativePath.toString() == ".git" || relativePath.startsWith(".git")) {
                    return FileVisitResult.SKIP_SUBTREE
                }

                val targetDir = targetPath.resolve(relativePath)
                Files.createDirectories(targetDir)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relativePath = sourcePath.relativize(file)

                // SKIP any .git files
                if (relativePath.startsWith(".git")) {
                    return FileVisitResult.CONTINUE
                }

                val targetFile = targetPath.resolve(relativePath)
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
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
        val diff = StringBuilder()
        val fromFiles = getFilesInDirectory(fromDir)
        val toFiles = getFilesInDirectory(toDir)

        val allFiles = (fromFiles + toFiles).distinct().sorted()

        allFiles.forEach { relativePath ->
            val fromFile = fromDir.resolve(relativePath).toFile()
            val toFile = toDir.resolve(relativePath).toFile()

            val fileDiff = generateFileDiff(fromFile, toFile, fromLabel, toLabel, relativePath)
            if (fileDiff.isNotEmpty()) {
                diff.append(fileDiff).append("\n")
            }
        }

        return diff.toString()
    }

    /**
     * Generate diff for a single file
     */
    private fun generateFileDiff(
        fromFile: File,
        toFile: File,
        fromLabel: String = "a",
        toLabel: String = "b",
        filePath: String? = null
    ): String {
        val path = filePath ?: fromFile.name

        return when {
            !fromFile.exists() && !toFile.exists() -> ""
            !fromFile.exists() && toFile.exists() -> {
                // New file
                buildString {
                    append("diff --git a/$path b/$path\n")
                    append("new file mode 100644\n")
                    append("index 0000000..${getFileHash(toFile)}\n")
                    append("--- /dev/null\n")
                    append("+++ b/$path\n")
                    toFile.readLines().forEach { line ->
                        append("+$line\n")
                    }
                }
            }

            fromFile.exists() && !toFile.exists() -> {
                // Deleted file
                buildString {
                    append("diff --git a/$path b/$path\n")
                    append("deleted file mode 100644\n")
                    append("index ${getFileHash(fromFile)}..0000000\n")
                    append("--- a/$path\n")
                    append("+++ /dev/null\n")
                    fromFile.readLines().forEach { line ->
                        append("-$line\n")
                    }
                }
            }

            else -> {
                // Modified file - generate unified diff
                generateUnifiedDiff(fromFile, toFile, fromLabel, toLabel, path)
            }
        }
    }

    /**
     * Generate unified diff between two files
     */
    private fun generateUnifiedDiff(
        fromFile: File,
        toFile: File,
        fromLabel: String,
        toLabel: String,
        filePath: String
    ): String {
        val fromLines = if (fromFile.exists()) fromFile.readLines() else emptyList()
        val toLines = if (toFile.exists()) toFile.readLines() else emptyList()

        // Simple implementation - for production you might want to use a proper diff library
        if (fromLines == toLines) {
            return "" // No differences
        }

        val diff = StringBuilder()
        diff.append("diff --git a/$filePath b/$filePath\n")
        diff.append("index ${getFileHash(fromFile)}..${getFileHash(toFile)}\n")
        diff.append("--- a/$filePath\n")
        diff.append("+++ b/$filePath\n")

        // Simple line-by-line diff (not optimal, but functional)
        val maxLines = maxOf(fromLines.size, toLines.size)
        for (i in 0 until maxLines) {
            val fromLine = fromLines.getOrNull(i)
            val toLine = toLines.getOrNull(i)

            when {
                fromLine != null && toLine == null -> {
                    diff.append("-$fromLine\n")
                }

                fromLine == null && toLine != null -> {
                    diff.append("+$toLine\n")
                }

                fromLine != toLine -> {
                    diff.append("-$fromLine\n")
                    diff.append("+$toLine\n")
                }
            }
        }

        return diff.toString()
    }

    /**
     * Get all files in a directory recursively
     */
    private fun getFilesInDirectory(dir: Path): List<String> {
        if (!Files.exists(dir)) return emptyList()

        val files = mutableListOf<String>()
        Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relativePath = dir.relativize(file).toString()
                files.add(relativePath)
                return FileVisitResult.CONTINUE
            }
        })
        return files
    }

    /**
     * Generate a simple hash for a file (for diff headers)
     */
    private fun getFileHash(file: File): String {
        return if (file.exists()) {
            file.readText().hashCode().toString(16).take(7)
        } else {
            "0000000"
        }
    }

    /**
     * Detect potential conflicts between sanctuary and current working directory
     */
    private fun detectPotentialConflicts(branchName: String, files: List<String>): List<ConflictInfo> {
            return sanctuaryGitService.detectPotentialConflicts(branchName, files)

    }


    /**
     * Check if a specific file would cause a conflict
     */
    private fun checkFileForConflict(filePath: String): ConflictInfo? {
        return sanctuaryGitService.checkFileForConflict(filePath)

    }


//    /**
//     * Check if two files have identical content
//     */
//    private fun filesAreIdentical(file1: File, file2: File): Boolean {
//        if (file1.length() != file2.length()) return false
//
//        return file1.readBytes().contentEquals(file2.readBytes())
//    }






    /**
     * Finds the root of the Git repository by traversing up from the starting directory.
     * @param startDir The directory to start searching from.
     * @return The File object representing the repository root.
     */
    private fun findGitRootSafely(startDir: File): Pair<File, Boolean> {
        var currentDir: File? = startDir.canonicalFile
        while (currentDir != null) {
            if (File(currentDir, ".git").exists()) {
                return Pair(currentDir, true)
            }
            currentDir = currentDir.parentFile
        }
        // No git repository found, use the starting directory
        return Pair(startDir, false)
    }

    // ← KEPT: Original throwing version for backward compatibility if needed
    private fun findGitRoot(startDir: File): File {
        var currentDir: File? = startDir.canonicalFile
        while (currentDir != null) {
            if (File(currentDir, ".git").exists()) {
                return currentDir
            }
            currentDir = currentDir.parentFile
        }
        throw IOException("Not a Git repository (or any of the parent directories). Cannot find .git folder.")
    }

    /**
     * Create a sanctuary snapshot with timestamped branch
     * Public method that wraps with locking
     */
    fun createSanctuarySnapshot(message: String): String {
        return lockManager.withLock("create_snapshot") {
            createSanctuarySnapshotUnsafe(message)
        }
    }

    /**
     * Create a sanctuary snapshot with timestamped branch
     */

    private fun createSanctuarySnapshotUnsafe(message: String = "Automated sanctuary"): String {
        return sanctuaryGitService.createSanctuarySnapshotUnsafe(message)
    }

    /**
     * Ensure hooksDir is a directory, deleting any file or symlink at that path.
     * Retries a couple of times if necessary, but doesn't hang forever.
     */
    fun ensureHooksDirectory(hooksDir: File, maxTries: Int = 3, retryDelayMs: Long = 50) {
        val hooksPath = hooksDir.toPath()

        // If a file or symlink exists at hooks path, delete it
        if (Files.exists(hooksPath, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(hooksPath, LinkOption.NOFOLLOW_LINKS)) {
            try {
                println("⚠️ Removing file or symlink at hooks path: $hooksPath")
                Files.delete(hooksPath)
            } catch (e: Exception) {
                println("❌ Could not delete file/symlink at $hooksPath: ${e.message}")
                if (!hooksDir.delete()) {
                    throw RuntimeException("Failed to remove file at hooks path: $hooksPath", e)
                }
            }
        }

        var tries = 0
        while ((!hooksDir.exists() || !hooksDir.isDirectory) && tries < maxTries) {
            try {
                Files.createDirectories(hooksPath)
            } catch (e: Exception) {
                println("WARNING: Failed to create hooks directory: $e")
            }
            if (hooksDir.exists() && hooksDir.isDirectory) break
            Thread.sleep(retryDelayMs)
            tries++
        }

        if (!hooksDir.exists() || !hooksDir.isDirectory) {
            throw RuntimeException("Failed to create hooks directory at $hooksPath after $tries tries")
        }
    }


    private fun setupBrowseSpaceProtection(branchName: String) {
        // we need this as The browse workspace is not a real git repo unless we create .git ourselves.
        // its come from a git reo where there is no .git its been renamed
        val workspaceGitDir = File(standardCheckoutPath, ".git")
        if (workspaceGitDir.exists() && !workspaceGitDir.isDirectory) {
            println("PRE-PROTECTION: .git exists but is not directory! Type: file? ${workspaceGitDir.isFile}")
            println("This probably just means it came from a sanctuary.")
            workspaceGitDir.delete()
            // Defensive: Try NIO as well
            try {
                Files.deleteIfExists(workspaceGitDir.toPath())
            } catch (e: Exception) {
                println("NIO delete failed: ${e.message}")
            }
            workspaceGitDir.mkdirs()
            println("FIXED: Now .git is directory? ${workspaceGitDir.isDirectory}")
        }


        val hooksDir = File(workspaceGitDir, "hooks")
        val preCommitHook = File(hooksDir, "pre-commit")

        println("DEBUG: Setting up protection in: ${workspaceGitDir.absolutePath}")
        println("DEBUG: Hooks dir: ${hooksDir.absolutePath}")
        println("DEBUG: Pre-commit path: ${preCommitHook.absolutePath}")

        // Check current state
        println("DEBUG: Workspace git dir exists: ${workspaceGitDir.exists()}")
        println("DEBUG: Workspace git dir is directory: ${workspaceGitDir.isDirectory()}")
        println("DEBUG: Hooks dir exists: ${hooksDir.exists()}")
        println("DEBUG: Hooks dir is directory: ${hooksDir.isDirectory()}")
        println("DEBUG: Pre-commit exists: ${preCommitHook.exists()}")

        // List what's actually in the .git directory
        if (workspaceGitDir.exists()) {
            println("DEBUG: Contents of .git directory:")
            workspaceGitDir.listFiles()?.forEach { file ->
                println("  - ${file.name} (${if (file.isDirectory()) "dir" else "file"})")
            }
        }

        ensureHooksDirectory(File(workspaceGitDir, "hooks"))

        val hookScript = """
        #!/bin/bash
        
        current_branch=${'$'}(git symbolic-ref --short HEAD 2>/dev/null)
        
        if [[ "${'$'}current_branch" =~ ^(auto-|fake-) ]]; then
            echo "🔒 Cannot commit to sanctuary branch: ${'$'}current_branch"
            exit 1
        fi
        
        if [[ "${'$'}current_branch" =~ ^practice- ]]; then
            echo "✅ Practice branch commit allowed: ${'$'}current_branch"
            exit 0
        fi
        
        echo "🔒 Commits blocked in sanctuary workspace for safety"
        exit 1
    """.trimIndent()

        try {
            preCommitHook.writeText(hookScript)
            preCommitHook.setExecutable(true)
            println("DEBUG: Successfully wrote pre-commit hook")
        } catch (e: Exception) {
            println("DEBUG: Failed to write hook: ${e.message}")
            throw e
        }
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

    /**
     * Checkout a sanctuary branch to the standardized location for inspection
     */
    fun checkoutSanctuaryToStandardLocation(branchName: String) {
        lockManager.withLock("checkout") {
            var success = false

            try {
                val git = initializeRepo()

                try {
                    val commit = git.repository.resolve("refs/heads/$branchName")

                    git.checkout()
                        .setName(branchName)
                        .call()

                    // NEW: Install protection hook after checkout
                    setupBrowseSpaceProtection(branchName)

                    println("✅ Checked out sanctuary '$branchName' to temp workspace (detached HEAD)")
                    println("📍 Detached at commit: ${commit.abbreviate(7).name()}")
                    println("🔒 Workspace protected against commits to sanctuary branches")

                    success = true

                } finally {
                    git.close()
                }
            } finally {

            }
        }
    }

    /**
     * Generate a patch file showing differences between two sanctuary branches
     */
    fun generatePatch(fromBranch: String, toBranch: String, outputFile: File? = null): String {
        return lockManager.withLock("generate_patch") {
            val git = initializeRepo()

            return@withLock try {
                val repository = git.repository
                val fromCommit = repository.resolve("refs/heads/$fromBranch")
                val toCommit = repository.resolve("refs/heads/$toBranch")

                if (fromCommit == null || toCommit == null) {
                    throw IllegalArgumentException("One or both branches not found: $fromBranch, $toBranch")
                }

                val outputStream = ByteArrayOutputStream()
                val formatter = DiffFormatter(outputStream)
                formatter.setRepository(repository)
                formatter.setDiffComparator(RawTextComparator.DEFAULT)
                formatter.isDetectRenames = true

                val fromTree = repository.parseCommit(fromCommit).tree
                val toTree = repository.parseCommit(toCommit).tree

                formatter.format(fromTree, toTree)
                formatter.flush()

                val patchContent = outputStream.toString()

                outputFile?.let { file ->
                    file.writeText(patchContent)
                    println("Patch saved to: ${file.absolutePath}")
                }

                patchContent
            } finally {
                git.close()
            }
        }
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
        return lockManager.withLock("compare") {
            val branches = listSanctuaryBranches()
            if (branches.isEmpty()) {
                println("No sanctuary branches found")
                return@withLock null
            }

            val latestBranch = branches.last()

            // Create temporary snapshot of current state
            val tempBranch = createSanctuarySnapshot("Temporary comparison snapshot")

            return@withLock try {
                generatePatch(latestBranch, tempBranch)
            } finally {
                // Clean up temporary branch
                val git = initializeRepo()
                try {
                    // First checkout a different branch
                    git.checkout()
                        .setName(latestBranch)
                        .call()

                    // Then delete the temporary branch
                    git.branchDelete()
                        .setBranchNames(tempBranch)
                        .setForce(true)
                        .call()
                } finally {
                    git.close()
                }
            }
        }
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
     * Check if sanctuary exists
     */
    fun sanctuaryExists(): Boolean {
        return sanctuaryDir.exists() &&
                sanctuaryGitDir.exists() &&
                sanctuaryGitDir.resolve("HEAD").exists()
    }

    /**
     * Validate sanctuary binding
     */
    fun validateBinding(): Boolean {
        return try {
            if (!metadataFile.exists()) {
                true // No metadata yet is valid
            } else {
                val metadata = parser.read(metadataFile)
                metadata.identity.boundProjectPath == projectRoot.absolutePath
            }
        } catch (e: Exception) {
            true // If can't read metadata, assume valid
        }
    }

    // No need for @OptIn(ExperimentalTime::class) anymore
    @OptIn(ExperimentalTime::class)
    fun getSanctuaryStatus(): String {
        return try {
            // Assume you have a parser instance to read the file
            val metadata = parser.read(metadataFile)
            """
        Project: $projectName
        Status: ${metadata.state.status}
        Last Operation: ${metadata.state.lastOperation?.type}
        Last Success: ${metadata.state.lastOperation?.success}
        Completed: ${metadata.state.lastOperation?.completedUtc}
        """.trimIndent()

        } catch (e: Exception) {
            "Status: Unknown (${e.message})"
        }
    }

    companion object {
        private const val CONFIG_FILE_NAME = "sanctuary.yaml"
    }

    // Add lock status methods
    fun isLocked(): Boolean = lockManager.isLocked()

    fun getLockInfo(): String? {
        val info = lockManager.getLockInfo()
        return info?.let {
            "Locked by process ${it.processId} at ${it.timestamp} for operation: ${it.operation}"
        }
    }

    fun forceUnlock(): Boolean = lockManager.forceUnlock()

}

// Extension function to make file operations easier
fun File.deleteRecursively(): Boolean {
    return if (isDirectory) {
        listFiles()?.all { it.deleteRecursively() } == true && delete()
    } else {
        delete()
    }
}