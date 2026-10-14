// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.22" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.7" apply false
}

extra["compileSdk"] = 34
extra["minSdk"] = 26
extra["targetSdk"] = 34
