package com.tylergimpel.traintracks.ui.routine

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tylergimpel.traintracks.ui.RestFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Setup's last page (#161): the two things the rest timer needs, each with Allow, and Skip that
 * always works.
 */
@RunWith(AndroidJUnit4::class)
class RestPermissionsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun eachMissingPermissionHasItsOwnAllow() {
        val allowed = mutableListOf<RestFix>()
        compose.setContent {
            RestPermissions(
                fixes = listOf(RestFix.NOTIFICATIONS, RestFix.EXACT_ALARMS),
                missing = setOf(RestFix.NOTIFICATIONS, RestFix.EXACT_ALARMS),
                onAllow = { allowed += it },
                onDone = {},
            )
        }

        compose.onNodeWithText("Show the countdown").assertExists()
        compose.onNodeWithText("Buzz on time").assertExists()
        compose.onAllNodesWithText("Allow").assertCountEquals(2)
        compose.onAllNodesWithText("Allow")[1].performClick()
        compose.onAllNodesWithText("Allow")[0].performClick()

        assertEquals(listOf(RestFix.EXACT_ALARMS, RestFix.NOTIFICATIONS), allowed)
    }

    @Test
    fun onlyWhatWasMissingIsShown() {
        compose.setContent {
            RestPermissions(fixes = listOf(RestFix.EXACT_ALARMS), missing = setOf(RestFix.EXACT_ALARMS), onAllow = {}, onDone = {})
        }

        compose.onNodeWithText("Buzz on time").assertExists()
        compose.onNodeWithText("Show the countdown").assertDoesNotExist()
    }

    @Test
    fun anAllowedPermissionIsTickedAndTheButtonSaysDone() {
        compose.setContent {
            RestPermissions(fixes = listOf(RestFix.EXACT_ALARMS), missing = emptySet(), onAllow = {}, onDone = {})
        }

        compose.onNodeWithText("Allow").assertDoesNotExist()
        compose.onNodeWithContentDescription("Allowed").assertExists()
        compose.onNodeWithText("Done").assertExists()
    }

    @Test
    fun skipAlwaysWorksAndSoDoesBack() {
        var done = 0
        compose.setContent {
            RestPermissions(
                fixes = listOf(RestFix.NOTIFICATIONS, RestFix.EXACT_ALARMS),
                missing = setOf(RestFix.NOTIFICATIONS, RestFix.EXACT_ALARMS),
                onAllow = {},
                onDone = { done++ },
            )
        }

        compose.onNodeWithText("Skip for now").assertExists()
        compose.onNodeWithTag(PERMISSIONS_DONE).performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertTrue("$done", done == 2)
    }
}
