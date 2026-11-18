plugins {
    alias(libs.plugins.android.application)
}

android {
    enableKotlin = false
    namespace = "com.thomaskuenneth.c64watchface"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.thomaskuenneth.c64watchface"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 14001
        versionName = "1.4.0"
    }

    lint {
        disable += setOf("UnusedResources", "NotShrinkingResources")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
        }
    }
}
