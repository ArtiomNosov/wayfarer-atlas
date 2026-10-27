package org.wayfarer.atlas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.wayfarer.atlas.domain.Country
import org.wayfarer.atlas.domain.EntryCategory
import org.wayfarer.atlas.domain.EntryRule
import org.wayfarer.atlas.domain.Trip
import org.wayfarer.atlas.ui.AtlasApp
import org.wayfarer.atlas.ui.AtlasTheme
import org.wayfarer.atlas.ui.TripEditorDialog

class AtlasUiTest {
    @get:Rule val compose = createComposeRule()

    // Catches accidentally saving reversed dates, rather than just changing an error label.
    @Test fun reversedDatesDoNotSaveJournalEntry() {
        var saved: Trip? = null
        compose.setContent {
            AtlasTheme {
                TripEditorDialog(
                    language = "en",
                    countries = listOf(Country("FR", "France", "Франция")),
                    initial = null,
                    onSave = { saved = it },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithTag("trip-destination").performClick()
        compose.onNodeWithText("France", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("trip-start").performScrollTo().performTextReplacement("2026-11-20")
        compose.onNodeWithTag("trip-end").performScrollTo().performTextReplacement("2026-11-10")
        compose.onNodeWithTag("trip-save").performScrollTo().performClick()
        compose.onNodeWithTag("trip-date-error").performScrollTo().assertIsDisplayed()
        assertNull(saved)
        compose.onNodeWithTag("trip-end").performScrollTo().performTextReplacement("2026-11-22")
        compose.onNodeWithTag("trip-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("FR", saved?.destination)
            assertEquals("2026-11-20", saved?.startDate)
            assertEquals("2026-11-22", saved?.endDate)
        }
    }

    // Catches turning missing rules into positive travel advice or an invented zero rank.
    @Test fun emptyDatasetExplainsUnknownAndJournalStillWorks() {
        compose.setContent { AtlasApp(state = AtlasUiState(loading = false)) }
        compose.onNodeWithTag("coverage-unknown").assertIsDisplayed()
        compose.onNodeWithTag("tab-trips").performClick()
        compose.onNodeWithTag("journal-empty").assertIsDisplayed()
        compose.onNodeWithTag("journal-add").performClick()
        compose.onNodeWithTag("trip-save").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertDoesNotExist()
    }

    @Test fun missingRuleRemainsUnknownInDestinationDetail() {
        compose.setContent {
            AtlasApp(state = AtlasUiState(
                loading = false,
                countries = listOf(Country("US", "United States", "США"), Country("FR", "France", "Франция"))
            ))
        }
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(hasText("France"))
        compose.onNodeWithText("France").performClick()
        compose.onNodeWithTag("destination-category").assertTextEquals("No reliable data")
    }

    // Losing saveable editor state must not discard a user's in-progress trip on rotation.
    @Test fun tripDraftSurvivesStateRestorationAndSavesEnteredValues() {
        var saved: Trip? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AtlasApp(
                state = AtlasUiState(loading = false, countries = listOf(Country("FR", "France", "Франция"))),
                onSaveTrip = { saved = it }
            )
        }
        compose.onNodeWithTag("tab-trips").performClick()
        compose.onNodeWithTag("journal-add").performClick()
        compose.onNodeWithTag("trip-destination").performClick()
        compose.onNodeWithText("France", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("trip-start").performScrollTo().performTextReplacement("2027-06-03")
        compose.onNodeWithTag("trip-end").performScrollTo().performTextReplacement("2027-06-09")
        compose.onNodeWithText("Note (optional)").performScrollTo().performTextReplacement("Train to Lyon")
        compose.onNode(isToggleable()).performScrollTo().performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("trip-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("FR", saved?.destination)
            assertEquals("2027-06-03", saved?.startDate)
            assertEquals("2027-06-09", saved?.endDate)
            assertEquals("Train to Lyon", saved?.note)
            assertEquals(true, saved?.isReminderEnabled)
        }
    }

    // A screen reader needs one labelled switch, and its label must toggle the reminder.
    @Test fun reminderCanBeToggledByItsLabel() {
        compose.setContent {
            AtlasTheme {
                TripEditorDialog("en", listOf(Country("FR", "France", "Франция")), null, {}, {})
            }
        }
        val reminder = compose.onNode(hasText("Trip reminder") and isToggleable())
        reminder.performScrollTo().assertIsOff().performClick().assertIsOn()
    }

    // A territory absent from the source must not be offered as a supported citizenship.
    @Test fun citizenshipPickerExcludesDestinationsThatAreNotSupportedOrigins() {
        val unitedStates = Country("US", "United States", "США")
        compose.setContent {
            AtlasApp(state = AtlasUiState(
                loading = false,
                countries = listOf(unitedStates, Country("GL", "Greenland", "Гренландия")),
                citizenships = listOf(unitedStates)
            ))
        }
        compose.onNodeWithText("Your citizenship").performClick()
        compose.onNode(hasText("Greenland") and hasAnyAncestor(isDialog())).assertDoesNotExist()
        compose.onNode(hasText("United States") and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }

    // Additional journal destinations must not make every complete source ranking look incomplete.
    @Test fun rankingCoverageUsesTheCommonSourceDestinations() {
        val origins = listOf(Country("US", "United States", "США"), Country("FR", "France", "Франция"))
        compose.setContent {
            AtlasApp(state = AtlasUiState(
                loading = false,
                countries = origins + Country("GL", "Greenland", "Гренландия"),
                citizenships = origins,
                rules = listOf(
                    EntryRule("US", "FR", EntryCategory.VISA_FREE),
                    EntryRule("FR", "US", EntryCategory.VISA_REQUIRED)
                )
            ))
        }
        compose.onNodeWithTag("tab-ranking").performClick()
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction())
            .performScrollToNode(hasText("Coverage: 1/1 source destinations · complete"))
        compose.onAllNodesWithText("Coverage: 1/1 source destinations · complete")[0].assertIsDisplayed()
    }

    @Test fun destructiveJournalActionRequiresConfirmation() {
        var cleared = false
        compose.setContent {
            AtlasApp(state = AtlasUiState(loading = false), onClearTrips = { cleared = true })
        }
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("clear-journal").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(false, cleared) }
        compose.onNodeWithTag("confirm-clear-journal").performClick()
        compose.runOnIdle { assertEquals(true, cleared) }
    }
}
