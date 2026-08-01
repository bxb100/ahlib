package cn.ahlib.reservation.data

import android.content.Context
import com.google.gson.Gson

internal interface ReaderQrNativeClient {
    fun fetch(cookieHeader: String): ReaderQrCodeResult
}

internal class JniReaderQrNativeClient(
    private val context: Context,
    private val gson: Gson,
) : ReaderQrNativeClient {
    private val initialized by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            System.loadLibrary(NATIVE_LIBRARY_NAME)
            ReaderQrNativeBridge.initialize(context.applicationContext)
        }.getOrDefault(false)
    }

    override fun fetch(cookieHeader: String): ReaderQrCodeResult {
        if (!initialized) {
            return ReaderQrCodeResult.Failure(ReaderQrCodeFailure.NATIVE_UNAVAILABLE)
        }
        val response = runCatching {
            gson.fromJson(
                ReaderQrNativeBridge.fetch(cookieHeader),
                NativeReaderQrResponse::class.java,
            )
        }.getOrNull()
            ?: return ReaderQrCodeResult.Failure(ReaderQrCodeFailure.INVALID_RESPONSE)
        val content = response.content
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        if (response.ok && content != null) {
            return ReaderQrCodeResult.Success(content)
        }
        return ReaderQrCodeResult.Failure(
            reason = response.kind.toReaderQrCodeFailure(),
            message = response.message?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private companion object {
        const val NATIVE_LIBRARY_NAME = "reader_qr_native"
    }
}

internal object ReaderQrNativeBridge {
    @JvmStatic
    external fun initialize(context: Context): Boolean

    @JvmStatic
    external fun fetch(cookieHeader: String): String
}

internal data class NativeReaderQrResponse(
    val ok: Boolean = false,
    val content: String? = null,
    val kind: String? = null,
    val message: String? = null,
)

internal fun String?.toReaderQrCodeFailure(): ReaderQrCodeFailure = when (this) {
    "session_expired" -> ReaderQrCodeFailure.SESSION_EXPIRED
    "network" -> ReaderQrCodeFailure.NETWORK
    "tls" -> ReaderQrCodeFailure.TLS
    "http" -> ReaderQrCodeFailure.HTTP
    "business" -> ReaderQrCodeFailure.BUSINESS
    else -> ReaderQrCodeFailure.INVALID_RESPONSE
}
