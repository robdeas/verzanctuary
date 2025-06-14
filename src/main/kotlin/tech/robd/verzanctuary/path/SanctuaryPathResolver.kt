/**
 * [File Info]
 * path=main/kotlin/tech/robd/verzanctuary/data/SanctuaryPathResolver.kt
 * description=General-purpose source file
 * generator=add-robokeytags.groovy
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

package tech.robd.verzanctuary.path

import java.io.File

/**
 * Handles all path resolution logic for VerZanctuary
 *
 * Manages the complex logic around where to place sanctuary metadata and workspace directories
 * based on environment variables, performance considerations, and storage preferences.
 */
class SanctuaryPathResolver(
    private val workingDirectory: File,
    private val projectName: String = workingDirectory.name
) {



    /**
     * Resolve all sanctuary paths based on environment and configuration
     */
    fun resolvePaths(): SanctuaryPaths {
        val sanctuaryBaseDir = getSanctuaryBaseDirectory()
        val workspaceBaseDir = getWorkspaceBaseDirectory()

        val sanctuaryDir = sanctuaryBaseDir.resolve("$projectName.sanctuary")
        val sanctuaryGitDir = sanctuaryDir.resolve(".sanctuary")

        val isColocated = workspaceBaseDir == sanctuaryBaseDir

        // Create the workspace container directory
        val workspaceContainerDir = if (isColocated) {
            sanctuaryDir  // Co-located: use sanctuary dir as container
        } else {
            workspaceBaseDir.resolve("$projectName.verzspaces")  // Separated: create verzspaces container
        }

        // Individual workspace directories
        val browseSpaceDir = workspaceContainerDir.resolve("browse")
        val labSpaceDir = workspaceContainerDir.resolve("lab")

        return SanctuaryPaths(
            sanctuaryDir = sanctuaryDir,
            sanctuaryGitDir = sanctuaryGitDir,
            workspaceContainerDir = workspaceContainerDir,
            browseSpaceDir = browseSpaceDir,
            labSpaceDir = labSpaceDir,
            metadataFile = sanctuaryDir.resolve("sanctuary.yaml"),
            logFile = sanctuaryDir.resolve("verzanctuary.log.jsonl"),
            isColocated = isColocated
        )
    }

    /**
     * Get the base directory for sanctuary metadata
     * Priority: System Property -> Environment Variable -> Project Parent
     */
    private fun getSanctuaryBaseDirectory(): File {
        val propDir = System.getProperty("VERZANCTUARY_SANCTUARY_DIR")
        val envDir = System.getenv("VERZANCTUARY_SANCTUARY_DIR")
        val baseDir = propDir ?: envDir

        return if (baseDir != null) {
            val customDir = File(baseDir)
            if (!customDir.exists()) {
                customDir.mkdirs()
            }
            customDir
        } else {
            // Default: parent of the working directory
            workingDirectory.parentFile
        }
    }

    /**
     * Get the base directory for workspace/tmp storage
     * Priority: System Property -> Environment Variable -> Sanctuary Base Directory
     */
    private fun getWorkspaceBaseDirectory(): File {
        val propDir = System.getProperty("VERZANCTUARY_WORKSPACE_PARENT")
        val envDir = System.getenv("VERZANCTUARY_WORKSPACE_PARENT")
        val baseDir = propDir ?: envDir

        return if (baseDir != null) {
            val customDir = File(baseDir)
            if (!customDir.exists()) customDir.mkdirs()
            customDir
        } else {
            // Default: use the sanctuary base directory (co-located)
            getSanctuaryBaseDirectory()
        }
    }

    /**
     * Get environment configuration info for debugging/status
     */
    fun getEnvironmentInfo(): String {
        val paths = resolvePaths()
        val sanctuaryEnv = System.getenv("VERZANCTUARY_SANCTUARY_DIR") ?: "Not set (using project parent)"
        val workspaceParentEnv = System.getenv("VERZANCTUARY_WORKSPACE_PARENT") ?: "Not set (using sanctuary dir)"

        return """
            Environment Configuration:
            VERZANCTUARY_SANCTUARY_DIR: $sanctuaryEnv
            VERZANCTUARY_WORKSPACE_PARENT: $workspaceParentEnv
            
            Resolved Paths:
            Sanctuary: ${paths.sanctuaryDir.absolutePath}
            Workspace parent: ${getWorkspaceBaseDirectory().absolutePath}
            Working copy: ${paths.browseSpaceDir.absolutePath}
            
            Storage Layout: ${if (paths.isColocated) "Co-located" else "Separated"}
        """.trimIndent()
    }

    /**
     * Validate that all required directories exist or can be created
     */
    fun validateAndCreateDirectories(): Boolean {
        return try {
            val paths = resolvePaths()

            // Ensure sanctuary directory exists
            if (!paths.sanctuaryDir.exists()) {
                paths.sanctuaryDir.mkdirs()
            }

            // Ensure workspace directory exists
            if (!paths.browseSpaceDir.exists()) {
                paths.browseSpaceDir.mkdirs()
            }

            // Verify we can write to both locations
            paths.sanctuaryDir.canWrite() && paths.browseSpaceDir.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if sanctuary is likely on a network drive (for performance optimization)
     */
    fun isNetworkStorage(): Boolean {
        val sanctuaryPath = getSanctuaryBaseDirectory().absolutePath
        return when {
            // Windows UNC paths
            sanctuaryPath.startsWith("\\\\") -> true
            // Unix/Linux common network mount points
            sanctuaryPath.startsWith("/mnt/") ||
                    sanctuaryPath.startsWith("/net/") ||
                    sanctuaryPath.startsWith("/nfs/") -> true
            else -> false
        }
    }

    /**
     * Get a summary of the path configuration
     */
    fun getPathSummary(): String {
        val paths = resolvePaths()
        val networkWarning = if (isNetworkStorage()) " (Network Storage Detected)" else ""

        return """
            Project: $projectName
            Layout: ${if (paths.isColocated) "Co-located" else "Separated"}$networkWarning
            Sanctuary: ${paths.sanctuaryDir.name}/
            Working copy: ${paths.browseSpaceDir.name}/
        """.trimIndent()
    }
}