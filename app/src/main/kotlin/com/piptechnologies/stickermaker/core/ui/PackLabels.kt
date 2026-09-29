package com.piptechnologies.stickermaker.core.ui

import com.piptechnologies.stickermaker.R

/** Real adds are shown from this many on; below it a card shows only its sticker count. */
const val ADDS_SHOWN_FROM = 100L

/** "1.2K adds" once a pack has [ADDS_SHOWN_FROM] real adds, null below: no invented numbers. */
fun addsLabel(adds: Long): UiText? =
    if (adds >= ADDS_SHOWN_FROM) UiText.res(R.string.pack_adds, UiText.Compact(adds)) else null
