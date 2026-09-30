package com.piptechnologies.stickermaker.debug

import android.app.Activity
import android.os.Bundle

/**
 * Debug builds only: crashes on start, so the crash screen (and its hand-off to
 * Crashlytics) can be checked on a device. Crash reporting shows its screen for crashes thrown from an
 * Activity; `am crash` injects the exception from the system and does not count as one. Only a
 * caller holding DUMP, i.e. adb, can start it:
 *
 *     adb shell am start -n com.piptechnologies.stickermaker/.debug.CrashTestActivity
 */
class CrashTestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        throw IllegalStateException("Test crash requested over adb (CrashTestActivity)")
    }
}
