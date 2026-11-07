plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.mobolauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.mobolauncher"
        minSdk = 21
        targetSdk = 33
        versionCode = 5
        versionName = "0.5.0"
    }

    // Same debug key on every build (the CI runner would otherwise create a new one each time),
    // signed with both the legacy (v1) and the modern (v2) scheme for maximum installer support.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release { isMinifyEnabled = false }
    }

    // "std": the plain app. "pwr": same app plus an optional Accessibility helper that can open
    // Android's power menu (separate build so "std" stays as simple as possible to install).
    flavorDimensions += "mode"
    productFlavors {
        create("std") { dimension = "mode" }
        create("pwr") {
            dimension = "mode"
            applicationIdSuffix = ".pwr"
            versionNameSuffix = "-pwr"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // Compose compiler 1.5.14 pairs with Kotlin 1.9.24
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
