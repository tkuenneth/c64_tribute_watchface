/*
 * This file is part of C64 Tribute Watch Face
 * Copyright (C) 2014 - 2017  Thomas Kuenneth
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.thomaskuenneth.c64watchface;

import static com.thomaskuenneth.common.C64.PREFS_NAME;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.thomaskuenneth.common.C64;

import java.util.TimeZone;
import java.util.concurrent.Callable;

/**
 * This class implements a Commodore 64 like watch face.
 *
 * @author Thomas Kuenneth
 */
public class C64WatchFaceService extends CanvasWatchFaceService {

    private C64 data;

    @Override
    public Engine onCreateEngine() {
        Context context = getBaseContext();
        data = new C64(context, context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE));
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        Callable<Boolean> callback = new Callable<Boolean>() {
            @Override
            public Boolean call() {
                boolean result = shouldTimerBeRunning();
                if (result) {
                    invalidate();
                }
                return result;
            }
        };

        /* receiver to update the time zone */
        final BroadcastReceiver mTimeZoneReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        data.cal.setTimeZone(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));
                        data.setCalToNow();
                    }
                };
        boolean mRegisteredTimeZoneReceiver;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            data.setup();
            mRegisteredTimeZoneReceiver = false;
            setWatchFaceStyle(
                    new WatchFaceStyle.Builder(C64WatchFaceService.this)
                            .setStatusBarGravity(Gravity.START | Gravity.TOP)
                            .build());
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            data.isRound = insets.isRound();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            data.setupPaint(inAmbientMode);
            onVisibilityChanged(isVisible());
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            data.draw(canvas, bounds, isInAmbientMode());
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                data.cal.setTimeZone(TimeZone.getDefault());
                data.setCalToNow();
                data.last = -1;
            } else {
                unregisterReceiver();
            }
            // Whether the timer should be running depends on whether we're visible and
            // whether we're in ambient mode), so we may need to start or stop the timer
            if (shouldTimerBeRunning()) {
                data.pulse(callback);
            }
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter =
                    new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            C64WatchFaceService.this.registerReceiver(mTimeZoneReceiver,
                    filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            C64WatchFaceService.this.unregisterReceiver(
                    mTimeZoneReceiver);
        }
    }
}
