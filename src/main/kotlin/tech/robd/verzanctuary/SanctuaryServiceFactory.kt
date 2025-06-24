/**
 * VerZanctuary Core Package - Main sanctuary management components.
 *
 * This package contains the primary API and orchestration layer for VerZanctuary's
 * parallel version control system. The core components work together to provide
 * safe, timestamped snapshots of your project without interfering with Git history.
 *
 * ## Key Components
 * - [VersionSanctuaryManager] - Primary API facade for all sanctuary operations
 * - [SanctuaryServiceFactory] - Dependency injection factory for service construction
 * - [SanctuaryMetadataParser] - YAML serialization for sanctuary configuration
 *
 * ## Architecture
 * The package follows a service-oriented architecture where the manager orchestrates
 * specialized services that handle file operations, Git management, conflict detection
 * and workspace management. This separation enables testing, modularity and clear
 * responsibility boundaries.
 *
 * @see tech.robd.verzanctuary.services Service implementations
 * @see tech.robd.verzanctuary.path Path resolution and workspace management
 * @see tech.robd.verzanctuary.lock Concurrency control
 */
package tech.robd.verzanctuary

/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/SanctuaryServiceFactory.kt
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
import tech.robd.verzanctuary.path.SanctuaryPathResolver
import tech.robd.verzanctuary.services.*
import tech.robd.verzanctuary.services.SanctuaryFileService
import java.io.File

/**
 * Service factory providing dependency injection for sanctuary service components.
 *
 * ## Purpose
 * Centralizes the complex dependency wiring required to construct sanctuary services
 * with their proper collaborators. Each service requires specific dependencies and
 * configuration - this factory encapsulates that complexity and ensures consistent
 * service construction across the application.
 *
 * ## Design Benefits
 * - **Dependency Management**: Handles complex service interdependencies
 * - **Configuration Consistency**: Ensures services receive consistent configuration
 * - **Testing Support**: Enables easy service mocking and test setup
 * - **Future Evolution**: Provides a place to add dependency injection frameworks later
 *
 * ## Service Dependencies
 * Services have a careful dependency hierarchy where some services depend on others.
 * This factory constructs them in the correct order and wires dependencies properly.
 *
 * @since 0.1.1
 */
object SanctuaryServiceFactory {
    /**
     * Creates the core Git service that manages the underlying sanctuary repository.
     *
     * ## Service Role
     * The Git service is the foundation of the sanctuary system - it manages the actual
     * Git repository that stores timestamped snapshots. All other services either depend
     * on it or provide supporting capabilities for Git operations.
     *
     * ## Dependency Complexity
     * This is the most complex service to construct because it requires:
     * - File operations service for copying project files
     * - Conflict detection service for safety checks
     * - Path resolution for directory structure
     * - Locking for thread safety
     * - Logging for audit trails
     *
     * ## Path Resolution Strategy
     * Creates its own path resolver to determine sanctuary directory structure.
     * This ensures the Git service has consistent access to all required paths
     * regardless of how the factory was called.
     *
     * @param sanctuaryFileService Handles file copying between project and sanctuary
     * @param sanctuaryConflictService Detects potential conflicts before operations
     * @param workingDirectory Base directory for path resolution
     * @param lockManager Provides thread-safe operation coordination
     * @param sanctuaryLogService Records audit trail of Git operations
     * @param isGitRepository Affects how Git operations are handled
     * @return Fully configured Git service ready for sanctuary operations
     */
    fun gitService(
        sanctuaryFileService: SanctuaryFileService,
        sanctuaryConflictService: SanctuaryConflictService,
        workingDirectory: File,
        lockManager: LockManager,
        sanctuaryLogService: SanctuaryLogService,
        isGitRepository: Boolean
    ): SanctuaryGitService {
        val pathResolver = SanctuaryPathResolver(workingDirectory)
        val paths = pathResolver.resolvePaths()
        val parser = SanctuaryMetadataParser()
        return SanctuaryGitService(
            sanctuaryFileService = sanctuaryFileService,
            sanctuaryConflictService = sanctuaryConflictService,
            sanctuaryGitDir = paths.sanctuaryGitDir,
            standardCheckoutPath = paths.browseSpaceDir,
            projectName = workingDirectory.name,
            sanctuaryDir = paths.sanctuaryDir,
            projectRoot = workingDirectory,
            metadataFile = paths.metadataFile,
            parser = parser,
            lockManager = lockManager,
            sanctuaryLogService = sanctuaryLogService,
            isGitRepository = isGitRepository
        )
    }

    /**
     * Creates the diff service for comparing sanctuary states with working directory.
     *
     * ## Service Role
     * Provides rich comparison capabilities between sanctuary snapshots and the current
     * working state. Generates unified diffs, patches and change summaries that
     * help users understand what has changed since snapshots were created.
     *
     * ## Dependency Requirements
     * Requires the Git service to access sanctuary snapshots and the lock manager
     * to ensure thread-safe operations during diff generation.
     *
     * @param lockManager Ensures thread-safe diff operations
     * @param sanctuaryGitService Provides access to sanctuary snapshots for comparison
     * @return Configured diff service ready for comparison operations
     */
    fun diffService(lockManager: LockManager, sanctuaryGitService: SanctuaryGitService): SanctuaryDiffService {
        return SanctuaryDiffService(lockManager, sanctuaryGitService)
    }

    /**
     * Creates the checkout service for managing workspace restoration operations.
     *
     * ## Service Role
     * Orchestrates the complex process of restoring sanctuary snapshots to different
     * workspace environments (browse space, lab space, working directory). Handles
     * workspace preparation, Git hook installation and safety mechanisms.
     *
     * ## Service Collaboration
     * This service coordinates multiple other services to perform checkout operations:
     * - Uses Git service to position repository at the correct snapshot
     * - Uses file service to copy files to target workspace
     * - Uses logging service to record checkout operations
     * - Uses lock manager to prevent concurrent workspace modifications
     *
     * @param lockManager Prevents concurrent workspace modifications
     * @param sanctuaryLogService Records checkout operations for audit trail
     * @param sanctuaryGitService Positions repository at target snapshot
     * @param sanctuaryFileService Copies files to the target workspace
     * @return Configured checkout service ready for workspace operations
     */
    fun checkoutService(
        lockManager: LockManager,
        sanctuaryLogService: SanctuaryLogService,
        sanctuaryGitService: SanctuaryGitService,
        sanctuaryFileService: SanctuaryFileService
    ): SanctuaryCheckoutService {
        return SanctuaryCheckoutService(
            lockManager,
            sanctuaryLogService,
            sanctuaryGitService,
            sanctuaryFileService
        )
    }

    /**
     * Creates the file service for managing file operations between project and sanctuary.
     *
     * ## Service Role
     * Handles all file copying operations with intelligent exclusion patterns.
     * Knows how to copy project files to sanctuary while excluding Git directories,
     * sanctuary metadata and other files that shouldn't be included in snapshots.
     *
     * ## Exclusion Intelligence
     * The service applies different exclusion rules based on whether the project
     * is a Git repository, ensuring sanctuary snapshots contain only relevant
     * project files without version control artifacts.
     *
     * @param projectRoot Source directory containing project files
     * @param standardCheckoutPath Target directory for sanctuary file operations (browse space)
     * @param sanctuaryDir Sanctuary root directory (excluded from copying)
     * @param isGitRepository Whether to apply Git-specific exclusion rules
     * @return Configured file service ready for copy operations
     */
    fun fileService(
        projectRoot: File,
        standardCheckoutPath: File,
        sanctuaryDir: File,
        isGitRepository: Boolean,
    ): SanctuaryFileService {
        return SanctuaryFileService(
            projectRoot,
            standardCheckoutPath = standardCheckoutPath,
            sanctuaryDir = sanctuaryDir,
            isGitRepository = isGitRepository,
        )
    }

    /**
     * Creates the metadata service for managing sanctuary configuration and binding.
     *
     * ## Service Role
     * Manages the YAML metadata file that binds a sanctuary to its associated project.
     * Provides validation to prevent cross-project contamination and maintains
     * sanctuary status information for monitoring and troubleshooting.
     *
     * ## Project Binding
     * The metadata service ensures sanctuaries are correctly bound to their projects
     * by storing and validating project path information. This prevents accidental
     * use of sanctuary data from different projects.
     *
     * @param parser YAML parser for reading/writing metadata files
     * @param metadataFile Path to the sanctuary.yaml metadata file
     * @return Configured metadata service ready for configuration operations
     */
    fun metadataService(parser: SanctuaryMetadataParser, metadataFile: File): SanctuaryMetadataService {
        return SanctuaryMetadataService(parser, metadataFile)
    }

    /**
     * Creates the conflict service for detecting potential operation conflicts.
     *
     * ## Service Role
     * Analyzes differences between sanctuary snapshots and working directory to
     * detect potential conflicts before risky operations like restoration to
     * the working directory. Prevents accidental overwriting of uncommitted work.
     *
     * ## Safety Analysis
     * Performs file-by-file comparison to identify:
     * - Files that exist in working directory but not in sanctuary
     * - Files that exist in sanctuary but not in working directory
     * - Files that exist in both but have different content
     *
     * This analysis enables informed decision-making about proceeding with
     * potentially destructive operations.
     *
     * @param projectRoot Current working directory for conflict analysis
     * @param standardCheckoutPath Sanctuary checkout location for comparison
     * @return Configured conflict service ready for safety analysis
     */
    fun conflictService(projectRoot: File, standardCheckoutPath: File): SanctuaryConflictService {
        return SanctuaryConflictService(projectRoot, standardCheckoutPath)
    }
}