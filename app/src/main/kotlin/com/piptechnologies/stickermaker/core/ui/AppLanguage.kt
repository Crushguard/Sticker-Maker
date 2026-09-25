package com.piptechnologies.stickermaker.core.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/**
 * [this] context in the language picked on the Language screen. API 33+
 * already localizes the application context; below that AppCompat only
 * localizes activities, so code holding the application context resolves
 * text it keeps (an exported pack's publisher line) through this.
 */
fun Context.inAppLanguage(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
    return createConfigurationContext(config)
}
