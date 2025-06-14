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

package tech.robd.verzanctuary

import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Base test class providing temp directory support for VerZanctuary tests
 */
abstract class BaseTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    open fun setUpFileSystem() {
        // Nothing needed - @TempDir handles setup
    }

    @AfterEach
    open fun tearDownFileSystem() {
        // Clean up any environment variables set during tests
        System.clearProperty("VERZANCTUARY_SANCTUARY_DIR")
        System.clearProperty("VERZANCTUARY_WORKSPACE_DIR")
    }

    protected fun createTestProject(name: String = "test-project"): File {
        val projectPath = tempDir.resolve(name)
        Files.createDirectories(projectPath)

        // Initialize a real Git repository
        val git = Git.init().setDirectory(projectPath.toFile()).call()
        git.close()

        // Create some test files
        Files.write(projectPath.resolve("README.md"), "# $name\nThis is a test project.".toByteArray())

        val srcPath = projectPath.resolve("src")
        Files.createDirectories(srcPath)
        Files.write(srcPath.resolve("main.kt"), "fun main() { println(\"Hello from $name\") }".toByteArray())
        Files.write(projectPath.resolve("build.gradle.kts"), "plugins { kotlin(\"jvm\") }".toByteArray())

        return projectPath.toFile()
    }

    /**
     * Create a directory in the temp filesystem
     */
    protected fun createDirectory(pathString: String): File {
        val path = tempDir.resolve(pathString)
        Files.createDirectories(path)
        return path.toFile()
    }

    /**
     * Create a file with content
     */
    protected fun createFile(pathString: String, content: String): File {
        val path = tempDir.resolve(pathString)
        Files.createDirectories(path.parent)
        Files.write(path, content.toByteArray())
        return path.toFile()
    }

    /**
     * Get a path in the temp filesystem
     */
    protected fun getPath(pathString: String): Path {
        return tempDir.resolve(pathString)
    }

    /**
     * Check if a path exists
     */
    protected fun exists(pathString: String): Boolean {
        return Files.exists(tempDir.resolve(pathString))
    }

    /**
     * Read file content
     */
    protected fun readFile(pathString: String): String {
        return Files.readString(tempDir.resolve(pathString))
    }

    /**
     * Write content to a file
     */
    protected fun writeFile(pathString: String, content: String) {
        val path = tempDir.resolve(pathString)
        Files.createDirectories(path.parent)
        Files.write(path, content.toByteArray())
    }
}