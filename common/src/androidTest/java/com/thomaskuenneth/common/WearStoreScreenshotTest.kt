package com.thomaskuenneth.common

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val TIMEOUT = 8_000L
private const val EDIT = "Edit watch face"
private const val BLINK_PERIOD_MILLIS = 666L
private const val CURSOR_RED = 90
private const val CURSOR_BLUE = 150
private const val CURSOR_BLOCK = 24
private const val CURSOR_AREA_TOP = 240
private const val SHOT_ATTEMPTS = 8

@RunWith(AndroidJUnit4::class)
class WearStoreScreenshotTest {

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val output = File(
        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: InstrumentationRegistry.getInstrumentation().context
                .getExternalFilesDir(null)?.absolutePath.orEmpty(),
        "wear-store-screenshots",
    )

    @Before
    fun prepareTheWatch() {
        output.deleteRecursively()
        output.mkdirs()
        unplugTheCharger()
    }

    @After
    fun restoreTheWatch() {
        choose("Seconds", "Hide seconds")
        choose("Date", "Hide date")
        choose("Capitals", "Upper and lower case")
        unplugTheCharger()
    }

    private fun unplugTheCharger() {
        device.executeShellCommand("dumpsys battery unplug")
        device.executeShellCommand("dumpsys battery set level 100")
    }

    private fun assertNoChargingIndicator() {
        val battery = device.executeShellCommand("dumpsys battery")
        assertTrue(
            "the watch reports itself as charging, which puts a power symbol on the face",
            battery.contains("AC powered: false") &&
                battery.contains("USB powered: false"),
        )
    }

    @Test
    fun captureTheStoreScreenshots() {
        choose("Seconds", "Hide seconds")
        choose("Date", "Hide date")
        choose("Capitals", "Upper and lower case")
        shoot("01")

        choose("Seconds", "Show seconds")
        choose("Date", "Show date")
        choose("Capitals", "Capitals only")
        shoot("02")

        shootEditor("03", "Seconds")
        shootEditor("04", "Date")
        shootEditor("05", "Capitals")

        val captured = output.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(
            listOf("01.png", "02.png", "03.png", "04.png", "05.png"),
            captured,
        )
    }

    private fun watchFace() {
        if (!device.isScreenOn) {
            device.wakeUp()
            device.waitForIdle()
        }
        repeat(3) {
            if (device.hasObject(By.descContains("Watch face"))) return
            device.pressHome()
            device.wait(Until.hasObject(By.descContains("Watch face")), TIMEOUT)
        }
        assertTrue("the watch face never came to the front", device.hasObject(By.descContains("Watch face")))
    }

    private fun openEditor() {
        watchFace()
        val face = device.findObject(By.descContains("Watch face"))
        assertNotNull("no watch face on screen", face)
        face.longClick()
        val edit = device.wait(Until.findObject(By.desc(EDIT)), TIMEOUT)
        assertNotNull("the watch face picker did not offer '$EDIT'", edit)
        edit.click()
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (currentSetting() == null && System.currentTimeMillis() < deadline) {
            device.waitForIdle()
        }
    }

    private fun chip(): UiObject2? = try {
        device.findObjects(By.descContains("watch face"))
            .firstOrNull { candidate ->
                !candidate.text.isNullOrEmpty() &&
                    candidate.contentDescription.orEmpty().startsWith(candidate.text)
            }
    } catch (_: StaleObjectException) {
        null
    }

    private fun currentSetting(): String? = try {
        chip()?.contentDescription?.substringAfterLast("watch face ")?.trim()
    } catch (_: StaleObjectException) {
        null
    }

    private fun pageSwipe(forward: Boolean) {
        val pager = device.findObject(By.scrollable(true))
        assertNotNull("the editor has no pager", pager)
        pager.fling(if (forward) Direction.LEFT else Direction.RIGHT)
        device.waitForIdle()
    }

    private fun onPage(setting: String, act: () -> Unit) {
        openEditor()
        repeat(3) {
            if (currentSetting() == null) openEditor()
            pageSwipe(forward = false)
        }
        if (currentSetting() == null) openEditor()
        repeat(4) {
            if (currentSetting() == setting) {
                act()
                device.pressBack()
                watchFace()
                return
            }
            pageSwipe(forward = true)
        }
        device.dumpWindowHierarchy(File(output, "seek_$setting.xml"))
        throw AssertionError("the editor never showed the page for $setting, saw ${currentSetting()}")
    }

    private fun choose(setting: String, option: String) = onPage(setting) {
        val chip = requireNotNull(chip()) { "no chip for $setting" }
        chip.click()
        val row = device.wait(Until.findObject(By.text(option)), TIMEOUT)
        assertNotNull("'$option' is not offered for $setting", row)
        row.click()
        device.waitForIdle()
    }

    private fun shoot(name: String) {
        watchFace()
        assertNoChargingIndicator()
        val shot = File(output, "$name.png")
        repeat(SHOT_ATTEMPTS) {
            device.takeScreenshot(shot)
            if (hasLitCursor(shot)) return
            SystemClock.sleep(BLINK_PERIOD_MILLIS / 3)
        }
        throw AssertionError("the cursor block is not lit in $name")
    }

    private fun shootEditor(name: String, setting: String) = onPage(setting) {
        assertNoChargingIndicator()
        device.takeScreenshot(File(output, "$name.png"))
    }

    private fun hasLitCursor(file: File): Boolean {
        val shot = BitmapFactory.decodeFile(file.absolutePath)
        val light = Array(shot.height) { y ->
            BooleanArray(shot.width) { x ->
                val pixel = shot.getPixel(x, y)
                ((pixel shr 16) and 0xFF) > CURSOR_RED && (pixel and 0xFF) > CURSOR_BLUE
            }
        }
        shot.recycle()
        for (top in CURSOR_AREA_TOP until light.size - CURSOR_BLOCK) {
            for (left in 0 until light[top].size / 2 - CURSOR_BLOCK) {
                if ((0 until CURSOR_BLOCK).all { row ->
                        (0 until CURSOR_BLOCK).all { column -> light[top + row][left + column] }
                    }
                ) {
                    return true
                }
            }
        }
        return false
    }

}
