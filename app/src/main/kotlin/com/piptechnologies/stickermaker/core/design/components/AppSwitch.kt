package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Rose

// Off-track grey from the switch spec; not part of the core palette.
private val TrackOff = Color(0xFFD8DCE3)

/**
 * The 48x28 switch from the design system, restyled to the tokens Material's
 * switch cannot match exactly: rose track when checked, 22dp white thumb sliding
 * 3dp inset, wrapped in a 44dp touch target with switch semantics.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackSize = DpSize(48.dp, 28.dp)
    val thumbSize = 22.dp
    val inset = 3.dp
    val track by animateColorAsState(if (checked) Rose else TrackOff, label = "switchTrack")
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackSize.width - thumbSize - inset else inset,
        label = "switchThumb"
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 44.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(trackSize.width, trackSize.height)
                .alpha(if (enabled) 1f else 0.45f)
                .clip(LoveShapes.Pill)
                .background(track)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .align(Alignment.CenterStart)
                    .size(thumbSize)
                    .shadow(2.dp, CircleShape, spotColor = Color(0x33000000))
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AppSwitchPreview() {
    LoveStickersTheme {
        Row(Modifier.padding(12.dp)) {
            AppSwitch(checked = true, onCheckedChange = {})
            AppSwitch(checked = false, onCheckedChange = {}, modifier = Modifier.padding(start = 14.dp))
            AppSwitch(
                checked = true,
                onCheckedChange = {},
                enabled = false,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
    }
}
