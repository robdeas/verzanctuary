/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/BackupScenario.kt
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

package tech.robd.verzanctuary
/**
 * Represents common scenarios where a sanctuary snapshot should be taken.
 *
 * These scenarios provide structured points to create code backups
 * for safety and traceability, especially when collaborating with AI,
 * performing large refactors, or before significant workflow events.
 *
 * Usage example:
 *   manager.quickBackup(BackupScenario.END_OF_DAY)
 */
enum class BackupScenario {
    BEFORE_AI_CONSULT,
    BEFORE_REFACTOR,
    END_OF_DAY,
    BEFORE_EXPERIMENT,
    WORKING_STATE
}
