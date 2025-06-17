package tech.robd.verzanctuary.services

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import tech.robd.verzanctuary.SanctuaryMetadataParser
import tech.robd.verzanctuary.data.SanctuaryMetadata
import tech.robd.verzanctuary.deleteRecursively
import tech.robd.verzanctuary.lock.LockManager
import tech.robd.verzanctuary.path.ConflictInfo
import tech.robd.verzanctuary.utils.Utils
import tech.robd.verzanctuary.utils.Utils.SANCTUARY_FOLDER_SUFFIX
import tech.robd.verzanctuary.utils.Utils.SANCTUARY_WORKING_TMP
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime

class SanctuaryGitService(
    private val sanctuaryGitDir: File,
    private val standardCheckoutPath: File,
    private val projectName: String,
    private val sanctuaryDir: File,
    private val projectRoot: File,
    private val metadataFile: File,
    private val parser: SanctuaryMetadataParser,
    private val lockManager: LockManager,
    private val sanctuaryLogService: SanctuaryLogService,
    private val isGitRepository: Boolean
) {

    fun initializeRepo(): Git {

        val headFile = sanctuaryGitDir.resolve("HEAD")
        val isNewRepo = !sanctuaryGitDir.exists() || !headFile.exists()

        val git: Git

        if (isNewRepo) {
            // --- This block runs when a sanctuary is created for the first time ---

            // Create directory structure
            sanctuaryDir.mkdirs()
            println("🏛️ Creating new version sanctuary: ${projectName}")
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
            val defaultMetadata = SanctuaryMetadata.Companion.createNew(
                projectName,
                projectRoot.absolutePath
            )
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
                val defaultMetadata = SanctuaryMetadata.Companion.createNew(
                    projectName,
                    projectRoot.absolutePath
                )
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
     * List all sanctuary branches
     */
    fun listBranches(): List<String> {
        val git = initializeRepo()

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
            val git = initializeRepo()

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
     * Delete old sanctuary branches (keep only the most recent N)
     */
    fun cleanupOldSanctuaries(keepCount: Int = 10) {

        lockManager.withLock("cleanup") {
            var success = false

            try {
                val branches = listBranches()
                val toDelete = branches.dropLast(keepCount)

                if (toDelete.isNotEmpty()) {
                    val git = initializeRepo()

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
     * Actually perform the checkout to working directory
     */
    private fun performCheckoutToWorkingUnsafe(branchName: String, files: List<String>) {
        val git = initializeRepo()
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

//    fun checkoutSanctuaryToWorking(
//        branchName: String,
//        files: List<String> = emptyList(),
//        forceOverwrite: Boolean = false
//    ) {
//        lockManager.withLock("checkout_to_working") {
//            // 1. First check for potential conflicts
//            val conflicts = detectPotentialConflicts(branchName, files)
//
//            if (conflicts.isNotEmpty()) {
//                // 2. Warn user about conflicts
//                println("⚠️  The following files have changed and will cause conflicts:")
//                conflicts.forEach { conflict ->
//                    println("  📝 ${conflict.filePath}")
//                    println("     Working: ${conflict.workingState}")
//                    println("     Sanctuary: ${conflict.sanctuaryState}")
//                }
//                println("")
//                println("💡 Options:")
//                println("   • Use --temp to inspect first: verz checkout $branchName --temp")
//                println("   • Use --force to proceed anyway")
//                println("   • Commit your current changes first")
//                println("")
//
//                // 3. Abort unless forced
//                if (!forceOverwrite) {
//                    println("❌ Checkout aborted to prevent conflicts")
//                    return@withLock
//                }
//            }
//
//            // 4. Create safety backup
//            createSanctuarySnapshotUnsafe("Auto-backup before checkout")
//
//            // 5. Proceed with checkout
//            performCheckoutToWorkingUnsafe(branchName, files)
//        }
//    }


    fun copyFilesToSanctuary() {
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
     */
    fun createSanctuarySnapshotUnsafe(message: String = "Automated sanctuary"): String {

        var branchName = ""
        var success = false


        val git = initializeRepo()

        try {
            // Copy all files to sanctuary repository
            copyFilesToSanctuary()

            // Generate timestamp for branch name
            val timestamp = LocalDateTime.now().format(Utils.dateFormatter)
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
                    sanctuaryLogService.logSanctuaryEvent(
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

            sanctuaryLogService.logSanctuaryEvent(
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
            sanctuaryLogService.logSanctuaryEvent(
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
     * Detect potential conflicts between sanctuary and current working directory
     */
    fun detectPotentialConflicts(branchName: String, files: List<String>): List<ConflictInfo> {
        val conflicts = mutableListOf<ConflictInfo>()

        // First checkout to temp to compare
        val git = initializeRepo()
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
     * Check if a specific file would cause a conflict
     */
    fun checkFileForConflict(filePath: String): ConflictInfo? {
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
                if (!Utils.filesAreIdentical(workingFile, sanctuaryFile)) {
                    ConflictInfo(filePath, "modified", "different content")
                } else {
                    null // Same content, no conflict
                }
            }

            else -> null
        }
    }



}