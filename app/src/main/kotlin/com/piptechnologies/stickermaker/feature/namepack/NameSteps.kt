package com.piptechnologies.stickermaker.feature.namepack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.feature.create.PrimaryButton
import com.piptechnologies.stickermaker.feature.language.LanguageTopStrip
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation

/** Column shared by both steps: Canvas background, 4/24/22 padding plus system bars and keyboard. */
private fun Modifier.stepFrame(): Modifier = this
    .fillMaxSize()
    .background(Canvas)
    .statusBarsPadding()
    .navigationBarsPadding()
    .imePadding()
    .padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 22.dp)

/**
 * Everything between the top strip and the hint: at least as tall as the room it has, so its
 * weighted preview fills what is left, and scrolling once large text or the keyboard leaves too
 * little room, so the hint and button below stay on screen.
 */
@Composable
private fun ColumnScope.StepBody(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            content = content
        )
    }
}

/**
 * Whether an arriving step's field takes focus, decided once: when the keyboard is up (Next on
 * the step before) and [fieldText] is empty. A filled field is left alone, as a String field
 * focused from code puts the caret before the first letter. A new window (a language switch, a
 * restore) reports the keyboard visible until its first insets arrive, so its height must be
 * above 0 too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun rememberFocusOnArrival(fieldText: String): Boolean {
    val imeVisible = WindowInsets.isImeVisible
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    return remember { imeVisible && ime.getBottom(density) > 0 && fieldText.isEmpty() }
}

/** Step 1/2 (screens spec §c): who says it, your optional name, the name_only preview. */
@Composable
internal fun NameYouStep(
    state: NamePackUiState,
    onLanguage: () -> Unit,
    onSkip: () -> Unit,
    onType: (String) -> Unit,
    onCharacter: (Character) -> Unit,
    onContinue: () -> Unit
) {
    val flow = state.flow
    val focusOnStart = rememberFocusOnArrival(flow.you.raw)
    Column(Modifier.stepFrame()) {
        LanguageTopStrip(onLanguage = onLanguage, onSkip = onSkip)
        StepBody {
            StepBar(filled = 1)
            Text(stringResource(R.string.namepack_you_title), style = TitleStyle, color = TitleInk)
            Text(
                stringResource(R.string.namepack_you_helper),
                style = HelperStyle,
                color = Ink2,
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
            )
            Text(stringResource(R.string.namepack_pick_character), style = LabelStyle, color = Ink2, modifier = Modifier.padding(bottom = 8.dp))
            CharacterRow(flow.character, onCharacter, state.tileArt, artSize = 46.dp, modifier = Modifier.padding(bottom = 14.dp))
            NameTextField(
                value = flow.you.raw,
                onValueChange = onType,
                placeholder = stringResource(R.string.namepack_you_placeholder),
                count = flow.you.count,
                error = flow.youNote == NameNote.EMOJI,
                imeAction = ImeAction.Next,
                onSubmit = onContinue,
                focusOnStart = focusOnStart
            )
            NameNoteLine(flow.youNote)
            PreviewArea(
                image = state.preview,
                description = stringResource(R.string.namepack_preview_alt, state.previewText),
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            stringResource(R.string.namepack_preview_hint),
            style = HintStyle,
            color = FootnoteGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        PrimaryButton(
            label = stringResource(R.string.namepack_continue),
            onClick = onContinue,
            enabled = flow.canContinue
        )
    }
}

/** Step 2/2 (screens spec §d): their name, relation, the love_you preview, "Make our stickers". */
@Composable
internal fun NameLoveStep(
    state: NamePackUiState,
    onLanguage: () -> Unit,
    onSkip: () -> Unit,
    onType: (String) -> Unit,
    onRelation: (Relation) -> Unit,
    onMake: () -> Unit
) {
    val flow = state.flow
    val focusOnStart = rememberFocusOnArrival(flow.love.raw)
    Column(Modifier.stepFrame()) {
        LanguageTopStrip(onLanguage = onLanguage, onSkip = onSkip)
        StepBody {
            StepBar(filled = 2)
            Text(stringResource(R.string.namepack_love_title), style = TitleStyle, color = TitleInk)
            Text(
                stringResource(R.string.namepack_love_helper),
                style = HelperStyle,
                color = Ink2,
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
            )
            NameTextField(
                value = flow.love.raw,
                onValueChange = onType,
                placeholder = stringResource(R.string.namepack_love_placeholder),
                count = flow.love.count,
                error = flow.loveNote == NameNote.EMOJI || flow.loveNote == NameNote.NEED_NAME,
                imeAction = ImeAction.Done,
                onSubmit = onMake,
                focusOnStart = focusOnStart
            )
            NameNoteLine(flow.loveNote)
            RelationChips(flow.relation, onRelation)
            PreviewArea(
                image = state.preview,
                description = stringResource(R.string.namepack_preview_alt, state.previewText),
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            stringResource(R.string.namepack_preview_hint),
            style = HintStyle,
            color = FootnoteGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        // Looks disabled until the name is valid, but an empty-name tap still explains why.
        PrimaryButton(
            label = stringResource(R.string.namepack_make),
            onClick = onMake,
            enabled = !flow.love.hasEmoji,
            dimmed = !flow.canMake
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 844)
@Composable
private fun NameYouStepPreview() {
    LoveStickersTheme {
        NameYouStep(NamePackUiState(flow = NamePackState(you = NameField("Aymen"))), {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 844)
@Composable
private fun NameLoveStepErrorPreview() {
    LoveStickersTheme {
        NameLoveStep(
            NamePackUiState(flow = NamePackState(step = NameStep.LOVE, needNameError = true)),
            {}, {}, {}, {}, {}
        )
    }
}
