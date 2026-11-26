package com.thomaskuenneth.c64watchface

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.thomaskuenneth.common.C64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val TICK_MILLIS = C64.INTERACTIVE_UPDATE_RATE_MS.toLong()
private const val TICKS_AWAITED = 4

@RunWith(RobolectricTestRunner::class)
class CursorPulseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun c64() = C64(context, PreferenceManager.getDefaultSharedPreferences(context))

    private fun awaitTicks(count: Int, c64: C64, keepGoing: () -> Boolean = { true }): Int {
        val ticks = AtomicInteger()
        val reached = CountDownLatch(count)
        c64.pulse {
            ticks.incrementAndGet()
            reached.countDown()
            keepGoing()
        }
        reached.await(count * TICK_MILLIS * 4, TimeUnit.MILLISECONDS)
        return ticks.get()
    }

    @Test
    fun theCursorKeepsBlinkingWhileTheCallbackAsksForMore() {
        val c64 = c64()
        val before = c64.c64CursorVisible

        val ticks = awaitTicks(TICKS_AWAITED, c64)
        c64.stopPulse()

        assertTrue("the pulse delivered only $ticks ticks", ticks >= TICKS_AWAITED)
        assertTrue("the cursor never changed", c64.c64CursorVisible != before || ticks % 2 == 0)
    }

    @Test
    fun aSecondPulseReplacesTheFirstInsteadOfBlinkingTwice() {
        val c64 = c64()
        val first = AtomicInteger()
        val second = AtomicInteger()

        c64.pulse {
            first.incrementAndGet()
            true
        }
        c64.pulse {
            second.incrementAndGet()
            true
        }
        Thread.sleep(TICK_MILLIS * 3)
        val firstAfterReplacement = first.get()
        Thread.sleep(TICK_MILLIS * 3)
        c64.stopPulse()

        assertEquals("the first pulse kept running", firstAfterReplacement, first.get())
        assertTrue("the second pulse never ran", second.get() > 0)
    }

    @Test
    fun stoppingThePulseEndsTheBlinking() {
        val c64 = c64()
        awaitTicks(2, c64)

        c64.stopPulse()
        val afterStop = c64.c64CursorVisible
        Thread.sleep(TICK_MILLIS * 3)

        assertEquals("the cursor changed after the pulse was stopped", afterStop, c64.c64CursorVisible)
    }

    @Test
    fun aCallbackThatSaysStopEndsTheBlinking() {
        val c64 = c64()
        val ticks = AtomicInteger()

        c64.pulse {
            ticks.incrementAndGet()
            false
        }
        Thread.sleep(TICK_MILLIS * 4)

        assertEquals("the pulse continued after the callback said stop", 1, ticks.get())
    }
}
