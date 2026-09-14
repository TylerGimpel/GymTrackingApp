package gymtracker.buildlogic

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser

/**
 * The parts of a Room exported schema (`room.schemaLocation`) needed to recreate the database
 * exactly as Room would have created it.
 *
 * The asset database is built from this rather than from hand-written DDL. Room verifies the
 * `identityHash` held in `room_master_table` when it opens a prepopulated file, and a mismatch is a
 * runtime crash with no compile-time warning — so the only safe source for the DDL is Room itself.
 */
data class RoomSchema(
    val version: Int,
    val identityHash: String,
    /** `CREATE TABLE` / `CREATE INDEX` / `CREATE VIEW` statements, in the order Room emits them. */
    val createStatements: List<String>,
    /** Room's own `room_master_table` creation and identity-hash insert. */
    val setupQueries: List<String>,
    /** Declared column names per table, used to build inserts that match the entity exactly. */
    val columnsByTable: Map<String, List<String>>,
) {
    companion object {

        /** Room exports DDL with the table or view name as a placeholder, already inside backticks. */
        private const val TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"
        private const val VIEW_NAME_PLACEHOLDER = "\${VIEW_NAME}"

        fun parse(json: String): RoomSchema {
            val root = JsonParser.parseString(json).asJsonObject
            val database = requireNotNull(root.getAsJsonObject("database")) {
                "Not a Room exported schema: no `database` member"
            }

            val createStatements = mutableListOf<String>()
            val columnsByTable = mutableMapOf<String, List<String>>()

            database.getAsJsonArray("entities").orEmpty().forEach { element ->
                val entity = element.asJsonObject
                val table = entity.get("tableName").asString
                createStatements += entity.get("createSql").asString
                    .replace(TABLE_NAME_PLACEHOLDER, table)
                columnsByTable[table] = entity.getAsJsonArray("fields").orEmpty()
                    .map { it.asJsonObject.get("columnName").asString }
                entity.getAsJsonArray("indices").orEmpty().forEach { index ->
                    createStatements += index.asJsonObject.get("createSql").asString
                        .replace(TABLE_NAME_PLACEHOLDER, table)
                }
            }

            database.getAsJsonArray("views").orEmpty().forEach { element ->
                val view = element.asJsonObject
                createStatements += view.get("createSql").asString
                    .replace(VIEW_NAME_PLACEHOLDER, view.get("viewName").asString)
            }

            return RoomSchema(
                version = database.get("version").asInt,
                identityHash = database.get("identityHash").asString,
                createStatements = createStatements,
                setupQueries = database.getAsJsonArray("setupQueries").orEmpty().map { it.asString },
                columnsByTable = columnsByTable,
            )
        }
    }
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList().orEmpty()
