package org.wayfarer.atlas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.wayfarer.atlas.ui.AtlasApp

class TravelDisclaimerSettingsTest {
    @get:Rule val compose = createComposeRule()

    // Reviewing the notice is always available; dismissing it must not remove that option.
    @Test fun noticeCanBeReopenedFromSettingsAfterAcknowledgement() {
        compose.setContent { AtlasApp(state = AtlasUiState(loading = false)) }
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("review-travel-disclaimer").performScrollTo().performClick()
        compose.onNodeWithTag("travel-disclaimer").assertIsDisplayed()
        compose.onNodeWithTag("acknowledge-disclaimer").performClick()
        compose.onNodeWithTag("travel-disclaimer").assertDoesNotExist()
        compose.onNodeWithTag("review-travel-disclaimer").performScrollTo().performClick()
        compose.onNodeWithTag("travel-disclaimer").assertIsDisplayed()
    }
}
