package tech.robd.verzanctuary.commands
/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/commands/VersionCommand.kt
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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context

class VersionCommand : CliktCommand(name = "version") {
    override fun help(context: Context) = "Show VerZanctuary version"

    override fun run() {
        echo("🏛️ VerZanctuary v0.1.0")
        echo("A safe sanctuary for your code versions")
        echo("Built with Kotlin ${KotlinVersion.CURRENT} on Java ${System.getProperty("java.version")}")
    }
}