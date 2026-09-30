package com.piptechnologies.stickermaker.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar
import com.piptechnologies.stickermaker.core.ui.inLayoutDirection

private val CardShape = RoundedCornerShape(16.dp)
private val TagShape = RoundedCornerShape(5.dp)

private val IntroStyle =
    TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 1.5.em)
private val NameStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.5.sp)
private val SubStyle =
    TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.5.sp, lineHeight = 1.45.em)
// A licence's name is Latin in every language: it stays left to right in its badge.
private val TagStyle = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.W600,
    fontSize = 9.sp,
    letterSpacing = 0.06.em,
    textDirection = TextDirection.Ltr
)

/** One open-source work the app is built with: what it is called, what it is here, and its licence. */
private class Licence(@StringRes val name: Int, @StringRes val sub: Int, @StringRes val tag: Int)

private val Licences = listOf(
    Licence(R.string.licences_fluent_name, R.string.licences_fluent_sub, R.string.licences_tag_mit),
    Licence(R.string.licences_fonts_name, R.string.licences_fonts_sub, R.string.licences_tag_ofl),
    Licence(R.string.licences_lucide_name, R.string.licences_lucide_sub, R.string.licences_tag_isc)
)

/**
 * Settings › Licences (spec §11): the open-source works Love Stickers is built with. A 52 dp back
 * header, a line of thanks, and one card with a row per work: its name, what it is in the app, and its
 * licence in a mono badge.
 */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            LoveTopBar(title = stringResource(R.string.settings_licences), onBack = onBack, height = 52.dp)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.licences_intro), style = IntroStyle, color = Ink2)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(Surface)
                        .border(1.dp, CardBorder, CardShape)
                ) {
                    Licences.forEachIndexed { index, licence ->
                        if (index > 0) HorizontalDivider(thickness = 1.dp, color = RowDivider)
                        LicenceRow(licence)
                    }
                }
            }
        }
    }
}

/** 14 × 15 dp of padding: the name over its line in a column, 13 dp from the licence badge. */
@Composable
private fun LicenceRow(licence: Licence) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Column(Modifier.weight(1f)) {
            // The names are Latin in every language; they still start where the screen's text starts.
            Text(stringResource(licence.name), style = NameStyle.inLayoutDirection(), color = Ink)
            Text(
                stringResource(licence.sub),
                style = SubStyle.inLayoutDirection(),
                color = Muted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            stringResource(licence.tag),
            style = TagStyle,
            color = Ink2,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clip(TagShape)
                .background(Subtle)
                .border(1.dp, Border, TagShape)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 844)
@Composable
private fun LicencesScreenPreview() {
    LoveStickersTheme {
        LicencesScreen(onBack = {})
    }
}
