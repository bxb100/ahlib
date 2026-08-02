package cn.ahlib.reservation.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ReaderQrCodeFailure {
    SESSION_EXPIRED,
    NETWORK,
    TLS,
    HTTP,
    INVALID_RESPONSE,
    BUSINESS,
    NATIVE_UNAVAILABLE,
    QR_CONTENT_INVALID,
}

sealed interface ReaderQrCodeResult {
    data class Success(val content: String) : ReaderQrCodeResult

    data class Failure(
        val reason: ReaderQrCodeFailure,
        val message: String? = null,
    ) : ReaderQrCodeResult
}

class ReaderQrCodeRepository internal constructor(
    private val nativeClient: ReaderQrNativeClient,
) {
    suspend fun refresh(
        cookieHeader: String,
    ): ReaderQrCodeResult = withContext(Dispatchers.IO) {
        if (cookieHeader.isBlank()) {
            return@withContext ReaderQrCodeResult.Failure(
                ReaderQrCodeFailure.SESSION_EXPIRED,
            )
        }
        when (val result = nativeClient.fetch(cookieHeader)) {
            is ReaderQrCodeResult.Success -> {
                if (!canEncodeReaderQrCode(result.content)) {
                    return@withContext ReaderQrCodeResult.Failure(
                        ReaderQrCodeFailure.QR_CONTENT_INVALID,
                    )
                }
                result
            }

            is ReaderQrCodeResult.Failure -> result
        }
    }
}
