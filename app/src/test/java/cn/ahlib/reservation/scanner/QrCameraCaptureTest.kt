package cn.ahlib.reservation.scanner

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCameraCaptureTest {
    @Test
    fun potentialQrCodeIsTrackedWithoutBeingConfirmed() {
        assertFalse(
            isConfirmedQrCode(
                format = Barcode.FORMAT_UNKNOWN,
                rawValue = null,
            ),
        )
    }

    @Test
    fun decodedQrCodeIsConfirmed() {
        assertTrue(
            isConfirmedQrCode(
                format = Barcode.FORMAT_QR_CODE,
                rawValue = "https://example.com",
            ),
        )
    }

    @Test
    fun decodedNonQrBarcodeIsNotConfirmed() {
        assertFalse(
            isConfirmedQrCode(
                format = Barcode.FORMAT_AZTEC,
                rawValue = "not-a-qr-code",
            ),
        )
    }

    @Test
    fun cropBoundsIncludePaddingAroundQrCode() {
        val bounds = calculateQrCropBounds(
            imageWidth = 100,
            imageHeight = 80,
            left = 10,
            top = 10,
            right = 50,
            bottom = 50,
        )

        assertEquals(
            QrCropBounds(
                left = 2,
                top = 2,
                width = 56,
                height = 56,
            ),
            bounds,
        )
    }

    @Test
    fun cropBoundsAreClampedAtImageEdges() {
        val bounds = calculateQrCropBounds(
            imageWidth = 100,
            imageHeight = 80,
            left = 0,
            top = 0,
            right = 30,
            bottom = 20,
        )

        assertEquals(
            QrCropBounds(
                left = 0,
                top = 0,
                width = 36,
                height = 26,
            ),
            bounds,
        )
    }

    @Test
    fun invalidQrBoundsAreRejected() {
        val bounds = calculateQrCropBounds(
            imageWidth = 100,
            imageHeight = 80,
            left = 20,
            top = 20,
            right = 20,
            bottom = 40,
        )

        assertNull(bounds)
    }

    @Test
    fun trackingBoundsUseViewReferencedCoordinates() {
        val bounds = calculateQrTrackingBounds(
            previewWidth = 200f,
            previewHeight = 300f,
            left = 25f,
            top = 60f,
            right = 175f,
            bottom = 240f,
        )

        assertTrackingBounds(
            expected = QrTrackingBounds(
                left = 25f,
                top = 60f,
                right = 175f,
                bottom = 240f,
            ),
            actual = bounds,
        )
    }

    @Test
    fun trackingBoundsAreClampedToPreviewEdges() {
        val bounds = calculateQrTrackingBounds(
            previewWidth = 100f,
            previewHeight = 100f,
            left = -10f,
            top = 20f,
            right = 30f,
            bottom = 120f,
        )

        assertTrackingBounds(
            expected = QrTrackingBounds(
                left = 0f,
                top = 20f,
                right = 30f,
                bottom = 100f,
            ),
            actual = bounds,
        )
    }

    @Test
    fun invalidTrackingBoundsAreRejected() {
        val bounds = calculateQrTrackingBounds(
            previewWidth = 200f,
            previewHeight = 200f,
            left = 40f,
            top = 20f,
            right = 40f,
            bottom = 60f,
        )

        assertNull(bounds)
    }

    private fun assertTrackingBounds(
        expected: QrTrackingBounds,
        actual: QrTrackingBounds?,
    ) {
        requireNotNull(actual)
        assertEquals(expected.left, actual.left, FLOAT_TOLERANCE)
        assertEquals(expected.top, actual.top, FLOAT_TOLERANCE)
        assertEquals(expected.right, actual.right, FLOAT_TOLERANCE)
        assertEquals(expected.bottom, actual.bottom, FLOAT_TOLERANCE)
    }

    private companion object {
        const val FLOAT_TOLERANCE = 0.01f
    }
}
