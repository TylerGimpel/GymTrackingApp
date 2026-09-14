package gymtracker.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Builds the prepopulated exercise bank into an assets directory, ready for Room to open it with
 * `createFromAsset()`.
 *
 * This is the step that lets the app show hundreds of exercises on first launch with no network and
 * no import: the merge, the validation and the SQLite write all happen here, at build time, so the
 * device does none of it.
 */
@CacheableTask
abstract class GenerateExerciseBankTask : DefaultTask() {

    /** Room's exported schema for the database version being shipped. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val roomSchema: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val upstreamExercises: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val authoredExercises: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val cardioRetag: RegularFileProperty

    /** Where the database lands inside assets, as Room's `createFromAsset()` will name it. */
    @get:Input
    abstract val assetPath: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "build"
        description = "Merges the exercise bank source data into a prepopulated SQLite asset."
    }

    @TaskAction
    fun generate() {
        val schema = RoomSchema.parse(roomSchema.get().asFile.readText())
        val exercises = ExerciseBankBuilder.build(
            upstreamJson = upstreamExercises.get().asFile.readText(),
            authoredJson = authoredExercises.get().asFile.readText(),
            cardioRetagJson = cardioRetag.get().asFile.readText(),
        )

        val target = outputDirectory.file(assetPath).get().asFile
        ExerciseBankDatabaseWriter.write(target, schema, exercises)

        val authored = exercises.count { it.source == ExerciseBankBuilder.SOURCE_AUTHORED }
        logger.lifecycle(
            "Exercise bank: ${exercises.size} exercises " +
                "(${exercises.size - authored} upstream, $authored authored), " +
                "schema v${schema.version} ${schema.identityHash}, " +
                "${target.length() / 1024} KB"
        )
    }
}
