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
        versionCode = project.findProperty("baseVersionCode").toString().toInt() * 10 + 3
        versionName = project.findProperty("baseVersionName").toString()
    }

    lint {
        disable += setOf("UnusedResources", "NotShrinkingResources")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
        }
    }
}
