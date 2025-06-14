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
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import tech.robd.verzanctuary.data.SanctuaryMetadata
import tech.robd.verzanctuary.lock.LockManagerFactory
import tech.robd.verzanctuary.path.ConflictInfo
import tech.robd.verzanctuary.path.SanctuaryPathResolver
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime


private const val SANCTUARY_WORKING_TMP = "sanctuary.workingTmp"
private const val SANCTUARY_FOLDER_SUFFIX = ".sanctuary"

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

    /**
     * export the absolute path of the lab workspace, we probably need this for testability
     */
    fun getLabWorkspaceLocation(): String = labWorkspacePath.absolutePath

    private fun getProjectLogFile(): File = logFile

    fun showSanctuaryLog(lastN: Int = 10): List<String> {
        val logFile = getProjectLogFile()
        if (!logFile.exists()) return emptyList()
        return logFile.readLines().takeLast(lastN)
    }

    private fun logSanctuaryEvent(
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
            "\"$k\":${toJsonValue(v)}"
        }
        getProjectLogFile().appendText(json + "\n")
    }

    // Utility for simple JSON encoding (does NOT handle lists, but fine for this use)
    private fun toJsonValue(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> v.entries.joinToString(
            prefix = "{", postfix = "}"
        ) { (k, v2) -> "\"$k\":${toJsonValue(v2)}" }

        is String -> "\"" + v.replace("\"", "\\\"") + "\""
        else -> "\"" + v.toString().replace("\"", "\\\"") + "\""
    }

    private fun saveMetadata(metadata: SanctuaryMetadata) {
        parser.write(metadataFile, metadata)
    }


    fun initializeSanctuaryRepo(): Git {

        val headFile = sanctuaryGitDir.resolve("HEAD")
        val isNewRepo = !sanctuaryGitDir.exists() || !headFile.exists()

        val git: Git

        if (isNewRepo) {
            // --- This block runs when a sanctuary is created for the first time ---

            // Create directory structure
            sanctuaryDir.mkdirs()
            println("🏛️ Creating new version sanctuary: $projectName")
            println("📍 Location: ${sanctuaryDir.absolutePath}")

            if (!standardCheckoutPath.exists()) {
                standardCheckoutPath.mkdirs()
            }

            // Initialize the Git repository
            val repository = FileRepositoryBuilder()
                .setGitDir(sanctuaryGitDir)
                .setWorkTree(standardCheckoutPath)
                .build()
            repository.create()

            // -----------------------------------------------------------
            // Create and write the initial metadata file <<
            // -----------------------------------------------------------
            println("✍️ Creating initial sanctuary.yaml metadata file...")
            val defaultMetadata = SanctuaryMetadata.createNew(projectName, projectRoot.absolutePath)
            try {
                parser.write(metadataFile, defaultMetadata)
                println(" -> Metadata file created successfully.")
            } catch (e: IOException) {
                sanctuaryDir.deleteRecursively() // Clean up on failure
                throw IOException("Failed to write initial metadata file.", e)
            }
            // -----------------------------------------------------------

            git = Git(repository)

        } else {
            // --- This block runs when opening an existing sanctuary ---

            // As a safety check, if the .git folder exists but the yaml is missing, create it.
            if (!metadataFile.exists()) {
                println("⚠️ Metadata file was missing. Recreating a default sanctuary.yaml...")
                val defaultMetadata = SanctuaryMetadata.createNew(projectName, projectRoot.absolutePath)
                parser.write(metadataFile, defaultMetadata)
            }

            val repository = FileRepositoryBuilder()
                .setGitDir(sanctuaryGitDir)
                .setWorkTree(standardCheckoutPath)
                .build()
            git = Git(repository)
        }

        return git

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
    private fun copyFilesToSanctuary() {
        val sourcePath = projectRoot.toPath()
        val targetPath = standardCheckoutPath.toPath()

        // Clear existing files in sanctuary (keep directory clean)
        if (standardCheckoutPath.exists()) {
            standardCheckoutPath.listFiles()?.forEach { file ->
                if (file.name != ".git") { // Don't delete the .git directory
                    file.deleteRecursively()
                }
            }
        } else {
            standardCheckoutPath.mkdirs()
        }

        // Copy files, excluding .git directories and sanctuary directories
        Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val dirName = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE

                // Skip .git directories only if we're in a git repository
                if (isGitRepository && dirName == ".git") {
                    return FileVisitResult.SKIP_SUBTREE
                }

                // Skip ALL sanctuary directories (.sanctuary extension)
                if (dirName.endsWith(SANCTUARY_FOLDER_SUFFIX)) {
                    return FileVisitResult.SKIP_SUBTREE
                }

                // Skip sanctuary working temp directories
                if (dirName == SANCTUARY_WORKING_TMP) {
                    return FileVisitResult.SKIP_SUBTREE
                }

                // Skip any directory that matches our known sanctuary locations  ← NEW
                if (isSanctuaryRelatedDirectory(dir)) {
                    return FileVisitResult.SKIP_SUBTREE
                }

                val targetDir = targetPath.resolve(sourcePath.relativize(dir))
                Files.createDirectories(targetDir)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                // Skip any file living under a .git parent at any level (only if git repo)
                if (isGitRepository) {
                    var parent = file.parent
                    while (parent != null && parent != sourcePath.parent) {
                        if (parent.fileName?.toString() == ".git") {
                            return FileVisitResult.CONTINUE
                        }
                        parent = parent.parent
                    }
                }

                // Skip files in sanctuary-related directories
                if (isInSanctuaryRelatedDirectory(file, sourcePath)) {
                    return FileVisitResult.CONTINUE
                }

                val targetFile = targetPath.resolve(sourcePath.relativize(file))
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
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
        lockManager.withLock("checkout_to_working") {
            // 1. First check for potential conflicts
            val conflicts = detectPotentialConflicts(branchName, files)

            if (conflicts.isNotEmpty()) {
                // 2. Warn user about conflicts
                println("⚠️  The following files have changed and will cause conflicts:")
                conflicts.forEach { conflict ->
                    println("  📝 ${conflict.filePath}")
                    println("     Working: ${conflict.workingState}")
                    println("     Sanctuary: ${conflict.sanctuaryState}")
                }
                println("")
                println("💡 Options:")
                println("   • Use --temp to inspect first: verz checkout $branchName --temp")
                println("   • Use --force to proceed anyway")
                println("   • Commit your current changes first")
                println("")

                // 3. Abort unless forced
                if (!forceOverwrite) {
                    println("❌ Checkout aborted to prevent conflicts")
                    return@withLock
                }
            }

            // 4. Create safety backup
            createSanctuarySnapshotUnsafe("Auto-backup before checkout")

            // 5. Proceed with checkout
            performCheckoutToWorkingUnsafe(branchName, files)
        }
    }

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
            val git = initializeSanctuaryRepo()
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
            val git = initializeSanctuaryRepo()
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
                val git = initializeSanctuaryRepo()
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
                logSanctuaryEvent(
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
        val conflicts = mutableListOf<ConflictInfo>()

        // First checkout to temp to compare
        val git = initializeSanctuaryRepo()
        try {
            git.checkout().setName(branchName).call()

            val filesToCheck = if (files.isEmpty()) {
                // Get all files from sanctuary
                getAllFilesInSanctuary()
            } else {
                files
            }

            filesToCheck.forEach { filePath ->
                val conflict = checkFileForConflict(filePath)
                if (conflict != null) {
                    conflicts.add(conflict)
                }
            }
        } finally {
            git.close()
        }

        return conflicts
    }

    /**
     * Check if a specific file would cause a conflict
     */
    private fun checkFileForConflict(filePath: String): ConflictInfo? {
        val workingFile = File(projectRoot, filePath)
        val sanctuaryFile = File(standardCheckoutPath, filePath)

        val workingExists = workingFile.exists()
        val sanctuaryExists = sanctuaryFile.exists()

        return when {
            !workingExists && !sanctuaryExists -> null // No conflict
            !workingExists && sanctuaryExists -> {
                // New file from sanctuary
                ConflictInfo(filePath, "missing", "new file")
            }

            workingExists && !sanctuaryExists -> {
                // File exists in working but not in sanctuary
                ConflictInfo(filePath, "new file", "missing")
            }

            workingExists && sanctuaryExists -> {
                // Both exist - check if different
                if (!filesAreIdentical(workingFile, sanctuaryFile)) {
                    ConflictInfo(filePath, "modified", "different content")
                } else {
                    null // Same content, no conflict
                }
            }

            else -> null
        }
    }

    /**
     * Check if two files have identical content
     */
    private fun filesAreIdentical(file1: File, file2: File): Boolean {
        if (file1.length() != file2.length()) return false

        return file1.readBytes().contentEquals(file2.readBytes())
    }

    /**
     * Get all files in the currently checked out sanctuary dont forget to ignore .git folder
     */
    private fun getAllFilesInSanctuary(): List<String> {
        val files = mutableListOf<String>()
        val basePath = standardCheckoutPath.toPath()

        Files.walkFileTree(basePath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val dirName = dir.fileName?.toString()

                // Skip .git directories (same as copying logic)
                if (isGitRepository && dirName == ".git") {
                    return FileVisitResult.SKIP_SUBTREE
                }

                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                // Skip files in .git directories (same as copying logic)
                if (isInSanctuaryRelatedDirectory(file, basePath)) {
                    return FileVisitResult.CONTINUE
                }

                val relativePath = basePath.relativize(file).toString()
                files.add(relativePath)
                return FileVisitResult.CONTINUE
            }
        })

        return files
    }

    /**
     * Actually perform the checkout to working directory
     */
    private fun performCheckoutToWorkingUnsafe(branchName: String, files: List<String>) {
        val git = initializeSanctuaryRepo()
        try {
            // Checkout to temp first
            git.checkout().setName(branchName).call()

            if (files.isEmpty()) {
                // Copy entire sanctuary to working directory
                copyFromSanctuaryToWorkingDirectory()
            } else {
                // Copy specific files
                files.forEach { filePath ->
                    copyFileFromSanctuaryToWorking(filePath)
                }
            }

            println("✅ Checked out sanctuary '$branchName' to working directory")
            if (files.isNotEmpty()) {
                println("📁 Restored ${files.size} file(s)")
            }
        } finally {
            git.close()
        }
    }

    /**
     * Copy all files from Sanctuary to working directory
     */
    private fun copyFromSanctuaryToWorkingDirectory() {
        val sourcePath = standardCheckoutPath.toPath()
        val targetPath = projectRoot.toPath()

        Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relPath = sourcePath.relativize(dir).toString()
                if (relPath == ".git" || relPath.startsWith(".git/")) {
                    // SKIP sanctuary .git entirely
                    return FileVisitResult.SKIP_SUBTREE
                }
                val targetDir = targetPath.resolve(sourcePath.relativize(dir))
                Files.createDirectories(targetDir)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relPath = sourcePath.relativize(file).toString()
                if (relPath.startsWith(".git/") || relPath == ".git") {
                    // Skip .git files
                    return FileVisitResult.CONTINUE
                }
                val targetFile = targetPath.resolve(sourcePath.relativize(file))
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
    }


    /**
     * Copy a single file from temp to working directory
     */
    private fun copyFileFromSanctuaryToWorking(filePath: String) {
        val sourceFile = File(standardCheckoutPath, filePath)
        val targetFile = File(projectRoot, filePath)

        if (sourceFile.exists()) {
            targetFile.parentFile?.mkdirs()
            sourceFile.copyTo(targetFile, overwrite = true)
            println("✅ Restored: $filePath")
        } else {
            println("⚠️  File not found in sanctuary: $filePath")
        }
    }

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
     * Check if a directory is sanctuary-related and should be excluded
     */
    private fun isSanctuaryRelatedDirectory(dir: Path): Boolean {
        val dirFile = dir.toFile()

        // Check if it's our exact sanctuary directory
        if (dirFile.absolutePath == sanctuaryDir.absolutePath) {
            return true
        }

        // Check if it's our exact working temp directory
        if (dirFile.absolutePath == standardCheckoutPath.absolutePath) {
            return true
        }

        // Check if it matches environment variable paths
        val envSanctuaryDir = System.getenv("VERZANCTUARY_SANCTUARY_DIR")
        if (envSanctuaryDir != null && dirFile.absolutePath.startsWith(envSanctuaryDir)) {
            return true
        }

        val envWorkspaceDir = System.getenv("VERZANCTUARY_WORKSPACE_DIR")
        if (envWorkspaceDir != null && dirFile.absolutePath.startsWith(envWorkspaceDir)) {
            return true
        }

        return false
    }

    /**
     * Check if a file is in a sanctuary-related directory and should be excluded
     */
    private fun isInSanctuaryRelatedDirectory(file: Path, sourcePath: Path): Boolean {
        var parent = file.parent
        while (parent != null && parent != sourcePath.parent) {
            val parentName = parent.fileName?.toString()

            // Skip files in .git directories (if git repo)
            if (isGitRepository && parentName == ".git") {
                return true
            }

            // Skip files in any .sanctuary directory
            if (parentName?.endsWith(SANCTUARY_FOLDER_SUFFIX) == true) {
                return true
            }

            // Skip files in sanctuary.workingTmp directories
            if (parentName == SANCTUARY_WORKING_TMP) {
                return true
            }

            parent = parent.parent
        }

        return false
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

        var branchName = ""
        var success = false


        val git = initializeSanctuaryRepo()

        try {
            // Copy all files to sanctuary repository
            copyFilesToSanctuary()

            // Generate timestamp for branch name
            val timestamp = LocalDateTime.now().format(dateFormatter)
            branchName = "auto-$timestamp"

            // Add all files to staging
            git.add()
                .addFilepattern(".")
                .call()

            // Also add deleted files
            git.add()
                .addFilepattern(".")
                .setUpdate(true)
                .call()

            // Check if this is the first commit (no HEAD yet)
            val repository = git.repository
            val hasCommits = try {
                repository.resolve("HEAD") != null
            } catch (e: Exception) {
                false
            }

            // Handle the first commit case
            if (!hasCommits) {
                // Create initial commit on the default branch (master)
                val commit = git.commit()
                    .setMessage("Initial sanctuary commit - $timestamp")
                    .setAuthor("VerZanctuary System", "verzanctuary@tech.robd")
                    .call()

                println("Created initial sanctuary commit: ${commit.id.name}")

                // Create and checkout new branch
                git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .call()
            } else {
                // Check if there are any changes to commit
                val status = git.status().call()
                if (status.added.isEmpty() &&
                    status.changed.isEmpty() &&
                    status.removed.isEmpty() &&
                    status.untracked.isEmpty()
                ) {
                    logSanctuaryEvent(
                        type = "snapshot",
                        message = message,
                        result = "nochange"
                    )
                    println("No changes detected, skipping commit")
                    success = true
                    return branchName
                }

                // Create new branch from current HEAD
                git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .call()
            }

            // Commit the changes
            val commit = git.commit()
                .setMessage("$message - $timestamp")
                .setAuthor("VerZanctuary System", "verzanctuary@tech.robd")
                .call()

            logSanctuaryEvent(
                type = "snapshot",
                message = message,
                result = "created",
                branch = branchName
            )
            println("Created sanctuary snapshot: $branchName")
            println("Commit: ${commit.id.name}")

            success = true
            return branchName
        } catch (e: Exception) {
            logSanctuaryEvent(
                type = "snapshot",
                message = message,
                result = "error",
                details = mapOf("exception" to e.toString())
            )
            throw e

        } finally {
            git.close()
        }

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
//
//        // Clean up any existing hooks that's not a directory
//        if (hooksDir.exists() && !hooksDir.isDirectory()) {
//            println("DEBUG: Hooks exists but is not a directory - deleting it")
//            val deleted = hooksDir.delete()
//            println("DEBUG: Deleted hooks: $deleted")
//        }


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
        val git = initializeSanctuaryRepo()

        return try {
            git.branchList()
                .call()
                .map { it.name.removePrefix("refs/heads/") }
                .filter { it.startsWith("auto-") }
                .sorted()
        } catch (e: Exception) {
            // Repository might be empty
            emptyList()
        } finally {
            git.close()
        }
    }

    /**
     * Checkout a specific sanctuary branch
     */
    fun checkoutSanctuary(branchName: String) {
        lockManager.withLock("checkout_branch") {
            val git = initializeSanctuaryRepo()

            try {
                git.checkout()
                    .setName(branchName)
                    .call()

                println("Checked out sanctuary branch: $branchName")
            } finally {
                git.close()
            }
        }
    }

    /**
     * Checkout a sanctuary branch to the standardized location for inspection
     */
    fun checkoutSanctuaryToStandardLocation(branchName: String) {
        lockManager.withLock("checkout") {
            var success = false

            try {
                val git = initializeSanctuaryRepo()

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
            val git = initializeSanctuaryRepo()

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
                val git = initializeSanctuaryRepo()
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

        lockManager.withLock("cleanup") {
            var success = false

            try {
                val branches = listSanctuaryBranches()
                val toDelete = branches.dropLast(keepCount)

                if (toDelete.isNotEmpty()) {
                    val git = initializeSanctuaryRepo()

                    try {
                        // First, ensure we're not on any branch we're about to delete
                        val branchesToKeep = branches.takeLast(keepCount)
                        if (branchesToKeep.isNotEmpty()) {
                            git.checkout()
                                .setName(branchesToKeep.first())
                                .call()
                        }

                        toDelete.forEach { branchName ->
                            try {
                                git.branchDelete()
                                    .setBranchNames(branchName)
                                    .setForce(true)
                                    .call()
                                println("Deleted old sanctuary branch: $branchName")
                            } catch (e: Exception) {
                                println("Failed to delete branch $branchName: ${e.message}")
                            }
                        }
                        success = true
                    } finally {
                        git.close()
                    }
                }
            } finally {

            }
        }
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