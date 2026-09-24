package com.piptechnologies.stickermaker.feature.contact

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.showToast
import kotlinx.coroutines.launch

// Subject line for the composed mail (mail needs one; not designed). It stays
// in English: it is read by the team, like the device details under the body.
private const val CONTACT_MAIL_SUBJECT = "Love Stickers · Contact us"

// ---- Off-token colors from the prototype's contact screen ---- //
private val NoteBg = Color(0xFFF6F7F9)
private val NoteBorder = Color(0xFFEBEEF2)
private val FooterBorder = Color(0xFFEEF0F4)

private val IntroStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 21.sp
)
private val FieldStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 22.sp
)
private val EmailLabelStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.5.sp
)
private val NoteStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 18.sp
)
private val SendStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 16.sp
)

/**
 * Settings › Contact us: a message box, an optional reply email and the note
 * on what gets attached. Send composes a mail to the publisher over
 * ACTION_SENDTO (mailto:), appending the disclosed device block; with no mail
 * app around, a toast says so.
 */
@Composable
fun ContactScreen(
    onBack: () -> Unit,
    viewModel: ContactViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toastHost = remember { SnackbarHostState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        ContactContent(
            state = state,
            onBack = onBack,
            onMessageChange = viewModel::setMessage,
            onEmailChange = viewModel::setEmail,
            onSend = {
                if (state.canSend) {
                    val launched = composeMail(context, viewModel.mailBody())
                    if (launched) {
                        // Like the prototype, sending returns to Settings; the
                        // mail app is in front by then.
                        onBack()
                    } else {
                        scope.launch { toastHost.showToast(context.getString(R.string.toast_no_email_app)) }
                    }
                }
            },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
        )
        ToastHost(
            hostState = toastHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(bottom = 12.dp)
        )
    }
}

/** Launches the mail composer; false when no app handles mailto:. */
private fun composeMail(context: Context, body: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_SENDTO, mailtoUri(CONTACT_MAIL_SUBJECT, body))
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}

@Composable
private fun ContactContent(
    state: ContactUiState,
    onBack: () -> Unit,
    onMessageChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        LoveTopBar(title = stringResource(R.string.settings_contact), onBack = onBack, height = 52.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.contact_intro), style = IntroStyle, color = Ink2)

            // Message box.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = state.message,
                    onValueChange = onMessageChange,
                    textStyle = FieldStyle.copy(color = Ink),
                    cursorBrush = SolidColor(Rose),
                    minLines = 6,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (state.message.isEmpty()) {
                            Text(stringResource(R.string.contact_message_placeholder), style = FieldStyle, color = Muted)
                        }
                        innerTextField()
                    }
                )
            }

            // Optional reply email.
            Column {
                Text(
                    stringResource(R.string.contact_email_label),
                    style = EmailLabelStyle,
                    color = Ink2,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        textStyle = FieldStyle.copy(color = Ink),
                        cursorBrush = SolidColor(Rose),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            if (state.email.isEmpty()) {
                                Text(stringResource(R.string.contact_email_placeholder), style = FieldStyle, color = Muted)
                            }
                            innerTextField()
                        }
                    )
                }
            }

            // What gets attached.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NoteBg)
                    .border(1.dp, NoteBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    LoveIcons.Info,
                    null,
                    Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                    tint = Muted
                )
                Text(stringResource(R.string.contact_attach_note), style = NoteStyle, color = Ink2)
            }
        }

        // Pinned send bar.
        HorizontalDivider(thickness = 1.dp, color = FooterBorder)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .alpha(if (state.canSend) 1f else 0.45f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Rose)
                    .clickable(
                        enabled = state.canSend,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.contact_send),
                        onClick = onSend
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.contact_send), style = SendStyle, color = Color.White)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 844)
@Composable
private fun ContactScreenPreview() {
    LoveStickersTheme {
        Box(Modifier.background(Canvas)) {
            ContactContent(
                state = ContactUiState(),
                onBack = {},
                onMessageChange = {},
                onEmailChange = {},
                onSend = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 844)
@Composable
private fun ContactScreenFilledPreview() {
    LoveStickersTheme {
        Box(Modifier.background(Canvas)) {
            ContactContent(
                state = ContactUiState(
                    message = "The Good Morning pack shows only six stickers on my phone.",
                    email = "you@example.com"
                ),
                onBack = {},
                onMessageChange = {},
                onEmailChange = {},
                onSend = {}
            )
        }
    }
}
