// Test double for WhatsApp's side of the sticker contract, installed only on the
// CI emulator by the screen tour (.github/workflows/screens.yml). Never shipped.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.piptechnologies.whatsappstub"
    compileSdk = 35

    defaultConfig {
        // The app under test looks WhatsApp up by this package name.
        applicationId = "com.whatsapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "stub"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
