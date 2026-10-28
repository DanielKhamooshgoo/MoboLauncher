plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.mobolauncher"
    compileSdk = 34

    defaultConfig {
        // Separate application ID so the GPT test build can coexist with the original.
        applicationId = "app.mobolauncher.gpt"
        // Broaden compatibility with older Android TV boxes.
        minSdk = 21
        targetSdk = 33
        versionCode = 2
        versionName = "0.1.1-gpt"
    }

    // Some older TV-box package installers handle legacy APK signatures more reliably.
    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release { isMinifyEnabled = false }
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
