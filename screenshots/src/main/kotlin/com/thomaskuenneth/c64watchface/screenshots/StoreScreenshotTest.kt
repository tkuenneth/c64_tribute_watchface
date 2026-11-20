package com.thomaskuenneth.c64watchface.screenshots

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.thomaskuenneth.c64watchface.MainActivity
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

@LargeTest
@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {

    @get:Rule(order = 0)
    val storeStateRule = object : ExternalResource() {
        override fun before() {
            device().executeShellCommand("cmd uimode night yes")
            seedStoreState()
        }

        override fun after() {
            device().executeShellCommand("cmd uimode night no")
        }
    }

    private val uiDevice by lazy { device() }
    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var outputDir: File
    private lateinit var labels: UiLabels

    @Test
    fun captureStoreScreenshots() {
        labels = targetUiLabels(targetContext)
        outputDir = File(targetContext.getExternalFilesDir(null), "store-screenshots").apply {
            deleteRecursively()
            mkdirs()
        }

        launchApp()
        File(outputDir, "device-class.txt").writeText(detectDeviceClass())

        capture(0, "watchface")

        clickDesc(labels.settings)
        awaitText(labels.appearance)
        capture(1, "settings")
        uiDevice.pressBack()
        awaitDesc(labels.settings)

        clickDesc(labels.info)
        awaitText(labels.fontAttribution)
        capture(2, "about")
        uiDevice.pressBack()
        awaitDesc(labels.settings)
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
        return when {
            layoutInfo?.displayFeatures.orEmpty().any { it is FoldingFeature } -> "fold"
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

    private fun capture(index: Int, name: String) {
        uiDevice.waitForIdle()
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
