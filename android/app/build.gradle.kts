plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.abap2ui5.mobileshell"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.abap2ui5.mobileshell"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
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
    implementation("com.google.android.material:material:1.12.0")
    // Barcode scanning for the Phase-0 bridge demo. Phase 1 swaps the plain
    // URL entry for the SAP BTP SDK onboarding flow (SAP repos required).
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
