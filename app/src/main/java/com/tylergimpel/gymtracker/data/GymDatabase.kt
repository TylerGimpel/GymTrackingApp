package com.tylergimpel.gymtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The app database, opened from the exercise bank that ships inside the APK.
 *
 * `createFromAsset` is what makes first launch instant and offline: the bank is already a SQLite
 * file, so there is no import step and nothing to download. Room checks the asset's identity hash
 * against the schema it was compiled for as it opens, which is why the asset is generated from
 * Room's own exported schema rather than from hand-written DDL.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        ExercisePreferenceEntity::class,
        ExerciseProgressEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SupersetGroupEntity::class,
        SetEntryEntity::class,
        SessionPlanEntity::class,
        DropSegmentEntity::class,
        RoutineEntity::class,
        RoutineDayEntity::class,
        RoutineDayExerciseEntity::class,
        CycleEntity::class,
        CycleCompletionEntity::class,
        SkipEntity::class,
        BodyweightEntryEntity::class,
        ProfileEntity::class,
    ],
    version = GymDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(StringListConverter::class, TimeConverters::class)
abstract class GymDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    abstract fun sessionDao(): SessionDao

    abstract fun routineDao(): RoutineDao

    abstract fun bodyweightDao(): BodyweightDao

    abstract fun profileDao(): ProfileDao

    companion object {
        const val VERSION = 20

        /** Path inside assets, written there at build time by `generate<Variant>ExerciseBank`. */
        const val ASSET_PATH = "databases/gym-bank.db"

        private const val FILE_NAME = "gym.db"

        @Volatile
        private var instance: GymDatabase? = null

        fun getInstance(context: Context): GymDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        fun build(context: Context): GymDatabase =
            Room.databaseBuilder(context, GymDatabase::class.java, FILE_NAME)
                .createFromAsset(ASSET_PATH)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
