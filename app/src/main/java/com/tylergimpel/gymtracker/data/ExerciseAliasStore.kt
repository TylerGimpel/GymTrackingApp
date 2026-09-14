package com.tylergimpel.gymtracker.data

import android.content.Context
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
class ExerciseAliasStore(private val context: Context) {

    private var cached: ExerciseAliases? = null

    suspend fun load(): ExerciseAliases = cached ?: withContext(Dispatchers.IO) {
        val json = JSONObject(context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() })
        buildMap {
            json.keys().forEach { alias ->
                val targets = json.getJSONArray(alias)
                put(alias, List(targets.length(), targets::getString))
            }
        }.also { cached = it }
    }

    private companion object {
        /** Written here at build time by `generate<Variant>ExerciseBank`. */
        const val ASSET_PATH = "exercise-aliases.json"
    }
}
