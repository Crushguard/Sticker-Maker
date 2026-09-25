package com.piptechnologies.stickermaker.tour

import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.piptechnologies.stickermaker.MainActivity
import com.piptechnologies.stickermaker.feature.create.editor.EDITOR_CANVAS_TAG
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Drives the real app on a device through every state of design/Screens.dc.html
 * and screenshots each one. The app talks to the Firebase emulators seeded by
 * scripts/upload-pack.js, and a WhatsApp test double (testing/whatsapp-stub)
 * reads packs back through the app's ContentProvider and checks WhatsApp's
 * pack rules. Tests run in order and build on each other's state, like a user
 * would: first run, themes, adds, own packs, settings.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenTourTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private var intentsActive = false

    @After
    fun tidyUp() {
        Tour.cancelStubIfOpen()
        EmulatorConsole.networkSpeed("full")
        if (intentsActive) {
            Intents.release()
            intentsActive = false
        }
    }

    // ------------------------------------------------------------------ //
    // 1. First run, offline: launch, onboarding, themes, offline Home.
    // ------------------------------------------------------------------ //

    @Test
    fun t01_firstRun() {
        step("Launch") {
            compose.waitFor(hasText("Love Stickers"), 10_000)
            // Let the 400 ms fade-in finish; the 1.6 s beat runs on the same test clock.
            compose.pump(450)
            shot(Frame.SPLASH, "Branding beat before onboarding (first run).", quick = true)
        }
        // Offline from here, so Home meets an empty cache like a first launch without signal.
        step("Go offline") { Tour.setOnline(false) }
        step("Onboarding") {
            compose.waitFor(hasText("Say it with a sticker"), 20_000)
            shot(Frame.ONBOARDING_1, "Slide 1 with Skip; Next advances.")
            compose.tap(hasText("Next") and hasClickAction())
            compose.waitFor(hasText("Make your own"))
            shot(Frame.ONBOARDING_2, "Slide 2; Skip hides, CTA reads Get started.")
            compose.tap(hasText("Get started") and hasClickAction())
        }
        step("Customization first run") {
            compose.waitFor(hasText("Pick your themes"))
            setTheme("Couples", on = true)
            setTheme("Cute", on = true)
            setTheme("Funny", on = true)
            compose.waitFor(hasText("Continue · 3 themes"))
            shot(Frame.CUSTOMIZE_FIRST_RUN, "Offline first run: the eight themes come from the built-in fallback list.")
            compose.tap(hasText("Continue · 3 themes") and hasClickAction())
        }
        step("Offline Home") {
            compose.waitFor(hasText("You're offline"), 60_000)
            shot(Frame.OFFLINE, "Airplane mode on a fresh install: nothing cached, full-screen offline state with Retry.")
        }
        step("Go online") { Tour.setOnline(true) }
        step("Retry") {
            if (compose.exists(hasClickLabel("Retry"))) compose.tap(hasClickLabel("Retry"))
            awaitHome()
            extra("x01", "Home after Retry (first-run themes)", "home",
                "Back online, Retry reloads the catalog from the Firestore emulator; only Couples/Cute/Funny packs show.")
        }
    }

    // ------------------------------------------------------------------ //
    // 2. Edit themes from Settings; WhatsApp missing.
    // ------------------------------------------------------------------ //

    @Test
    fun t02_themesAndMissingWhatsApp() {
        Tour.uninstallWhatsAppStub()
        awaitHome()
        step("Edit themes") {
            compose.tap(hasClickLabel("Settings"))
            compose.tap(hasClickLabel("Edit themes"))
            compose.waitFor(hasText("Your themes"))
            setTheme("Funny", on = false)
            setTheme("Romantic", on = true)
            setTheme("Flirty", on = true)
            setTheme("Good night", on = true)
            setTheme("Long distance", on = true)
            setTheme("Couples", on = true)
            setTheme("Cute", on = true)
            shot(Frame.CUSTOMIZE_EDIT, "Settings › Edit themes, pre-checked from prefs; six themes picked, CTA reads Save.")
            compose.tap(hasText("Save") and hasClickAction())
            compose.waitFor(hasClickLabel("Edit themes"))
            back()
            awaitHome()
        }
        step("WhatsApp not installed") {
            openPack("Flirty & Shy")
            compose.tap(addBar("Add to WhatsApp"))
            compose.waitFor(hasText("WhatsApp isn't installed"))
            shot(Frame.NO_WHATSAPP, "No com.whatsapp on the device: tapping Add raises the install sheet before any download.")
            compose.tap(hasClickLabel("Not now"))
            compose.waitGone(hasText("WhatsApp isn't installed"))
        }
        Tour.installWhatsAppStub()
    }

    // ------------------------------------------------------------------ //
    // 3. The Add state machine on a pack page, then the empty tabs.
    // ------------------------------------------------------------------ //

    @Test
    fun t03_addStates() {
        Tour.installWhatsAppStub()
        awaitHome()
        val brokenFile = "packs/sorry-love/07.webp"
        var savedBytes: ByteArray? = null
        step("Idle") {
            openPack("Sorry, My Love")
            shot(Frame.ADD_IDLE, "Idle bar with its hint; 18 stickers stream from the Storage emulator.")
        }
        try {
            step("Download failed") {
                savedBytes = StorageEmulator.read(brokenFile)
                StorageEmulator.delete(brokenFile)
                compose.tap(addBar("Add to WhatsApp"))
                compose.waitFor(addBar("Download failed · Retry"), 90_000)
                shot(Frame.ADD_FAILED, "Real failure: $brokenFile was deleted from the Storage emulator mid-catalog, so getFile() 404s.")
            }
        } finally {
            savedBytes?.let { StorageEmulator.write(brokenFile, it, "image/webp") }
        }
        step("Downloading") {
            // The bar, unlike the card pill, lets Compose go idle mid-download, so
            // the default clock works here and polls fast enough for a small pack.
            val throttled = EmulatorConsole.networkSpeed("edge")
            compose.tap(addBar("Download failed · Retry"))
            if (throttled) {
                compose.waitFor(downloadingAtLeast(20), 90_000)
            } else {
                compose.waitFor(addBar("Downloading", substring = true), 30_000)
            }
            shot(Frame.ADD_DOWNLOADING,
                if (throttled) "Retry after the file was restored; network slowed to EDGE through the emulator console to hold the state."
                else "Retry after the file was restored (captured at full network speed).",
                quick = true)
            EmulatorConsole.networkSpeed("full")
        }
        step("Sent to WhatsApp") {
            compose.awaitStubDialog(90_000)
            val report = Tour.stubContractReport()
            compose.settle(300)
            shot(Frame.ADD_SENT, "Bar reads Sent to WhatsApp… under WhatsApp's confirm (test double). Provider check:\n$report", quick = true)
            compose.confirmStub()
        }
        step("Added") {
            compose.waitFor(addBar("Added to WhatsApp"), 30_000)
            shot(Frame.ADD_ADDED, "WhatsApp answered RESULT_OK and its whitelist provider reports the pack.")
        }
        step("Empty My Packs") {
            back()
            awaitHome()
            compose.tap(hasClickLabel("My Packs"))
            compose.reveal(card("Sorry, My Love"))
            compose.tap(inCard("Sorry, My Love", hasClickLabel("Pack options")))
            compose.tap(hasClickLabel("Remove from this app"))
            compose.tap(hasClickLabel("Remove"))
            compose.waitFor(hasText("No packs yet"), 20_000)
            val packDir = File(Tour.targetContext.filesDir, "packs/sorry-love")
            check(eventually { !packDir.exists() }) { "Removing the pack left $packDir behind" }
            shot(Frame.MY_PACKS_EMPTY, "After removing the only added pack for real; its files are gone from filesDir.")
        }
        step("Empty Saved") {
            compose.tap(hasClickLabel("Saved packs"))
            compose.waitFor(hasText("No saved packs yet"))
            shot(Frame.SAVED_EMPTY, "Nothing hearted yet.")
            back()
        }
    }

    // ------------------------------------------------------------------ //
    // 4. Home with hearts, adds and chips.
    // ------------------------------------------------------------------ //

    @Test
    fun t04_home() {
        Tour.installWhatsAppStub()
        awaitHome()
        step("Hearts") {
            heart("Big Words")
            heart("Flirty & Shy")
        }
        step("Add from the card pill") {
            compose.scrollListToTop(card("Clingy Mango"))
            compose.tap(inCard("Clingy Mango", hasClickLabel("Add")))
            compose.awaitStubDialog(90_000)
            Tour.stubContractReport()
            compose.confirmStub()
            compose.waitFor(inCard("Clingy Mango", hasClickLabel("Added")), 30_000)
        }
        step("Card pill states") {
            compose.clearToasts()
            compose.reveal(card("Mango Moves"))
            compose.settle()
            val throttled = EmulatorConsole.networkSpeed("edge")
            val inFlight = inCard(
                "Mango Moves",
                if (throttled) pillDownloadingAtLeast(15) else hasClickLabelStartingWith("Downloading")
            )
            compose.withManualClock {
                compose.onAllNodes(inCard("Mango Moves", hasClickLabel("Add"))).onFirst().performClick()
                compose.pumpUntil(inFlight.description, 90_000) { compose.exists(inFlight) }
                shot(Frame.CARD_PILL, "Card pills in miniature: Clingy Mango added, Mango Moves downloading (network slowed to EDGE).", quick = true)
            }
            EmulatorConsole.networkSpeed("full")
            compose.awaitStubDialog(120_000)
            Tour.stubContractReport()
            compose.confirmStub()
            compose.waitFor(inCard("Mango Moves", hasClickLabel("Added")), 30_000)
        }
        step("Trending") {
            compose.clearToasts()
            compose.scrollListToTop(card("Clingy Mango"))
            compose.tap(hasClickLabel("Trending"))
            shot(Frame.HOME_TRENDING, "Six themes picked; two packs added, two hearted (♥ Saved chip shows).")
        }
        step("Animated chip") {
            compose.tap(hasClickLabel("Animated"))
            compose.waitFor(card("Mango Moves"))
            shot(Frame.HOME_ANIMATED, "Animated chip: only animated packs, ANIMATED badges, static first-frame previews.")
        }
        step("Saved chip") {
            compose.tap(hasClickLabel("Saved · 2"))
            compose.waitFor(card("Big Words"))
            shot(Frame.HOME_SAVED_CHIP, "♥ Saved chip filters Home to the hearted packs.")
            compose.tap(hasClickLabel("Trending"))
        }
        step("Search") {
            compose.tap(hasClickLabel("Search"))
            compose.onNode(hasSetTextAction()).performTextInput("night")
            compose.waitFor(card("Good Morning, Good Night"))
            Espresso.closeSoftKeyboard()
            shot(Frame.HOME_SEARCH, "Search replaces the bar and chips; \"night\" matches titles and the Good night theme.")
            compose.tap(hasClickLabel("Cancel"))
        }
    }

    // ------------------------------------------------------------------ //
    // 5. Pack pages.
    // ------------------------------------------------------------------ //

    @Test
    fun t05_packPages() {
        awaitHome()
        step("Clingy Mango") {
            openPack("Clingy Mango")
            compose.waitFor(hasText("18 stickers · 96.4K adds"))
            shot(Frame.DETAIL_CLINGY_MANGO, "Added pack: grid of 18, bar reads Added to WhatsApp.")
            back()
        }
        step("Mango Moves") {
            awaitHome()
            openPack("Mango Moves")
            shot(Frame.DETAIL_MANGO_MOVES, "Animated pack with the ANIMATED badge; added.")
            back()
        }
        step("Big Words") {
            awaitHome()
            openPack("Big Words")
            compose.waitFor(hasClickLabel("Remove from saved"))
            shot(Frame.DETAIL_BIG_WORDS, "Hearted pack (rose heart), not added yet.")
            back()
        }
    }

    // ------------------------------------------------------------------ //
    // 6. Create: import, cut out, details, export to WhatsApp; a second pack.
    // ------------------------------------------------------------------ //

    @Test
    fun t06_create() {
        Tour.installWhatsAppStub()
        awaitHome()
        step("Import") {
            beginIntents()
            Tour.stubPhotoPicker(TourPhotos.insert(4, "us-always"))
            compose.tap(hasClickLabel("Create a pack"))
            compose.waitFor(hasText("New pack"))
            compose.tap(hasClickLabel("Add a sticker"))
            compose.waitFor(hasClickLabel("Next · 4 stickers"))
            shot(Frame.CREATE_IMPORT, "Four photos from MediaStore through the (stubbed) system photo picker.")
            compose.tap(hasClickLabel("Next · 4 stickers"))
        }
        step("Cut out") {
            awaitCutouts(4)
            shot(Frame.CREATE_EDITOR_AUTO, "ML Kit selfie segmentation ran on the phone for every sticker; white outline on.")
        }
        step("Brush") {
            compose.tap(hasClickLabel("Brush"))
            compose.waitFor(hasText("Tap the picture to bring parts back"))
            compose.onNodeWithTag(EDITOR_CANVAS_TAG, useUnmergedTree = true).performTouchInput {
                click(Offset(width * 0.20f, height * 0.74f))
                click(Offset(width * 0.31f, height * 0.84f))
            }
            shot(Frame.CREATE_EDITOR_BRUSH, "Brush tool with two dabs restoring edges; brush size row shows.")
        }
        step("Caption") {
            compose.tap(hasClickLabel("Text"))
            compose.onNode(hasSetTextAction()).performTextInput("miss u")
            Espresso.closeSoftKeyboard()
            extra("x02", "Cut out · caption", "editor", "Text tool: the design's \"miss u\" caption on the first sticker.")
        }
        step("Pack details") {
            compose.tap(hasClickLabel("Next · 4 stickers"))
            compose.waitFor(hasText("Pack details"))
            compose.onNode(hasSetTextAction()).performTextInput("Us, always")
            Espresso.closeSoftKeyboard()
            shot(Frame.CREATE_DETAILS, "Tray from the first sticker, name typed, export facts card.")
        }
        step("Export to WhatsApp") {
            compose.tap(addBar("Add to WhatsApp"))
            compose.awaitStubDialog(120_000)
            val report = Tour.stubContractReport()
            compose.settle(300)
            extra("x03", "Own pack in WhatsApp's confirm", "packDetails",
                "Exported on the phone (512×512 WebP, 96×96 tray) and read back through the provider:\n$report")
            compose.confirmStub()
            compose.waitFor(hasText("My Packs"), 30_000)
            compose.reveal(card("Us, always"), 30_000)
        }
        step("Us, always") {
            compose.tap(card("Us, always"))
            compose.waitFor(hasText("4 stickers · Made by you"), 30_000)
            compose.waitFor(addBar("Added to WhatsApp"), 30_000)
            shot(Frame.OWN_US_ALWAYS, "Own pack page from Room + filesDir: Made by you, added.")
            back()
        }
        step("Just Us") {
            endIntents()
            beginIntents()
            Tour.stubPhotoPicker(TourPhotos.insert(3, "just-us"))
            compose.tap(hasClickLabel("Create a pack"))
            compose.waitFor(hasText("New pack"))
            compose.tap(hasClickLabel("Add a sticker"))
            compose.tap(hasClickLabel("Next · 3 stickers"))
            awaitCutouts(3)
            compose.tap(hasClickLabel("Next · 3 stickers"))
            compose.waitFor(hasText("Pack details"))
            compose.onNode(hasSetTextAction()).performTextInput("Just Us")
            Espresso.closeSoftKeyboard()
            compose.tap(hasClickLabel("Save to My Packs only"))
            compose.reveal(card("Just Us"), 30_000)
            compose.tap(card("Just Us"))
            compose.waitFor(hasText("3 stickers · Made by you"), 30_000)
            compose.waitFor(addBar("Add to WhatsApp"))
            shot(Frame.OWN_JUST_US, "Saved to My Packs only: never sent to WhatsApp, so the bar offers Add.")
        }
        step("Add an own pack from its page") {
            compose.tap(addBar("Add to WhatsApp"))
            compose.awaitStubDialog(60_000)
            val report = Tour.stubContractReport()
            compose.confirmStub()
            compose.waitFor(addBar("Added to WhatsApp"), 30_000)
            extra("x04", "Own pack added from its page", "detail/own",
                "Local packs skip the download and hand straight to WhatsApp; provider check:\n$report")
            back()
        }
    }

    // ------------------------------------------------------------------ //
    // 7. My Packs and Saved.
    // ------------------------------------------------------------------ //

    @Test
    fun t07_myPacks() {
        awaitHome()
        step("My Packs") {
            compose.tap(hasClickLabel("My Packs"))
            compose.reveal(card("Us, always"))
            compose.reveal(card("Clingy Mango"))
            shot(Frame.MY_PACKS, "In WhatsApp (2) and Made by you (2), removal info card, tab bar with My Packs selected.")
        }
        step("Card menu") {
            compose.tap(inCard("Clingy Mango", hasClickLabel("Pack options")))
            compose.waitFor(hasClickLabel("Remove from this app"))
            shot(Frame.CARD_MENU, "⋯ menu for an added catalog pack: re-add or remove the local copy.")
        }
        step("Confirm remove") {
            compose.tap(hasClickLabel("Remove from this app"))
            compose.waitFor(hasText("Remove “Clingy Mango” from this app?"))
            shot(Frame.CONFIRM_REMOVE, "Destructive confirm; Keep cancels.")
            compose.tap(hasClickLabel("Keep"))
        }
        step("Confirm delete") {
            compose.reveal(card("Us, always"))
            compose.tap(inCard("Us, always", hasClickLabel("Pack options")))
            compose.tap(hasClickLabel("Delete pack"))
            compose.waitFor(hasText("Delete “Us, always”?"))
            shot(Frame.CONFIRM_DELETE, "Deleting your own pack; Keep cancels.")
            compose.tap(hasClickLabel("Keep"))
        }
        step("Saved") {
            compose.tap(hasClickLabel("Saved packs"))
            compose.waitFor(card("Big Words"))
            shot(Frame.SAVED, "Hearted packs, reached from the My Packs heart.")
            back()
        }
    }

    // ------------------------------------------------------------------ //
    // 8. Settings suite.
    // ------------------------------------------------------------------ //

    @Test
    fun t08_settings() {
        awaitHome()
        step("Settings") {
            compose.tap(hasClickLabel("Settings"))
            compose.waitFor(hasClickLabel("Edit themes"))
            shot(Frame.SETTINGS, "Alerts hero, preferences with values, about rows, free-forever card.")
        }
        step("Rate stars") {
            compose.tap(hasClickLabel("Rate us"))
            compose.waitFor(hasText("Enjoying Love Stickers?"))
            shot(Frame.RATE_STARS, "Rate sheet, no star picked.")
        }
        step("Rate store") {
            compose.tap(hasClickLabel("5 stars"))
            compose.waitFor(hasText("Thank you!"))
            shot(Frame.RATE_STORE, "Five stars lead to the Google Play ask.")
            compose.tap(hasClickLabel("Not now"))
            compose.waitGone(hasText("Thank you!"))
        }
        step("Rate feedback") {
            compose.tap(hasClickLabel("Rate us"))
            compose.tap(hasClickLabel("3 stars"))
            compose.waitFor(hasText("How can we do better?"))
            compose.onNode(hasSetTextAction()).performTextInput("More anime packs, please.")
            Espresso.closeSoftKeyboard()
            shot(Frame.RATE_FEEDBACK, "One to four stars lead to the feedback note.")
        }
        step("Rate thanks") {
            beginIntents()
            Tour.stubMailComposer()
            compose.tap(hasClickLabel("Send feedback"))
            compose.waitFor(hasText("Thank you, we hear you"))
            shot(Frame.RATE_THANKS, "Sent through the mail composer (stubbed: the emulator has no mail app).")
            compose.tap(hasClickLabel("Done"))
            endIntents()
        }
        step("Contact") {
            compose.tap(hasClickLabel("Contact us"))
            compose.waitFor(hasText("Write your message…"))
            shot(Frame.CONTACT, "Contact form: message, optional email, attach note.")
            back()
        }
        step("Language") {
            compose.tap(hasClickLabel("Language"))
            compose.waitFor(hasClickLabel("العربية"))
            shot(Frame.LANGUAGE, "System default selected; RTL badges on Arabic and Urdu.")
            back()
        }
        step("Alerts permission") {
            compose.waitFor(hasClickLabel("Edit themes"))
            toggleAlerts()
            compose.waitFor(hasText("Off. You won't hear about new packs."))
            toggleAlerts()
            compose.waitFor(hasText("Allow Love Stickers to send notifications?"))
            shot(Frame.NOTIFICATION_PERMISSION, "Turning alerts back on without the Android 13+ notification permission asks first.")
            compose.tap(hasClickLabel("Don't allow"))
        }
        step("Clear downloads") {
            compose.tap(hasClickLabel("Clear downloaded packs"))
            compose.waitFor(hasText("Clear downloaded packs?"))
            shot(Frame.CONFIRM_CLEAR, "Confirm with the real size of the downloaded packs.")
            compose.tap(hasClickLabel("Keep them"))
        }
    }

    // ------------------------------------------------------------------ //
    // 9. Deleting for real cleans up files, rows and WhatsApp's copy stays.
    // ------------------------------------------------------------------ //

    @Test
    fun t09_deleteOwnPack() {
        awaitHome()
        step("Delete Just Us") {
            compose.tap(hasClickLabel("My Packs"))
            compose.reveal(card("Just Us"))
            compose.tap(inCard("Just Us", hasClickLabel("Pack options")))
            compose.tap(hasClickLabel("Delete pack"))
            compose.tap(hasClickLabel("Delete"))
            compose.waitGone(card("Just Us"), 20_000)
            val ownRoot = File(Tour.targetContext.filesDir, "own")
            fun packDirs() = ownRoot.listFiles().orEmpty().filter { it.isDirectory && !it.name.endsWith(".tmp") }
            check(eventually { packDirs().size == 1 }) {
                "Expected only Us, always under filesDir/own, found ${packDirs().map { it.name }}"
            }
            extra("x05", "My Packs after deleting Just Us", "myPacks",
                "Delete removed the Room rows and the pack directory; Us, always stays.")
        }
    }

    // ------------------------------------------------------------------ //

    private fun step(name: String, block: () -> Unit) {
        Tour.dismissSystemDialogs()
        try {
            block()
        } catch (t: Throwable) {
            Tour.recordFailure(name, t)
            throw t
        }
    }

    private fun shot(frame: Frame, notes: String, quick: Boolean = false) {
        if (!quick) compose.settle()
        Tour.screenshot(frame.fileName)
        Tour.recordFrame(frame, notes)
    }

    private fun extra(key: String, title: String, route: String, notes: String) {
        compose.settle()
        val slug = title.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val file = "$key-$slug.png"
        Tour.screenshot(file)
        Tour.recordExtra(key, title, route, file, notes)
    }

    private fun back() {
        Tour.device.pressBack()
        compose.waitForIdle()
    }

    /** Home is up and the catalog rendered at least one card (a heart is on screen). */
    private fun awaitHome() {
        compose.waitFor(hasClickLabel("Search"), 60_000)
        compose.waitFor(hasClickLabel("Save pack") or hasClickLabel("Remove from saved"), 90_000)
    }

    private fun card(title: String): SemanticsMatcher = hasClickLabel(title)

    private fun inCard(title: String, matcher: SemanticsMatcher): SemanticsMatcher =
        matcher and hasAnyAncestor(card(title))

    private fun heart(title: String) {
        compose.reveal(card(title))
        compose.tap(inCard(title, hasClickLabel("Save pack")))
        compose.waitFor(inCard(title, hasClickLabel("Remove from saved")))
    }

    private fun openPack(title: String) {
        compose.tap(card(title))
        compose.waitFor(hasClickLabel("Back"))
        compose.waitFor(
            addBar("Add to WhatsApp") or addBar("Added to WhatsApp") or addBar("Download failed · Retry"),
            60_000
        )
    }

    /** The detail/details footer bar: a clickable whose merged text is [label]. */
    private fun addBar(label: String, substring: Boolean = false): SemanticsMatcher =
        hasText(label, substring = substring) and hasClickAction()

    private fun downloadingAtLeast(percent: Int): SemanticsMatcher =
        SemanticsMatcher("Add bar downloading at ≥ $percent%") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text ->
                Regex("Downloading · (\\d+)%").find(text.text)?.groupValues?.get(1)?.toInt()?.let { it >= percent } == true
            }
        }

    private fun pillDownloadingAtLeast(percent: Int): SemanticsMatcher =
        SemanticsMatcher("Card pill downloading at ≥ $percent%") { node ->
            val label = node.config.getOrNull(SemanticsActions.OnClick)?.label.orEmpty()
            Regex("Downloading (\\d+) percent").find(label)?.groupValues?.get(1)?.toInt()?.let { it >= percent } == true
        }

    private fun setTheme(label: String, on: Boolean) {
        val tile = hasText(label) and isToggleable()
        compose.reveal(tile)
        val node = compose.onAllNodes(tile).onFirst()
        val checked = node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
        if (checked != on) node.performClick()
        compose.waitForIdle()
    }

    private fun toggleAlerts() {
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.waitForIdle()
    }

    /** The editor is up and every sticker has its on-device cut-out. */
    private fun awaitCutouts(count: Int) {
        compose.waitFor(hasText("Cut out"))
        compose.waitGone(hasText("New pack"))
        compose.waitFor(hasClickLabel("Next · $count stickers"), 180_000)
    }

    private fun eventually(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            compose.pump(200)
        }
        return condition()
    }

    private fun beginIntents() {
        if (!intentsActive) {
            Intents.init()
            intentsActive = true
        }
    }

    private fun endIntents() {
        if (intentsActive) {
            Intents.release()
            intentsActive = false
        }
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpDevice() {
            Tour.reset()
            ImageTracker.install(Tour.targetContext)
            Tour.enterDemoMode()
        }

        @AfterClass
        @JvmStatic
        fun tearDownDevice() {
            Tour.exitDemoMode()
            Tour.setOnline(true)
        }
    }
}
