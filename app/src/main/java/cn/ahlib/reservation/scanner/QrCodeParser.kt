package cn.ahlib.reservation.scanner

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ParsedQrCode(
    val roomId: String,
    val scanType: String?,
    val rawValue: String,
)

enum class QrCodeParseError {
    EMPTY_VALUE,
    MALFORMED_URL,
    UNSUPPORTED_SCHEME,
    UNTRUSTED_AUTHORITY,
    MISSING_ROOM_ID,
    EMPTY_ROOM_ID,
    AMBIGUOUS_ROOM_ID,
    AMBIGUOUS_SCAN_TYPE,
}

sealed interface QrCodeParseResult {
    data class Success(val code: ParsedQrCode) : QrCodeParseResult

    data class Failure(val error: QrCodeParseError) : QrCodeParseResult
}

object QrCodeParser {
    fun parse(rawValue: String): QrCodeParseResult {
        if (rawValue.isBlank()) {
            return QrCodeParseResult.Failure(QrCodeParseError.EMPTY_VALUE)
        }

        val uri = try {
            URI(rawValue.trim())
        } catch (_: Exception) {
            return QrCodeParseResult.Failure(QrCodeParseError.MALFORMED_URL)
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return QrCodeParseResult.Failure(QrCodeParseError.UNSUPPORTED_SCHEME)
        }

        if (uri.host.isNullOrBlank()) {
            return QrCodeParseResult.Failure(QrCodeParseError.MALFORMED_URL)
        }

        if (uri.rawUserInfo != null || uri.port !in setOf(-1, 443)) {
            return QrCodeParseResult.Failure(QrCodeParseError.UNTRUSTED_AUTHORITY)
        }

        val queryParameters = try {
            parseQueries(uri)
        } catch (_: IllegalArgumentException) {
            return QrCodeParseResult.Failure(QrCodeParseError.MALFORMED_URL)
        }

        val roomIdParameters = buildList {
            addAll(queryParameters.valuesFor("roomId", "id"))
        }
        if (roomIdParameters.isEmpty()) {
            return QrCodeParseResult.Failure(QrCodeParseError.MISSING_ROOM_ID)
        }
        if (roomIdParameters.size > 1) {
            return QrCodeParseResult.Failure(QrCodeParseError.AMBIGUOUS_ROOM_ID)
        }

        val roomId = roomIdParameters.single().trim()
        if (roomId.isEmpty()) {
            return QrCodeParseResult.Failure(QrCodeParseError.EMPTY_ROOM_ID)
        }

        val scanTypeParameters = queryParameters.valuesFor("scanType")
        if (scanTypeParameters.size > 1) {
            return QrCodeParseResult.Failure(QrCodeParseError.AMBIGUOUS_SCAN_TYPE)
        }
        val scanType = scanTypeParameters
            .singleOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        return QrCodeParseResult.Success(
            ParsedQrCode(
                roomId = roomId,
                scanType = scanType,
                rawValue = rawValue,
            ),
        )
    }

    private fun parseQueries(uri: URI): Map<String, List<String>> {
        val rawQueries = buildList {
            uri.rawQuery?.takeIf(String::isNotEmpty)?.let(::add)
            uri.rawFragment
                ?.substringAfter('?', missingDelimiterValue = "")
                ?.takeIf(String::isNotEmpty)
                ?.let(::add)
        }
        return rawQueries
            .flatMap { rawQuery ->
                parseQuery(rawQuery).flatMap { (name, values) ->
                    values.map { value -> name to value }
                }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
    }

    private fun parseQuery(rawQuery: String?): Map<String, List<String>> {
        if (rawQuery.isNullOrEmpty()) {
            return emptyMap()
        }

        return rawQuery
            .split('&')
            .map { parameter ->
                val separatorIndex = parameter.indexOf('=')
                val rawName = if (separatorIndex >= 0) {
                    parameter.substring(0, separatorIndex)
                } else {
                    parameter
                }
                val rawValue = if (separatorIndex >= 0) {
                    parameter.substring(separatorIndex + 1)
                } else {
                    ""
                }
                decodeQueryPart(rawName) to decodeQueryPart(rawValue)
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
    }

    private fun decodeQueryPart(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun Map<String, List<String>>.valuesFor(
        vararg names: String,
    ): List<String> {
        val normalizedNames = names.map { name -> name.lowercase() }.toSet()
        return entries
            .filter { (name, _) -> name.lowercase() in normalizedNames }
            .flatMap { (_, values) -> values }
    }

}
