package com.piptechnologies.stickermaker.feature.create

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Surface

/** Footer top border grey (#EEF0F4 in the design; between Subtle and Border). */
internal val FooterDivider = Color(0xFFEEF0F4)

/** Footnote grey the helper lines under footers use. */
internal val FootnoteGrey = Color(0xFF99A0AC)

internal val MonoCounterText = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W500, fontSize = 12.sp)

/**
 * Resolves the one [CreatePackViewModel] of the Create session. The prototype
 * keeps a single `create` object across Import → Cut out → Pack details, so
 * the view model is scoped to the activity: all three screens share it, and
 * it survives pushes and pops between them. Screens accept it as a default
 * parameter, so a host that wires a nav-graph scope later can pass its own.
 */
@Composable
fun createPackViewModel(): CreatePackViewModel {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    return hiltViewModel(viewModelStoreOwner = activity)
}

private tailrec fun Context.findComponentActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> error("Create flow needs a ComponentActivity host")
}

/**
 * The design's 52/14 filled-rose primary. Disabled reads at 45% opacity and
 * swallows taps, like the prototype's `opacity:.45;cursor:default` buttons.
 */
@Composable
internal fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(LoveShapes.Medium)
            .background(Rose)
            .clickable(role = Role.Button, onClickLabel = label, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 16.sp),
            color = Color.White
        )
    }
}

/**
 * Pinned white footer with the 1dp top divider every Create step ends with.
 */
@Composable
internal fun CreateFooter(
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface)
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FooterDivider)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = bottomPadding)
        ) {
            content()
        }
    }
}

/** The centred 11.5 footnote line under a footer primary. */
@Composable
internal fun FooterHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 11.5.sp),
        color = FootnoteGrey,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    )
}
