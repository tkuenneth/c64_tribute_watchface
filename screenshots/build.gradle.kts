import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.thomaskuenneth.c64watchface.screenshots"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 24
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":mobile"

    buildTypes {
        create("screenshots") {
            initWith(getByName("debug"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    beforeVariants { variant ->
        variant.enable = variant.buildType == "screenshots"
    }
}

dependencies {
    implementation(project(":mobile"))
    implementation(project(":common"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.core)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.window)
    implementation(libs.window.java)
}

val storeScreenshotSequence = listOf("watchface", "settings", "about")

val storeScreenshotAvds = listOf("Pixel_10", "Pixel_10_Pro_Fold", "Pixel_Tablet")

val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows

val bashExecutable: String? by lazy {
    if (!isWindows) {
        "bash"
    } else {
        val gitDirectory = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, "git.exe") }.firstOrNull { it.isFile }?.parentFile?.parentFile
        listOfNotNull(
            gitDirectory?.resolve("bin/bash.exe"),
            File("C:/Program Files/Git/bin/bash.exe")
        ).firstOrNull { it.isFile }?.absolutePath
    }
}

val missingBash = "Git Bash was not found; it runs the helper scripts."

tasks.register("generateStoreScreenshots") {
    group = "screenshots"
    description = "Starts each of the AVDs ${storeScreenshotAvds.joinToString()} in turn and " +
        "captures and frames the store screenshots for every device class."

    val rootDirectory = project.rootDir
    val avds = storeScreenshotAvds
    val resolvedBash = bashExecutable
    val bashMissing = missingBash

    doLast {
        val bash = resolvedBash ?: throw GradleException(bashMissing)
        val withAvd = File(rootDirectory, "scripts/with_avd.sh").absolutePath.replace('\\', '/')
        val gradlew = File(rootDirectory, "gradlew").absolutePath.replace('\\', '/')
        check(File(withAvd).isFile) { "Missing scripts/with_avd.sh in ${rootDirectory.name}" }
        avds.forEach { avd ->
            logger.lifecycle("Capturing on $avd")
            val process = ProcessBuilder(
                bash, withAvd, avd, gradlew, "--console=plain",
                ":screenshots:captureStoreScreenshots"
            ).directory(rootDirectory).redirectErrorStream(true).apply {
                environment().entries.removeIf { it.value.isEmpty() }
            }.start()
            process.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException(
                    "Capturing the store screenshots on $avd failed (exit $exitCode)"
                )
            }
        }
    }
}

val storeScreenshotOutput = layout.buildDirectory
    .dir("outputs/connected_android_test_additional_output")

tasks.register<Delete>("cleanStoreScreenshotOutput") {
    group = "screenshots"
    description = "Removes screenshots pulled from an earlier capture, so only this run is framed."
    delete(storeScreenshotOutput)
}

tasks.register("prepareStoreScreenshotDevice") {
    group = "screenshots"
    description = "Opens the foldable and checks that ImageMagick can convert the emulator skins, " +
        "before the capture spends time on the device."

    val rootDirectory = project.rootDir
    val resolvedBash = bashExecutable
    val bashMissing = missingBash

    doLast {
        val bash = resolvedBash ?: throw GradleException(bashMissing)
        val magickProbe = ProcessBuilder(bash, "-lc", "command -v magick")
            .redirectErrorStream(true).start()
        check(magickProbe.waitFor() == 0) {
            "ImageMagick is not on the PATH. Framing converts the emulator skins with its " +
                "'magick' command - install it (winget install ImageMagick.ImageMagick) and " +
                "run this task again."
        }
        val foldableScript = File(rootDirectory, "scripts/open_foldable.sh")
        check(foldableScript.isFile) { "Missing scripts/open_foldable.sh in ${rootDirectory.name}" }
        val process = ProcessBuilder(bash, foldableScript.absolutePath.replace('\\', '/'))
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Opening the foldable failed (exit $exitCode):\n$output")
        }
    }
}

tasks.matching { it.name == "connectedScreenshotsAndroidTest" }.configureEach {
    mustRunAfter("cleanStoreScreenshotOutput", "prepareStoreScreenshotDevice")
}

tasks.register("captureStoreScreenshots") {
    group = "screenshots"
    description = "Captures and frames store screenshots on the emulator ANDROID_SERIAL names " +
        "(generateStoreScreenshots starts the emulators). The device class is detected from " +
        "the device's own screen."
    dependsOn(
        "cleanStoreScreenshotOutput",
        "prepareStoreScreenshotDevice",
        "connectedScreenshotsAndroidTest"
    )

    val rootDirectory = project.rootDir
    val rawDirectory = storeScreenshotOutput.get().asFile
    val sequence = storeScreenshotSequence
    val resolvedBash = bashExecutable
    val bashMissing = missingBash
    val onWindows = isWindows

    doLast {
        val bash = resolvedBash ?: throw GradleException(bashMissing)

        fun runCommand(vararg command: String): String {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException(
                    "Command failed (exit $exitCode): ${command.joinToString(" ")}\n$output"
                )
            }
            return output
        }

        fun parseSkinLayout(layoutFile: File): IntArray {
            val text = layoutFile.readText()
            val layoutsIndex = text.indexOf("layouts {")
            require(layoutsIndex >= 0) { "No 'layouts' block found in $layoutFile" }
            val layoutsSection = text.substring(layoutsIndex)

            val part2Body = Regex("part2\\s*\\{([^}]*)}").find(layoutsSection)!!.groupValues[1]
            val deviceX = Regex("x\\s+(\\d+)").find(part2Body)!!.groupValues[1].toInt()
            val deviceY = Regex("y\\s+(\\d+)").find(part2Body)!!.groupValues[1].toInt()

            return intArrayOf(deviceX, deviceY)
        }

        fun displaySize(layoutFile: File): Pair<Int, Int>? {
            val body = Regex("display\\s*\\{([^}]*)}").find(layoutFile.readText())
                ?.groupValues?.get(1) ?: return null
            val width = Regex("width\\s+(\\d+)").find(body)?.groupValues?.get(1)?.toInt()
            val height = Regex("height\\s+(\\d+)").find(body)?.groupValues?.get(1)?.toInt()
            return if (width == null || height == null) null else width to height
        }

        fun resolveSkin(sdkDir: String, avdName: String, width: Int, height: Int): File {
            val skinsRoot = File(sdkDir, "skins")
            val layouts = skinsRoot.listFiles().orEmpty().sortedBy { it.name }.flatMap { dir ->
                listOf(File(dir, "layout"), File(dir, "default/layout"))
            }.filter { it.isFile }
            val fitting = layouts.filter { displaySize(it) == width to height }
            check(fitting.isNotEmpty()) {
                "No emulator skin under $skinsRoot has a ${width}x$height display, so the " +
                    "screenshot cannot be framed. Install the skin for this device."
            }
            val avd = avdName.lowercase().replace(' ', '_')
            val chosen = fitting.firstOrNull { layout ->
                val name = generateSequence(layout.parentFile) { it.parentFile }
                    .first { it.parentFile?.name == "skins" }.name
                avd.startsWith(name)
            } ?: fitting.first()
            return chosen.parentFile
        }

        fun readSkinImage(skinDir: File, name: String): BufferedImage {
            val converted = File(rawDirectory.parentFile, "skin-${skinDir.name}-$name.png")
            runCommand(bash, "-lc", "magick '${File(skinDir, "$name.webp")}' '$converted'")
            return ImageIO.read(converted) ?: throw GradleException("Could not read $converted")
        }

        fun frameScreenshot(source: BufferedImage, sdkDir: String, avdName: String): BufferedImage {
            val skinDir = resolveSkin(sdkDir, avdName, source.width, source.height)
            logger.lifecycle("Framing ${source.width}x${source.height} with skin ${skinDir.name}")
            val layoutValues = parseSkinLayout(File(skinDir, "layout"))
            val deviceX = layoutValues[0]
            val deviceY = layoutValues[1]

            val back = readSkinImage(skinDir, "back")
            val mask = readSkinImage(skinDir, "mask")

            val maskedSource = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
            val sg = maskedSource.createGraphics()
            sg.drawImage(source, 0, 0, source.width, source.height, null)
            sg.composite = AlphaComposite.DstOut
            sg.drawImage(mask, 0, 0, source.width, source.height, null)
            sg.dispose()

            val canvas = BufferedImage(back.width, back.height, BufferedImage.TYPE_INT_ARGB)
            val g = canvas.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(back, 0, 0, null)
            g.drawImage(maskedSource, deviceX, deviceY, null)
            g.dispose()

            return canvas
        }

        val sdkDir = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: throw GradleException("Set ANDROID_HOME (or ANDROID_SDK_ROOT) to your Android SDK location")
        val adbName = if (onWindows) "adb.exe" else "adb"
        val adb = File(sdkDir, "platform-tools/$adbName").absolutePath

        val rawFiles = rawDirectory.walkTopDown()
            .filter { it.isFile && it.parentFile.name == "store-screenshots" }
            .toList()
        check(rawFiles.isNotEmpty()) {
            "No screenshots found under ${rawDirectory.relativeTo(rootDirectory)} - " +
                "did connectedScreenshotsAndroidTest run on one device?"
        }

        val deviceClassFile = rawFiles.firstOrNull { it.name == "device-class.txt" }
            ?: throw GradleException("Missing device-class.txt - did StoreScreenshotTest run?")
        val deviceType = deviceClassFile.readText().trim()
        val outputDirName = when (deviceType) {
            "phone" -> "phoneScreenshots"
            "fold" -> "sevenInchScreenshots"
            "tablet" -> "tenInchScreenshots"
            else -> throw GradleException(
                "No output folder configured for device class '$deviceType' yet"
            )
        }

        val outputDir = File(rootDirectory, "fastlane/metadata/android/en-US/images/$outputDirName")
        outputDir.mkdirs()

        val avdName = runCommand(adb, "shell", "getprop", "ro.boot.qemu.avd_name").trim()

        sequence.forEachIndexed { index, name ->
            val rawFile = rawFiles.firstOrNull { it.name.startsWith("%02d_".format(index)) }
                ?: throw GradleException("Missing captured screenshot for '$name' (index $index)")

            val framed = frameScreenshot(ImageIO.read(rawFile), sdkDir, avdName)
            val outputFile = File(outputDir, "%02d.png".format(index + 1))
            ImageIO.write(framed, "png", outputFile)
            logger.lifecycle("Wrote ${outputFile.relativeTo(rootDirectory)}")
        }
    }
}
