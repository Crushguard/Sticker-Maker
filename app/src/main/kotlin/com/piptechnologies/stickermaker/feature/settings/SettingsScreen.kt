package com.piptechnologies.stickermaker.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.piptechnologies.stickermaker.BuildConfig
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.AppSwitch
import com.piptechnologies.stickermaker.core.design.components.ConfirmSheet
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.feature.contact.deviceInfoBlock
import com.piptechnologies.stickermaker.feature.contact.mailtoUri
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import kotlinx.coroutines.launch

/** PLACEHOLDER URL — swap for the real published policy before release. */
const val PRIVACY_POLICY_URL = "https://piptechnologies.example/privacy"

// Subject line for the low-star feedback mail (mail needs one; not designed).
// It stays in English: the team reads it, like the device details below it.
private const val FEEDBACK_MAIL_SUBJECT = "Love Stickers · Feedback"

// ---- External destinations ---- //
private const val MORE_APPS_URL = "https://play.google.com/store/apps/developer?id=PIP+Technologies"
private val PLAY_MARKET_URI = "market://details?id=${BuildConfig.APPLICATION_ID}"
private val PLAY_LISTING_URL = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"

// ---- Off-token colors from the prototype's settings screen ---- //
private val CardBorder = Color(0xFFEEF0F4)
private val RowDivider = Color(0xFFF2F4F7)
private val RowIconTint = Color(0xFF565C67)
private val ChevronTint = Color(0xFFB4BAC4)

private val HeroTitleStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 16.sp)
private val HeroSubStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 18.sp)
private val SectionLabelStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W600, fontSize = 11.sp, letterSpacing = 0.08.em)
private val RowLabelStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.5.sp)
private val RowValueStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 13.5.sp)
private val VersionValueStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 12.sp)
private val AdBadgeStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W600, fontSize = 9.sp, letterSpacing = 0.06.em)
private val FreeCardStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.5.sp, lineHeight = 19.sp)

/**
 * Settings, straight from the design: back header, the "New pack alerts" hero
 * card, PREFERENCES and ABOUT groups, and the free-forever card. Rating,
 * the notification pre-ask and clear-downloads run as bottom sheets on top.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLanguage: () -> Unit,
    onContact: () -> Unit,
    onEditThemes: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // A language change recreates the activity, so this is read fresh then.
    val languageTag = remember { AppLanguages.selectedTag() }
    val scope = rememberCoroutineScope()
    val toastHost = remember { SnackbarHostState() }

    var notifPromptVisible by rememberSaveable { mutableStateOf(false) }
    var clearConfirmVisible by rememberSaveable { mutableStateOf(false) }
    var rateSheetVisible by rememberSaveable { mutableStateOf(false) }

    val showToast: (String) -> Unit = { message ->
        scope.launch { toastHost.showToast(message) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.DownloadsCleared ->
                    toastHost.showToast(context.getString(R.string.settings_toast_cleared))
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setAlertsEnabled(true)
            showToast(context.getString(R.string.settings_toast_alerts_on))
        }
        // Denied: the switch stays off — the design's ask ends the same way
        // on "Don't allow", with no extra message.
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        SettingsContent(
            state = state,
            languageTag = languageTag,
            onBack = onBack,
            onToggleAlerts = { wantOn ->
                when {
                    !wantOn -> {
                        viewModel.setAlertsEnabled(false)
                        showToast(context.getString(R.string.settings_toast_alerts_off))
                    }
                    needsNotificationsPermission(context) -> notifPromptVisible = true
                    else -> {
                        viewModel.setAlertsEnabled(true)
                        showToast(context.getString(R.string.settings_toast_alerts_on))
                    }
                }
            },
            onEditThemes = onEditThemes,
            onLanguage = onLanguage,
            onClear = {
                if (state.hasDownloads) clearConfirmVisible = true
                else showToast(context.getString(R.string.settings_toast_nothing_to_clear))
            },
            onRate = { rateSheetVisible = true },
            onContact = onContact,
            onMoreApps = { if (!openLink(context, MORE_APPS_URL)) showToast(context.getString(R.string.toast_link_failed)) },
            onPrivacy = { if (!openLink(context, PRIVACY_POLICY_URL)) showToast(context.getString(R.string.toast_link_failed)) },
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
        )
        ToastHost(
            hostState = toastHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(bottom = 12.dp)
        )
    }

    if (notifPromptVisible) {
        ConfirmSheet(
            title = stringResource(R.string.settings_notif_title),
            body = stringResource(R.string.settings_notif_body),
            confirmLabel = stringResource(R.string.settings_notif_allow),
            cancelLabel = stringResource(R.string.settings_notif_deny),
            destructive = false,
            icon = LoveIcons.Bell,
            onConfirm = {
                notifPromptVisible = false
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { notifPromptVisible = false }
        )
    }

    if (clearConfirmVisible) {
        ConfirmSheet(
            title = stringResource(R.string.settings_clear_title),
            body = stringResource(R.string.settings_clear_body, sizeLabel(state.downloadedBytes)),
            confirmLabel = stringResource(R.string.settings_clear_confirm),
            cancelLabel = stringResource(R.string.settings_clear_keep),
            destructive = true,
            onConfirm = {
                clearConfirmVisible = false
                viewModel.clearDownloads()
            },
            onDismiss = { clearConfirmVisible = false }
        )
    }

    if (rateSheetVisible) {
        RateSheet(
            onDismiss = { rateSheetVisible = false },
            onOpenStore = {
                if (!openPlayListing(context)) showToast(context.getString(R.string.toast_link_failed))
            },
            onSendFeedback = { text ->
                val sent = openMail(
                    context = context,
                    subject = FEEDBACK_MAIL_SUBJECT,
                    body = text.trim() + deviceInfoBlock()
                )
                if (!sent) showToast(context.getString(R.string.toast_no_email_app))
                sent
            }
        )
    }
}

// ------------------------------------------------------------------ //
// Layout
// ------------------------------------------------------------------ //

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    languageTag: String,
    onBack: () -> Unit,
    onToggleAlerts: (Boolean) -> Unit,
    onEditThemes: () -> Unit,
    onLanguage: () -> Unit,
    onClear: () -> Unit,
    onRate: () -> Unit,
    onContact: () -> Unit,
    onMoreApps: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        LoveTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, top = 2.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AlertsHeroCard(enabled = state.alertsEnabled, onToggle = onToggleAlerts)

            Column {
                SectionLabel(stringResource(R.string.settings_section_preferences))
                SettingsCard {
                    SettingsRow(
                        icon = SettingsIcons.SlidersHorizontal,
                        label = stringResource(R.string.settings_edit_themes),
                        value = pluralStringResource(R.plurals.settings_theme_count, state.themesCount, state.themesCount),
                        onClick = onEditThemes
                    )
                    RowDividerLine()
                    SettingsRow(
                        icon = SettingsIcons.Languages,
                        label = stringResource(R.string.settings_language),
                        value = languageLabel(languageTag),
                        onClick = onLanguage
                    )
                    RowDividerLine()
                    SettingsRow(
                        icon = LoveIcons.Trash2,
                        label = stringResource(R.string.settings_clear),
                        value = storageLabel(state),
                        onClick = onClear
                    )
                }
            }

            Column {
                SectionLabel(stringResource(R.string.settings_section_about))
                SettingsCard {
                    SettingsRow(
                        icon = LoveIcons.Star,
                        label = stringResource(R.string.settings_rate),
                        onClick = onRate
                    )
                    RowDividerLine()
                    SettingsRow(
                        icon = LoveIcons.Mail,
                        label = stringResource(R.string.settings_contact),
                        onClick = onContact
                    )
                    RowDividerLine()
                    SettingsRow(
                        icon = SettingsIcons.LayoutGrid,
                        label = stringResource(R.string.settings_more_apps),
                        showAdBadge = true,
                        trailingIcon = SettingsIcons.ArrowUpRight,
                        onClick = onMoreApps
                    )
                    RowDividerLine()
                    SettingsRow(
                        icon = SettingsIcons.ShieldCheck,
                        label = stringResource(R.string.settings_privacy),
                        trailingIcon = SettingsIcons.ArrowUpRight,
                        onClick = onPrivacy
                    )
                    RowDividerLine()
                    SettingsRow(
                        icon = LoveIcons.Info,
                        label = stringResource(R.string.settings_version),
                        value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        valueStyle = VersionValueStyle,
                        trailingIcon = null,
                        onClick = null
                    )
                }
            }

            FreeForeverCard()
        }
    }
}

@Composable
private fun AlertsHeroCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (enabled) RoseTint else Subtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (enabled) LoveIcons.Bell else SettingsIcons.BellOff,
                null,
                Modifier.size(23.dp),
                tint = if (enabled) Rose else Muted
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_alerts_title), style = HeroTitleStyle, color = Ink)
            Text(
                stringResource(if (enabled) R.string.settings_alerts_on else R.string.settings_alerts_off),
                style = HeroSubStyle,
                color = Ink2,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        AppSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = SectionLabelStyle,
        color = Muted,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        content = content
    )
}

@Composable
private fun RowDividerLine() {
    HorizontalDivider(thickness = 1.dp, color = RowDivider)
}

/**
 * One grouped-settings row: 20dp icon, 14.5/600 label (with the AD badge on
 * More apps), a muted value on the right, then a chevron — or the up-right
 * arrow for anything that leaves the app, or nothing on the version row.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    valueStyle: TextStyle = RowValueStyle,
    showAdBadge: Boolean = false,
    trailingIcon: ImageVector? = LoveIcons.ChevronRight,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                } else Modifier
            )
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = RowIconTint)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = RowLabelStyle, color = Ink)
            if (showAdBadge) {
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.settings_ad_badge),
                    style = AdBadgeStyle,
                    color = Ink2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Subtle)
                        .border(1.dp, Border, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        if (value != null) {
            Text(value, style = valueStyle, color = Muted)
        }
        if (trailingIcon != null) {
            Icon(trailingIcon, null, Modifier.size(18.dp), tint = ChevronTint)
        }
    }
}

@Composable
private fun FreeForeverCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RoseTint)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            LoveIcons.HeartHandshake,
            null,
            Modifier
                .padding(top = 1.dp)
                .size(18.dp),
            tint = Rose
        )
        Text(stringResource(R.string.settings_free_card), style = FreeCardStyle, color = RowIconTint)
    }
}

// ------------------------------------------------------------------ //
// Labels and intents
// ------------------------------------------------------------------ //

/** Language row value: the language's own name, or "System default". */
@Composable
private fun languageLabel(tag: String): String =
    AppLanguages.byTag(tag)?.nativeName ?: stringResource(R.string.language_system)

/** Row value: "12.4 MB" while something is stored, otherwise "None". */
@Composable
private fun storageLabel(state: SettingsUiState): String =
    if (!state.hasDownloads) stringResource(R.string.settings_storage_none) else sizeLabel(state.downloadedBytes)

/** "12.4 MB" in the app language's number format. */
@Composable
private fun sizeLabel(bytes: Long): String =
    stringResource(R.string.settings_size_mb, (bytes / (1024.0 * 1024.0)).coerceAtLeast(0.1))

/** True on API 33+ while POST_NOTIFICATIONS still needs the system ask. */
private fun needsNotificationsPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED

private fun openLink(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** Play listing of this app: the market: intent, then the https fallback. */
private fun openPlayListing(context: Context): Boolean =
    openLink(context, PLAY_MARKET_URI) || openLink(context, PLAY_LISTING_URL)

private fun openMail(context: Context, subject: String, body: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_SENDTO, mailtoUri(subject, body)))
    true
} catch (_: ActivityNotFoundException) {
    false
}

// ------------------------------------------------------------------ //
// Previews
// ------------------------------------------------------------------ //

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenPreview() {
    LoveStickersTheme {
        Box(Modifier.background(Canvas)) {
            SettingsContent(
                state = SettingsUiState(
                    alertsEnabled = true,
                    themesCount = 6,
                    downloadedBytes = 13_002_342L,
                    hasDownloads = true
                ),
                languageTag = "en",
                onBack = {},
                onToggleAlerts = {},
                onEditThemes = {},
                onLanguage = {},
                onClear = {},
                onRate = {},
                onContact = {},
                onMoreApps = {},
                onPrivacy = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenFreshPreview() {
    LoveStickersTheme {
        Box(Modifier.background(Canvas)) {
            SettingsContent(
                state = SettingsUiState(
                    alertsEnabled = false,
                    themesCount = 3,
                    downloadedBytes = 0L,
                    hasDownloads = false
                ),
                languageTag = AppLanguages.SYSTEM,
                onBack = {},
                onToggleAlerts = {},
                onEditThemes = {},
                onLanguage = {},
                onClear = {},
                onRate = {},
                onContact = {},
                onMoreApps = {},
                onPrivacy = {}
            )
        }
    }
}
