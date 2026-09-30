package com.piptechnologies.stickermaker.feature.namepack

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Amber
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Muted2
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.CategoryChip
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.NameInput
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation

internal val TitleInk = Color(0xFF171A20)
/** Name-field border on error (emoji, missing name): the design's one new colour. */
internal val ErrorLine = Color(0xFFE0B778)
internal val FootnoteGrey = Color(0xFF99A0AC)
internal val TileBg = Color(0xFFF6F7F9)
internal val FooterLine = Color(0xFFEEF0F4)

internal val TitleStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W800, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em
)
internal val HelperStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 21.sp)
internal val LabelStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.5.sp)
internal val HintStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 11.5.sp)
private val NoteStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.5.sp, lineHeight = 17.5.sp)
private val FieldText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 16.sp, color = Ink)
private val CounterText = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 11.sp)
private val TileLabel = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 11.sp)

/** While [blocked], consumes every touch before the content under it sees one, so nothing there clicks. */
internal fun Modifier.blockTaps(blocked: Boolean): Modifier =
    if (!blocked) {
        this
    } else {
        this.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }

/** Two 4 segments 6 apart, [filled] of them rose; 6 above, 16 below. */
@Composable
internal fun StepBar(filled: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(2) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) Rose else Border)
            )
        }
    }
}

/**
 * 52/r12 field: user icon 17, input 16/400, mono n/14 counter; rose border focused, amber on
 * error. The keyboard's action key ([imeAction]) runs [onSubmit], like the step's button.
 * [focusOnStart] focuses it once, as it appears.
 */
@Composable
internal fun NameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    count: Int,
    error: Boolean,
    imeAction: ImeAction,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    focusOnStart: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusOnStart) focusRequester.requestFocus() }
    val shape = RoundedCornerShape(12.dp)
    val line = when {
        error -> ErrorLine
        focused -> Rose
        else -> Border
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Surface)
            .border(1.dp, line, shape)
            // The whole 52 dp row focuses the field, not only its one text line.
            .pointerInput(Unit) { detectTapGestures { focusRequester.requestFocus() } }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(NamePackIcons.UserRound, contentDescription = null, modifier = Modifier.size(17.dp), tint = Muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = FieldText,
            cursorBrush = SolidColor(Rose),
            // Names are lettered as typed: the keyboard must not "correct" them.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onNext = { onSubmit() }, onDone = { onSubmit() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = FieldText, color = Muted)
                    inner()
                }
            }
        )
        Text(
            stringResource(R.string.create_name_counter, count, NameInput.MAX_GRAPHEMES),
            style = CounterText,
            color = if (count >= NameInput.MAX_GRAPHEMES) Amber else Muted2
        )
    }
}

/** The note under a field: limit in Muted, emoji and missing name in Amber; 8 above. Screen readers announce it. */
@Composable
internal fun NameNoteLine(note: NameNote) {
    val (res, color) = when (note) {
        NameNote.NONE -> return
        NameNote.EMOJI -> R.string.namepack_note_emoji to Amber
        NameNote.NEED_NAME -> R.string.namepack_note_need_name to Amber
        NameNote.LIMIT -> R.string.namepack_note_limit to Muted
    }
    Text(
        stringResource(res),
        style = NoteStyle,
        color = color,
        modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** "Who says it?": four equal tiles, art [artSize] (46 on your-name, 40 on the reveal). */
@Composable
internal fun CharacterRow(
    selected: Character,
    onPick: (Character) -> Unit,
    tileArt: Map<Character, ImageBitmap>,
    artSize: Dp,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Character.entries.forEach { character ->
            CharacterTile(character, character == selected, tileArt[character], artSize, { onPick(character) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CharacterTile(
    character: Character,
    selected: Boolean,
    art: ImageBitmap?,
    artSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val bg by animateColorAsState(if (selected) RoseTint else Surface, tween(120), label = "tileBg")
    val line by animateColorAsState(if (selected) Rose else Border, tween(120), label = "tileLine")
    Column(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, line, shape)
            .clickable(role = Role.Button, onClickLabel = character.label, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(artSize)) {
            if (art != null) Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Text(character.label, style = TileLabel, color = if (selected) Rose else Ink2, maxLines = 1)
    }
}

/** The eight relation chips (36, pad 15, 13/600), wrapping, 8 apart, 14 above; one always selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RelationChips(selected: Relation, onPick: (Relation) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Relation.entries.forEach { relation ->
            CategoryChip(
                label = stringResource(relation.labelRes()),
                selected = relation == selected,
                onClick = { onPick(relation) },
                modifier = Modifier.semantics { this.selected = relation == selected }
            )
        }
    }
}

internal fun Relation.labelRes(): Int = when (this) {
    Relation.GIRLFRIEND -> R.string.namepack_rel_girlfriend
    Relation.BOYFRIEND -> R.string.namepack_rel_boyfriend
    Relation.WIFE -> R.string.namepack_rel_wife
    Relation.HUSBAND -> R.string.namepack_rel_husband
    Relation.CRUSH -> R.string.namepack_rel_crush
    Relation.PARTNER -> R.string.namepack_rel_partner
    Relation.MOM -> R.string.namepack_rel_mom
    Relation.FRIEND -> R.string.namepack_rel_friend
}

/**
 * The live preview: 210 at most, shrinking to the room the keyboard leaves (hidden below 72).
 * It swaps instantly on each keystroke, as the prototype does: art and lettering are one bitmap,
 * so a crossfade would dim the character on every letter typed.
 */
@Composable
internal fun PreviewArea(image: ImageBitmap?, description: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val side = minOf(210.dp, maxHeight, maxWidth)
        if (image != null && side >= 72.dp) {
            Image(image, contentDescription = description, modifier = Modifier.size(side))
        }
    }
}
