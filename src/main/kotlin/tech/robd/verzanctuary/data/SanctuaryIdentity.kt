/**
 * [File Info]
 * path=main/kotlin/tech/robd/verzanctuary/data/SanctuaryState.kt
 * description=General-purpose source file
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

package tech.robd.verzanctuary.data

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class SanctuaryIdentity @OptIn(ExperimentalTime::class) constructor(
    val projectName: String,
    val sanctuaryId: String, // Assuming UUID as a String is fine
    val version: String,
    val createdUtc: Instant, // Using Instant for the timestamp
    val boundProjectPath: String
)