package tech.robd.verzanctuary.commands
/**
 * [🧩 File Info]
 * path=src/main/kotlin/tech/robd/verzanctuary/commands/CheckoutCommand.kt
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
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import tech.robd.verzanctuary.VersionSanctuaryManager
import java.io.File
import java.io.IOException
import kotlin.collections.orEmpty


class CheckoutCommand : CliktCommand(name = "checkout") {
    override fun help(context: Context) = "Checkout a sanctuary to working directory or browse folder"

    private val branch by argument(
        name = "branch",
        help = "Branch name to checkout"
    )

    private val files by argument(
        name = "files",
        help = "Specific files to checkout"
    ).multiple().optional()

    private val directory by option(
        "-d", "--directory",
        help = "Project directory"
    ).default(".")

    private val browse by option(
        "--browse",
        help = "Checkout to browse folder for safe inspection"
    ).flag()

    private val lab by option(
        "--lab",
        help = "Checkout to lab workspace for experimentation with real Git repo"
    ).flag()

    private val force by option(
        "--force",
        help = "Force checkout despite conflicts"
    ).flag()

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

        try {
            val manager = VersionSanctuaryManager(
                workingDir,
                enableLocking = !ignoreLocks,
                useGitRoot = !noGit
            )

            if (ignoreLocks && manager.isLocked()) {
                echo("⚠️  Ignoring existing lock - this could cause corruption!")
            }

            when {
                lab -> {
                    // New: checkout to lab workspace
                    manager.checkoutSanctuaryToLabSpace(branch)
                    echo("✅ Checked out sanctuary '$branch' to lab workspace")
                    echo("🔬 Lab workspace ready - Git sees these as local changes")
                    echo("💡 Lab workspace preserves your real Git repository")
                }
                browse -> {
                    // Safe inspection in browse folder
                    manager.checkoutSanctuaryToBrowseSpace(branch)
                    echo("✅ Checked out sanctuary '$branch' to browse folder")
                    echo("📁 Files available in: ${File(manager.getStandardCheckoutLocation()).name}/")
                    echo("💡 Use 'verz checkout $branch' to restore to working directory")
                }
                else -> {
                    // Default: checkout to working directory
                    manager.checkoutSanctuaryToWorking(branch, files.orEmpty(), force)
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
