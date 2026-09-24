plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// ---------------------------------------------------------------------------
// google-services.json
// The real file is gitignored (local dev) and injected in CI from the
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
        """{"project_info":{"project_number":"000000000000","project_id":"play-console-f33dd","storage_bucket":"play-console-f33dd.appspot.com"},"client":[{"client_info":{"mobilesdk_app_id":"1:000000000000:android:0000000000000000000000","android_client_info":{"package_name":"com.piptechnologies.stickermaker"}},"oauth_client":[],"api_key":[{"current_key":"AIzaSyA-PLACEHOLDER-0000000000000000000"}],"services":{"appinvite_service":{"other_platform_oauth_client":[]}}}],"configuration_version":"1"}"""
    )
}

// Debug builds can target the local Firebase emulators instead of the real
// project: ./gradlew installDebug -PfirebaseEmulatorHost=10.0.2.2
val firebaseEmulatorHost = (findProperty("firebaseEmulatorHost") as String?).orEmpty()

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

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$firebaseEmulatorHost\"")
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

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

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
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.uiautomator)
    debugImplementation(libs.compose.ui.test.manifest)
}
