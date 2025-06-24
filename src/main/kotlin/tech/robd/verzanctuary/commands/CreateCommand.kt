package tech.robd.verzanctuary.commands
/**
* [🧩 File Info]
* path=src/main/kotlin/tech/robd/verzanctuary/commands/CreateCommand.kt
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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import tech.robd.verzanctuary.BackupScenario
import tech.robd.verzanctuary.VersionSanctuaryManager
import java.io.File
import java.io.IOException

class CreateCommand : CliktCommand(name = "create") {
    override fun help(context: Context) = "Create a version sanctuary"

    private val scenario by option(
        "-s", "--scenario",
        help = "Sanctuary scenario type"
    ).choice(
        BackupScenario.entries.associateBy { it.name.lowercase() }
    ).default(BackupScenario.WORKING_STATE)

    private val message by option(
        "-m", "--message",
        help = "Custom sanctuary message"
    )

    private val directory by option(
        "-d", "--directory",
        help = "Project directory"
    ).default(".")

    private val ignoreLocks by option(
        "--ignore-locks",
        help = "Ignore sanctuary locks (use with caution)"
    ).flag()

    private val noGit by option(  // ← NEW OPTION
        "--no-git",
        help = "Don't search for git root, use current directory as project root"
    ).flag()


    override fun run() {
        try {
            val workingDir = File(directory).canonicalFile
            val manager = VersionSanctuaryManager(
                workingDir,
                enableLocking = !ignoreLocks,
                useGitRoot = !noGit
            )

            if (ignoreLocks && manager.isLocked()) {
                echo("⚠️  Ignoring existing lock - this could cause corruption!")
            }

            if (!manager.isGitRepository) {
                echo("📁 Working with directory (non-Git): ${manager.projectRoot.name}")
            } else {
                echo("📁 Working with Git repository: ${manager.projectRoot.name}")
            }

            val customMessage = message ?: scenario.name.lowercase().replace('_', ' ')
            val branch = manager.createSanctuarySnapshot(customMessage)

            echo("✅ Created sanctuary: $branch")
            echo("📍 Location: ${manager.getSanctuaryLocation()}")
        } catch (e: IOException) {  // ← NEW: Handle IOException
            if (e.message?.contains("Not a Git repository") == true) {
                echo("❌ ${e.message}", err = true)
                echo("💡 Use --no-git flag to create sanctuary for non-Git directories", err = true)
                echo("   Example: verz create --no-git", err = true)
            } else {
                echo("❌ Error: ${e.message}", err = true)
            }
        }
    }

}

