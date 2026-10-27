package org.wayfarer.atlas

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.wayfarer.atlas.data.AtlasRepository
import org.wayfarer.atlas.domain.EntryCategory

class GovernmentSupplementTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun clearTestPreferences() {
        context.getSharedPreferences("preferences", 0).edit().clear().commit()
    }

    // A missing base-matrix destination must gain the government's concrete entry rule in the UI.
    @Test fun russianHongKongDetailShowsVisaFreeFourteenDays() {
        context.getSharedPreferences("preferences", 0).edit().clear()
            .putString("language", "en").putString("citizenship", "RU").commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.acknowledgeTravelDisclaimerIfPresent()
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasText("Where will you go?")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Search destinations"))
            compose.onNodeWithText("Search destinations").performTextReplacement("HK")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("destination-HK"))
            compose.onNodeWithTag("destination-HK").performClick()
            compose.onNodeWithTag("destination-category").assertTextEquals("Visa free")
            compose.onAllNodesWithText("14 days", substring = true)[0].assertIsDisplayed()
        }
    }

    // Source integration must preserve government provenance, not silently substitute a community URL.
    @Test fun selectedHongKongRuleKeepsOfficialImmigrationSource() {
        val reference = AtlasRepository(context).load()
        val rule = reference.rules.singleOrNull { it.citizenship == "RU" && it.destination == "HK" }
        assertNotNull("The official RU → HK supplement must be bundled", rule)
        assertEquals(EntryCategory.VISA_FREE, rule!!.category)
        assertEquals(14, rule.maxStayDays)
        val source = Uri.parse(rule.sourceUrl)
        assertEquals("https", source.scheme)
        assertEquals("immd.gov.hk", source.host?.removePrefix("www."))
    }
}
