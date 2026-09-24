package com.piptechnologies.stickermaker.feature.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar
import com.piptechnologies.stickermaker.core.design.components.ThemeTile
import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.ui.displayName

// Copy lives in res/values/strings.xml (customize_*, theme_*).


private val TitleInk = Color(0xFF171A20)
private val SubtitleInk = Color(0xFF626873)
private val FooterHairline = Color(0xFFEEF0F4)

private val TitleText = TextStyle(
    fontFamily = Hanken,
    fontWeight = FontWeight.W800,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.02).em
)
private val SubtitleText = TextStyle(
    fontFamily = Hanken,
    fontWeight = FontWeight.W400,
    fontSize = 14.sp,
    lineHeight = 21.sp
)
private val CtaText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 16.sp)

/** Maps the catalog's Lucide icon names onto the vendored [LoveIcons] set. */
internal fun themeIcon(name: String): ImageVector = when (name) {
    "heart-handshake" -> LoveIcons.HeartHandshake
    "rabbit" -> LoveIcons.Rabbit
    "laugh" -> LoveIcons.Laugh
    "sparkles" -> LoveIcons.Sparkles
    "flower-2" -> LoveIcons.Flower2
    "message-circle-heart" -> LoveIcons.MessageCircleHeart
    "moon" -> LoveIcons.Moon
    "plane" -> LoveIcons.Plane
    else -> LoveIcons.Heart
}

/**
 * Theme picker (Prototype `is.customize`). First run: display title, no top
 * bar, footer reads "Continue · N themes" and is disabled at zero. Edit mode
 * (Settings > Edit themes): 52dp back header titled "Your themes", tiles
 * arrive pre-checked from prefs, footer reads "Save".
 *
 * The selection persists through [CustomizationViewModel.onSave]; [onDone]
 * fires only after the write lands.
 */
@Composable
fun CustomizationScreen(
    isEdit: Boolean = false,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: CustomizationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val latestOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.saved) {
        if (state.saved) latestOnDone()
    }
    CustomizationContent(
        isEdit = isEdit,
        categories = state.categories,
        selected = state.selected,
        onToggleTheme = viewModel::onToggleTheme,
        onSave = viewModel::onSave,
        onBack = onBack
    )
}

/** Stateless picker layout, previewable without Hilt. */
@Composable
fun CustomizationContent(
    isEdit: Boolean,
    categories: List<Category>,
    selected: Set<String>,
    onToggleTheme: (String) -> Unit,
    onSave: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val count = selected.size
    val enabled = count > 0
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
    ) {
        if (isEdit) {
            LoveTopBar(title = stringResource(R.string.customize_edit_title), onBack = onBack, height = 52.dp)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    if (!isEdit) {
                        Text(
                            stringResource(R.string.customize_title),
                            style = TitleText,
                            color = TitleInk,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.customize_subtitle),
                        style = SubtitleText,
                        color = SubtitleInk,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                }
            }
            items(categories, key = { it.id }) { category ->
                ThemeTile(
                    label = category.displayName(),
                    icon = themeIcon(category.icon),
                    hue = category.hue,
                    selected = category.id in selected,
                    onToggle = { onToggleTheme(category.id) }
                )
            }
        }
        // Footer: white bar over a 1px hairline, primary CTA reads the count.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .navigationBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(FooterHairline))
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 22.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .alpha(if (enabled) 1f else 0.45f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Rose)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onSave),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isEdit -> stringResource(R.string.customize_save)
                        enabled -> pluralStringResource(R.plurals.customize_continue, count, count)
                        else -> stringResource(R.string.customize_pick_one)
                    },
                    style = CtaText,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CustomizationFirstRunPreview() {
    LoveStickersTheme {
        CustomizationContent(
            isEdit = false,
            categories = FallbackCategories,
            selected = setOf("couples", "cute", "funny"),
            onToggleTheme = {},
            onSave = {},
            onBack = null
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CustomizationEmptyPreview() {
    LoveStickersTheme {
        CustomizationContent(
            isEdit = false,
            categories = FallbackCategories,
            selected = emptySet(),
            onToggleTheme = {},
            onSave = {},
            onBack = null
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CustomizationEditPreview() {
    LoveStickersTheme {
        CustomizationContent(
            isEdit = true,
            categories = FallbackCategories,
            selected = setOf("couples", "cute", "romantic", "flirty", "goodnight", "distance"),
            onToggleTheme = {},
            onSave = {},
            onBack = {}
        )
    }
}
