pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "love-stickers"
include(":app")

// Test double for WhatsApp, used only by the on-device screen tour in CI.
include(":whatsapp-stub")
project(":whatsapp-stub").projectDir = file("testing/whatsapp-stub")
