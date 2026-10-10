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
    abstract val bankPatches: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aliases: RegularFileProperty

    /** Where the shorthand lands inside assets, beside the database rather than inside it. */
    @get:Input
    abstract val aliasAssetPath: Property<String>

    /** Where the former names land inside assets: what renamed rows were called before. */
    @get:Input
    abstract val formerNameAssetPath: Property<String>

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
        val bank = ExerciseBankBuilder.build(
            upstreamJson = upstreamExercises.get().asFile.readText(),
            authoredJson = authoredExercises.get().asFile.readText(),
            bankPatchesJson = bankPatches.get().asFile.readText(),
            aliasesJson = aliases.get().asFile.readText(),
        )

        val target = outputDirectory.file(assetPath).get().asFile
        ExerciseBankDatabaseWriter.write(target, schema, bank.exercises)

        val aliasTarget = outputDirectory.file(aliasAssetPath).get().asFile
        aliasTarget.parentFile?.mkdirs()
        aliasTarget.writeText(ExerciseAliasWriter.toJson(bank.aliases))

        val formerNameTarget = outputDirectory.file(formerNameAssetPath).get().asFile
        formerNameTarget.parentFile?.mkdirs()
        formerNameTarget.writeText(ExerciseAliasWriter.toJson(bank.formerNames))

        val authored = bank.exercises.count { it.source == ExerciseBankBuilder.SOURCE_AUTHORED }
        logger.lifecycle(
            "Exercise bank: ${bank.exercises.size} exercises " +
                "(${bank.exercises.size - authored} upstream, $authored authored), " +
                "${bank.aliases.size} aliases, ${bank.formerNames.size} former names, " +
                "schema v${schema.version} ${schema.identityHash}, " +
                "${target.length() / 1024} KB"
        )
    }
}
