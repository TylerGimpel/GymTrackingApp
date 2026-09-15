package gymtracker.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Builds the bank that actually ships, from the files that actually ship it.
 *
 * The other tests drive the rules with fixtures; this one drives the real data through them, so a
 * source file edited into an invalid state fails here rather than at the next build of the app.
 */
class ShippedExerciseBankTest {

    private val bank = ExerciseBankBuilder.build(
        upstreamJson = data("upstream-exercises.json"),
        authoredJson = data("authored-exercises.json"),
        cardioRetagJson = data("cardio-retag.json"),
        aliasesJson = data("aliases.json"),
    )

    @Test
    fun `the shipped data merges into one bank`() {
        assertEquals(896, bank.exercises.size)
        assertEquals(133, bank.aliases.size)
    }

    @Test
    fun `every alias names an exercise that is in the bank`() {
        val names = bank.exercises.mapTo(mutableSetOf()) { it.name }
        val missing = bank.aliases.flatMap { (alias, targets) ->
            targets.filterNot { it in names }.map { "$alias -> $it" }
        }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `the shorthand this ticket was written for resolves`() {
        // The three examples the ticket names. A test rather than a manual check, because the whole
        // point of the alias list is that nobody would think to look for these by their bank names.
        assertEquals(listOf("Butterfly"), bank.aliases["pec deck"])
        assertEquals(listOf("Romanian Deadlift"), bank.aliases["rdl"])
        assertTrue(
            bank.aliases.getValue("ohp").toString(),
            bank.aliases.getValue("ohp").any { "Press" in it },
        )
    }

    private fun data(name: String): String {
        val file = File("../data/$name")
        assertTrue("missing source data: ${file.absolutePath}", file.exists())
        return file.readText()
    }
}
