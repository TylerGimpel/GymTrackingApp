package com.tylergimpel.gymtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shapes a lifter picks from, and the rule that keeps every one of them sound.
 *
 * The divisibility rule exists because this app rejected the calendar. A week-anchored routine
 * self-corrects — an A/B alternation lands differently each week and evens out — and an N-day
 * cycle does not: a three-position `Upper / Lower / Upper` is a permanent 2:1 imbalance, forever.
 * These assert that no such thing is ever on offer.
 */
class SplitShapeTest {

    @Test
    fun `every shape the app offers balances its own rotation`() {
        // Not checked at the point of picking, because there is nothing to check: it is true of
        // everything authored. This is what stops a variant added later quietly breaking it.
        SPLIT_SHAPES.forEach {
            assertTrue("${it.id} does not divide by its own rotation", it.isBalanced)
        }
    }

    @Test
    fun `a five position upper lower is not among them`() {
        // The case the rule is for. Five positions on a two-rotation cannot alternate, so it is
        // never authored — rather than offered and then rejected.
        assertTrue(
            SPLIT_SHAPES.none { it.rotation == 2 && it.liftingPositions % 2 != 0 },
        )
    }

    @Test
    fun `a four position push pull legs is not among them`() {
        assertTrue(SPLIT_SHAPES.none { it.rotation == 3 && it.liftingPositions % 3 != 0 })
    }

    @Test
    fun `five days a week and upper lower is offered`() {
        val offered = splitShapesFor(cadence = 5)

        // Entirely sound: a two-position cycle at cadence 5. Because the cycle is not anchored to
        // a calendar it produces U/L/U/L/U then L/U/L/U/L for free, which is the thing a
        // week-anchored app needs an explicit two-week template for.
        assertTrue(offered.any { it.id == "upper-lower-2" })
    }

    @Test
    fun `weekly frequency narrows nothing`() {
        // It decides the order and nothing else. Frequency has never constrained cycle length,
        // and a lifter who wants a six-position cycle at twice a week is running a three-week
        // cycle, which is a thing people do.
        (1..7).forEach { frequency ->
            assertEquals(SPLIT_SHAPES.size, splitShapesFor(frequency).size)
        }
    }

    @Test
    fun `the likeliest shape is near the top without being called the best`() {
        val offered = splitShapesFor(cadence = 3)

        // A convenience, not a ranking on quality: somebody training three times a week most
        // often wants a cycle that comes round about weekly.
        assertEquals(3, offered.first().liftingPositions)
    }

    @Test
    fun `no shape describes itself as better than another`() {
        val loaded = listOf("best", "optimal", "superior", "recommended", "ideal", "better")

        // Every volume-equated split trial found no difference, so the app has no business
        // implying one. This is a guard on the copy, which is where such a claim would creep in.
        SPLIT_SHAPES.forEach { shape ->
            loaded.forEach {
                assertFalse(
                    "${shape.id} describes itself as $it",
                    shape.description.lowercase().contains(it),
                )
            }
        }
    }

    @Test
    fun `upper lower cardio is offered, because a cardio position carries no volume`() {
        val offered = splitShapesFor(cadence = 3, cardio = CardioPlacement.OWN_DAY)

        val upperLowerCardio = offered.single { it.id == "upper-lower-2-cardio" }

        // Three positions on a two-rotation, and sound: divisibility counts only the positions
        // carrying resistance work, and a run is neither upper nor lower.
        assertEquals(3, upperLowerCardio.days.size)
        assertEquals(2, upperLowerCardio.liftingPositions)
        assertTrue(upperLowerCardio.isBalanced)
    }

    @Test
    fun `nobody gets a cardio day they did not ask for`() {
        listOf(CardioPlacement.NONE, CardioPlacement.AFTER_LIFTING).forEach { placement ->
            val offered = splitShapesFor(cadence = 3, cardio = placement)
            assertTrue(offered.none { day -> day.days.any { it.kind == RoutineDayKind.CARDIO } })
        }
    }

    @Test
    fun `every shape the app offers trains every main muscle directly somewhere in its cycle`() {
        // The coverage check reads the same list, so a shape that missed one would have the app
        // flagging a gap in a routine it built itself — the lifter chose "full body" and was told
        // their full body never trains traps.
        //
        // Less the one shape that misses them on purpose: one day holding all twelve runs three
        // hours at three times a week, so it names five and the check says what it leaves out.
        SPLIT_SHAPES.filter { it.id != "full-body-1" }.forEach { shape ->
            val named = shape.days.flatMap { it.muscles }.toSet()
            assertEquals(shape.id, emptySet<String>(), MAIN_MUSCLES.toSet() - named)
        }
    }

    @Test
    fun `full body every session is still the one shape that leaves main muscles out`() {
        val everySession = SPLIT_SHAPES.single { it.id == "full-body-1" }

        // Pinned so the exception above stays one: if this shape ever covers everything, the
        // exemption is stale and should go; if another shape needs one, it should be argued here.
        val leftOut = MAIN_MUSCLES.toSet() - everySession.days.flatMap { it.muscles }.toSet()
        assertTrue(leftOut.isNotEmpty())
    }

    @Test
    fun `every day a shape lays out names muscles the bank knows`() {
        // A day asking for a muscle the bank has never heard of is a day nothing can fill, and
        // the failure would show up as an empty routine rather than as an error.
        val vocabulary = setOf(
            "abdominals", "abductors", "adductors", "biceps", "calves", "chest", "forearms",
            "glutes", "hamstrings", "lats", "lower back", "middle back", "neck", "quadriceps",
            "shoulders", "traps", "triceps",
        )

        SPLIT_SHAPES.flatMap { it.days }.forEach { day ->
            day.muscles.forEach {
                assertTrue(
                    "${day.name} asks for $it, which the bank does not use",
                    it in vocabulary,
                )
            }
        }
    }

    @Test
    fun `every lifting day a shape lays out asks for something`() {
        SPLIT_SHAPES.flatMap { it.days }
            .filter { it.kind == RoutineDayKind.LIFTING }
            .forEach { assertTrue("${it.name} asks for no muscles", it.muscles.isNotEmpty()) }
    }
}
