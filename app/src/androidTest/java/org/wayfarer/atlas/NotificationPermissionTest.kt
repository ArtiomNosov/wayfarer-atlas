package org.wayfarer.atlas

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.wayfarer.atlas.storage.TripStore

class NotificationPermissionTest {
    @get:Rule val compose = createEmptyComposeRule()

    // A denied system prompt must preserve the trip, without claiming reminders are enabled.
    @Test fun denyingNotificationsStillSavesTripWithRemindersOff() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val context = instrumentation.targetContext
        val journal = TripStore(context.filesDir)
        journal.deleteAll()
        context.getSharedPreferences("preferences", 0).edit().clear().putString("language", "en").commit()
        instrumentation.uiAutomation.executeShellCommand(
            "pm revoke ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
        instrumentation.uiAutomation.executeShellCommand(
            "pm clear-permission-flags ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS} user-set user-fixed"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.acknowledgeTravelDisclaimerIfPresent()
                compose.waitUntil(15_000) {
                    compose.onAllNodes(androidx.compose.ui.test.hasTestTag("journal-add")).fetchSemanticsNodes().isNotEmpty() ||
                        compose.onAllNodes(androidx.compose.ui.test.hasText("Where will you go?")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("tab-trips").performClick()
                compose.onNodeWithTag("journal-add").performClick()
                compose.onNodeWithTag("trip-destination").performClick()
                compose.onNodeWithText("Find a country").performTextReplacement("France")
                compose.onNode(hasText("France") and !hasSetTextAction()).performClick()
                compose.onNodeWithTag("trip-start").performScrollTo().performTextReplacement("2027-06-03")
                compose.onNodeWithTag("trip-end").performScrollTo().performTextReplacement("2027-06-09")
                compose.onNode(isToggleable()).performScrollTo().performClick()
                compose.onNodeWithTag("trip-save").performScrollTo().performClick()

                var denyButton: AccessibilityNodeInfo? = null
                compose.waitUntil(10_000) {
                    denyButton = findDenial(instrumentation.uiAutomation.rootInActiveWindow)
                    denyButton != null
                }
                check(denyButton!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                compose.waitUntil(10_000) { journal.load().size == 1 }

                val saved = journal.load().single()
                assertEquals("FR", saved.destination)
                assertEquals("2027-06-03", saved.startDate)
                assertEquals("2027-06-09", saved.endDate)
                assertEquals(false, saved.isReminderEnabled)
                assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            }
        } finally {
            journal.deleteAll()
            context.getSharedPreferences("preferences", 0).edit().clear().commit()
        }
    }

    private fun findDenial(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.viewIdResourceName?.endsWith(":id/permission_deny_button") == true) return node
        for (index in 0 until node.childCount) findDenial(node.getChild(index))?.let { return it }
        return null
    }
}
