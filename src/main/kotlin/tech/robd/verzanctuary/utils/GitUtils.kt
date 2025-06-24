package tech.robd.verzanctuary.utils

/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/utils/GitUtils.kt
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
import java.io.File
import java.io.IOException
import java.nio.file.Path

/**
 * Git repository detection and validation utilities.
 *
 * ## Purpose
 * Provides reliable Git repository detection by traversing the filesystem hierarchy
 * to locate `.git` directories. This is essential for VerZanctuary to understand
 * the project scope and apply appropriate file exclusion rules.
 *
 * ## Repository Detection Strategy
 * Uses the standard Git approach of walking up the directory tree from a starting
 * point until a `.git` directory is found. This matches Git's own behavior and
 * ensures VerZanctuary operates on the same repository boundaries that Git recognizes.
 *
 * ## Error Handling Variants
 * Provides both strict (throwing) and lenient (safe) variants to support different
 * use cases:
 * - **Strict**: Fails fast when no Git repo found - use when Git is required
 * - **Safe**: Returns indicators when no Git repo found - use when Git is optional
 *
 */
object GitUtils {
    /**
     * Finds Git repository root by traversing up from starting directory.
     *
     * ## Use Case
     * **Strict Git requirement** - Use when VerZanctuary must operate within a Git
     * repository. This ensures sanctuary operations capture the complete project
     * scope as defined by Git repository boundaries.
     *
     * ## Traversal Logic
     * 1. Start from the given directory (canonicalized to resolve symlinks)
     * 2. Check for `.git` directory in current location
     * 3. If found, return the current directory as repository root
     * 4. If not found, move up one directory level and repeat
     * 5. If filesystem root reached without finding `.git`, throw exception
     *
     * ## Why Canonicalize
     * Uses `canonicalFile` to resolve symlinks and ensure we're working with
     * real filesystem paths. This prevents issues with symlinked project
     * directories or development environments that use filesystem links.
     *
     * @param startDir Directory to begin searching from (typically working directory)
     * @return File representing the Git repository root directory
     * @throws IOException if no Git repository found in directory hierarchy
     *
     * @see findGitRootSafely for non-throwing variant when Git is optional
     */
    fun findGitRoot(startDir: File): File {
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
     * Safely finds Git repository root with fallback behavior.
     *
     * ## Use Case
     * **Optional Git integration** - Use when VerZanctuary should work with or
     * without Git repositories. Provides graceful fallback to the starting
     * directory when no Git repository is found.
     *
     * ## Return Value Strategy
     * Returns a Pair containing:
     * - **First**: Directory to use (either Git root or fallback to start directory)
     * - **Second**: Boolean indicating whether it is in a Git repository
     *
     * This allows callers to:
     * - Always get a usable directory for operations
     * - Know whether Git-specific logic should be applied
     * - Make informed decisions about file exclusion patterns
     *
     * ## Fallback Rationale
     * When no Git repository is found, falls back to the starting directory rather
     * than throwing an exception. This enables VerZanctuary to work with non-Git
     * projects while still providing version sanctuary functionality.
     *
     * @param startDir Directory to begin searching from
     * @return Pair of (which directory to use, whether it is in a Git repository w)
     */
    fun findGitRootSafely(startDir: File): Pair<File, Boolean> {
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

    /**
     * Extension function to check if a Path represents a Git directory.
     *
     * ## Purpose
     * Provides a clean, readable way to test whether a filesystem path points
     * to a Git metadata directory. Used internally for path filtering and
     * exclusion logic throughout the sanctuary system.
     *
     * ## Implementation Note
     * Checks only the final component of the path (filename) rather than the
     * full path. This correctly identifies `.git` directories regardless of
     * their location in the filesystem hierarchy.
     *
     * ## Null Safety
     * Handles the edge case where a Path might not have a filename component
     * (e.g., filesystem roots) by returning false rather than throwing.
     *
     * @return true if the path's filename is exactly ".git", false otherwise
     */
    private fun Path.isGitDir(): Boolean {
        val name = this.fileName?.toString() ?: return false
        return name == ".git"
    }
}