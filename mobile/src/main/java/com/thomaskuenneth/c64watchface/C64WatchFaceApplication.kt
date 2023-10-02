package com.thomaskuenneth.c64watchface

import android.app.Application
import com.google.android.material.color.DynamicColors

class C64WatchFaceApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
