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
    // Barcode scanning: bridge scanBarcode() + QR onboarding (Phase 1).
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // App lock + bridge biometricConfirm() (Phase 1).
    implementation("androidx.biometric:biometric:1.1.0")
    // Push (Phase 2) — inert without google-services.json, see below.
    implementation("com.google.firebase:firebase-messaging:24.0.0")

    testImplementation("junit:junit:4.13.2")
    // The android.jar of unit tests only stubs org.json (every call throws);
    // the real implementation on the test classpath makes the QR payload
    // parser testable without an emulator.
    testImplementation("org.json:json:20240303")
}

// Push is opt-in: drop a google-services.json (Firebase console) next to
// this file to activate FCM. Without it the shell builds and runs, only
// push stays inactive — keeps SAP-/Google-account-free builds working.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
