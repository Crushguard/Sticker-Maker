package com.piptechnologies.stickermaker.core.ui

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One toast waiting for the next screen: the name flow posts "Added to WhatsApp" and Home
 * shows it when it opens (screens own their toast hosts, so nothing else crosses routes).
 */
@Singleton
class PendingToasts @Inject constructor() {

    private val pending = AtomicReference<UiText?>(null)

    fun post(message: UiText) = pending.set(message)

    fun take(): UiText? = pending.getAndSet(null)
}
