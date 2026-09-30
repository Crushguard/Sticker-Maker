package com.piptechnologies.stickermaker.feature.language

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.LoveBottomSheet
import com.piptechnologies.stickermaker.core.telemetry.AppAnalytics
import java.util.Locale

/**
 * The language the app shows and letters stickers in: the per-app pick, else the phone's
 * language when the app has it, else English. Never [AppLanguages.SYSTEM].
 */
internal fun AppLanguages.effectiveTag(): String {
    val picked = selectedTag()
    if (picked != AppLanguages.SYSTEM) return picked
    return match(Locale.getDefault().toLanguageTag()).takeIf { it != AppLanguages.SYSTEM } ?: "en"
}

private val ChipInk = Color(0xFF3D4550)
private val ChipText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 13.sp)
private val SheetTitle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 15.sp)
private val SheetSub = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.sp)
private val CellText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.sp)
private val BadgeText = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W600, fontSize = 9.sp)
private val SkipText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 13.5.sp)

/** Lucide `globe` (its circle pre-flattened to arcs), built like every LoveIcons glyph. */
private val Globe: ImageVector by lazy {
    LoveIcons.lucideIcon(
        "globe",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
        "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
        "M2 12h20"
    )
}

/** 36 dp pill: globe + the current language's native name. Shown on the intro and both name steps. */
@Composable
fun LanguageChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // A language change recreates the activity, so this is read fresh then.
    val tag = remember { AppLanguages.effectiveTag() }
    val label = stringResource(R.string.settings_language)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(LoveShapes.Pill)
            .background(Surface)
            .border(1.dp, Border, LoveShapes.Pill)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Globe, contentDescription = label, modifier = Modifier.size(15.dp), tint = ChipInk)
        Text(AppLanguages.byTag(tag)?.nativeName ?: tag, style = ChipText, color = ChipInk, maxLines = 1)
    }
}

/**
 * The 40 dp top strip of the intro and both name steps: the language chip at the start and a
 * ghost Skip (36 tall, 12 padding, radius 10, 13.5/600 Ink2) at the end. The pair swaps sides in
 * right-to-left languages.
 */
@Composable
fun LanguageTopStrip(onLanguage: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val skip = stringResource(R.string.onboarding_skip)
    Row(
        modifier = modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanguageChip(onClick = onLanguage)
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = skip, onClick = onSkip)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(skip, style = SkipText, color = Ink2)
        }
    }
}

/**
 * Bottom sheet of the app's languages, each in its own script, two columns, the current one
 * rose. A tap switches the whole app (the activity recreates; ViewModels keep the flow).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(onDismiss: () -> Unit) {
    val current = remember { AppLanguages.effectiveTag() }
    // About 650 dp of languages: open whole, as the design shows, not half-expanded.
    LoveBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(R.string.settings_language),
            style = SheetTitle,
            color = Ink,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 2.dp)
        )
        Text(
            stringResource(R.string.namepack_language_sub),
            style = SheetSub,
            color = Muted,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(AppLanguages.entries, key = { it.tag }) { language ->
                LanguageCell(language, selected = language.tag == current) {
                    onDismiss()
                    if (language.tag != current) {
                        AppAnalytics.logLanguageChanged(language.tag)
                        AppLanguages.apply(language.tag)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageCell(language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val ink = if (selected) Rose else Ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(if (selected) RoseTint else Surface)
            .border(1.dp, if (selected) Rose else Border, shape)
            .clickable(role = Role.Button, onClickLabel = language.nativeName, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            language.nativeName,
            style = CellText,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (language.rtl) {
            Text(
                stringResource(R.string.language_rtl_badge),
                style = BadgeText,
                color = Muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Subtle)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        if (selected) Icon(LoveIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Rose)
    }
}
