package tech.robd.verzanctuary.path
/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/path/SanctuaryPaths.kt
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

/**
 * Configuration for sanctuary paths
 */
data class SanctuaryPaths(
    val sanctuaryDir: File,              // project.sanctuary/
    val sanctuaryGitDir: File,           // project.sanctuary/.sanctuary/
    val workspaceContainerDir: File,     // project.sanctuary/ (co-located) or project.verzspaces/ (separated)
    val browseSpaceDir: File,            // browse/ (inside container)
    val labSpaceDir: File,               // lab/ (inside container)
    val metadataFile: File,              // project.sanctuary/sanctuary.yaml
    val logFile: File,                   // project.sanctuary/verzanctuary.log.jsonl
    val isColocated: Boolean             // true if workspace is inside sanctuary

)