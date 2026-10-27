package org.wayfarer.atlas

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test

class FirstLaunchDisclaimerTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun clearTestPreferences() {
        context.getSharedPreferences("onboarding", 0).edit().clear().commit()
        context.getSharedPreferences("preferences", 0).edit().clear().commit()
    }

    // Losing the local acknowledgement must not repeat the first-run gate after recreation/relaunch.
    @Test fun acknowledgementIsRequiredAndSurvivesActivityRecreationAndRelaunch() {
        freshInstallPreferences("en")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("travel-disclaimer").assertIsDisplayed()
            compose.onNodeWithText("official immigration authorities", substring = true).assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("travel-disclaimer").assertIsDisplayed()
            tapScreenCorner()
            compose.onNodeWithTag("travel-disclaimer").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithTag("travel-disclaimer").assertIsDisplayed()

            compose.onNodeWithTag("acknowledge-disclaimer").performClick()
            scenario.recreate()
            compose.onNodeWithTag("tab-explore").assertIsDisplayed()
            compose.onNodeWithTag("travel-disclaimer").assertDoesNotExist()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("tab-explore").assertIsDisplayed()
            compose.onNodeWithTag("travel-disclaimer").assertDoesNotExist()
        }
    }

    // A clean installation must show the chosen UI language, including a usable acknowledgement.
    @Test fun freshInstallationShowsRussianDisclaimerAndAcknowledgement() {
        freshInstallPreferences("ru")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("travel-disclaimer").assertIsDisplayed()
            compose.onNodeWithText("Перед покупкой билетов", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Понятно").assertIsDisplayed().performClick()
            compose.onNodeWithTag("travel-disclaimer").assertDoesNotExist()
        }
    }

    private fun freshInstallPreferences(language: String) {
        context.getSharedPreferences("onboarding", 0).edit().clear().commit()
        context.getSharedPreferences("preferences", 0).edit().clear().putString("language", language).commit()
    }

    private fun tapScreenCorner() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            MotionEvent.obtain(now, now, action, 4f, 4f, 0).let { event ->
                check(automation.injectInputEvent(event, true))
                event.recycle()
            }
        }
    }
}
