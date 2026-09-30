package com.piptechnologies.stickermaker

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.notifications.PushNotifications
import com.piptechnologies.stickermaker.core.telemetry.AppAnalytics
import com.piptechnologies.stickermaker.core.ui.inAppLanguage
import crashguard.android.library.CrashGuard
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class LoveStickersApp : Application() {

    @Inject
    lateinit var prefs: PrefsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Crash reporting first, before anything else can throw, so it owns the uncaught-exception
        // handler and handles every crash itself. Crashlytics only gets crashes best-effort
        // through propagate(true); see initCrashReporting.
        initCrashReporting()
        AppAnalytics.init(this)
        // The design is light-only; never follow the system dark setting.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        initNotifications()
    }

    /**
     * The notifications channel, and FCM registered only while Settings' switch is on and Android
     * allows notifications (a user may have changed either while the app was closed).
     */
    private fun initNotifications() {
        PushNotifications.createChannel(this)
        appScope.launch {
            PushNotifications.setPushEnabled(prefs.alertsEnabled.first() && PushNotifications.systemAllows(this@LoveStickersApp))
        }
    }

    /**
     * Installs the crash-reporting SDK, the app's primary crash/ANR layer, mirroring the Status Saver
     * integration. Its project codes come from local.properties (see settings.gradle.kts), never git.
     *
     * Ordering is load-bearing in two places:
     *  · `initialize` must run from `Application.onCreate`; the SDK verifies that itself and
     *    refuses to arm otherwise.
     *  · `propagate(true)` hands the fatal on to the previously installed handler, which is
     *    Firebase Crashlytics (installed from its ContentProvider before Application.onCreate).
     *    It is best-effort only: the SDK runs the Crashlytics handler on its own thread and
     *    tears the process down a few ms later, so most main-thread crashes land in the SDK's
     *    dashboard rather than Crashlytics, while the non-fatals the app records explicitly
     *    (core/telemetry/Telemetry.kt) stay reliable. Keep it true: false would cut Crashlytics
     *    out of the chain entirely.
     *
     * The crash screen's look comes from res/layout/activity_crash_guard.xml and the
     * Theme.CrashGuardActivity override; its texts from [localizeCrashScreen].
     */
    private fun initCrashReporting() {
        CrashGuard.initialize(
            this,
            CrashGuard.Project(
                getString(R.string.crash_reporting_access_code),
                getString(R.string.crash_reporting_secret_code),
            ),
        )
        val crashGuard = CrashGuard.getInstance(applicationContext)
        crashGuard.start()
        crashGuard.propagate(true)
        localizeCrashScreen(inAppLanguage())
    }

    /**
     * Configures the crash screen with its texts in [words]' language. The SDK hands these
     * strings to its screen as they are, so they are resolved again whenever the language can
     * have changed: at process start, and from MainActivity.onCreate, since picking a language
     * on the Language screen recreates the activity but not the process.
     */
    fun localizeCrashScreen(words: Context) {
        CrashGuard.getInstance(applicationContext).setConfiguration(
            CrashGuard.Configuration.Builder()
                .setImageResourceId(R.drawable.ic_crash_error)
                .setBackgroundColorResourceId(R.color.crash_bg)
                .setTitle(words.getString(R.string.crash_title))
                .setTitleColorResourceId(R.color.crash_title)
                .setMessage(words.getString(R.string.crash_message))
                .setMessageColorResourceId(R.color.crash_body)
                .build(),
        )
    }
}
