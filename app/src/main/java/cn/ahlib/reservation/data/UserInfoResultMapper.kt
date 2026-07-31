package cn.ahlib.reservation.data

import com.google.gson.Gson
import com.google.gson.JsonElement

internal class UserInfoResultMapper(
    private val gson: Gson,
) {
    fun map(envelope: ApiEnvelope<JsonElement>): ApiResult<UserInfo?> =
        when (val envelopeResult = ApiResultMapper.optional(envelope)) {
            is ApiResult.Failure -> envelopeResult
            is ApiResult.Success -> mapData(envelopeResult.data)
        }

    private fun mapData(data: JsonElement?): ApiResult<UserInfo?> {
        if (data == null || data.isJsonNull || data.isAnonymousValue()) {
            return ApiResult.Success(null)
        }
        if (!data.isJsonObject) {
            return ApiResult.Failure(
                ApiException(
                    kind = ApiErrorKind.SERIALIZATION,
                    code = 200,
                    message = "User information has an unexpected format",
                ),
            )
        }
        return ApiResult.Success(gson.fromJson(data, UserInfo::class.java))
    }

    private fun JsonElement.isAnonymousValue(): Boolean {
        if (!isJsonPrimitive) {
            return false
        }
        return with(asJsonPrimitive) {
            when {
                isBoolean -> !asBoolean
                isNumber -> asDouble == 0.0
                isString -> asString.equals("false", ignoreCase = true) ||
                    asString == "0"
                else -> false
            }
        }
    }
}
