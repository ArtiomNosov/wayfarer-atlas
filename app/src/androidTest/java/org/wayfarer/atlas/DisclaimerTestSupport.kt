package org.wayfarer.atlas

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/** Real first-run acknowledgement for tests that exercise other application features. */
internal fun ComposeTestRule.acknowledgeTravelDisclaimerIfPresent() {
    waitForIdle()
    if (onAllNodesWithTag("acknowledge-disclaimer").fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag("acknowledge-disclaimer").performClick()
    }
}
