package cn.ahlib.reservation.scanner

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression coverage for the gallery picker decoding path. Historically a
 * readable image was rejected with "could not open input stream" because the
 * bounds-only [BitmapFactory.decodeStream] pass always returns null by
 * design, which made every picked image fail with
 * [QrImageScanError.ImageFailure].
 *
 * Robolectric's [BitmapFactory] is lenient: it returns placeholder bitmaps
 * for the bounds-only pass (and for corrupt data) instead of null, unlike
 * real devices. [deviceContractDecoder] restores the documented device
 * behaviour so this suite fails exactly like a physical phone would.
 * Corrupt-image coverage lives in the instrumented
 * QrImageScannerInstrumentedTest, which exercises the genuine framework
 * decoder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class QrImageDecodingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()

    private val deviceContractDecoder = BitmapStreamDecoder { inputStream, options ->
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
        if (options.inJustDecodeBounds) null else bitmap
    }

    @Test
    fun decodesReadableImage() {
        val uri = writeImage("readable.png") { stream ->
            newFilledBitmap(640, 480).compress(
                Bitmap.CompressFormat.PNG,
                100,
                stream,
            )
        }

        val decoded = decodeQrImageForScanning(
            context.contentResolver,
            uri,
            deviceContractDecoder,
        )

        assertEquals(640, decoded.bitmap.width)
        assertEquals(480, decoded.bitmap.height)
        assertEquals(0, decoded.rotationDegrees)
    }

    @Test
    fun downsamplesLargeImage() {
        val uri = writeImage("large.jpg") { stream ->
            newFilledBitmap(4500, 3000).compress(
                Bitmap.CompressFormat.JPEG,
                90,
                stream,
            )
        }

        val decoded = decodeQrImageForScanning(
            context.contentResolver,
            uri,
            deviceContractDecoder,
        )

        val longestSide = maxOf(decoded.bitmap.width, decoded.bitmap.height)
        assertTrue(
            "longest side $longestSide should be downsampled to at most 2048",
            longestSide in 1024..2048,
        )
    }

    @Test
    fun readsExifRotation() {
        val uri = writeImage("rotated.jpg") { stream ->
            newFilledBitmap(320, 200).compress(
                Bitmap.CompressFormat.JPEG,
                90,
                stream,
            )
        }
        ExifInterface(requireNotNull(uri.path)).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString(),
            )
            saveAttributes()
        }

        val decoded = decodeQrImageForScanning(
            context.contentResolver,
            uri,
            deviceContractDecoder,
        )

        assertEquals(90, decoded.rotationDegrees)
    }

    @Test
    fun failsForMissingFile() {
        val uri = Uri.fromFile(File(temporaryFolder.root, "missing.png"))

        assertThrows(IOException::class.java) {
            decodeQrImageForScanning(
                context.contentResolver,
                uri,
                deviceContractDecoder,
            )
        }
    }

    private fun newFilledBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

    private fun writeImage(
        fileName: String,
        write: (OutputStream) -> Unit,
    ): Uri {
        val file = temporaryFolder.newFile(fileName)
        file.outputStream().use(write)
        return Uri.fromFile(file)
    }
}
