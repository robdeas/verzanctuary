package tech.robd.verzanctuary.services

/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/services/SanctuaryGitService.kt
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

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import tech.robd.verzanctuary.SanctuaryMetadataParser
import tech.robd.verzanctuary.data.SanctuaryMetadata
import tech.robd.verzanctuary.lock.LockManager
import tech.robd.verzanctuary.path.ConflictInfo
import tech.robd.verzanctuary.utils.Utils
import tech.robd.verzanctuary.utils.Utils.SANCTUARY_FOLDER_SUFFIX
import tech.robd.verzanctuary.utils.Utils.SANCTUARY_WORKING_TMP
import tech.robd.verzanctuary.utils.deleteRecursively
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * Provides all Git and version control operations for the VerZanctuary system.
 *
 * Handles repository initialization, branch management, patch/diff creation, sanctuary snapshotting,
 * safe checkouts, and conflict detection for AI-assisted or manual developer workflows.
 *
 * Integrates with SanctuaryFileService, SanctuaryConflictService, and event logging for robust,
 * atomic operations that protect developer work and system integrity.
 *
 * @property sanctuaryFileService      For copying files in/out of the repo.
 * @property sanctuaryConflictService  For finding file/directory conflicts.
 * @property sanctuaryGitDir           The .git directory for the sanctuary.
 * @property standardCheckoutPath      Working tree root used for repo operations.
 * @property projectName               The project’s human-friendly name.
 * @property sanctuaryDir              Root folder for sanctuary metadata/state.
 * @property projectRoot               User’s source/project root directory.
 * @property metadataFile              Location of sanctuary.yaml metadata.
 * @property parser                    Reads/writes SanctuaryMetadata to YAML.
 * @property lockManager               For serializing mutating operations.
 * @property sanctuaryLogService       For audit and trace logging.
 * @property isGitRepository           Whether the target project is git-based.
 */
class SanctuaryGitService(
    private val sanctuaryFileService: SanctuaryFileService,
    private val sanctuaryConflictService: SanctuaryConflictService,
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

    /**
     * Initialize or open a sanctuary repository.
     * - Creates directories and initial metadata if needed.
     * - If missing, repairs sanctuary.yaml using default values.
     *
     * @return Git handle for further operations.
     */
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
     * Generate a patch between two branches using JGit.
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
     * Actually perform the checkout to working directory
     */
    private fun performCheckoutToWorkingUnsafe(branchName: String, files: List<String>) {
        val git = initializeRepo()
        try {
            // Checkout to temp first
            git.checkout().setName(branchName).call()

            if (files.isEmpty()) {
                // Copy entire sanctuary to working directory
                sanctuaryFileService.copyFromSanctuaryToWorkingDirectory()
            } else {
                // Copy specific files
                files.forEach { filePath ->
                    sanctuaryFileService.copyFileFromSanctuaryToWorking(filePath)
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

    fun copyFilesToSanctuary() {
        sanctuaryFileService.copyFilesToSanctuary()
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
     * List files in a branch, or all files if not specified.
     * Used for diff/conflict detection.
     */
    private fun listFilesInBranch(branchName: String, files: List<String>): List<String> {
        // First checkout to temp to compare
        val git = initializeRepo()
        val filesToCheck: List<String>
        try {
            git.checkout().setName(branchName).call()
            filesToCheck = files.ifEmpty {
                // this returns the list it was called on if its not empty
                // and only does the getAllFilesInSanctuary if its empty.
                getAllFilesInSanctuary()
            }
        } finally {
            git.close()
        }

        return filesToCheck
    }

    /**
     * Detect potential conflicts between sanctuary and current working directory
     */
    fun detectPotentialConflicts(branchName: String, files: List<String>): List<ConflictInfo> {
        val conflicts: List<ConflictInfo>//mutableListOf<ConflictInfo>()
        val filesToCheck = listFilesInBranch(branchName, files)
        conflicts = sanctuaryConflictService.detectAllPotentialConflicts(branchName, filesToCheck)

        return conflicts
    }

    /**
     * Get all files in the currently checked-out sanctuary don't forget to ignore the .git folder
     */
    private fun getAllFilesInSanctuary(): List<String> {
        return sanctuaryFileService.getAllFilesInSanctuary()
    }


}