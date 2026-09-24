package com.piptechnologies.stickermaker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LoveStickersApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // The design is light-only; never follow the system dark setting.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
