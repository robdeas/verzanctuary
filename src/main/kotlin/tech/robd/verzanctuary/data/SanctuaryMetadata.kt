/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/data/SanctuaryMetadata.kt
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

package tech.robd.verzanctuary.data

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

// The main container for our metadata
data class SanctuaryMetadata(
    val identity: SanctuaryIdentity,
    val state: SanctuaryState
) {
    companion object {
        // Factory to create a default metadata object for a new repo
        @OptIn(ExperimentalTime::class)
        fun createNew(projectName: String, projectPath: String): SanctuaryMetadata {
            val now = Clock.System.now()
            val nowString = now.toJavaInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            return SanctuaryMetadata(
                identity = SanctuaryIdentity(
                    projectName = projectName,
                    sanctuaryId = UUID.randomUUID().toString(),
                    version = "0.1.0",
                    createdUtc = now,
                    boundProjectPath = projectPath
                ),
                state = SanctuaryState(
                    status = "initialized",
                    lastOperation = LastOperation(
                        completedUtc = now,
                        type = "create",
                        sanctuaryBranch = null,
                        success = true
                    )
                )
            )
        }
    }
}
