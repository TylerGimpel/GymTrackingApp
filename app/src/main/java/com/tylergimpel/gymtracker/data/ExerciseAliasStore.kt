package com.tylergimpel.gymtracker.data

import android.content.Context
import android.util.Log
import com.tylergimpel.gymtracker.domain.ExerciseAliases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Reads the shorthand that ships beside the bank.
 *
 * An asset rather than a table: aliases carry no user-specific state, so they arrive with the APK
 * and are replaced wholesale by the next one. There is nothing to migrate and nothing to reconcile
 * against what a lifter has done.
 */
class ExerciseAliasStore(context: Context) {

    private val assets = context.applicationContext.assets

    /**
     * The shorthand, or nothing at all if the asset cannot be read.
     *
     * Search without aliases still finds every exercise by name, so a missing file costs shorthand
     * rather than the feature. Taking the app down over it would be the worse trade — and the build
     * is where a missing or invalid alias file is supposed to be caught.
     */
    suspend fun load(): ExerciseAliases = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(assets.open(ASSET_PATH).bufferedReader().use { it.readText() })
            buildMap {
                json.keys().forEach { alias ->
                    val targets = json.getJSONArray(alias)
                    put(alias, List(targets.length(), targets::getString))
                }
            }
        }.onFailure {
            Log.e(TAG, "Could not read $ASSET_PATH; search will match names only", it)
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val TAG = "ExerciseAliasStore"

        /** Written here at build time by `generate<Variant>ExerciseBank`. */
        const val ASSET_PATH = "exercise-aliases.json"
    }
}
