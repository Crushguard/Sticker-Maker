package com.piptechnologies.stickermaker.core.model

/**
 * State machine for adding a pack to WhatsApp:
 * [Idle] -> [Downloading] -> [Sent] -> [Added], or [Failed] at any point.
 *
 * The repository stops at [Sent] (files downloaded + Room rows ready); firing
 * the ADD_PACK intent and confirming [Added] belong to the UI layer.
 */
sealed interface AddState {

    /** Nothing in flight; the pack is not installed. */
    data object Idle : AddState

    /** Files are downloading; [progress] is 0f..1f. */
    data class Downloading(val progress: Float) : AddState

    /** Files + Room ready; the ADD_PACK intent is being handed to WhatsApp. */
    data object Sent : AddState

    /** WhatsApp confirmed the pack (whitelisted). */
    data object Added : AddState

    /** Download or install failed; [message] is optional detail for the toast. */
    data class Failed(val message: String?) : AddState
}
