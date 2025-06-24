/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/SanctuaryMetadataParser.kt
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import tech.robd.verzanctuary.data.SanctuaryMetadata
import java.io.File
import java.io.IOException

/**
 * The single, correct parser for reading and writing sanctuary.yaml files.
 * It works exclusively with the SanctuaryMetadata data model.
 */
class SanctuaryMetadataParser {
    private val mapper = ObjectMapper(YAMLFactory()).apply {
        // The JavaTimeModule is not needed here if all dates are stored as ISO-8601 strings,
        // but registerKotlinModule is essential.
        registerKotlinModule()
    }

    /**
     * Reads a sanctuary.yaml file and deserializes it into a SanctuaryMetadata object.
     */
    @Throws(IOException::class)
    fun read(file: File): SanctuaryMetadata {
        return mapper.readValue(file, SanctuaryMetadata::class.java)
    }

    /**
     * Serializes a SanctuaryMetadata object and writes it to the specified file.
     */
    @Throws(IOException::class)
    fun write(file: File, metadata: SanctuaryMetadata) {
        mapper.writeValue(file, metadata)
    }
}