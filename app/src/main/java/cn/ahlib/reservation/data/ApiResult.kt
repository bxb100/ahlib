package cn.ahlib.reservation.data

enum class ApiErrorKind {
    SESSION_EXPIRED,
    BUSINESS,
    HTTP,
    NETWORK,
    SERIALIZATION,
    VALIDATION,
    UNKNOWN,
}

class ApiException(
    val kind: ApiErrorKind,
    val code: Int? = null,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isSessionExpired: Boolean
        get() = kind == ApiErrorKind.SESSION_EXPIRED
}

sealed interface ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>

    data class Failure(val exception: ApiException) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(data))
        is ApiResult.Failure -> this
    }

fun <T> ApiResult<T>.getOrNull(): T? =
    when (this) {
        is ApiResult.Success -> data
        is ApiResult.Failure -> null
    }

internal enum class SessionValidation {
    AUTHENTICATED,
    EXPIRED,
    INCONCLUSIVE,
}

internal fun ApiResult<Boolean>.toSessionValidation(): SessionValidation =
    when (this) {
        is ApiResult.Success -> {
            if (data) {
                SessionValidation.AUTHENTICATED
            } else {
                SessionValidation.EXPIRED
            }
        }

        is ApiResult.Failure -> {
            if (exception.isSessionExpired) {
                SessionValidation.EXPIRED
            } else {
                SessionValidation.INCONCLUSIVE
            }
        }
    }
