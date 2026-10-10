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
        bankPatchesJson = data("bank-patches.json"),
        aliasesJson = data("aliases.json"),
    )

    @Test
    fun `the shipped data merges into one bank`() {
        // 876 upstream rows less the 3 superseded by a row that records the same movement with an
        // optional added weight, plus the 20 authored ones.
        assertEquals(893, bank.exercises.size)
        // The 133 hand-written aliases, the 3 superseded rows' own names redirecting to what
        // replaced them, the assisted dip shorthand, and the 54 renamed rows' old names.
        assertEquals(191, bank.aliases.size)
        assertEquals(54, bank.formerNames.size)
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
        assertEquals(listOf("Pec Deck"), bank.aliases["pec deck"])
        assertEquals(listOf("Romanian Deadlift"), bank.aliases["rdl"])
        assertTrue(
            bank.aliases.getValue("ohp").toString(),
            bank.aliases.getValue("ohp").any { "Press" in it },
        )
    }

    @Test
    fun `the staples ship under the names programs use, and their old names still find them`() {
        // A sample of #149's renames, the ones a routine shows most. The id is unchanged, which is
        // what keeps every logged set and routine pointing at the same exercise.
        mapOf(
            "Standing_Military_Press" to "Overhead Press",
            "Barbell_Bench_Press_-_Medium_Grip" to "Bench Press",
            "Barbell_Squat" to "Back Squat",
            "Pullups" to "Pull-Up",
            "Butterfly" to "Pec Deck",
        ).forEach { (id, name) ->
            assertEquals(id, name, bank.exercises.single { it.id == id }.name)
        }
        assertEquals(listOf("Overhead Press"), bank.aliases["standing military press"])
        assertEquals("Overhead Press", bank.formerNames["Standing Military Press"])
        // The weighted twin's redirect follows the rename rather than naming a row that is gone.
        assertEquals(listOf("Pull-Up"), bank.aliases["weighted pull ups"])
    }

    @Test
    fun `every former name leads to an exercise that ships`() {
        val names = bank.exercises.mapTo(mutableSetOf()) { it.name }

        assertEquals(emptyMap<String, String>(), bank.formerNames.filterValues { it !in names })
    }

    private fun data(name: String): String {
        val file = File("../data/$name")
        assertTrue("missing source data: ${file.absolutePath}", file.exists())
        return file.readText()
    }
}
