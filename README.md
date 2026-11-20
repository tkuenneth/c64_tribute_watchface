### Welcome to C64 Tribute Watchface

<img src="./artwork/app_symbol.png" width="10%">

This watchface for Wear OS by Google depicts the legendary colors of the Commodore 64 as well as its iconic font. Based on system settings time is shown in 12 or 24 hour format.

<p>
<img src="./fastlane/metadata/android/en-US/images/wearScreenshots/01.png" width="25%">
<img src="./fastlane/metadata/android/en-US/images/wearScreenshots/02.png" width="25%">
<img src="./fastlane/metadata/android/en-US/images/wearScreenshots/03.png" width="25%">
<img src="./fastlane/metadata/android/en-US/images/wearScreenshots/04.png" width="25%">
</p>

### What it shows

- The time in the C64 screen colors and character set, with the blinking cursor below it
- The date, if you want it, in the order your language uses
- Seconds, if you want them, and uppercase letters, if you prefer them
- White on black in ambient mode, with the cursor at rest
- Round, square and rectangular watches each get their own layout

The three options live where Wear OS keeps watch face settings: press and hold the face, tap Customize, and pick what the face should show.

### The phone app

The phone app is a separate install. It shows the face as a preview and holds the same settings along with the About page. Installing the watch face does not install the phone app, and installing the phone app does not install the watch face.

### Gradle cheat sheet

- Build every bundle: `./gradlew bundleRelease` (one AAB per module, version codes derived from `baseVersionCode`)
- Update store screenshots for every device class: `./gradlew :screenshots:generateStoreScreenshots` (starts `Pixel_10`, `Pixel_10_Pro_Fold` and `Pixel_Tablet` in turn, so none of them may already be running)
- Update store screenshots on one running emulator only: `ANDROID_SERIAL=<serial> ./gradlew :screenshots:captureStoreScreenshots` (the device class is detected on the device)
- Release to Google Play: the *Upload to Play Internal Testing* workflow, which signs all three bundles, uploads the two watch bundles to the Wear OS track and the phone bundle to its own

### Modules

| module | what it is |
|---|---|
| `:watchface` | The Watch Face Format (WFF) watch face. Resource only (`android:hasCode="false"`), minSdk 33, serves Wear OS 4 and newer. |
| `:wear` | The original `WatchFaceService` watch face in Java, minSdk 23, serves Wear OS 2 and 3, which cannot run WFF. |
| `:mobile` | The phone companion in Jetpack Compose: it shows the same face as a preview and holds the settings and the About page. |
| `:common` | The shared Java drawing code (`C64.java`) that both `:wear` and `:mobile` draw with. `:watchface` does not use it, because WFF has no code. |

All four ship under one application id, `com.thomaskuenneth.c64watchface`. Version codes come from a single `baseVersionCode` in `gradle.properties`, multiplied by ten plus an offset: `+1` for `:wear`, `+2` for `:mobile`, `+3` for `:watchface`, so the WFF bundle always outranks the legacy one on devices that can run it. The two watch bundles go into the same Google Play Wear OS release, the phone bundle goes to the mobile track.

The phone app used to carry the watch face inside it, so installing one installed the other. The Android Gradle Plugin dropped that mechanism, and Google Play expects the watch and the phone to ship as separate bundles.

### License

C64 Tribute Watchface is published under the terms of the [GNU General Public License Version 3](./gpl-3.0.txt). It uses the [Pet Me 64](https://www.kreativekorp.com/software/fonts/) font by Rebecca G. Bettencourt, Kreative Software, in accordance with the Kreative Software Relay Fonts Free Use License in [license.txt](./license.txt). The first version of this app, or rather its source code, appeared 2015 in the third edition of my book about Android development called *Android 5*.

Get it for free on [Google Play](https://play.google.com/store/apps/details?id=com.thomaskuenneth.c64watchface).
