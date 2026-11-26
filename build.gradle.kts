plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version "9.5.1" apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.aboutLibraries.android) apply false
    alias(libs.plugins.android.test) apply false
}
