package cn.ahlib.reservation.data

internal object ApiResultMapper {
    fun <T : Any> required(envelope: ApiEnvelope<T>): ApiResult<T> {
        val failure = envelope.failureOrNull()
        if (failure != null) {
            return failure
        }
        return envelope.data?.let { data -> ApiResult.Success(data) }
            ?: ApiResult.Failure(
                ApiException(
                    kind = ApiErrorKind.SERIALIZATION,
                    code = envelope.code,
                    message = "Response data is missing",
                ),
            )
    }

    fun <T> optional(envelope: ApiEnvelope<T>): ApiResult<T?> {
        val failure = envelope.failureOrNull()
        return failure ?: ApiResult.Success(envelope.data)
    }

    fun unit(envelope: ApiEnvelope<*>): ApiResult<Unit> {
        val failure = envelope.failureOrNull()
        return failure ?: ApiResult.Success(Unit)
    }

    private fun ApiEnvelope<*>.failureOrNull(): ApiResult.Failure? {
        if (code == SUCCESS_CODE) {
            return null
        }
        val kind = if (
            code == SESSION_EXPIRED_CODE ||
            resolvedMessage?.contains(NOT_LOGGED_IN_MESSAGE) == true
        ) {
            ApiErrorKind.SESSION_EXPIRED
        } else {
            ApiErrorKind.BUSINESS
        }
        val fallbackMessage = if (kind == ApiErrorKind.SESSION_EXPIRED) {
            "Session expired"
        } else {
            "Request failed with code $code"
        }
        return ApiResult.Failure(
            ApiException(
                kind = kind,
                code = code,
                message = resolvedMessage ?: fallbackMessage,
            ),
        )
    }

    private const val SUCCESS_CODE = 200
    private const val SESSION_EXPIRED_CODE = 401
    private const val NOT_LOGGED_IN_MESSAGE = "\u7528\u6237\u672a\u767b\u5f55"
}
