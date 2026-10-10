package gymtracker.buildlogic

import com.google.gson.GsonBuilder

/**
 * Writes the validated shorthand out beside the database.
 *
 * Emitted rather than copied, so what ships is what the build checked: targets are rewritten to the
 * exercise names the bank actually uses, and an alias that survived validation is one the app can
 * resolve.
 */
object ExerciseAliasWriter {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Also writes the former names, which share the shape: a name to what it leads to. */
    fun toJson(aliases: Map<String, *>): String =
        gson.toJson(aliases.toSortedMap()) + "\n"
}
