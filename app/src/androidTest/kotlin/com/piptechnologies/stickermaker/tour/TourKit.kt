package com.piptechnologies.stickermaker.tour

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.FirebaseApp
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Collections
import java.util.IdentityHashMap
import org.hamcrest.CoreMatchers.anyOf
import org.json.JSONArray
import org.json.JSONObject

/** Device control, screenshots and the manifest CI turns into the screen map. */
object Tour {
    const val WHATSAPP = "com.whatsapp"

    /** Pushed by .github/workflows/screens.yml before the tour starts. */
    private const val STUB_APK = "/data/local/tmp/whatsapp-stub.apk"

    val instrumentation: Instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val targetContext: Context get() = instrumentation.targetContext
    val testContext: Context get() = instrumentation.context
    val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    /** Pulled by CI with `run-as` once the tour is done. */
    val outDir: File get() = File(targetContext.filesDir, "tour")

    private val shots = JSONArray()

    fun reset() {
        outDir.deleteRecursively()
        outDir.mkdirs()
        writeManifest()
    }

    fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { String(it.readBytes()) }
    }

    // ------------------------------------------------------------ capture //

    /** Full-screen screenshot (status bar, sheets and other apps' dialogs included). */
    fun screenshot(fileName: String): File {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("UiAutomation.takeScreenshot returned null")
        val file = File(outDir, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    fun recordFrame(frame: Frame, notes: String) {
        record(
            JSONObject()
                .put("key", String.format(java.util.Locale.ROOT, "%02d", frame.n))
                .put("frame", frame.n)
                .put("section", frame.section)
                .put("title", frame.title)
                .put("route", frame.route)
                .put("file", frame.fileName)
                .put("status", "pass")
                .put("notes", notes)
        )
    }

    fun recordExtra(key: String, title: String, route: String, fileName: String, notes: String) {
        record(
            JSONObject()
                .put("key", key)
                .put("frame", JSONObject.NULL)
                .put("section", "Extra")
                .put("title", title)
                .put("route", route)
                .put("file", fileName)
                .put("status", "pass")
                .put("notes", notes)
        )
    }

    /** A step that threw: keeps a screenshot of the failing state for the map. */
    fun recordFailure(step: String, error: Throwable) {
        val safe = step.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val file = "fail-$safe.png"
        runCatching { screenshot(file) }
        record(
            JSONObject()
                .put("key", "fail-$safe")
                .put("frame", JSONObject.NULL)
                .put("section", "Failures")
                .put("title", step)
                .put("route", "")
                .put("file", file)
                .put("status", "fail")
                .put("notes", "")
                .put("error", "${error.javaClass.simpleName}: ${error.message}".take(900))
        )
    }

    private fun record(entry: JSONObject) {
        synchronized(shots) {
            shots.put(entry.put("at", System.currentTimeMillis()))
            writeManifest()
        }
    }

    private fun writeManifest() {
        val metrics = targetContext.resources.displayMetrics
        val json = JSONObject()
            .put(
                "device",
                JSONObject()
                    .put("model", Build.MODEL)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("widthPx", metrics.widthPixels)
                    .put("heightPx", metrics.heightPixels)
                    .put("densityDpi", metrics.densityDpi)
            )
            .put("shots", shots)
        File(outDir, "manifest.json").writeText(json.toString(2))
    }

    // ------------------------------------------------------------ network //

    fun setOnline(online: Boolean) {
        if (online) {
            shell("cmd connectivity airplane-mode disable")
            shell("svc wifi enable")
            shell("svc data enable")
        } else {
            shell("cmd connectivity airplane-mode enable")
        }
        if (!waitForInternet(online, 20_000) && !online) {
            // Some images keep Wi-Fi up in airplane mode; switch the radios off directly.
            shell("svc wifi disable")
            shell("svc data disable")
        }
        check(waitForInternet(online, 45_000)) { "network did not go ${if (online) "up" else "down"}" }
        demoNetwork(online)
    }

    private fun waitForInternet(expected: Boolean, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (backendReachable() == expected) return true
            SystemClock.sleep(500)
        }
        return backendReachable() == expected
    }

    /** Whether the app can reach the Firebase emulators on the host (10.0.2.2). */
    private fun backendReachable(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("10.0.2.2", 9199), 1_500) }
        true
    } catch (e: IOException) {
        false
    }

    // ------------------------------------------------------- status bar //

    /** Clean, stable status bar for the screenshots (SystemUI demo mode). */
    fun enterDemoMode() {
        shell("settings put global sysui_demo_allowed 1")
        demo("-e command enter")
        demo("-e command clock -e hhmm 0941")
        demo("-e command battery -e level 100 -e plugged false")
        demo("-e command notifications -e visible false")
        demoNetwork(online = true)
    }

    fun exitDemoMode() {
        demo("-e command exit")
    }

    private fun demoNetwork(online: Boolean) {
        if (online) {
            demo("-e command network -e airplane hide -e wifi show -e level 4 -e mobile hide")
        } else {
            demo("-e command network -e airplane show -e wifi hide -e mobile hide")
        }
    }

    private fun demo(args: String) {
        shell("am broadcast -a com.android.systemui.demo $args")
    }

    // ------------------------------------------------- WhatsApp stub //

    fun isInstalled(packageName: String): Boolean =
        shell("pm list packages $packageName").lines().any { it.trim() == "package:$packageName" }

    fun installWhatsAppStub() {
        if (isInstalled(WHATSAPP)) return
        val output = shell("pm install -r -t $STUB_APK")
        check(isInstalled(WHATSAPP)) { "pm install $STUB_APK failed: $output" }
    }

    fun uninstallWhatsAppStub() {
        if (isInstalled(WHATSAPP)) shell("pm uninstall $WHATSAPP")
        check(!isInstalled(WHATSAPP)) { "com.whatsapp is still installed" }
    }

    /** Waits for the stub's confirm dialog (its Add button). */
    fun awaitStubDialog(timeoutMs: Long = 30_000): UiObject2 =
        device.wait(Until.findObject(By.desc("stub-add")), timeoutMs)
            ?: throw AssertionError("The WhatsApp stub dialog did not open within ${timeoutMs}ms")

    /** The stub's contract report; fails the step when the provider broke the contract. */
    fun stubContractReport(): String {
        device.findObject(By.desc("stub-contract-ok"))?.let { return it.text.orEmpty() }
        val failed = device.findObject(By.desc("stub-contract-failed"))?.text
        throw AssertionError("WhatsApp contract check failed:\n${failed ?: "no report on screen"}")
    }

    fun confirmStub() {
        awaitStubDialog().click()
        device.wait(Until.gone(By.desc("stub-add")), 15_000)
    }

    fun cancelStubIfOpen() {
        device.findObject(By.desc("stub-cancel"))?.click()
        device.wait(Until.gone(By.desc("stub-cancel")), 5_000)
    }

    // --------------------------------------------------------- intents //

    /** Answers the system photo picker with [uris], as a multi-select pick would. */
    fun stubPhotoPicker(uris: List<Uri>) {
        val clip = ClipData.newRawUri("photos", uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        val data = Intent().apply {
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intents.intending(
            anyOf(
                hasAction(MediaStore.ACTION_PICK_IMAGES),
                hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                hasAction(Intent.ACTION_OPEN_DOCUMENT),
                hasAction(Intent.ACTION_GET_CONTENT)
            )
        ).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, data))
    }

    /** Pretends a mail app took the feedback (the emulator has none). */
    fun stubMailComposer() {
        Intents.intending(hasAction(Intent.ACTION_SENDTO))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }
}

/** Counts Coil requests in flight so screenshots wait for thumbnails. */
object ImageTracker : EventListener {
    private val inFlight: MutableSet<ImageRequest> =
        Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<ImageRequest, Boolean>()))

    fun install(context: Context) {
        Coil.setImageLoader(ImageLoader.Builder(context).eventListener(this).build())
    }

    fun idle(): Boolean = inFlight.isEmpty()

    override fun onStart(request: ImageRequest) {
        inFlight += request
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        inFlight -= request
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        inFlight -= request
    }

    override fun onCancel(request: ImageRequest) {
        inFlight -= request
    }
}

/**
 * Admin access to the Storage emulator the app is pointed at (10.0.2.2 is the
 * CI host). "Bearer owner" bypasses the security rules, like the Admin SDK.
 */
object StorageEmulator {
    private const val BASE = "http://10.0.2.2:9199/v0/b"

    private val bucket: String
        get() = FirebaseApp.getInstance().options.storageBucket
            ?: throw IllegalStateException("google-services.json has no storage bucket")

    fun read(path: String): ByteArray = call("GET", "$BASE/$bucket/o/${Uri.encode(path)}?alt=media")

    fun delete(path: String) {
        call("DELETE", "$BASE/$bucket/o/${Uri.encode(path)}")
    }

    fun write(path: String, bytes: ByteArray, contentType: String) {
        call("POST", "$BASE/$bucket/o?uploadType=media&name=${Uri.encode(path)}", bytes, contentType)
    }

    private fun call(method: String, url: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer owner")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("$method $url answered HTTP $code")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * The Android emulator console, reached from the guest at 10.0.2.2:5554 (CI
 * creates an empty ~/.emulator_console_auth_token so no auth is needed).
 * Used to slow the network so the transient Downloading state can be captured.
 */
object EmulatorConsole {
    fun networkSpeed(preset: String): Boolean = command("network speed $preset")

    private fun command(line: String): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("10.0.2.2", 5554), 3_000)
                socket.soTimeout = 5_000
                val input = socket.getInputStream().bufferedReader()
                val output = socket.getOutputStream().bufferedWriter()
                val banner = readReply(input)
                if (banner.contains("Authentication required")) {
                    false
                } else {
                    output.write("$line\r\n")
                    output.flush()
                    val reply = readReply(input)
                    output.write("quit\r\n")
                    output.flush()
                    reply.trimEnd().endsWith("OK")
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun readReply(input: java.io.BufferedReader): String {
        val reply = StringBuilder()
        while (true) {
            val line = input.readLine() ?: break
            reply.appendLine(line)
            if (line.startsWith("OK") || line.startsWith("KO")) break
        }
        return reply.toString()
    }
}

/** Photos for the Create flow, written to MediaStore like camera-roll pictures. */
object TourPhotos {
    fun insert(count: Int, prefix: String): List<Uri> {
        val source = Tour.testContext.assets.open("ob-photo.jpg").use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("androidTest asset ob-photo.jpg is missing")
        val variants = listOf(
            source,
            mirror(source),
            crop(source, 0.8f),
            mirror(crop(source, 0.88f)),
            crop(source, 0.7f)
        )
        return variants.take(count).mapIndexed { index, bitmap ->
            insertJpeg(bitmap, "$prefix-${index + 1}-${System.currentTimeMillis()}.jpg")
        }
    }

    private fun insertJpeg(bitmap: Bitmap, name: String): Uri {
        val resolver = Tour.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LoveStickersTour")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore refused $name")
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            ?: throw IllegalStateException("MediaStore gave no stream for $name")
        return uri
    }

    private fun mirror(bitmap: Bitmap): Bitmap =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { preScale(-1f, 1f) }, true)

    private fun crop(bitmap: Bitmap, fraction: Float): Bitmap {
        val w = (bitmap.width * fraction).toInt()
        val h = (bitmap.height * fraction).toInt()
        return Bitmap.createBitmap(bitmap, (bitmap.width - w) / 2, (bitmap.height - h) / 2, w, h)
    }
}

// ------------------------------------------------------------ semantics //

/** Matches a node whose click action carries [label] (onClickLabel in the app). */
fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("OnClick label == '$label'") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

fun hasClickLabelStartingWith(prefix: String): SemanticsMatcher =
    SemanticsMatcher("OnClick label starts with '$prefix'") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label?.startsWith(prefix) == true
    }

private val verticalLazyList: SemanticsMatcher =
    hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

fun ComposeTestRule.exists(matcher: SemanticsMatcher): Boolean =
    onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

fun ComposeTestRule.waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 20_000) {
    try {
        waitUntil(timeoutMs) { exists(matcher) }
    } catch (e: ComposeTimeoutException) {
        throw AssertionError("Timed out after ${timeoutMs}ms waiting for: ${matcher.description}")
    }
}

fun ComposeTestRule.waitGone(matcher: SemanticsMatcher, timeoutMs: Long = 20_000) {
    try {
        waitUntil(timeoutMs) { !exists(matcher) }
    } catch (e: ComposeTimeoutException) {
        throw AssertionError("Timed out after ${timeoutMs}ms waiting for this to go away: ${matcher.description}")
    }
}

/** Waits for a node, brings it on screen (lazy list or scroll column) and clicks it. */
fun ComposeTestRule.tap(matcher: SemanticsMatcher, timeoutMs: Long = 20_000) {
    reveal(matcher, timeoutMs)
    onAllNodes(matcher).onFirst().performClick()
    waitForIdle()
}

/**
 * Scrolls the screen's vertical lazy list (or scroll column) until [matcher]
 * is composed and on screen. Polls, because lists often compose after
 * navigation or once their data arrives.
 */
fun ComposeTestRule.reveal(matcher: SemanticsMatcher, timeoutMs: Long = 20_000) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (!exists(matcher)) {
        if (exists(verticalLazyList)) {
            runCatching { onAllNodes(verticalLazyList).onFirst().performScrollToNode(matcher) }
            if (exists(matcher)) break
        }
        if (SystemClock.uptimeMillis() > deadline) {
            throw AssertionError("Timed out after ${timeoutMs}ms waiting for: ${matcher.description}")
        }
        SystemClock.sleep(250)
    }
    runCatching { onAllNodes(matcher).onFirst().performScrollTo() }
    waitForIdle()
}

/** Scrolls the vertical lazy list back to its first item. */
fun ComposeTestRule.scrollListToTop(firstItem: SemanticsMatcher) {
    if (exists(verticalLazyList)) {
        runCatching { onAllNodes(verticalLazyList).onFirst().performScrollToNode(firstItem) }
        waitForIdle()
    }
}

/** Idle UI, finished image loads, then a beat for crossfades before a capture. */
fun ComposeTestRule.settle(extraMs: Long = 600) {
    runCatching { waitForIdle() }
    try {
        waitUntil(20_000) { ImageTracker.idle() }
    } catch (ignored: ComposeTimeoutException) {
        // A stuck request should not block the tour; the screenshot shows what loaded.
    }
    runCatching { waitForIdle() }
    SystemClock.sleep(extraMs)
}
