package tech.robd.verzanctuary.commands
/**
 * [File Info]
 * path=main/kotlin/tech/robd/verzanctuary/commands/InfoCommand.kt
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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import tech.robd.verzanctuary.VersionSanctuaryManager
import java.io.File
import java.io.IOException


class InfoCommand : CliktCommand(name = "info") {
    override fun help(context: Context) = "Show sanctuary information"

    private val directory by option(
        "-d", "--directory",
        help = "Project directory"
    ).default(".")

    private val env by option(
        "-e", "--env",
        help = "Show environment variable configuration"
    ).flag()

    private val noGit by option("--no-git", help = "Use current directory as project root").flag()


    override fun run() {
        try {
            val workingDir = File(directory).canonicalFile
            val manager = VersionSanctuaryManager(workingDir, useGitRoot = !noGit)

            echo("ℹ️  VerZanctuary Info")
            echo("=".repeat(30))
            echo(manager.getSanctuaryInfo())

            if (env) {
                echo("")
                echo("🌍 Environment Configuration")
                echo("-".repeat(30))
                echo(manager.getEnvironmentInfo())
            }
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


