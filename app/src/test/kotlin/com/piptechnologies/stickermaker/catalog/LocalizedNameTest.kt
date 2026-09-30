package com.piptechnologies.stickermaker.catalog

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.ui.asString
import com.piptechnologies.stickermaker.core.ui.nameText
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LocalizedNameTest {

    private fun context(tag: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(config)
    }

    private val missYou = Category(
        "missyou", "Miss you", "hourglass", 200, 3,
        names = mapOf("en" to "Miss you", "pt" to "Saudades", "pt-BR" to "Saudade", "he" to "געגועים"),
    )

    @Test
    fun theAppLanguagePicksTheCategoryNameFromTheCatalog() {
        assertEquals("Saudade", missYou.nameText().asString(context("pt-BR")))
        assertEquals("Saudades", missYou.nameText().asString(context("pt-PT")))
        assertEquals("געגועים", missYou.nameText().asString(context("he")))
        assertEquals("Miss you", missYou.nameText().asString(context("en")))
    }

    @Test
    fun aLanguageTheCatalogLacksFallsBackToTheAppStringsThenEnglish() {
        val couples = Category("couples", "Couples", "heart-handshake", 10, 1, names = mapOf("en" to "Couples"))
        assertEquals("Couples", couples.nameText().asString(context("xx")))
        assertEquals("Miss you", missYou.nameText().asString(context("ja")))
    }
}
