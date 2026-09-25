package com.piptechnologies.stickermaker.feature.language

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar

// ---- Off-token colors from the prototype's language list ---- //
private val CardBorder = Color(0xFFEEF0F4)
private val RowDivider = Color(0xFFF2F4F7)
private val SelectedRowBg = Color(0xFFFFF7F8)
private val SecondaryName = Color(0xFF99A0AC)

private val NoteStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 13.5.sp, lineHeight = 20.sp
)
private val NativeNameStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.5.sp
)
private val EnglishNameStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 11.5.sp
)
private val BadgeStyle = TextStyle(
    fontFamily = Mono, fontWeight = FontWeight.W600, fontSize = 9.sp
)

/**
 * Settings › Language: the design's list of native names over their names in
 * the app language, an RTL badge in mono, and a rose check on the current
 * row. Tapping a row applies it as the per-app locale and returns.
 */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    // A language change recreates the activity, so this is read fresh then.
    val selectedTag = remember { AppLanguages.selectedTag() }

    LanguageContent(
        selectedTag = selectedTag,
        onBack = onBack,
        onSelect = { tag ->
            // Like the prototype, picking a language returns to Settings.
            // setApplicationLocales may recreate the activity right after;
            // the nav stack is saved state, so Settings is what comes back.
            AppLanguages.apply(tag)
            onBack()
        }
    )
}

/** One row: the system entry or a shipped language. */
private data class LanguageRowUi(
    val tag: String,
    val title: String,
    val subtitle: String,
    val rtl: Boolean
)

@Composable
private fun LanguageContent(
    selectedTag: String,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        LoveTopBar(title = stringResource(R.string.settings_language), onBack = onBack, height = 52.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Text(
                stringResource(R.string.language_note),
                style = NoteStyle,
                color = Muted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            ) {
                val rows = listOf(
                    LanguageRowUi(
                        tag = AppLanguages.SYSTEM,
                        title = stringResource(R.string.language_system),
                        subtitle = stringResource(R.string.language_system_sub),
                        rtl = false
                    )
                ) + AppLanguages.entries.map { language ->
                    LanguageRowUi(
                        tag = language.tag,
                        title = language.nativeName,
                        subtitle = stringResource(language.nameRes),
                        rtl = language.rtl
                    )
                }
                rows.forEachIndexed { index, row ->
                    LanguageRow(
                        row = row,
                        selected = row.tag == selectedTag,
                        onClick = { onSelect(row.tag) }
                    )
                    if (index != rows.lastIndex) {
                        HorizontalDivider(thickness = 1.dp, color = RowDivider)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    row: LanguageRowUi,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = row.title, onClick = onClick)
            .background(if (selected) SelectedRowBg else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, style = NativeNameStyle, color = Ink)
            Text(
                row.subtitle,
                style = EnglishNameStyle,
                color = SecondaryName,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        if (row.rtl) {
            Text(
                stringResource(R.string.language_rtl_badge),
                style = BadgeStyle,
                color = Muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Subtle)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        if (selected) {
            Icon(LoveIcons.Check, null, Modifier.size(19.dp), tint = Rose)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 844)
@Composable
private fun LanguageScreenPreview() {
    LoveStickersTheme {
        LanguageContent(
            selectedTag = "en",
            onBack = {},
            onSelect = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun LanguageScreenSystemPreview() {
    LoveStickersTheme {
        LanguageContent(
            selectedTag = AppLanguages.SYSTEM,
            onBack = {},
            onSelect = {}
        )
    }
}
