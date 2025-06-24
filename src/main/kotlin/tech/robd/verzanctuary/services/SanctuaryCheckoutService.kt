package tech.robd.verzanctuary.services

/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/services/SanctuaryCheckoutService.kt
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
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Handles atomic and safe checkout of sanctuary branches into isolated workspaces.
 *
 * Integrates locking, logging, and git/file safety measures to ensure checkouts for
 * experimentation or review are robust and protected against accidental changes.
 *
 * Purpose:
 * - Checkout to lab or standard/browse workspaces
 * - Enforce pre-commit protection hooks on checked-out workspaces
 * - Ensure workspace filesystem is valid (e.g., no file/symlink at .git path)
 * - Log and handle all errors
 *
 * @property lockManager           Manages process/thread locks for safe concurrent access.
 * @property sanctuaryLogService   Records all checkout and error events for traceability.
 * @property sanctuaryGitService   Provides git/JGit repository and checkout operations.
 * @property sanctuaryFileService  Handles file/directory copying and workspace setup.
 */
class SanctuaryCheckoutService(
    private val lockManager: LockManager,
    private val sanctuaryLogService: SanctuaryLogService,
    private val sanctuaryGitService: SanctuaryGitService,
    private val sanctuaryFileService: SanctuaryFileService
) {

    /**
     * Check out the specified sanctuary branch into a "lab" workspace for experimentation.
     * Preserves .git directory so changes are seen as uncommitted changes in Git.
     * All actions are locked to prevent concurrent modification.
     */
    fun checkoutSanctuaryToLabSpace(branchName: String, labWorkspacePath: File) {
        lockManager.withLock("checkout_lab") {
            try {
                // 1. Checkout sanctuary to temp location first
                val git = sanctuaryGitService.initializeRepo()
                try {
                    git.checkout().setName(branchName).call()
                } finally {
                    git.close()
                }

                // 2. Copy sanctuary files over the lab workspace (preserving .git)
                sanctuaryFileService.copyFilesToLabWorkspace(
                    labWorkspacePath
                )

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

    /**
     * Ensure hooksDir is a directory, deleting any file or symlink at that path.
     * Retries a couple of times if necessary, but doesn't hang forever.
     */
    fun ensureHooksDirectory(hooksDir: File, maxTries: Int = 3, retryDelayMs: Long = 50) {
        val hooksPath = hooksDir.toPath()

        // If a file or symlink exists at hooks path, delete it
        if (Files.exists(hooksPath, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(
                hooksPath,
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
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

    /**
     * Sets up a pre-commit hook in the checked-out workspace to prevent commits
     * to protected sanctuary or auto-generated branches.
     * Allows commits only to `practice-` branches.
     *
     * - Ensures .git and hooks directories exist.
     * - If .git is a file/symlink (possible after copy), deletes and recreates it as a directory.
     * - Installs a shell script as .git/hooks/pre-commit.
     *
     */
    fun setupBrowseSpaceProtection(branchName: String, standardCheckoutPath: File) {
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
     * Checkout a sanctuary branch to the standardized location for inspection
     */
    fun checkoutSanctuaryToStandardLocation(branchName: String, standardCheckoutPath: File) {
        lockManager.withLock("checkout") {
            var success = false

            try {
                val git = sanctuaryGitService.initializeRepo()

                try {
                    val commit = git.repository.resolve("refs/heads/$branchName")

                    git.checkout()
                        .setName(branchName)
                        .call()

                    // NEW: Install protection hook after checkout
                    setupBrowseSpaceProtection(
                        branchName,
                        standardCheckoutPath
                    )

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

}