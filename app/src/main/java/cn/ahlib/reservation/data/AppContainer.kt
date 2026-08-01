package cn.ahlib.reservation.data

import android.content.Context
import cn.ahlib.reservation.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(
    context: Context,
    backgroundScope: CoroutineScope,
) {
    val repository: ReservationRepository
    val readerQrCodeRepository: ReaderQrCodeRepository

    init {
        val appContext = context.applicationContext
        backgroundScope.launch(Dispatchers.IO) {
            clearDeprecatedReaderAccountHistory(appContext)
        }
        val gson = GsonFactory.create()
        readerQrCodeRepository = ReaderQrCodeRepository(
            context = appContext,
            nativeClient = JniReaderQrNativeClient(appContext, gson),
        )
        val cookieJar = EncryptedCookieJar(context.applicationContext, gson)
        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(CacheBustingInterceptor())
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val api = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.withTrailingSlash())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ReservationApi::class.java)

        repository = ReservationRepository(
            api = api,
            passwordCipher = PasswordCipher(),
            cookieJar = cookieJar,
            gson = gson,
        )
    }

    private fun clearDeprecatedReaderAccountHistory(context: Context) {
        context.deleteSharedPreferences(DEPRECATED_READER_ACCOUNTS_PREFERENCES)
    }

    private fun String.withTrailingSlash(): String =
        if (endsWith('/')) this else "$this/"

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
        const val WRITE_TIMEOUT_SECONDS = 30L
        const val DEPRECATED_READER_ACCOUNTS_PREFERENCES = "reader_accounts"
    }
}
