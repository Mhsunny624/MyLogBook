// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false // Match your Kotlin version
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}