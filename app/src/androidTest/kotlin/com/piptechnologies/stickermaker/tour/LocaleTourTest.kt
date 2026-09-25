package com.piptechnologies.stickermaker.tour

import android.content.res.Configuration
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.piptechnologies.stickermaker.MainActivity
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.feature.language.AppLanguage
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import java.util.Locale
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs after [ScreenTourTest]: switches the per-app language through every
 * shipped language and captures Home, a pack page, My Packs and Settings in
 * each, finding every control by its label in that language. A language that
 * fails is recorded and the pass moves on to the next one.
 */
@RunWith(AndroidJUnit4::class)
class LocaleTourTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyLanguage() {
        val failed = mutableListOf<String>()
        for (language in AppLanguages.entries) {
            try {
                capture(language)
            } catch (t: Throwable) {
                Tour.recordFailure("Language ${language.tag}", t)
                failed += language.tag
                // Start the next language from Home whatever state this one left.
                runCatching { returnHome(language.tag) }
            }
        }
        applyLocale(AppLanguages.SYSTEM)
        check(failed.isEmpty()) { "Languages that failed: $failed" }
    }

    private fun capture(language: AppLanguage) {
        val tag = language.tag
        Tour.dismissSystemDialogs()
        applyLocale(tag)
        awaitHome(tag)
        shot(language, "home", "Home")

        compose.reveal(hasClickLabel(PACK))
        compose.tap(hasClickLabel(PACK))
        compose.waitFor(hasClickLabel(text(tag, R.string.common_back)))
        compose.waitFor(
            addBar(text(tag, R.string.add_bar_idle)) or addBar(text(tag, R.string.add_bar_added)),
            60_000
        )
        shot(language, "pack", "Pack page")
        back()

        compose.tap(hasClickLabel(text(tag, R.string.nav_my_packs)))
        compose.waitFor(hasClickLabel(text(tag, R.string.my_packs_saved)))
        shot(language, "my-packs", "My Packs")

        compose.tap(hasClickLabel(text(tag, R.string.home_settings)))
        compose.waitFor(hasClickLabel(text(tag, R.string.settings_edit_themes)))
        shot(language, "settings", "Settings")
        back()

        compose.tap(hasClickLabel(text(tag, R.string.nav_home)))
        awaitHome(tag)
    }

    /** Home in [tag]: the search button and at least one pack card are up. */
    private fun awaitHome(tag: String) {
        compose.waitFor(hasClickLabel(text(tag, R.string.home_search)), 60_000)
        compose.waitFor(
            hasClickLabel(text(tag, R.string.pack_save)) or hasClickLabel(text(tag, R.string.pack_unsave)),
            90_000
        )
        compose.scrollListToTop(hasClickLabel(text(tag, R.string.home_search)))
    }

    private fun returnHome(tag: String) {
        repeat(3) {
            if (compose.exists(hasClickLabel(text(tag, R.string.home_search)))) return
            back()
        }
        compose.tap(hasClickLabel(text(tag, R.string.nav_home)))
    }

    /**
     * Sets the per-app locale ([AppLanguages.SYSTEM] clears it) and waits for
     * the recreated activity to run in it.
     */
    private fun applyLocale(tag: String) {
        compose.runOnUiThread { AppLanguages.apply(tag) }
        val wanted = if (tag == AppLanguages.SYSTEM) LocaleListCompat.getEmptyLocaleList().toLanguageTags() else tag
        check(AppCompatDelegate.getApplicationLocales().toLanguageTags() == wanted) {
            "per-app locale is ${AppCompatDelegate.getApplicationLocales().toLanguageTags()}, wanted $wanted"
        }
        if (tag == AppLanguages.SYSTEM) {
            compose.waitForIdle()
            return
        }
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (activityLocale() != tag) {
            check(SystemClock.uptimeMillis() < deadline) { "activity still in ${activityLocale()}, wanted $tag" }
            SystemClock.sleep(100)
        }
        compose.waitForIdle()
    }

    /** The shipped language the current activity's resources resolve to. */
    private fun activityLocale(): String? = runCatching {
        AppLanguages.match(compose.activity.resources.configuration.locales[0].toLanguageTag())
    }.getOrNull()

    /** The app's own string [id] in language [tag], as the screen under test renders it. */
    private fun text(tag: String, id: Int, vararg args: Any): String {
        val config = Configuration(Tour.targetContext.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return Tour.targetContext.createConfigurationContext(config).getString(id, *args)
    }

    private fun addBar(label: String): SemanticsMatcher = hasText(label) and hasClickAction()

    private fun shot(language: AppLanguage, screen: String, title: String) {
        compose.settle()
        val file = "lang-${language.tag}-$screen.png"
        Tour.screenshot(file, scale = 0.5f)
        Tour.recordLocale(language.tag, language.nativeName, screen, title, file)
    }

    private fun back() {
        Tour.device.pressBack()
        compose.waitForIdle()
    }

    companion object {
        /** A catalog pack every theme selection from the main tour shows on Home. */
        private const val PACK = "Clingy Mango"

        @BeforeClass
        @JvmStatic
        fun setUpDevice() {
            Tour.resume()
            ImageTracker.install(Tour.targetContext)
            Tour.enterDemoMode()
        }

        @AfterClass
        @JvmStatic
        fun tearDownDevice() {
            Tour.exitDemoMode()
        }
    }
}
