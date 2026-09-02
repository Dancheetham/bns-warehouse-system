plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uk.co.bns.warehouse.kiosk"
    compileSdk = 34

    defaultConfig {
        applicationId = "uk.co.bns.warehouse.kiosk"
        // 26 (Android 8.0) specifically because the launcher icon here is an
        // adaptive icon (vector-based, no bitmap assets needed to build) -
        // those only work from API 26 onwards. Any warehouse scanner bought
        // in the last several years will clear this comfortably.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
