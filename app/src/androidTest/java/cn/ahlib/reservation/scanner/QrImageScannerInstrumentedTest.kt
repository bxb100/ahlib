package cn.ahlib.reservation.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end coverage for [scanQrCodeFromImage], mirroring the gallery
 * picker flow: an image file on disk is handed over as a [Uri] and must be
 * decoded and recognized without falling back to
 * [QrImageScanError.ImageFailure] for readable images.
 */
@RunWith(AndroidJUnit4::class)
class QrImageScannerInstrumentedTest {
    private lateinit var targetContext: Context
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
    }

    @After
    fun tearDown() {
        createdFiles.forEach(File::delete)
        createdFiles.clear()
    }

    @Test
    fun recognizesQrCodeFromPngImage() {
        val uri = copyAssetToCache(QR_ASSET_NAME, "picked-qr.png")

        val result = runBlocking { scanQrCodeFromImage(targetContext, uri) }

        assertEquals(
            QrImageScanResult.Success(
                ParsedQrCode(
                    roomId = "room-101",
                    scanType = null,
                    rawValue = QR_RAW_VALUE,
                ),
            ),
            result,
        )
    }

    @Test
    fun recognizesQrCodeInLargeImageRequiringDownsampling() {
        val source = loadQrAssetBitmap()
        val scaled = Bitmap.createScaledBitmap(source, 4500, 4500, false)
        source.recycle()
        val uri = writeTestImage("picked-qr-large.jpg") { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        scaled.recycle()

        val result = runBlocking { scanQrCodeFromImage(targetContext, uri) }

        assertTrue(result.toString(), result is QrImageScanResult.Success)
        assertEquals(
            "room-101",
            (result as QrImageScanResult.Success).code.roomId,
        )
    }

    @Test
    fun recognizesQrCodeFromExifRotatedJpeg() {
        val source = loadQrAssetBitmap()
        val matrix = Matrix().apply { postRotate(270f) }
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            false,
        )
        source.recycle()
        val uri = writeTestImage("picked-qr-rotated.jpg") { stream ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        rotated.recycle()
        ExifInterface(requireNotNull(uri.path)).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString(),
            )
            saveAttributes()
        }

        val result = runBlocking { scanQrCodeFromImage(targetContext, uri) }

        assertTrue(result.toString(), result is QrImageScanResult.Success)
        assertEquals(
            "room-101",
            (result as QrImageScanResult.Success).code.roomId,
        )
    }

    @Test
    fun reportsMissingQrCodeForReadableImageWithoutQrCode() {
        val blank = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        blank.eraseColor(Color.WHITE)
        val uri = writeTestImage("picked-blank.png") { stream ->
            blank.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        blank.recycle()

        val result = runBlocking { scanQrCodeFromImage(targetContext, uri) }

        assertEquals(
            QrImageScanResult.Failure(QrImageScanError.NoQrCode),
            result,
        )
    }

    @Test
    fun reportsImageFailureForUnreadableFile() {
        val uri = writeTestImage("picked-corrupt.png") { stream ->
            stream.write(byteArrayOf(0x13, 0x37, 0x00, 0x42))
        }

        val result = runBlocking { scanQrCodeFromImage(targetContext, uri) }

        assertTrue(result.toString(), result is QrImageScanResult.Failure)
        assertTrue(
            (result as QrImageScanResult.Failure).error
                is QrImageScanError.ImageFailure,
        )
    }

    private fun loadQrAssetBitmap(): Bitmap {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.open(QR_ASSET_NAME).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) {
                "Could not decode test asset $QR_ASSET_NAME"
            }
        }
    }

    private fun copyAssetToCache(assetName: String, fileName: String): Uri {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return writeTestImage(fileName) { stream ->
            assets.open(assetName).use { input -> input.copyTo(stream) }
        }
    }

    private fun writeTestImage(
        fileName: String,
        write: (OutputStream) -> Unit,
    ): Uri {
        val file = File(targetContext.cacheDir, fileName)
        createdFiles += file
        file.outputStream().use(write)
        return Uri.fromFile(file)
    }

    private companion object {
        const val QR_ASSET_NAME = "qr_room_101.png"
        const val QR_RAW_VALUE =
            "https://www.lib.ah.cn/reservation?id=room-101"
    }
}
