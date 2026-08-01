package cn.ahlib.reservation.data

import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException

class ReservationRepository internal constructor(
    private val api: ReservationApi,
    private val passwordCipher: PasswordCipher,
    private val cookieJar: ClearableCookieJar,
    gson: Gson,
) {
    private val userInfoResultMapper = UserInfoResultMapper(gson)
    private val cacheLock = Any()
    private val categoryCache =
        mutableMapOf<String?, CacheEntry<List<Category>>>()
    private val roomPageCache =
        mutableMapOf<RoomPageCacheKey, CacheEntry<RoomPage>>()
    private val roomDetailCache = mutableMapOf<String, CacheEntry<RoomDetail>>()

    suspend fun getCaptcha(
        width: Int = 150,
        height: Int = 50,
        lineSpacing: Int = 20,
    ): ApiResult<Captcha> {
        if (width <= 0 || height <= 0 || lineSpacing < 0) {
            return validationFailure("Captcha dimensions are invalid")
        }
        return safeCall(
            call = { api.getCaptcha(width, height, lineSpacing) },
            transform = ApiResultMapper::required,
        )
    }

    suspend fun login(
        readerId: String,
        password: String,
        verifyCode: String,
        uniCode: String,
        loginTime: Int = 2,
    ): ApiResult<UserInfo> {
        if (
            readerId.isBlank() ||
            password.isEmpty() ||
            verifyCode.isBlank() ||
            uniCode.isBlank()
        ) {
            return validationFailure("Login fields must not be empty")
        }
        if (loginTime !in LOGIN_RETENTION_DAYS) {
            return validationFailure("Login retention must be 2, 15, or 30 days")
        }

        val result = safeCall(
            call = {
                api.login(
                    LoginRequest(
                        readerId = readerId,
                        userPassword = passwordCipher.encrypt(password),
                        verifyCode = verifyCode,
                        uniCode = uniCode,
                        loginTime = loginTime,
                    ),
                )
            },
            transform = ApiResultMapper::required,
        )
        if (result is ApiResult.Success) {
            val tokenSaved = result.data.token
                ?.let { token ->
                    withContext(Dispatchers.IO) {
                        cookieJar.saveAuthenticationToken(token, loginTime)
                    }
                }
                ?: false
            if (!tokenSaved) {
                return ApiResult.Failure(
                    ApiException(
                        kind = ApiErrorKind.SERIALIZATION,
                        message = "Login response did not contain a valid authentication token",
                    ),
                )
            }
        }
        return result
    }

    suspend fun isLoggedIn(): ApiResult<Boolean> =
        safeCall(
            call = api::isLoggedIn,
            transform = ApiResultMapper::required,
        )

    suspend fun logout(): ApiResult<Unit> =
        try {
            safeCall(
                call = api::logout,
                transform = ApiResultMapper::unit,
            )
        } finally {
            clearLocalSession()
        }

    internal fun clearLocalSession() {
        clearCaches()
        cookieJar.clear()
    }

    internal fun webViewCookies(url: String): List<String> {
        val httpUrl = url.toHttpUrlOrNull() ?: return emptyList()
        return cookieJar.loadForRequest(httpUrl).map { cookie ->
            buildString {
                append(cookie.name)
                append('=')
                append(cookie.value)
                append("; Path=")
                append(cookie.path)
                if (cookie.secure) {
                    append("; Secure")
                }
                append("; SameSite=Lax")
            }
        }
    }

    internal fun authenticationCookieHeader(): String? =
        cookieJar.loadForRequest(AUTHENTICATION_COOKIE_URL.toHttpUrl())
            .firstOrNull { cookie -> cookie.name == AUTHENTICATION_COOKIE_NAME }
            ?.let { cookie -> "${cookie.name}=${cookie.value}" }

    suspend fun sendMessageCode(
        mobile: String,
        verifyCode: String,
        uniCode: String,
    ): ApiResult<Unit> {
        if (mobile.isBlank() || verifyCode.isBlank() || uniCode.isBlank()) {
            return validationFailure("Message verification fields must not be empty")
        }
        return safeCall(
            call = { api.sendMessageCode(mobile, verifyCode, uniCode) },
            transform = ApiResultMapper::unit,
        )
    }

    suspend fun updatePhone(
        mobile: String,
        mobileCode: String,
    ): ApiResult<Unit> {
        if (mobile.isBlank() || mobileCode.isBlank()) {
            return validationFailure("Phone update fields must not be empty")
        }
        return safeCall(
            call = {
                api.updatePhone(
                    UpdatePhoneRequest(
                        mobileCode = mobileCode,
                        mobile = mobile,
                    ),
                )
            },
            transform = ApiResultMapper::unit,
        )
    }

    suspend fun getUserInfo(): ApiResult<UserInfo?> =
        safeCall(
            call = api::getUserInfo,
            transform = userInfoResultMapper::map,
        )

    suspend fun getWechatConfig(pageUrl: String): ApiResult<WechatConfig> {
        if (pageUrl.isBlank()) {
            return validationFailure("Page URL must not be empty")
        }
        return safeCall(
            call = { api.getWechatConfig(pageUrl.trim()) },
            transform = ApiResultMapper::required,
        )
    }

    suspend fun getCategories(
        siteCode: String? = null,
        forceRefresh: Boolean = false,
    ): ApiResult<List<Category>> {
        val normalizedSiteCode = siteCode?.trim()?.takeIf(String::isNotEmpty)
        if (!forceRefresh) {
            synchronized(cacheLock) {
                categoryCache[normalizedSiteCode]
                    ?.takeIf { entry -> entry.isFresh(CATEGORY_CACHE_MILLIS) }
                    ?.let { entry -> return ApiResult.Success(entry.value) }
            }
        }
        val result = safeCall(
            call = { api.getCategories(normalizedSiteCode) },
            transform = ApiResultMapper::required,
        )
        if (result is ApiResult.Success) {
            synchronized(cacheLock) {
                categoryCache[normalizedSiteCode] = CacheEntry(result.data)
            }
        }
        return result
    }

    suspend fun findReservationCategory(
        forceRefresh: Boolean = false,
    ): ApiResult<Category?> =
        getCategories(forceRefresh = forceRefresh)
            .map { categories -> categories.findReservationCategory() }

    suspend fun getRooms(
        categoryId: String,
        pageNum: Int = 1,
        pageSize: Int = 20,
        resourcesType: String = "",
        keywords: String = "",
        year: String? = null,
        total: Int = 0,
        forceRefresh: Boolean = false,
    ): ApiResult<RoomPage> {
        if (categoryId.isBlank()) {
            return validationFailure("Category id must not be empty")
        }
        if (pageNum <= 0 || pageSize <= 0 || total < 0) {
            return validationFailure("Room pagination values are invalid")
        }
        val cacheKey = RoomPageCacheKey(
            categoryId = categoryId,
            pageNum = pageNum,
            pageSize = pageSize,
            resourcesType = resourcesType,
            keywords = keywords,
            year = year,
            total = total,
        )
        if (!forceRefresh) {
            synchronized(cacheLock) {
                roomPageCache[cacheKey]
                    ?.takeIf { entry -> entry.isFresh(ROOM_PAGE_CACHE_MILLIS) }
                    ?.let { entry -> return ApiResult.Success(entry.value) }
            }
        }
        val result = safeCall(
            call = {
                api.getRooms(
                    pageNum = pageNum,
                    pageSize = pageSize,
                    categoryId = categoryId,
                    resourcesType = resourcesType,
                    keywords = keywords,
                    year = year,
                    total = total,
                )
            },
            transform = ApiResultMapper::required,
        )
        if (result is ApiResult.Success) {
            synchronized(cacheLock) {
                roomPageCache[cacheKey] = CacheEntry(result.data)
            }
        }
        return result
    }

    suspend fun getRoomDetail(
        roomId: String,
        forceRefresh: Boolean = false,
    ): ApiResult<RoomDetail> {
        if (roomId.isBlank()) {
            return validationFailure("Room id must not be empty")
        }
        if (!forceRefresh) {
            synchronized(cacheLock) {
                roomDetailCache[roomId]
                    ?.takeIf { entry -> entry.isFresh(ROOM_DETAIL_CACHE_MILLIS) }
                    ?.let { entry -> return ApiResult.Success(entry.value) }
            }
        }
        val result = safeCall(
            call = { api.getRoomDetail(roomId) },
            transform = ApiResultMapper::required,
        )
        if (result is ApiResult.Success) {
            synchronized(cacheLock) {
                roomDetailCache[roomId] = CacheEntry(result.data)
            }
        }
        return result
    }

    suspend fun getRoomAvailability(
        roomId: String,
    ): ApiResult<List<AvailabilityDay>> {
        if (roomId.isBlank()) {
            return validationFailure("Room id must not be empty")
        }
        return safeCall(
            call = { api.getRoomAvailability(roomId) },
            transform = ApiResultMapper::required,
        )
    }

    suspend fun createReservation(
        request: CreateReservationRequest,
    ): ApiResult<Unit> {
        if (
            request.venueName.isBlank() ||
            request.dateTime.isBlank() ||
            request.bookingId.isBlank()
        ) {
            return validationFailure("Reservation fields must not be empty")
        }
        val result = safeCall(
            call = { api.createReservation(request) },
            transform = ApiResultMapper::unit,
        )
        return result
    }

    suspend fun getMyReservations(
        pageNum: Int = 1,
        pageSize: Int = 10,
        type: String = "1",
        total: Int = 0,
    ): ApiResult<ReservationPage> {
        if (pageNum <= 0 || pageSize <= 0 || type.isBlank() || total < 0) {
            return validationFailure("Reservation pagination values are invalid")
        }
        return safeCall(
            call = {
                api.getMyReservations(
                    pageNum = pageNum,
                    pageSize = pageSize,
                    type = type,
                    total = total,
                )
            },
            transform = ApiResultMapper::required,
        )
    }

    suspend fun cancelReservation(id: String): ApiResult<Unit> {
        if (id.isBlank()) {
            return validationFailure("Reservation id must not be empty")
        }
        val result = safeCall(
            call = { api.cancelReservation(CancelReservationRequest(id)) },
            transform = ApiResultMapper::unit,
        )
        return result
    }

    suspend fun getCurrentReservation(
        roomId: String,
    ): ApiResult<AppointmentRecord?> {
        if (roomId.isBlank()) {
            return validationFailure("Room id must not be empty")
        }
        return safeCall(
            call = { api.getCurrentReservation(roomId) },
            transform = ApiResultMapper::optional,
        )
    }

    suspend fun roomSign(request: RoomSignRequest): ApiResult<Unit> {
        if (request.id.isBlank() || request.bookingId.isBlank()) {
            return validationFailure("Sign-in identifiers must not be empty")
        }
        return safeCall(
            call = { api.roomSign(request) },
            transform = ApiResultMapper::unit,
        )
    }

    suspend fun roomSignOff(request: RoomSignOffRequest): ApiResult<Unit> {
        if (request.id.isBlank() || request.bookingId.isBlank()) {
            return validationFailure("Sign-out identifiers must not be empty")
        }
        return safeCall(
            call = { api.roomSignOff(request) },
            transform = ApiResultMapper::unit,
        )
    }

    private suspend fun <Envelope, Result> safeCall(
        call: suspend () -> Envelope,
        transform: (Envelope) -> ApiResult<Result>,
    ): ApiResult<Result> = withContext(Dispatchers.IO) {
        try {
            transform(call())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            val statusCode = exception.code()
            val kind = if (statusCode == SESSION_EXPIRED_CODE) {
                ApiErrorKind.SESSION_EXPIRED
            } else {
                ApiErrorKind.HTTP
            }
            ApiResult.Failure(
                ApiException(
                    kind = kind,
                    code = statusCode,
                    message = exception.message(),
                    cause = exception,
                ),
            )
        } catch (exception: JsonParseException) {
            ApiResult.Failure(
                ApiException(
                    kind = ApiErrorKind.SERIALIZATION,
                    message = "Unable to read the server response",
                    cause = exception,
                ),
            )
        } catch (exception: IOException) {
            ApiResult.Failure(
                ApiException(
                    kind = ApiErrorKind.NETWORK,
                    message = "Unable to reach the server",
                    cause = exception,
                ),
            )
        } catch (exception: Exception) {
            ApiResult.Failure(
                ApiException(
                    kind = ApiErrorKind.UNKNOWN,
                    message = exception.message ?: "Unexpected request failure",
                    cause = exception,
                ),
            )
        }
    }

    private fun validationFailure(message: String): ApiResult.Failure =
        ApiResult.Failure(
            ApiException(
                kind = ApiErrorKind.VALIDATION,
                message = message,
            ),
        )

    private fun clearCaches() {
        synchronized(cacheLock) {
            categoryCache.clear()
            roomPageCache.clear()
            roomDetailCache.clear()
        }
    }

    private companion object {
        const val AUTHENTICATION_COOKIE_URL = "https://www.lib.ah.cn/"
        val LOGIN_RETENTION_DAYS = setOf(2, 15, 30)
        const val SESSION_EXPIRED_CODE = 401
        const val CATEGORY_CACHE_MILLIS = 10L * 60L * 1_000L
        const val ROOM_PAGE_CACHE_MILLIS = 60_000L
        const val ROOM_DETAIL_CACHE_MILLIS = 10L * 60L * 1_000L
    }
}

private data class CacheEntry<T>(
    val value: T,
    val storedAtMillis: Long = System.currentTimeMillis(),
) {
    fun isFresh(
        lifetimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = nowMillis - storedAtMillis in 0..lifetimeMillis
}

private data class RoomPageCacheKey(
    val categoryId: String,
    val pageNum: Int,
    val pageSize: Int,
    val resourcesType: String,
    val keywords: String,
    val year: String?,
    val total: Int,
)
