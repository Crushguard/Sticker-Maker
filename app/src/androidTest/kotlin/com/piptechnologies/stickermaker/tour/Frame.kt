package com.piptechnologies.stickermaker.tour

import java.util.Locale

/**
 * The 42 frames of design/Screens.dc.html ("Every screen, every state"), in the
 * design's order. The tour captures the real app in each state and records
 * which frame every screenshot maps to.
 */
enum class Frame(
    val n: Int,
    val slug: String,
    val section: String,
    val title: String,
    val route: String
) {
    SPLASH(1, "launch", "00 Launch", "Loading screen", "splash"),
    ONBOARDING_1(2, "onboarding-slide-1", "01 Onboarding", "Slide 1 · Curated packs, one tap to WhatsApp", "onboarding"),
    ONBOARDING_2(3, "onboarding-slide-2", "01 Onboarding", "Slide 2 · Make your own, animated too", "onboarding"),
    CUSTOMIZE_FIRST_RUN(4, "customize-first-run", "02 Customization", "First run", "customize"),
    CUSTOMIZE_EDIT(5, "customize-edit-themes", "02 Customization", "From Settings · Edit themes", "customizeEdit"),
    HOME_TRENDING(6, "home-trending", "03 Home", "Home · Trending", "home"),
    HOME_ANIMATED(7, "home-animated", "03 Home", "Home · Animated", "home"),
    HOME_SEARCH(8, "home-search", "03 Home", "Home › Search", "home"),
    DETAIL_CLINGY_MANGO(9, "detail-clingy-mango", "04 Stickers", "Clingy Mango · 18 stickers", "detail/clingy-mango"),
    DETAIL_MANGO_MOVES(10, "detail-mango-moves", "04 Stickers", "Mango Moves · animated", "detail/mango-moves"),
    DETAIL_BIG_WORDS(11, "detail-big-words", "04 Stickers", "Big Words · 18 stickers", "detail/big-words"),
    ADD_IDLE(12, "add-1-idle", "05 Add states", "1 · Idle", "detail/sorry-love"),
    ADD_DOWNLOADING(13, "add-2-downloading", "05 Add states", "2 · Downloading", "detail/sorry-love"),
    ADD_SENT(14, "add-3-sent", "05 Add states", "3 · Sent to WhatsApp", "detail/sorry-love"),
    ADD_ADDED(15, "add-4-added", "05 Add states", "4 · Added", "detail/sorry-love"),
    ADD_FAILED(16, "add-retry-failed", "05 Add states", "Retry · download failed", "detail/sorry-love"),
    CARD_PILL(17, "add-card-pill", "05 Add states", "Card pill · same states, small", "home"),
    CREATE_IMPORT(18, "create-import", "06 Create", "Import", "create"),
    CREATE_EDITOR_AUTO(19, "create-cutout-auto", "06 Create", "Cut out · auto result", "editor"),
    CREATE_EDITOR_BRUSH(20, "create-cutout-brush", "06 Create", "Cut out · fixing edges", "editor"),
    CREATE_DETAILS(21, "create-pack-details", "06 Create", "Pack details", "packDetails"),
    OWN_US_ALWAYS(22, "own-us-always", "06 Create", "Made by you · Us, always", "detail/own"),
    OWN_JUST_US(23, "own-just-us", "06 Create", "Made by you · Just Us", "detail/own"),
    MY_PACKS(24, "my-packs", "07 My Packs and Saved", "My Packs", "myPacks"),
    SAVED(25, "saved", "07 My Packs and Saved", "My Packs › Saved", "saved"),
    CARD_MENU(26, "my-packs-card-menu", "07 My Packs and Saved", "Card menu", "myPacks"),
    CONFIRM_REMOVE(27, "my-packs-confirm-remove", "07 My Packs and Saved", "Confirm · remove from app", "myPacks"),
    CONFIRM_DELETE(28, "my-packs-confirm-delete", "07 My Packs and Saved", "Confirm · delete your pack", "myPacks"),
    HOME_SAVED_CHIP(29, "home-saved-chip", "07 My Packs and Saved", "Home · ♥ Saved", "home"),
    SETTINGS(30, "settings", "08 Settings", "Settings", "settings"),
    RATE_STARS(31, "rate-stars", "08 Settings", "Rate · pick a star", "settings"),
    RATE_STORE(32, "rate-store", "08 Settings", "Rate · 5 stars → Google Play", "settings"),
    RATE_FEEDBACK(33, "rate-feedback", "08 Settings", "Rate · 1 to 4 stars → feedback", "settings"),
    RATE_THANKS(34, "rate-thanks", "08 Settings", "Rate · thanks", "settings"),
    CONTACT(35, "contact", "08 Settings", "Contact us", "contact"),
    LANGUAGE(36, "language", "08 Settings", "Language", "language"),
    NOTIFICATION_PERMISSION(37, "alerts-permission", "08 Settings", "New pack alerts · permission", "settings"),
    CONFIRM_CLEAR(38, "settings-confirm-clear", "08 Settings", "Confirm · clear downloads", "settings"),
    OFFLINE(39, "home-offline", "09 Errors and empty states", "No internet", "home"),
    NO_WHATSAPP(40, "no-whatsapp", "09 Errors and empty states", "WhatsApp not installed", "detail/flirty-shy"),
    MY_PACKS_EMPTY(41, "my-packs-empty", "09 Errors and empty states", "My Packs · empty", "myPacks"),
    SAVED_EMPTY(42, "saved-empty", "09 Errors and empty states", "Saved · empty", "saved");

    /** Screenshot file name, e.g. "06-home-trending.png". */
    val fileName: String get() = String.format(Locale.ROOT, "%02d-%s.png", n, slug)
}
