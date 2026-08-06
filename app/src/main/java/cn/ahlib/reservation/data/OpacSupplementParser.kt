package cn.ahlib.reservation.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class OpacSupplementParser {
    fun parseHoldings(json: String): Map<String, List<OpacHolding>?>? {
        val root = parseObject(json) ?: return null
        val previewsElement = root.get("previews") ?: return null
        val previews = previewsElement
            .takeIf { element -> element.isJsonObject }
            ?.asJsonObject
            ?: return null
        val parsedHoldings = linkedMapOf<String, List<OpacHolding>?>()
        previews.entrySet().forEach previewLoop@{ (bookId, value) ->
            if (!value.isJsonArray) {
                parsedHoldings[bookId] = null
                return@previewLoop
            }
            val holdings = mutableListOf<OpacHolding>()
            var isMalformed = false
            value.asJsonArray.forEach holdingLoop@{ element ->
                val holding = element
                    .takeIf { item -> item.isJsonObject }
                    ?.asJsonObject
                    ?.toHolding()
                if (holding == null) {
                    isMalformed = true
                    return@holdingLoop
                }
                if (holding.totalCopies > 0) {
                    holdings += holding
                }
            }
            parsedHoldings[bookId] = if (isMalformed) {
                null
            } else {
                holdings.distinct()
            }
        }
        return parsedHoldings
    }

    fun parseCovers(jsonp: String): Map<String, String>? {
        val firstBrace = jsonp.indexOf('{')
        val lastBrace = jsonp.lastIndexOf('}')
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return null
        }
        val root = parseObject(jsonp.substring(firstBrace, lastBrace + 1)) ?: return null
        val results = root.getAsJsonArray("result") ?: return null
        return buildMap {
            results.forEach { element ->
                val record = element.takeIf { item -> item.isJsonObject }?.asJsonObject
                    ?: return@forEach
                val isbn = record.stringValue("isbn")
                    ?.normalizedOpacIsbn()
                    ?: return@forEach
                val coverUrl = sequenceOf(
                    record.stringValue("resourceLink"),
                    record.stringValue("coverlink"),
                ).mapNotNull(::validatedCoverUrl).firstOrNull() ?: return@forEach
                putIfAbsent(isbn, coverUrl)
            }
        }
    }

    private fun parseObject(value: String): JsonObject? = runCatching {
        JsonParser.parseString(value)
            .takeIf { element -> element.isJsonObject }
            ?.asJsonObject
    }.getOrNull()

    private fun JsonObject.toHolding(): OpacHolding? {
        val totalCopies = nonNegativeIntValue("copycount") ?: return null
        val availableCopies = nonNegativeIntValue("loanableCount") ?: return null
        return OpacHolding(
            libraryName = stringValue("curlibName"),
            locationName = stringValue("curlocalName"),
            callNumber = stringValue("callno"),
            availableCopies = availableCopies.coerceAtMost(totalCopies),
            totalCopies = totalCopies,
        )
    }

    private fun JsonObject.stringValue(name: String): String? =
        get(name)
            ?.takeIf { element -> element.isJsonPrimitive && element.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.replace(WHITESPACE_PATTERN, " ")
            ?.takeIf { value ->
                value.isNotEmpty() &&
                    value.length <= MAX_TEXT_LENGTH &&
                    value.none(Character::isISOControl)
            }

    private fun JsonObject.nonNegativeIntValue(name: String): Int? =
        get(name)
            ?.takeIf { element -> element.isJsonPrimitive }
            ?.asString
            ?.toIntOrNull()
            ?.takeIf { value -> value >= 0 }

    private fun validatedCoverUrl(value: String?): String? {
        val url = value
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.toHttpUrlOrNull()
            ?: return null
        if (!url.isHttps ||
            url.username.isNotEmpty() ||
            url.password.isNotEmpty() ||
            url.fragment != null ||
            !isAllowedCoverEndpoint(url.host, url.port)
        ) {
            return null
        }
        return url.toString()
    }

    private fun isAllowedCoverEndpoint(host: String, port: Int): Boolean = when {
        host == "book-resource.dataesb.com" -> port == 443
        host.endsWith(".doubanio.com") -> port == 443
        host.endsWith(".openbookscan.com.cn") -> port == 443 || port == 1235
        else -> false
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 512
        val WHITESPACE_PATTERN = Regex("[\\s\\u00a0]+")
    }
}

internal fun String.normalizedOpacIsbn(): String? {
    val candidate = trim()
        .replaceFirst(ISBN_PREFIX_PATTERN, "")
        .trim()
        .trimEnd(':', '\uFF1A')
        .trim()
    if (
        candidate.isEmpty() ||
        candidate.any { character ->
            character !in '0'..'9' &&
                character != 'X' &&
                character != 'x' &&
                character != '-' &&
                !character.isWhitespace()
        }
    ) {
        return null
    }
    val normalized = candidate.uppercase().filter { character ->
        character in '0'..'9' || character == 'X'
    }
    return normalized.takeIf { value ->
        when (value.length) {
            10 -> value.hasValidIsbn10Checksum()
            13 -> (value.startsWith("978") || value.startsWith("979")) &&
                value.hasValidIsbn13Checksum()
            else -> false
        }
    }
}

private fun String.hasValidIsbn10Checksum(): Boolean {
    if (!take(9).all { character -> character in '0'..'9' } ||
        !(last() in '0'..'9' || last() == 'X')
    ) {
        return false
    }
    val checksum = mapIndexed { index, character ->
        val value = if (character == 'X') 10 else character.digitToInt()
        value * (10 - index)
    }.sum()
    return checksum % 11 == 0
}

private fun String.hasValidIsbn13Checksum(): Boolean {
    if (!all { character -> character in '0'..'9' }) {
        return false
    }
    val sum = take(12).mapIndexed { index, character ->
        character.digitToInt() * if (index % 2 == 0) 1 else 3
    }.sum()
    val checkDigit = (10 - sum % 10) % 10
    return last().digitToInt() == checkDigit
}

private val ISBN_PREFIX_PATTERN = Regex(
    pattern = "^ISBN(?:-1[03])?\\s*[:\\uFF1A]?\\s*",
    option = RegexOption.IGNORE_CASE,
)
