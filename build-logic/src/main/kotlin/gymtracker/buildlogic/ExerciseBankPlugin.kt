package gymtracker.buildlogic

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/** How the exercise bank is built for this project. */
interface ExerciseBankExtension {

    /** Where Room exports its schema, as configured by `room.schemaLocation`. */
    val schemaDirectory: DirectoryProperty

    /** Fully-qualified name of the `@Database` class, which names the schema subdirectory. */
    val databaseClass: Property<String>

    /** The schema version to build the asset from. */
    val databaseVersion: Property<Int>

    /** Directory holding `upstream-exercises.json`, `authored-exercises.json` and `bank-patches.json`. */
    val sourceDataDirectory: DirectoryProperty

    /** Path inside assets, as Room's `createFromAsset()` will name it. */
    val assetPath: Property<String>

    /** Path inside assets for the shorthand, which travels beside the database. */
    val aliasAssetPath: Property<String>

    /** Path inside assets for the names renamed rows shipped under before, which an import still matches. */
    val formerNameAssetPath: Property<String>
}

/**
 * Registers the build-time step that turns the exercise bank source data into a prepopulated SQLite
 * asset, one per variant.
 *
 * Ordering is the whole point of doing this in a plugin rather than inline: the generator runs after
 * KSP, so the schema it reads is the one this build just exported rather than a stale committed
 * copy, and `addGeneratedSourceDirectory` puts the result into the variant's assets ahead of merging
 * and packaging.
 */
class ExerciseBankPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(EXTENSION_NAME, ExerciseBankExtension::class.java)
        extension.assetPath.convention(DEFAULT_ASSET_PATH)
        extension.aliasAssetPath.convention(DEFAULT_ALIAS_ASSET_PATH)
        extension.formerNameAssetPath.convention(DEFAULT_FORMER_NAME_ASSET_PATH)
        extension.sourceDataDirectory.convention(
            project.rootProject.layout.projectDirectory.dir(DEFAULT_SOURCE_DATA_DIRECTORY),
        )

        project.plugins.withId("com.android.application") {
            project.extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
                val variantName = variant.name.replaceFirstChar(Char::uppercase)
                val generate = project.tasks.register<GenerateExerciseBankTask>(
                    "generate${variantName}ExerciseBank",
                ) {
                    // The Room Gradle plugin's copy task is what actually publishes the exported
                    // schema; KSP writes it somewhere private first. Depending on the copy is what
                    // makes a build with no committed schema work rather than only a warm one.
                    dependsOn(ROOM_SCHEMA_TASK)
                    dependsOn("ksp${variantName}Kotlin")
                    roomSchema.set(
                        extension.schemaDirectory.file(
                            extension.databaseClass.zip(extension.databaseVersion) { className, version ->
                                "$className/$version.json"
                            },
                        ),
                    )
                    upstreamExercises.set(extension.sourceDataDirectory.file("upstream-exercises.json"))
                    authoredExercises.set(extension.sourceDataDirectory.file("authored-exercises.json"))
                    bankPatches.set(extension.sourceDataDirectory.file("bank-patches.json"))
                    aliases.set(extension.sourceDataDirectory.file("aliases.json"))
                    assetPath.set(extension.assetPath)
                    aliasAssetPath.set(extension.aliasAssetPath)
                    formerNameAssetPath.set(extension.formerNameAssetPath)
                }
                variant.sources.assets?.addGeneratedSourceDirectory(
                    generate,
                    GenerateExerciseBankTask::outputDirectory,
                )
            }
        }
    }

    private companion object {
        const val EXTENSION_NAME = "exerciseBank"
        const val ROOM_SCHEMA_TASK = "copyRoomSchemas"
        const val DEFAULT_ASSET_PATH = "databases/gym-bank.db"
        const val DEFAULT_ALIAS_ASSET_PATH = "exercise-aliases.json"
        const val DEFAULT_FORMER_NAME_ASSET_PATH = "exercise-former-names.json"
        const val DEFAULT_SOURCE_DATA_DIRECTORY = "data"
    }
}
