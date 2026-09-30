import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// ---------------------------------------------------------------------------
// google-services.json
// The real file is the "Love Stickers" Android app of the PIP Technologies
// Firebase project play-console-f33dd (Analytics, Crashlytics, Firestore,
// Storage). It is gitignored (local dev) and injected in CI from the
// GOOGLE_SERVICES_JSON secret. When neither exists, write a syntactically
// valid placeholder at configuration time so the google-services plugin task
// succeeds and CI stays green. The placeholder cannot reach Firebase.
// ---------------------------------------------------------------------------
val googleServicesJson = file("google-services.json")
if (!googleServicesJson.exists()) {
    println(
        "WARNING: using PLACEHOLDER google-services.json — Firebase will not connect. " +
            "Provide app/google-services.json or the GOOGLE_SERVICES_JSON secret in CI."
    )
    googleServicesJson.writeText(
        """{"project_info":{"project_number":"000000000000","project_id":"play-console-f33dd","storage_bucket":"play-console-f33dd.firebasestorage.app"},"client":[{"client_info":{"mobilesdk_app_id":"1:000000000000:android:0000000000000000000000","android_client_info":{"package_name":"com.piptechnologies.stickermaker"}},"oauth_client":[],"api_key":[{"current_key":"AIzaSyA-PLACEHOLDER-0000000000000000000"}],"services":{"appinvite_service":{"other_platform_oauth_client":[]}}}],"configuration_version":"1"}"""
    )
}

// Debug builds can target the local Firebase emulators instead of the real
// project: ./gradlew installDebug -PfirebaseEmulatorHost=10.0.2.2
// Those builds (local dev, the CI screen tour) also switch Analytics and
// Crashlytics collection off, so their sessions stay out of the production data.
val firebaseEmulatorHost = (findProperty("firebaseEmulatorHost") as String?).orEmpty()

// The crash-reporting SDK's project codes stay out of git, like its Maven credentials in
// settings.gradle.kts: local.properties, or the CRASH_REPORTING_* environment variables in CI.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun crashReportingCode(property: String, environment: String): String =
    localProperties.getProperty(property) ?: System.getenv(environment).orEmpty()

android {
    namespace = "com.piptechnologies.stickermaker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.piptechnologies.stickermaker"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
        // Firebase Analytics + Crashlytics collection (AndroidManifest.xml meta-data).
        manifestPlaceholders["telemetryEnabled"] = "true"
        // The crash-reporting SDK's project codes (LoveStickersApp), generated so they stay out of git.
        resValue("string", "crash_reporting_access_code", crashReportingCode("crashReporting.accessCode", "CRASH_REPORTING_ACCESS_CODE"))
        resValue("string", "crash_reporting_secret_code", crashReportingCode("crashReporting.secretCode", "CRASH_REPORTING_SECRET_CODE"))

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$firebaseEmulatorHost\"")
            manifestPlaceholders["telemetryEnabled"] = firebaseEmulatorHost.isEmpty().toString()
        }
        // Debug builds only; no signingConfigs, no release keystore.
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // The design system's sheets and top bars sit on experimental Material 3
        // APIs; opting in module-wide keeps call sites annotation-free.
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        // The screen tour imports the design's reference photo in the Create flow.
        getByName("androidTest").assets.srcDir(rootProject.file("design/assets"))
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX core
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.core.splashscreen)
    implementation(libs.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation + lifecycle
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Firebase (catalog + analytics + crash reporting + notifications)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)

    // Crash reporting: the primary crash/ANR layer and its branded crash screen
    // (res/layout/activity_crash_guard.xml). Chains into Crashlytics via propagate(true);
    // see LoveStickersApp. Pinned exactly (a dynamic version can swap in a build whose
    // crash-handler semantics differ): 1.1.9.4, the version the other PIP apps ship on this
    // same toolchain. Status Saver's 1.2.0.x requires compileSdk 36 (so AGP 8.9.1+) and
    // declares a kotlin-stdlib too new for Kotlin 2.0; move to it together with that upgrade.
    implementation(libs.crashguard)

    // Images, coroutines, prefs, ML Kit
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.datastore.preferences)
    implementation(libs.mlkit.segmentation.selfie)
    implementation(libs.exifinterface)

    // Unit tests (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // Instrumented screen tour (androidTest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.uiautomator)
    debugImplementation(libs.compose.ui.test.manifest)
}
