package tech.robd.verzanctuary.services

/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/services/SanctuaryFileService.kt
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
import tech.robd.verzanctuary.utils.SanctuaryPathUtils.isInSanctuaryRelatedDirectory
import tech.robd.verzanctuary.utils.Utils.SANCTUARY_FOLDER_SUFFIX
import tech.robd.verzanctuary.utils.Utils.SANCTUARY_WORKING_TMP
import tech.robd.verzanctuary.utils.deleteRecursively
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * Handles all file and directory operations for sanctuary checkouts, lab spaces, and working directories.
 *
 * This service is responsible for:
 * - Copying files between different workspaces (lab, sanctuary, working)
 * - Skipping `.git` and sanctuary-related directories to avoid accidental interference with git repos or system state
 * - Ensuring directories are created as needed and files are always copied safely
 * - Exposing file discovery helpers for use in diff and synchronization logic
 *
 * @property projectRoot           The root directory of the user's working project.
 * @property standardCheckoutPath  The main path where sanctuary checkouts occur.
 * @property sanctuaryDir          The directory containing sanctuary state and metadata.
 * @property isGitRepository       True if the project uses git; affects which folders are skipped.
 */
class SanctuaryFileService(
    private val projectRoot: File,
    private val standardCheckoutPath: File,
    private val sanctuaryDir: File,
    private val isGitRepository: Boolean,

    ) {

    /**
     * Copies all files from the current sanctuary checkout (standardCheckoutPath) to the given lab workspace,
     * skipping `.git` directories to avoid interfering with the lab's git repository.
     *
     * @param labWorkspacePath  The destination directory for the lab workspace.
     */
    fun copyFilesToLabWorkspace(labWorkspacePath: File) {
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
     * Copies files from the project working directory to the sanctuary checkout location.
     * - Skips `.git` and all sanctuary-related folders to avoid copying internal or temporary state.
     * - Cleans the target sanctuary folder before copying, preserving `.git`.
     */
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
                if (isInSanctuaryRelatedDirectory(
                        file, sourcePath,
                        sanctuaryDir,
                        standardCheckoutPath
                    )
                ) {
                    return FileVisitResult.CONTINUE
                }

                val targetFile = targetPath.resolve(sourcePath.relativize(file))
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
    }

    /**
     * Copy all files from Sanctuary to working directory
     */
    fun copyFromSanctuaryToWorkingDirectory() {
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
    fun copyFileFromSanctuaryToWorking(filePath: String) {
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
     * Get all files in the currently checked out sanctuary dont forget to ignore .git folder
     */
    fun getAllFilesInSanctuary(): List<String> {
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
                if (isInSanctuaryRelatedDirectory(
                        file, basePath,
                        sanctuaryDir,
                        standardCheckoutPath
                    )
                ) {
                    return FileVisitResult.CONTINUE
                }

                val relativePath = basePath.relativize(file).toString()
                files.add(relativePath)
                return FileVisitResult.CONTINUE
            }
        })

        return files
    }

}