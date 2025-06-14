package tech.robd.verzanctuary.commands
/**
 * [File Info]
 * path=main/kotlin/tech/robd/verzanctuary/commands/DiffCommand.kt
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
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import tech.robd.verzanctuary.VersionSanctuaryManager
import java.io.File
import java.io.IOException


class DiffCommand : CliktCommand(name = "diff") {
    override fun help(context: Context) = "Compare sanctuaries or show changes"

    private val sanctuary by argument(
        name = "sanctuary",
        help = "Sanctuary to compare against working directory"
    ).optional()

    private val files by argument(
        name = "files",
        help = "Specific files to diff"
    ).multiple().optional()

    private val from by option(
        "--from",
        help = "Compare from this sanctuary"
    )

    private val to by option(
        "--to",
        help = "Compare to this sanctuary"
    )

    private val latest by option(
        "--latest",
        help = "Compare current state with latest sanctuary"
    ).flag()

    private val output by option(
        "-o", "--output",
        help = "Save diff to file"
    )

    private val directory by option(
        "-d", "--directory",
        help = "Project directory"
    ).default(".")

    private val ignoreLocks by option(
        "--ignore-locks",
        help = "Ignore sanctuary locks (use with caution)"
    ).flag()

    private val noGit by option(
        "--no-git",
        help = "Don't search for git root, use current directory as project root"
    ).flag()

    override fun run() {
        val workingDir = File(directory).canonicalFile
        // Capture delegated property values to enable smart casting
        val sanctuaryValue = sanctuary
        val filesValue = files
        val fromValue = from
        val toValue = to

        try {
            val manager = VersionSanctuaryManager(
                workingDir,
                enableLocking = !ignoreLocks,
                useGitRoot = !noGit)

            val diff = when {
                // Single sanctuary vs working directory
                sanctuaryValue != null && (filesValue == null || filesValue.isEmpty()) -> {
                    echo("🔍 Comparing sanctuary '$sanctuaryValue' with working directory...")
                    manager.diffSanctuaryWithWorking(sanctuaryValue)
                }

                // Single sanctuary with specific files
                sanctuaryValue != null && !filesValue.isNullOrEmpty() -> {
                    echo("🔍 Comparing ${filesValue.size} file(s) with sanctuary '$sanctuaryValue'...")
                    filesValue.joinToString("\n") { file ->
                        manager.diffFileWithWorking(sanctuaryValue, file)
                    }
                }

                fromValue != null && toValue != null -> {
                    echo("🔍 Comparing sanctuary '$fromValue' with '$toValue'...")
                    manager.generatePatch(fromValue, toValue)
                }


                // Existing: Compare with latest
                latest -> {
                    echo("🔍 Comparing current state with latest sanctuary...")
                    manager.compareWithLatest() ?: ""
                }

                else -> {
                    echo("❌ Please specify sanctuary to compare", err = true)
                    echo("Examples:")
                    echo("  verz diff auto-20250612-1430-45-123")
                    echo("  verz diff auto-20250612-1430-45-123 src/main.kt")
                    echo("  verz diff --latest")
                    echo("  verz diff --from sanctuary1 --to sanctuary2")
                    return
                }
            }

            if (diff.isEmpty()) {
                echo("✨ No differences found")
                return
            }

            output?.let { filename ->
                File(filename).writeText(diff)
                echo("💾 Diff saved to: $filename")
            } ?: run {
                echo("📋 Changes:")
                echo(diff.take(2000)) // Show first 2000 chars
                if (diff.length > 2000) {
                    echo("\n... (truncated, use -o to save full diff)")
                }
            }
        } catch (e: IOException) {
            if (e.message?.contains("Not a Git repository") == true) {
                echo("❌ ${e.message}", err = true)
                echo("💡 Use --no-git flag to work with non-Git directories", err = true)
            } else {
                echo("❌ Error: ${e.message}", err = true)
            }
        }
    }
}

