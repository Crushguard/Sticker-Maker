pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// The crash-reporting SDK's project access/secret pair: the credentials of the SDK's Maven repository
// below and, through app/build.gradle.kts, the app's R.string.crash_reporting_* codes. It never enters
// git: local.properties holds it (crashReporting.accessCode, crashReporting.secretCode), and CI passes
// the CRASH_REPORTING_ACCESS_CODE and CRASH_REPORTING_SECRET_CODE secrets.
val localProperties = java.util.Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun crashReportingCode(property: String, environment: String): String =
    localProperties.getProperty(property) ?: System.getenv(environment).orEmpty()

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // The crash-reporting SDK is not on Maven Central.
        maven {
            url = uri("https://artifacts.crashguard.me/crashguard")
            credentials {
                username = crashReportingCode("crashReporting.accessCode", "CRASH_REPORTING_ACCESS_CODE")
                password = crashReportingCode("crashReporting.secretCode", "CRASH_REPORTING_SECRET_CODE")
            }
            content { includeGroup("me.crashguard") }
        }
    }
}

rootProject.name = "love-stickers"
include(":app")

// Test double for WhatsApp, used only by the on-device screen tour in CI.
include(":whatsapp-stub")
project(":whatsapp-stub").projectDir = file("testing/whatsapp-stub")
