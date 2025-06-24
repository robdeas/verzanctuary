package tech.robd.verzanctuary.services
/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/services/SanctuaryDiffService.kt
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
import tech.robd.verzanctuary.lock.LockManager
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Provides diffing and patch generation functionality for the VerZanctuary system.
 *
 * Supports comparing files, directories, and entire branches using simple or git-style diffs.
 * All operations are protected with locks to ensure safe concurrent access.
 *
 * @property lockManager             Used to serialize diff and patch operations.
 * @property sanctuaryGitService     Handles git operations, patch creation, and branch management.
 */
class SanctuaryDiffService(private val lockManager: LockManager, private val sanctuaryGitService: SanctuaryGitService) {

    /**
     * Generate diff for a single file
     */
    fun generateFileDiff(
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
     * Generate a patch file showing differences between two sanctuary branches
     */
    fun generatePatch(fromBranch: String, toBranch: String, outputFile: File? = null): String {
        return sanctuaryGitService.generatePatch(fromBranch, toBranch, outputFile)
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
     * Generate diff between two directories
     */
    fun generateDiffBetweenDirectories(
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
     * Diffs the current working directory against a specified sanctuary branch.
     * Locks the operation to prevent races. Uses a standard checkout path for the branch.
     */
    fun diffSanctuaryWithWorking(branchName: String, projectRoot: File, standardCheckoutPath: File): String {
        return lockManager.withLock("diff_sanctuary") {
            val git = sanctuaryGitService.initializeRepo()
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
     * Diffs a specific file between a sanctuary branch and the working directory.
     * Useful for focused reviews or inline patch previews.
     */
    fun diffFileWithWorking(
        branchName: String,
        filePath: String,
        projectRoot: File,
        standardCheckoutPath: File
    ): String {
        return lockManager.withLock("diff_file") {
            val git = sanctuaryGitService.initializeRepo()
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

    /**
     * Compares the working directory to the latest sanctuary branch using a temporary snapshot.
     * Generates a patch and then deletes the temp branch. Safe for CI or inspection flows.
     */
    fun compareWithLatest(): String? {
        return lockManager.withLock("compare") {
            val branches = sanctuaryGitService.listBranches()
            if (branches.isEmpty()) {
                println("No sanctuary branches found")
                return@withLock null
            }

            val latestBranch = branches.last()

            // Create temporary snapshot of current state
            val tempBranch = sanctuaryGitService.createSanctuarySnapshot("Temporary comparison snapshot")

            return@withLock try {
                generatePatch(latestBranch, tempBranch)
            } finally {
                // Clean up temporary branch
                val git = sanctuaryGitService.initializeRepo()
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

}