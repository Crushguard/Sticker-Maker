package com.piptechnologies.stickermaker.feature.create.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.feature.create.EditorTool

private val ToolIdle = Color(0xFFC3C9D2)
private val ToolGap = 4.dp
/** Each button keeps 2 dp clear on both sides of its label. */
private val ToolInset = 2.dp
/**
 * Add, Draw and Animate while the decor data or the cut-out isn't ready, and the preset strip on a clip:
 * what can't be used now shows at 40%.
 */
internal const val DISABLED_ALPHA = 0.4f

/** One button of the bar; [layers] marks the tools that edit layers (Add, Draw, Animate). */
private class ToolSpec(val tool: EditorTool, @StringRes val label: Int, val icon: ImageVector, val layers: Boolean)

private val Tools = listOf(
    ToolSpec(EditorTool.Auto, R.string.create_tool_auto, LoveIcons.Wand2, layers = false),
    ToolSpec(EditorTool.Brush, R.string.create_tool_brush, LoveIcons.Brush, layers = false),
    ToolSpec(EditorTool.Erase, R.string.create_tool_erase, LoveIcons.Eraser, layers = false),
    ToolSpec(EditorTool.Add, R.string.create_tool_add, LoveIcons.SmilePlus, layers = true),
    ToolSpec(EditorTool.Draw, R.string.create_tool_draw, LoveIcons.PenLine, layers = true),
    ToolSpec(EditorTool.Animate, R.string.create_tool_animate, LoveIcons.CirclePlay, layers = true)
)

private fun labelStyle(sp: Float) = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = sp.sp)

/**
 * The dark tool bar (spec §8): Auto · Brush · Erase · Add · Draw · Animate, six equal buttons.
 * Labels are 10 sp on one line; when one doesn't fit its slot they all drop to 9.5 sp, and when one
 * still doesn't fit they hide, each button keeping its label for TalkBack. Add, Draw and Animate
 * dim and ignore taps until [layerToolsEnabled].
 */
@Composable
internal fun EditorToolbar(
    tool: EditorTool,
    layerToolsEnabled: Boolean,
    onTool: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = Tools.map { stringResource(it.label) }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink)
            .padding(8.dp)
    ) {
        val slotPx = with(LocalDensity.current) {
            ((maxWidth - ToolGap * (Tools.size - 1)) / Tools.size - ToolInset * 2).toPx()
        }
        val labelSp = remember(labels, slotPx, measurer) {
            toolLabelSp(slotPx) { sp ->
                labels.maxOf { measurer.measure(it, labelStyle(sp), maxLines = 1, softWrap = false).size.width }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ToolGap)) {
            Tools.forEachIndexed { i, spec ->
                ToolButton(
                    label = labels[i],
                    icon = spec.icon,
                    active = tool == spec.tool,
                    enabled = !spec.layers || layerToolsEnabled,
                    labelSp = labelSp,
                    modifier = Modifier.weight(1f)
                ) { onTool(spec.tool) }
            }
        }
    }
}

/** 54 dp, radius 11: Rose and white when [active], #C3C9D2 otherwise; no label when [labelSp] is 0. */
@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    enabled: Boolean,
    labelSp: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val fg = if (active) Color.White else ToolIdle
    val showLabel = labelSp > 0f
    Column(
        modifier = modifier
            .height(54.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) Rose else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = ToolInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = if (showLabel) null else label, modifier = Modifier.size(20.dp), tint = fg)
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(label, style = labelStyle(labelSp), color = fg, maxLines = 1, softWrap = false)
        }
    }
}
