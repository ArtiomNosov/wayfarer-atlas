package org.wayfarer.atlas

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.wayfarer.atlas.storage.TripStore
import java.io.File
import java.time.Instant

/** Explicit release-artifact tool. Captures the installed app with its real bundled/cache data. */
class StoreScreenshotCapture {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun captureRealApplicationScreens() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureScreenshots") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(TripStore(context.filesDir).load().isEmpty()) { "Screenshots require an empty real journal" }
        val output = File(context.getExternalFilesDir(null), "store-screenshots").apply { mkdirs() }
        for (language in listOf("en", "ru")) {
            context.getSharedPreferences("preferences", 0).edit()
                .putString("language", language).putString("citizenship", "RU").commit()
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.acknowledgeTravelDisclaimerIfPresent()
                compose.waitUntil(15_000) {
                    compose.onAllNodes(hasText(if (language == "ru") "Куда отправимся?" else "Where will you go?")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
                capture(output, "$language-01-explore.png")

                compose.onNodeWithTag("tab-compare").performClick()
                compose.onNodeWithText(if (language == "ru") "Второе гражданство" else "Second citizenship").performClick()
                compose.onNodeWithText(if (language == "ru") "Найти страну" else "Find a country").performTextReplacement("DE")
                compose.onNodeWithText(if (language == "ru") "Германия" else "Germany").performClick()
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
                capture(output, "$language-02-compare.png")

                compose.onNodeWithTag("tab-ranking").performClick()
                capture(output, "$language-03-ranking.png")

                compose.onNodeWithTag("tab-trips").performClick()
                compose.onNodeWithTag("journal-empty").fetchSemanticsNode()
                capture(output, "$language-04-trips.png")
            }
        }
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        File(output, "capture-metadata.json").writeText(JSONObject()
            .put("capturedAt", Instant.now().toString())
            .put("applicationId", context.packageName)
            .put("versionName", info.versionName)
            .put("versionCode", info.longVersionCode)
            .put("androidApi", Build.VERSION.SDK_INT)
            .put("device", Build.MODEL)
            .put("method", "Compose captureToImage on real MainActivity; AndroidX enables ATD rendering for capture")
            .put("data", "Real bundled or validated cached reference; no fabricated journal entries")
            .toString(2))
    }

    private fun capture(output: File, name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(output, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
