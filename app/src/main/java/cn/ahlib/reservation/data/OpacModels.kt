package cn.ahlib.reservation.data

import androidx.compose.runtime.Immutable

@Immutable
data class OpacBook(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publicationDate: String? = null,
    val documentType: String? = null,
    val callNumber: String? = null,
    val isbn: String? = null,
    val borrowCount: Int? = null,
    val coverUrl: String? = null,
    val holdings: List<OpacHolding>? = null,
)

@Immutable
data class OpacHolding(
    val libraryName: String? = null,
    val locationName: String? = null,
    val callNumber: String? = null,
    val availableCopies: Int,
    val totalCopies: Int,
)

data class OpacSearchPage(
    val items: List<OpacBook>,
    val total: Int,
    val page: Int,
    val totalPages: Int,
    val isFirstPage: Boolean = page <= 1,
    val isLastPage: Boolean = totalPages == 0 || page >= totalPages,
)

enum class OpacSearchFailure {
    NETWORK,
    TLS,
    HTTP,
    INVALID_RESPONSE,
}

sealed interface OpacSearchResult {
    data class Success(val page: OpacSearchPage) : OpacSearchResult

    data class Failure(
        val reason: OpacSearchFailure,
        val message: String? = null,
    ) : OpacSearchResult
}
