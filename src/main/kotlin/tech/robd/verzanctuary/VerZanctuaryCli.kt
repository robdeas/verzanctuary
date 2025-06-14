/**
 * [File Info]
 * path=main/kotlin/tech/robd/verzanctuary/data/SanctuaryState.kt
 * description=CLI handler for the VerZanctuary app.
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

package tech.robd.verzanctuary.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import tech.robd.verzanctuary.commands.*

/**
 * CLI wrapper for VerZanctuary using Clikt 5.0
 * Command: verz
 * All commands are in the commands package
 *
 * @author Rob
 */
class VerZanctuaryCli : CliktCommand(name = "verz") {
    override fun help(context: Context) = "VerZanctuary - A safe sanctuary for your code versions"

    override fun run() {
        // This will be called if no subcommand is specified
        // Clikt will automatically show help
    }
}

fun main(args: Array<String>) {
    VerZanctuaryCli()
        .subcommands(
            CreateCommand(),
            ListCommand(),
            CheckoutCommand(),
            DiffCommand(),
            CleanupCommand(),
            InfoCommand(),
            StatusCommand(),
            VersionCommand(),
            LockCommand().subcommands(  // Add this
                UnlockCommand(),
                LockStatusCommand()
            )
        )
        .main(args)
}