package com.thomaskuenneth.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

public class C64 {
    public static final int INTERACTIVE_UPDATE_RATE_MS = 333;

    // Commodore 64 colour 14 (http://unusedino.de/ec64/technical/misc/vic656x/colors/)
    public static final int LIGHT_BLUE = 0xff6C5EB5;

    // Commodore 64 colour 6
    public static final int BLUE = 0xff352879;

    // black
    public static final int BLACK = 0xff000000;

    // white
    public static final int WHITE = 0xffffffff;

    // used to be C64WatchFaceService.class.getSimpleName()
    // but this way we can use it in the phone/tablet app, too
    public static final String PREFS_NAME = "C64WatchFaceService";
    public static final String PREFS_DATE = "date";
    public static final String PREFS_SECONDS = "seconds";
    public static final String PREFS_UPPERCASE = "uppercase";

    // calculazed text height
    public float last;

    // C64 cursor visible
    public boolean c64CursorVisible;

    public boolean isRound = false;

    public boolean centerHorizontally = true;

    private static final String TAG = C64.class.getSimpleName();

    private final Context context;
    private final SharedPreferences prefs;
    public final Paint textPaint = new Paint();
    public final Calendar cal = Calendar.getInstance();
    public final Paint borderPaint = new Paint();
    public final Paint backgroundPaint = new Paint();

    public C64(Context context, SharedPreferences prefs) {
        this.context = context;
        this.prefs = prefs;
    }

    // Should be called in onCreate()
    public void setup() {
        setCalToNow();
        last = -1;
        Typeface typface = Typeface.createFromAsset(context.getAssets(),
                "C64_Pro_Mono-STYLE.ttf");
        textPaint.setTypeface(typface);
        setupPaint(false);
        c64CursorVisible = false;
    }

    public void setupPaint(boolean inAmbientMode) {
        textPaint.setColor(inAmbientMode ? WHITE : LIGHT_BLUE);
        borderPaint.setColor(inAmbientMode ? BLACK : LIGHT_BLUE);
        backgroundPaint.setColor(inAmbientMode ? BLACK : BLUE);
        textPaint.setAntiAlias(!inAmbientMode);
    }

    public void setCalToNow() {
        cal.setTime(new Date());
    }

    public void draw(Canvas canvas, Rect bounds, boolean ambientMode) {
        boolean dateVisible = prefs.getBoolean(PREFS_DATE, false);
        boolean seconds = prefs.getBoolean(PREFS_SECONDS, false);
        setCalToNow();
        String strDate;
        if (dateVisible) {
            String strWeekday = new SimpleDateFormat("EE", Locale.getDefault()).format(cal.getTime());
            if (strWeekday.endsWith(".")) {
                strWeekday = strWeekday.substring(0,
                        strWeekday.length() - 1);
            }
            strDate = strWeekday + " " + cal.get(Calendar.DAY_OF_MONTH);
        } else {
            strDate = "";
        }
        String patternTime;
        if (DateFormat.is24HourFormat(context)) {
            patternTime = "HH:mm";
        } else {
            patternTime = "KK:mm";
        }
        if (seconds) {
            patternTime += ":ss";
        }
        if (!DateFormat.is24HourFormat(context)) {
            patternTime += " a";
        }
        StringBuilder sb = new StringBuilder(new SimpleDateFormat(patternTime,
                Locale.getDefault()).format(cal.getTime()));
        while (strDate.length() > sb.length()) {
            sb.append(" ");
        }
        String strTime = sb.toString();
        int w = bounds.width();
        int h = bounds.height();
        int borderHeight = (int) (((float) h / 100f) * 5f);
        int borderWidth = (int) (((float) w / 100f) * 5f);
        canvas.drawPaint(borderPaint);
        Rect r = new Rect(borderWidth,
                borderHeight, w - 1 - borderWidth,
                h - borderHeight - 1);

        if (isRound) {
            canvas.drawCircle(bounds.width() / 2,
                    bounds.height() / 2,
                    (bounds.width() - borderWidth) / 2,
                    backgroundPaint);
        } else {
            canvas.drawRect(r, backgroundPaint);
        }

        if (prefs.getBoolean(PREFS_UPPERCASE, false)) {
            strTime = strTime.toUpperCase();
            strDate = strDate.toUpperCase();
        }

        if (last == -1) {
            int maxWidth = r.width();
            float size = 12f;
            last = size;
            while (true) {
                textPaint.setTextSize(size);
                float current = textPaint.measureText(strTime);
                if (current < maxWidth) {
                    last = size;
                    size += 4;
                } else {
                    break;
                }
            }
            textPaint.setTextSize(last);
        }
        int x = centerHorizontally ? (w - (int) textPaint.measureText(strTime)) / 2 : borderWidth;
        int th = dateVisible ? 2 * (int) last : (int) last;
        int y = ((h - th) / 2) - (int) textPaint.ascent();
        canvas.drawText(strTime, x, y, textPaint);
        if (dateVisible) {
            y += last;
            canvas.drawText(strDate, x, y, textPaint);
        }
        if (!ambientMode) {
            y += last;
            String a = c64CursorVisible ? "\u2588" : " ";
            canvas.drawText(a, x, y, textPaint);
        }
    }

    public void pulse(Callable<Boolean> callback) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                c64CursorVisible = !c64CursorVisible;
                try {
                    if (callback.call()) {
                        pulse(callback);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "pulse()", e);
                }
            }
        }, INTERACTIVE_UPDATE_RATE_MS);
    }
}
