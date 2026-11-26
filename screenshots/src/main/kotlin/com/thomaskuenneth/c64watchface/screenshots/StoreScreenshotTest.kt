package com.thomaskuenneth.c64watchface.screenshots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.thomaskuenneth.c64watchface.C64WatchFace
import com.thomaskuenneth.c64watchface.MainActivity
import com.thomaskuenneth.common.C64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val HINT_ACKNOWLEDGED = "hint_acknowledged"
private const val PREFS_DATE = "date"
private const val PREFS_SECONDS = "seconds"
private const val PREFS_UPPERCASE = "uppercase"
private const val CURSOR_TIMEOUT_MILLIS = 5_000L

@LargeTest
@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {

    @get:Rule(order = 0)
    val storeStateRule = object : ExternalResource() {
        override fun before() {
            outputDir.deleteRecursively()
            outputDir.mkdirs()
            device().executeShellCommand("cmd uimode night yes")
            unplugTheCharger()
            seedStoreState()
        }

        override fun after() {
            device().executeShellCommand("cmd uimode night no")
            device().executeShellCommand("dumpsys battery reset")
        }
    }

    private val uiDevice by lazy { device() }
    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val outputDir = File(
        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: InstrumentationRegistry.getInstrumentation().targetContext
                .getExternalFilesDir(null)?.absolutePath.orEmpty(),
        "store-screenshots",
    )
    private lateinit var labels: UiLabels

    @Test
    fun captureStoreScreenshots() {
        labels = targetUiLabels(targetContext)

        launchApp()
        File(outputDir, "device-class.txt").writeText(detectDeviceClass())

        captureLitFace(0, "watchface")

        clickDesc(labels.settings)
        awaitText(labels.appearance)
        awaitGone(By.desc(labels.info))
        capture(1, "settings")
        uiDevice.pressBack()
        awaitDesc(labels.settings)
        awaitGone(By.text(labels.appearance))

        clickDesc(labels.info)
        awaitText(labels.fontAttribution)
        awaitGone(By.desc(labels.settings))
        capture(2, "about")
        uiDevice.pressBack()
        awaitDesc(labels.settings)
        awaitGone(By.text(labels.fontAttribution))

        assertEquals(
            listOf("00_watchface.png", "01_settings.png", "02_about.png", "device-class.txt"),
            outputDir.listFiles().orEmpty().map { it.name }.sorted(),
        )
    }

    private fun unplugTheCharger() {
        device().executeShellCommand("dumpsys battery unplug")
        device().executeShellCommand("dumpsys battery set level 100")
    }

    private fun readFontScale(): String? = uiDevice
        .executeShellCommand("settings get system font_scale")
        .trim()
        .takeIf { it.isNotEmpty() && it != "null" }

    private fun assertDefaultTextSize() {
        val scale = readFontScale() ?: return
        assertEquals(
            "the device draws text at font scale $scale, which reflows and truncates every shot. " +
                "with_avd.sh resets it at boot; reset it with " +
                "'adb shell settings put system font_scale 1.0'",
            1.0f,
            scale.toFloat(),
            0.001f,
        )
    }

    private fun assertNoChargingIndicator() {
        val battery = uiDevice.executeShellCommand("dumpsys battery")
        assertTrue(
            "the device reports itself as charging, which puts a power symbol in the status bar",
            battery.contains("AC powered: false") && battery.contains("USB powered: false"),
        )
    }

    private fun launchApp() {
        val context = targetContext
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        awaitDesc(labels.settings)
    }

    // The hint snackbar covers the bottom of the face until it is acknowledged, and the three
    // display options decide what the face shows, so both are fixed before the app starts.
    private fun seedStoreState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean(HINT_ACKNOWLEDGED, true)
            .putBoolean(PREFS_DATE, true)
            .putBoolean(PREFS_SECONDS, false)
            .putBoolean(PREFS_UPPERCASE, false)
            .commit()
    }

    private fun detectDeviceClass(): String {
        val current = checkNotNull(resumedActivity()) { "MainActivity was not resumed" }
        val adapter = WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(current))
        val seen = AtomicReference<WindowLayoutInfo>()
        val latch = CountDownLatch(1)
        val executor = Executor { it.run() }
        val consumer = androidx.core.util.Consumer<WindowLayoutInfo> { info ->
            seen.set(info)
            latch.countDown()
        }
        adapter.addWindowLayoutInfoListener(current, executor, consumer)
        try {
            latch.await(5, TimeUnit.SECONDS)
        } finally {
            adapter.removeWindowLayoutInfoListener(consumer)
        }
        val layoutInfo = seen.get()
        val folds = layoutInfo?.displayFeatures.orEmpty().filterIsInstance<FoldingFeature>()
        return when {
            folds.isNotEmpty() -> {
                assertEquals(
                    "the foldable is not open, so this is the cover display, not the inner one",
                    FoldingFeature.State.FLAT,
                    folds.first().state,
                )
                "fold"
            }
            current.resources.configuration.smallestScreenWidthDp >= 600 -> "tablet"
            else -> "phone"
        }
    }

    private fun resumedActivity(): Activity? {
        var found: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstOrNull()
        }
        return found
    }

    private fun captureLitFace(index: Int, name: String) {
        val view = watchFaceView()
        val c64 = checkNotNull(view.data) {
            "The watch face preview carries no C64 to draw"
        }
        val drawn = CountDownLatch(1)
        val onDraw = ViewTreeObserver.OnDrawListener { drawn.countDown() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            c64.cursorSteady = true
            c64.c64CursorVisible = true
            view.viewTreeObserver.addOnDrawListener(onDraw)
            view.invalidate()
        }
        try {
            check(drawn.await(CURSOR_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                "The watch face did not redraw with a steady cursor"
            }
            capture(index, name)
        } finally {
            instrumentation.runOnMainSync {
                view.viewTreeObserver.removeOnDrawListener(onDraw)
                c64.cursorSteady = false
            }
        }
    }

    private fun watchFaceView(): C64WatchFace {
        val activity = checkNotNull(resumedActivity()) { "MainActivity was not resumed" }
        return checkNotNull(findWatchFace(activity.window.decorView)) {
            "The watch face preview is not in the view hierarchy"
        }
    }

    private fun findWatchFace(view: View): C64WatchFace? = when {
        view is C64WatchFace -> view
        view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull {
            findWatchFace(view.getChildAt(it))
        }
        else -> null
    }

    private fun capture(index: Int, name: String) {
        uiDevice.waitForIdle()
        assertNoChargingIndicator()
        assertDefaultTextSize()
        uiDevice.takeScreenshot(File(outputDir, "%02d_%s.png".format(index, name)))
    }

    private fun awaitText(text: String, timeoutMs: Long = 5_000) {
        check(uiDevice.wait(Until.hasObject(By.text(text)), timeoutMs)) {
            "Expected text not visible within $timeoutMs ms: $text\nOn screen: ${visibleTexts()}"
        }
    }

    private fun visibleTexts(): List<String> =
        uiDevice.findObjects(By.pkg(targetContext.packageName))
            .mapNotNull { it.text }
            .filter { it.isNotBlank() }

    private fun awaitGone(selector: BySelector, timeoutMs: Long = 5_000) {
        check(uiDevice.wait(Until.gone(selector), timeoutMs)) {
            "The previous destination is still on screen after $timeoutMs ms, so the capture " +
                "would catch the transition between them: $selector"
        }
    }

    private fun awaitDesc(desc: String, timeoutMs: Long = 5_000) {
        check(uiDevice.wait(Until.hasObject(By.desc(desc)), timeoutMs)) {
            "Expected contentDescription not visible within $timeoutMs ms: $desc"
        }
    }

    private fun clickDesc(desc: String) {
        val node = checkNotNull(uiDevice.findObject(By.desc(desc))) {
            "UI node not found, contentDescription: $desc"
        }
        node.click()
        uiDevice.waitForIdle()
    }
}

private fun device() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

internal data class UiLabels(
    val settings: String,
    val info: String,
    val appearance: String,
    val fontAttribution: String,
)

// Read from the app's own resources, so the capture follows the device language instead of
// carrying a second copy of every label.
internal fun targetUiLabels(context: Context): UiLabels {
    fun string(name: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        check(id != 0) { "Missing string resource '$name' in ${context.packageName}" }
        return context.getString(id)
    }
    return UiLabels(
        settings = string("settings"),
        info = string("info"),
        appearance = string("appearance"),
        fontAttribution = string("about4"),
    )
}
