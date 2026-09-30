package com.piptechnologies.stickermaker.feature.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.feature.create.decor.DecorSpec
import com.piptechnologies.stickermaker.feature.create.decor.EmojiTones
import com.piptechnologies.stickermaker.feature.create.decor.MarkerSize
import com.piptechnologies.stickermaker.feature.create.decor.SkinTone
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/**
 * The view model's layer tools on real data: they wait for the active sticker's cut-out, a rail
 * switch onto a sticker still being cut returns to Auto, and the edits around them (limit toast,
 * skin tones, marker points, the thumbnail after a switch) behave.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class CreatePackViewModelLayerToolsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, LoveDb::class.java).allowMainThreadQueries().build()
    private val vm = CreatePackViewModel(
        context,
        MyPacksRepository(db.installedPackDao(), db.ownPackDao(), Dispatchers.Unconfined),
        PrefsRepository(context),
        Dispatchers.IO
    )
    private val state get() = vm.state.value
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private val limitToast = UiText.res(R.string.create_toast_layer_limit, DecorSpec.MAX_LAYERS)

    @After
    fun close() {
        scope.cancel()
        db.close()
    }

    @Test
    fun layerToolsAndEditsWaitForTheCutOut() {
        addShots(1)
        vm.beginCutouts()
        assertEquals(CutStatus.Pending, state.activeCut)
        assertFalse(state.layerToolsEnabled)
        for (tool in listOf(EditorTool.Add, EditorTool.Draw, EditorTool.Animate)) {
            vm.selectTool(tool)
            assertEquals("$tool is refused while cutting", EditorTool.Auto, state.tool)
        }
        vm.addEmoji("red_heart.webp")
        vm.addDecor("doodles-1.webp")
        vm.setText("miss u")
        vm.setPreset("heartbeat")
        assertTrue("nothing lands on a sticker still being cut", state.layers.isEmpty())
        assertEquals("none", state.preset)
        assertFalse(state.canUndo)

        waitFor("the cut-out") { state.activeCut == CutStatus.Done }
        assertTrue(state.layerToolsEnabled)
        vm.selectTool(EditorTool.Add)
        assertEquals(EditorTool.Add, state.tool)
        vm.addEmoji("red_heart.webp")
        assertEquals(listOf(LayerKind.Emoji), state.layers.map { it.kind })
        assertEquals("an emoji pick closes the sheet", EditorTool.Auto, state.tool)
    }

    @Test
    fun aRailSwitchOntoAStickerStillBeingCutReturnsToAuto() {
        cutOut(2)
        // Auto re-runs sticker 2's cut-out; it stays Pending until the main looper runs again.
        vm.selectSticker(1)
        vm.selectTool(EditorTool.Auto)
        assertEquals(CutStatus.Pending, state.activeCut)
        vm.selectSticker(0)
        vm.selectTool(EditorTool.Draw)
        assertEquals(EditorTool.Draw, state.tool)
        vm.beginMarker(100f, 100f)
        vm.extendMarker(200f, 160f)
        vm.endMarker()

        vm.selectSticker(1)
        assertEquals(EditorTool.Auto, state.tool)
        assertFalse(state.layerToolsEnabled)
        assertEquals("no second re-run: still the first one", CutStatus.Pending, state.activeCut)
        vm.selectSticker(0)
        assertEquals("the strokes became a drawing", listOf(LayerKind.Drawing), state.layers.map { it.kind })
    }

    @Test
    fun theLayerLimitToastShowsOncePerTextSession() {
        val toasts = collectToasts()
        cutOut(1)
        repeat(DecorSpec.MAX_LAYERS) { vm.addEmoji("red_heart.webp") }
        assertEquals(DecorSpec.MAX_LAYERS, state.layers.size)
        vm.selectTool(EditorTool.Add)
        // No text layer is selected, so every keystroke asks for a 9th layer.
        vm.setText("m")
        vm.setText("mi")
        vm.setText("mis")
        idle()
        assertEquals(1, toasts.count { it == limitToast })
        vm.closeAddSheet()
        vm.selectTool(EditorTool.Add)
        vm.setText("x")
        idle()
        assertEquals("a new session may say it again", 2, toasts.count { it == limitToast })
        vm.addEmoji("red_heart.webp")
        vm.addEmoji("red_heart.webp")
        idle()
        assertEquals("every refused emoji tap says it", 4, toasts.count { it == limitToast })
    }

    @Test
    fun aSkinToneThatIsNotOnDiskAddsNothing() {
        cutOut(1)
        vm.addEmoji("waving_hand.webp", SkinTone.Dark)
        assertTrue(state.layers.isEmpty())
        val tones = File(context.filesDir, EmojiTones.DIR).apply { mkdirs() }
        File(tones, "waving_hand_dark.png").outputStream().use {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        vm.addEmoji("waving_hand.webp", SkinTone.Dark)
        assertEquals(listOf(LayerKind.Emoji), state.layers.map { it.kind })
    }

    @Test
    fun markerPointsStayOnTheCanvas() {
        cutOut(1)
        vm.selectTool(EditorTool.Draw)
        vm.beginMarker(-200f, -200f)
        vm.extendMarker(900f, 900f)
        vm.endMarker()
        vm.selectTool(EditorTool.Brush)                               // leaving Draw makes the drawing
        val drawing = state.layers.single()
        val styles = checkNotNull(vm.data).styles
        val ink = styles.markerPx.getValue(MarkerSize.M) + 2f * styles.markerEdgeExtra
        assertEquals(256f, drawing.cx, 0.01f)
        assertEquals("the canvas edge to edge, plus the ink", 512f + ink, drawing.width, 0.01f)
    }

    @Test
    fun aSwitchThatEndsAGestureRefreshesTheThumbnail() {
        cutOut(1)
        val bare = state.items[0].stickerThumb
        vm.addEmoji("red_heart.webp")
        waitFor("the thumbnail with the emoji") { state.items[0].stickerThumb !== bare }
        val before = state.items[0].stickerThumb
        assertTrue(vm.beginLayerGesture(256f, 256f))
        vm.layerDrag(120f, 0f)
        vm.selectTool(EditorTool.Brush)                               // the UI drops the gesture; the switch ends it
        assertNull(state.liveLayerId)
        waitFor("the thumbnail after the drag") { state.items[0].stickerThumb !== before }
    }

    /** [n] camera shots (no picker, no content resolver), cut out; the decor data is loaded. */
    private fun cutOut(n: Int) {
        addShots(n)
        vm.beginCutouts()
        waitFor("the cut-outs") { state.items.all { it.cut == CutStatus.Done && it.stickerThumb != null } }
    }

    private fun addShots(n: Int) {
        repeat(n) { i ->
            val shot = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            shot.eraseColor(Color.rgb(40 * i, 90, 200))
            vm.addCameraShot(shot)
            waitFor("shot ${i + 1}") { state.items.size == i + 1 }
        }
        waitFor("the decor data") { state.dataReady }
    }

    private fun collectToasts(): List<UiText> {
        val toasts = mutableListOf<UiText>()
        scope.launch { vm.events.collect { if (it is CreateEvent.ShowToast) toasts += it.message } }
        return toasts
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))

    /** Runs the main looper (where the view model lives), its clock included, until [done]; at most 20 s. */
    private fun waitFor(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (true) {
            idle()
            if (done()) return
            check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }
}
