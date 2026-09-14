package gymtracker.buildlogic

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** A row as it will be written into the shipped bank. */
data class BankExercise(
    val id: String,
    val name: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipment: String?,
    val mechanic: String?,
    val force: String?,
    val category: String,
    val level: String,
    val loggingType: String,
    val unilateral: Boolean,
    /** `upstream` or `authored` — which source shipped the row, not whether a user created it. */
    val source: String,
)

/**
 * Everything the build produces: the exercises, and the shorthand people actually type.
 *
 * Aliases are shipped data with no user-specific state, so they travel beside the database rather
 * than inside it — nothing about them ever needs reconciling against what a lifter has done.
 */
data class ExerciseBank(
    val exercises: List<BankExercise>,
    /** Alias to the names it resolves to, which may be several. */
    val aliases: Map<String, List<String>>,
)

/** Thrown when the source data cannot produce a valid bank. Carries every problem, not just the first. */
class ExerciseBankException(val problems: List<String>) : RuntimeException(
    buildString {
        append("The exercise bank source data is invalid (")
        append(problems.size)
        append(if (problems.size == 1) " problem):" else " problems):")
        problems.forEach { append("\n  - ").append(it) }
    },
)

/**
 * Merges the upstream dataset with this project's authored rows and cardio re-tags, validating
 * everything on the way through.
 *
 * Validation is not a formality here. An out-of-vocabulary muscle fails silently at runtime — the
 * exercise simply never appears under any filter — which is the worst failure mode for data that
 * ships inside the APK and cannot be corrected without a release.
 */
object ExerciseBankBuilder {

    const val SOURCE_UPSTREAM = "upstream"
    const val SOURCE_AUTHORED = "authored"

    /**
     * Fields that must never appear on any row. Upstream instruction prose and images are excluded
     * on provenance grounds — see `docs/adr/0002-exercise-bank-source-and-omissions.md`. This check
     * exists so that reintroducing them is a build failure rather than a quiet legal problem.
     */
    private val FORBIDDEN_FIELDS = listOf("instructions", "images")

    /**
     * Name fragments that mark an exercise as trained one side at a time. Deliberately literal: only
     * explicit one-side wording counts, because "alternating" and "split" describe how a set is
     * performed rather than that each side is loaded on its own.
     */
    private val UNILATERAL_NAME_PATTERNS = listOf(
        "one-arm", "one arm", "one-armed", "one-handed",
        "single-arm", "single arm", "single-handed",
        "one-leg", "one leg", "one-legged", "single-leg", "single leg",
        "unilateral",
    )

    fun build(
        upstreamJson: String,
        authoredJson: String,
        cardioRetagJson: String,
        aliasesJson: String,
    ): ExerciseBank {
        val problems = mutableListOf<String>()

        val upstream = readRows(upstreamJson, "upstream-exercises.json", problems)
            .mapNotNull { it.toUpstreamExercise(problems) }
        val authored = readRows(authoredJson, "authored-exercises.json", problems)
            .mapNotNull { it.toAuthoredExercise(problems) }
        val patches = readPatches(cardioRetagJson, problems)

        checkCollisions(upstream, authored, problems)
        val patched = applyPatches(upstream, patches, problems)

        val exercises = (patched + authored)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val aliases = readAliases(aliasesJson, exercises, problems)

        if (problems.isNotEmpty()) throw ExerciseBankException(problems)

        return ExerciseBank(exercises, aliases)
    }

    /**
     * Reads the shorthand, checking every target names an exercise that survived the merge.
     *
     * An alias pointing at a name that is not there fails silently at runtime — the shorthand finds
     * nothing and nothing says why — which is the worst failure mode a search feature has.
     */
    private fun readAliases(
        json: String,
        exercises: List<BankExercise>,
        problems: MutableList<String>,
    ): Map<String, List<String>> {
        val parsed = JsonParser.parseString(json)
        if (!parsed.isJsonObject) {
            problems += "aliases.json: expected an object of alias to exercise names"
            return emptyMap()
        }

        val byName = exercises.associateBy { it.name.lowercase() }

        return parsed.asJsonObject.entrySet().associate { (alias, value) ->
            val targets = when {
                value.isJsonArray -> value.asJsonArray.mapNotNull { it.stringOrNull() }
                else -> listOfNotNull(value.stringOrNull())
            }
            if (targets.isEmpty()) {
                problems += "aliases.json: `$alias` names no exercise at all"
            }
            targets.forEach { target ->
                if (target.lowercase() !in byName) {
                    problems += "aliases.json: `$alias` names `$target`, which is not in the bank"
                }
            }
            // Stored under the name the bank actually uses, so a differently-cased target in the
            // source resolves to something the app can find.
            alias to targets.map { byName[it.lowercase()]?.name ?: it }
        }
    }

    // --- reading -----------------------------------------------------------------------------

    private fun readRows(json: String, label: String, problems: MutableList<String>): List<Row> {
        val parsed = JsonParser.parseString(json)
        if (!parsed.isJsonArray) {
            problems += "$label: expected a JSON array of exercises"
            return emptyList()
        }
        return parsed.asJsonArray.mapIndexedNotNull { index, element ->
            if (element.isJsonObject) {
                Row(label, index, element.asJsonObject)
            } else {
                problems += "$label[$index]: expected an object"
                null
            }
        }
    }

    private fun readPatches(json: String, problems: MutableList<String>): Map<String, JsonObject> {
        val patches = JsonParser.parseString(json)
            .takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.getAsJsonObject("patches")
        if (patches == null) {
            problems += "cardio-retag.json: expected an object with a `patches` member"
            return emptyMap()
        }
        return patches.entrySet().associate { (id, value) -> id to value.asJsonObject }
    }

    // --- validation --------------------------------------------------------------------------

    private fun checkCollisions(
        upstream: List<BankExercise>,
        authored: List<BankExercise>,
        problems: MutableList<String>,
    ) {
        // Case-insensitive, because the collision this catches was a real one: `Air Bike` was already
        // taken upstream by an abdominal exercise that has nothing to do with a fan bike.
        val upstreamIds = upstream.associateBy { it.id.lowercase() }
        val upstreamNames = upstream.associateBy { it.name.lowercase() }
        val seenIds = mutableMapOf<String, String>()
        val seenNames = mutableMapOf<String, String>()

        authored.forEach { row ->
            upstreamIds[row.id.lowercase()]?.let {
                problems += "authored id `${row.id}` collides with upstream `${it.id}` (${it.name})"
            }
            upstreamNames[row.name.lowercase()]?.let {
                problems += "authored name `${row.name}` collides with upstream `${it.name}` (id ${it.id})"
            }
            seenIds.put(row.id.lowercase(), row.id)?.let {
                problems += "authored id `${row.id}` is a duplicate of `$it`"
            }
            seenNames.put(row.name.lowercase(), row.name)?.let {
                problems += "authored name `${row.name}` is a duplicate of `$it`"
            }
        }
    }

    private fun applyPatches(
        upstream: List<BankExercise>,
        patches: Map<String, JsonObject>,
        problems: MutableList<String>,
    ): List<BankExercise> {
        val byId = upstream.associateBy { it.id }
        patches.keys.filterNot(byId::containsKey).forEach {
            problems += "cardio-retag.json: patch target `$it` is not an upstream exercise"
        }
        return upstream.map { row ->
            val patch = patches[row.id] ?: return@map row
            val equipment = patch.patchedValue(row.id, "equipment", Vocabulary.EQUIPMENT, problems)
            val loggingType = patch.patchedValue(row.id, "loggingType", Vocabulary.LOGGING_TYPE, problems)
            row.copy(
                equipment = equipment ?: row.equipment,
                loggingType = loggingType ?: row.loggingType,
            )
        }
    }

    private fun JsonObject.patchedValue(
        id: String,
        field: String,
        allowed: Set<String>,
        problems: MutableList<String>,
    ): String? = string(field)?.also {
        if (it !in allowed) {
            problems += "cardio-retag.json: `$id` sets `$field` to `$it`, which is outside the closed vocabulary"
        }
    }

    // --- derivation --------------------------------------------------------------------------

    /**
     * What a set of this exercise records, derived from category and equipment.
     *
     * Upstream carries no such field, so it has to come from somewhere. The cardio rows are patched
     * by `cardio-retag.json` rather than relying on the `cardio` branch here, which is why that
     * branch almost never fires; it is kept so that a future untagged cardio row still lands
     * somewhere sensible rather than being logged as weight and reps.
     */
    internal fun deriveLoggingType(category: String, equipment: String?): String = when {
        category == "stretching" -> "time"
        category == "cardio" -> "distance_time"
        equipment == null || equipment == "body only" -> "bodyweight_reps"
        else -> "weight_reps"
    }

    internal fun deriveUnilateral(name: String): Boolean {
        val lowered = name.lowercase()
        return UNILATERAL_NAME_PATTERNS.any { it in lowered }
    }

    // --- one source row ----------------------------------------------------------------------

    private class Row(private val label: String, private val index: Int, private val json: JsonObject) {

        fun toUpstreamExercise(problems: MutableList<String>): BankExercise? {
            val common = common(problems) ?: return null
            return common.copy(
                loggingType = deriveLoggingType(common.category, common.equipment),
                unilateral = deriveUnilateral(common.name),
                source = SOURCE_UPSTREAM,
            )
        }

        fun toAuthoredExercise(problems: MutableList<String>): BankExercise? {
            val common = common(problems) ?: return null
            // Authored rows state their logging type rather than deriving it: the machines that
            // motivated them are exactly the ones a category-and-equipment rule gets wrong,
            // `assisted` above all, where guessing means coaching someone to get weaker.
            val loggingType = requiredEnum("loggingType", Vocabulary.LOGGING_TYPE, problems) ?: return null
            return common.copy(
                loggingType = loggingType,
                unilateral = optionalBoolean("unilateral") ?: deriveUnilateral(common.name),
                source = SOURCE_AUTHORED,
            )
        }

        /** The fields both sources share. Logging type, unilateral and source are filled by the caller. */
        private fun common(problems: MutableList<String>): BankExercise? {
            FORBIDDEN_FIELDS.filter(json::has).forEach {
                problems += "$where: carries `$it`, which is excluded on provenance grounds (ADR 0002)"
            }
            val id = requiredString("id", problems)
            val name = requiredString("name", problems)
            val category = requiredEnum("category", Vocabulary.CATEGORY, problems)
            val level = requiredEnum("level", Vocabulary.LEVEL, problems)
            val primaryMuscles = muscles("primaryMuscles", problems)
            if (primaryMuscles.isEmpty()) problems += "$where: has no primary muscle"
            val secondaryMuscles = muscles("secondaryMuscles", problems)
            val equipment = optionalEnum("equipment", Vocabulary.EQUIPMENT, problems)
            val mechanic = optionalEnum("mechanic", Vocabulary.MECHANIC, problems)
            val force = optionalEnum("force", Vocabulary.FORCE, problems)

            if (id == null || name == null || category == null || level == null) return null
            return BankExercise(
                id = id,
                name = name,
                primaryMuscles = primaryMuscles,
                secondaryMuscles = secondaryMuscles,
                equipment = equipment,
                mechanic = mechanic,
                force = force,
                category = category,
                level = level,
                loggingType = "",
                unilateral = false,
                source = "",
            )
        }

        private val where: String
            get() = "$label[$index]" + (json.string("id")?.let { " (`$it`)" } ?: "")

        private fun requiredString(field: String, problems: MutableList<String>): String? =
            json.string(field) ?: null.also {
                problems += "$where: `$field` is missing or not a string"
            }

        private fun requiredEnum(field: String, allowed: Set<String>, problems: MutableList<String>): String? {
            val value = requiredString(field, problems) ?: return null
            if (value !in allowed) {
                problems += "$where: `$field` is `$value`, which is outside the closed vocabulary"
                return null
            }
            return value
        }

        private fun optionalEnum(field: String, allowed: Set<String>, problems: MutableList<String>): String? =
            json.string(field)?.also {
                if (it !in allowed) {
                    problems += "$where: `$field` is `$it`, which is outside the closed vocabulary"
                }
            }

        private fun optionalBoolean(field: String): Boolean? =
            json.get(field)
                ?.takeIf { it.isJsonPrimitive }
                ?.asJsonPrimitive
                ?.takeIf { it.isBoolean }
                ?.asBoolean

        private fun muscles(field: String, problems: MutableList<String>): List<String> {
            val element = json.get(field) ?: return emptyList()
            if (element.isJsonNull) return emptyList()
            if (!element.isJsonArray) {
                problems += "$where: `$field` is not an array"
                return emptyList()
            }
            return element.asJsonArray.mapNotNull { it.stringOrNull() }.onEach { muscle ->
                if (muscle !in Vocabulary.MUSCLES) {
                    problems += "$where: `$field` contains `$muscle`, which is outside the 17-muscle vocabulary"
                }
            }
        }
    }
}

private fun JsonObject.string(field: String): String? = get(field)?.stringOrNull()

private fun JsonElement.stringOrNull(): String? =
    takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
