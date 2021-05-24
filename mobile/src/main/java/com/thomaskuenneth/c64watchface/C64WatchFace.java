package com.thomaskuenneth.c64watchface;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.thomaskuenneth.common.C64;

public class C64WatchFace extends View {

    private final Rect bounds;

    public C64WatchFace(Context context) {
        super(context);
        bounds = new Rect();
    }

    public C64WatchFace(Context context, AttributeSet atts) {
        this(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bounds.right = w - 1;
        bounds.bottom = h - 1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        C64 data = (C64) getTag();
        if (data != null) {
            data.draw(canvas, bounds, false);
        }
    }
}
