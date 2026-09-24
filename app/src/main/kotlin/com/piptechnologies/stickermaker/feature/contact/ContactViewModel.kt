package com.piptechnologies.stickermaker.feature.contact

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import com.piptechnologies.stickermaker.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Publisher support address, the single constant for every mail the app
 * composes (Contact us, low-star feedback). Already public as
 * `publisher_email` in packs/&#42;/pack.json.
 */
internal const val SUPPORT_EMAIL = "aymen@chikhaoui.me"

/**
 * What the contact note discloses gets attached: the app version and the
 * Android version (plus the device model that names that Android build).
 */
internal fun deviceInfoBlock(): String =
    "\n\n--\nLove Stickers ${BuildConfig.VERSION_NAME}\nAndroid ${Build.VERSION.RELEASE} · ${Build.MODEL}"

/** mailto: URI to the publisher with the subject and body URL-encoded. */
internal fun mailtoUri(subject: String, body: String): Uri =
    Uri.parse("mailto:$SUPPORT_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")

/** State for the contact screen: the two fields; Send needs message text. */
data class ContactUiState(
    val message: String = "",
    val email: String = ""
) {
    val canSend: Boolean get() = message.isNotBlank()
}

@HiltViewModel
class ContactViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ContactUiState())
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()

    fun setMessage(value: String) {
        _uiState.update { it.copy(message = value) }
    }

    fun setEmail(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    /**
     * The composed mail body: the message, the optional reply address, then
     * the device block the screen's note discloses. Nothing else.
     */
    fun mailBody(): String {
        val state = _uiState.value
        return buildString {
            append(state.message.trim())
            if (state.email.isNotBlank()) {
                append("\n\nReply to: ${state.email.trim()}")
            }
            append(deviceInfoBlock())
        }
    }
}
