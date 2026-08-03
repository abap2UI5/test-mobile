plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    // Applied conditionally in app/build.gradle.kts — only when a
    // google-services.json is present (push is optional, Phase 2).
    id("com.google.gms.google-services") version "4.4.2" apply false
}
