### Welcome to C64 Tribute WatchFace

This WatchFace for Wear OS by Google depicts the legendary colors of the Commodore 64 and its iconic font. Based on system settings, time is shown in 12 or 24-hour format.

<p>
<img src="./fastlane/metadata/android/en-US/images/wearScreenshots/01.png" width="32%">
<img src="./fastlane/metadata/android/en-US/images/wearScreenshots/02.png" width="32%">
<img src="./fastlane/metadata/android/en-US/images/wearScreenshots/03.png" width="32%">
</p>

### What it shows

- The time in the C64 screen colors and character set
- A blinking cursor below the time
- Optionally, seconds and the date as weekday and day of the month
- Capitals only, or upper and lower case
- Ambient mode in white on black, with the cursor stopped
- A separate layout for round, square and rectangular watches

To change these settings, tap and hold the face, tap the pencil, and swipe through the available options.

Get it for free on [Google Play](https://play.google.com/store/apps/details?id=com.thomaskuenneth.c64watchface).

### The phone app

The phone app is a separate installation. It previews the face and carries the same three settings plus an About page. The watch face used to ship inside it, so installing one installed the other; the Android Gradle Plugin removed that, and Play takes the watch and the phone as separate bundles.

### License

C64 Tribute WatchFace is published under the terms of the [GNU General Public License Version 3](./gpl-3.0.txt). It uses the [Pet Me 64](https://www.kreativekorp.com/software/fonts/) font by Rebecca G. Bettencourt, Kreative Software, in accordance with the Kreative Software Relay Fonts Free Use License in [license.txt](./license.txt). The first version of this app, or rather its source code, appeared 2015 in the third edition of my book about Android development called *Android 5*.

### Gradle cheat sheet

- Build every bundle: `./gradlew bundleRelease` (one AAB per module, version codes derived from `baseVersionCode`)
- Update store screenshots for every device class: `./gradlew :screenshots:generateStoreScreenshots` (starts `Pixel_10`, `Pixel_10_Pro_Fold` and `Pixel_Tablet` in turn, so none of them may already be running)
- Update store screenshots on one running emulator only: `ANDROID_SERIAL=<serial> ./gradlew :screenshots:captureStoreScreenshots` (the device class is detected on the device)
- Update the Wear store screenshots: `ANDROID_SERIAL=<serial of a 454x454 round Wear AVD> ./gradlew :common:captureWearScreenshots` (needs the face installed and active; writes `wearScreenshots/01-05.png`)
- Release to Google Play: the *Upload to Play Internal Testing* workflow, which signs both bundles, uploads the watch face to the Wear OS track and the phone app to its own
