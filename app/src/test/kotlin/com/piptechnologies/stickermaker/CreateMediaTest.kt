package com.piptechnologies.stickermaker

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.feature.create.CreateMedia
import com.piptechnologies.stickermaker.feature.create.CreateSpec
import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A photo picked in Create must load as the square editor canvas. It once came
 * back null for every photo (a bounds-only decode was read as a failure), so
 * each sticker silently fell back to a blank placeholder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CreateMediaTest {

    @Test
    fun loadsAPickedPhotoAsTheSquareCanvas() {
        val photo = File(PackFixtures.packsDir.parentFile, "design/assets/ob-photo.jpg")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://media/external/images/media/42")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { FileInputStream(photo) }

        val bitmap = CreateMedia.loadSquareBitmap(context, uri)

        assertNotNull("a readable photo must load", bitmap)
        assertEquals(CreateSpec.CANVAS_SIZE, bitmap!!.width)
        assertEquals(CreateSpec.CANVAS_SIZE, bitmap.height)
    }
}
