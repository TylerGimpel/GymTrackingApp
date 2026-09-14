package gymtracker.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fails the build if the exported schema of an already-shipped database version has changed.
 *
 * Room writes `<version>.json` every time it compiles, so editing an entity without bumping the
 * database version silently rewrites the record of a version that is already on people's phones.
 * Nothing in Room notices: the file is simply overwritten. What breaks is everything downstream —
 * the migration onto that version is generated from it, and `MigrationTestHelper` builds its
 * starting database from it, so a corrupted baseline makes migration tests fail for reasons that
 * point nowhere near the actual mistake.
 *
 * Git already holds the answer to "has this file changed", so there is nothing to keep in step: the
 * committed schemas are the record, and this only asks whether any of them have moved.
 */
abstract class CheckRoomSchemasFrozenTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaDirectory: DirectoryProperty

    /** Schemas below this version are frozen. The current one is still being written. */
    @get:Input
    abstract val currentVersion: Property<Int>

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    init {
        group = "verification"
        description = "Checks that the exported schema of a shipped database version has not changed."
    }

    @TaskAction
    fun check() {
        val root = repositoryRoot.get().asFile
        val frozen = schemaDirectory.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .filter { (it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE) < currentVersion.get() }
            .toList()

        if (frozen.isEmpty()) return

        val changed = gitChangedFiles(root, frozen) ?: run {
            logger.warn("Skipping frozen schema check: git is unavailable, so there is nothing to compare against.")
            return
        }

        if (changed.isEmpty()) return

        throw GradleException(
            buildString {
                appendLine("The exported schema of an already-shipped database version has changed:")
                changed.sorted().forEach { appendLine("  - $it") }
                appendLine()
                appendLine("A shipped version's schema is a record of what is on people's phones, and the")
                appendLine("migration onto it is generated from it. Room rewrites this file whenever an entity")
                appendLine("changes while the database version stays the same, so this almost always means the")
                appendLine("version was not bumped before the change.")
                appendLine()
                appendLine("Bump GymDatabase.VERSION, restore the frozen schema with")
                appendLine("`git checkout -- app/schemas`, rebuild, and write a migration onto the new version.")
            },
        )
    }

    /** The subset of [files] that git reports as differing from HEAD, or null if git cannot answer. */
    private fun gitChangedFiles(root: File, files: List<File>): List<String>? = runCatching {
        val process = ProcessBuilder(
            listOf("git", "diff", "--name-only", "HEAD", "--") + files.map(File::getAbsolutePath),
        )
            .directory(root)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
            return null
        }
        output.lines().map(String::trim).filter(String::isNotEmpty)
    }.getOrNull()

    private companion object {
        const val GIT_TIMEOUT_SECONDS = 30L
    }
}
