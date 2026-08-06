package cn.ahlib.reservation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import cn.ahlib.reservation.R
import cn.ahlib.reservation.data.OpacBook
import cn.ahlib.reservation.data.OpacHolding
import cn.ahlib.reservation.ui.theme.spacing
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

@Composable
fun OpacSearchScreen(
    books: List<OpacBook>,
    searchQuery: String,
    total: Int,
    hasSearched: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    errorText: String?,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onScrollConsumed: (Float) -> Unit = {},
    onViewportHeightChanged: (Int) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        OpacSearchInput(
            searchQuery = searchQuery,
            isSearchEnabled = searchQuery.isNotBlank() && !isLoading && !isLoadingMore,
            onSearchQueryChange = onSearchQueryChange,
            onSearch = onSearch,
        )

        when {
            isLoading && books.isEmpty() -> {
                OpacLoadingContent(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }

            errorText != null && books.isEmpty() -> {
                ErrorContent(
                    message = errorText,
                    onRetry = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            !hasSearched -> {
                EmptyContent(
                    text = stringResource(R.string.opac_initial_hint),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    icon = Icons.Outlined.Search,
                )
            }

            books.isEmpty() -> {
                EmptyContent(
                    text = stringResource(R.string.opac_zero_results),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    icon = Icons.Outlined.Book,
                )
            }

            else -> {
                OpacResultList(
                    books = books,
                    total = total,
                    isLoading = isLoading,
                    isLoadingMore = isLoadingMore,
                    canLoadMore = canLoadMore,
                    errorText = errorText,
                    onRetry = onRetry,
                    onLoadMore = onLoadMore,
                    onScrollConsumed = onScrollConsumed,
                    onViewportHeightChanged = onViewportHeightChanged,
                    listState = listState,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OpacSearchInput(
    searchQuery: String,
    isSearchEnabled: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.screen,
                vertical = MaterialTheme.spacing.medium,
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            singleLine = true,
            label = { Text(stringResource(R.string.opac_search_hint)) },
            trailingIcon = {
                Row {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                    IconButton(
                        onClick = onSearch,
                        enabled = isSearchEnabled,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                }
            },
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (isSearchEnabled) {
                        onSearch()
                    }
                },
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
        )
    }
}

@Composable
private fun OpacLoadingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(MaterialTheme.spacing.extraLarge)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            LoadingIndicator()
            Text(
                text = stringResource(R.string.opac_search_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun OpacResultList(
    books: List<OpacBook>,
    total: Int,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    errorText: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onScrollConsumed: (Float) -> Unit,
    onViewportHeightChanged: (Int) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val latestCanLoadMore = rememberUpdatedState(
        canLoadMore && !isLoading && !isLoadingMore && errorText == null,
    )
    val latestOnLoadMore = rememberUpdatedState(onLoadMore)
    val latestOnScrollConsumed = rememberUpdatedState(onScrollConsumed)
    val latestOnViewportHeightChanged = rememberUpdatedState(onViewportHeightChanged)
    val loadMoreGestureState = remember(listState) { OpacLoadMoreGestureState() }

    LaunchedEffect(listState, loadMoreGestureState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> loadMoreGestureState.onDragStarted(interaction)
                is DragInteraction.Stop -> loadMoreGestureState.onDragStopped(interaction.start)
                is DragInteraction.Cancel -> loadMoreGestureState.onDragCancelled(interaction.start)
            }
        }
    }

    val loadMoreNestedScrollConnection = remember(listState, loadMoreGestureState) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    loadMoreGestureState.onUserScrollDelta(available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y != 0f) {
                    latestOnScrollConsumed.value(consumed.y)
                }
                if (
                    source != NestedScrollSource.UserInput &&
                    source != NestedScrollSource.SideEffect
                ) {
                    return Offset.Zero
                }
                if (
                    !latestCanLoadMore.value ||
                    !loadMoreGestureState.canRequestLoad()
                ) {
                    return Offset.Zero
                }
                if (consumed.y == 0f && available.y == 0f) {
                    return Offset.Zero
                }
                val layoutInfo = listState.layoutInfo
                if (layoutInfo.totalItemsCount == 0) {
                    return Offset.Zero
                }

                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    ?: return Offset.Zero
                if (
                    lastVisibleIndex <
                    layoutInfo.totalItemsCount - LOAD_MORE_ITEM_THRESHOLD
                ) {
                    return Offset.Zero
                }

                loadMoreGestureState.markLoadRequested()
                latestOnLoadMore.value()
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                loadMoreGestureState.onFlingFinished()
                return Velocity.Zero
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                latestOnViewportHeightChanged.value(size.height)
            }
            .nestedScroll(loadMoreNestedScrollConnection)
            .testTag(OPAC_RESULTS_TEST_TAG),
        contentPadding = PaddingValues(
            start = MaterialTheme.spacing.screen,
            top = MaterialTheme.spacing.extraSmall,
            end = MaterialTheme.spacing.screen,
            bottom = MaterialTheme.spacing.section,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        if (isLoading) {
            item(
                key = "opac-search-loading",
                contentType = OpacListItemContentType.INLINE_LOADING,
            ) {
                OpacInlineLoading(text = stringResource(R.string.opac_search_loading))
            }
        }

        item(
            key = "opac-result-count",
            contentType = OpacListItemContentType.RESULT_COUNT,
        ) {
            Text(
                text = stringResource(R.string.opac_result_count, total),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        items(
            items = books,
            key = { book -> book.id },
            contentType = { OpacListItemContentType.BOOK },
        ) { book ->
            OpacBookCard(book = book)
        }

        if (isLoadingMore) {
            item(
                key = "opac-load-more",
                contentType = OpacListItemContentType.INLINE_LOADING,
            ) {
                OpacInlineLoading(
                    text = stringResource(R.string.loading_more),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.spacing.small),
                )
            }
        } else if (errorText != null) {
            item(
                key = "opac-list-error",
                contentType = OpacListItemContentType.INLINE_ERROR,
            ) {
                InlineErrorMessage(
                    message = errorText,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                )
            }
        }
    }
}

internal const val OPAC_RESULTS_TEST_TAG = "opac-results"

private enum class OpacListItemContentType {
    RESULT_COUNT,
    BOOK,
    INLINE_LOADING,
    INLINE_ERROR,
}

private class OpacLoadMoreGestureState {
    private var activeDrag: DragInteraction.Start? = null
    private var isGestureInProgress = false
    private var hasRequestedLoad = false
    private var isMovingTowardBottom = false

    fun onDragStarted(start: DragInteraction.Start) {
        activeDrag = start
        isGestureInProgress = true
        hasRequestedLoad = false
        isMovingTowardBottom = false
    }

    fun onDragStopped(start: DragInteraction.Start) {
        if (activeDrag === start) {
            activeDrag = null
        }
    }

    fun onDragCancelled(start: DragInteraction.Start) {
        if (activeDrag === start) {
            reset()
        }
    }

    fun onUserScrollDelta(deltaY: Float) {
        if (isGestureInProgress && deltaY != 0f) {
            isMovingTowardBottom = deltaY < 0f
        }
    }

    fun onFlingFinished() {
        reset()
    }

    fun canRequestLoad(): Boolean =
        isGestureInProgress && isMovingTowardBottom && !hasRequestedLoad

    fun markLoadRequested() {
        check(canRequestLoad())
        hasRequestedLoad = true
    }

    private fun reset() {
        activeDrag = null
        isGestureInProgress = false
        isMovingTowardBottom = false
    }
}

@Composable
private fun OpacInlineLoading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingIndicator(modifier = Modifier.size(MaterialTheme.spacing.extraLarge))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun OpacBookCard(
    book: OpacBook,
    modifier: Modifier = Modifier,
) {
    val authorSeparator = stringResource(R.string.opac_author_separator)
    val displayData = remember(book, authorSeparator) {
        book.toDisplayData(authorSeparator)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
                verticalAlignment = Alignment.Top,
            ) {
                OpacBookCover(
                    title = book.title,
                    coverUrl = book.coverUrl,
                    modifier = Modifier
                        .size(MaterialTheme.spacing.extraLarge * 4f),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(
                        MaterialTheme.spacing.extraSmall,
                    ),
                ) {
                    displayData.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    displayData.authors?.let { value ->
                        BookMetadataText(
                            text = stringResource(R.string.opac_authors_value, value),
                        )
                    }
                    displayData.publisher?.let { value ->
                        BookMetadataText(
                            text = stringResource(R.string.opac_publisher_value, value),
                        )
                    }
                    displayData.publicationDate?.let { value ->
                        BookMetadataText(
                            text = stringResource(R.string.opac_publication_date_value, value),
                        )
                    }
                    displayData.documentType?.let { value ->
                        BookMetadataText(
                            text = stringResource(R.string.opac_document_type_value, value),
                        )
                    }
                    displayData.callNumber?.let { value ->
                        BookMetadataText(
                            text = stringResource(R.string.opac_call_number_value, value),
                        )
                    }
                    displayData.isbn?.let { value ->
                        BookMetadataText(
                            text = stringResource(R.string.opac_isbn_value, value),
                        )
                    }
                    book.borrowCount?.let { value ->
                        BookMetadataText(
                            text = stringResource(R.string.opac_borrow_count_value, value),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OpacHoldingsContent(holdings = book.holdings)
        }
    }
}

@Composable
private fun OpacBookCover(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.opac_book_cover, title)
    val normalizedCoverUrl = remember(coverUrl) { coverUrl.metadataValue() }
    var imageState by remember(normalizedCoverUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (normalizedCoverUrl != null) {
                AsyncImage(
                    model = normalizedCoverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onState = { state -> imageState = state },
                )
            }
            when {
                normalizedCoverUrl == null -> Icon(
                    imageVector = Icons.Outlined.Book,
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.spacing.extraLarge),
                )

                imageState is AsyncImagePainter.State.Error -> Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.spacing.extraLarge),
                )

                imageState is AsyncImagePainter.State.Empty ||
                    imageState is AsyncImagePainter.State.Loading ->
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(MaterialTheme.spacing.extraLarge),
                    )
            }
        }
    }
}

@Composable
private fun OpacHoldingsContent(
    holdings: List<OpacHolding>?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = stringResource(R.string.opac_holdings_title),
            style = MaterialTheme.typography.titleSmall,
        )
        when {
            holdings == null -> BookMetadataText(
                text = stringResource(R.string.opac_holdings_unavailable),
            )

            holdings.isEmpty() -> BookMetadataText(
                text = stringResource(R.string.opac_no_holdings),
            )

            else -> {
                val displayData = remember(holdings) {
                    holdings.toDisplayData()
                }
                Surface(
                    shape = CircleShape,
                    color = if (displayData.totalAvailable > 0) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (displayData.totalAvailable > 0) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Text(
                        text = stringResource(
                            R.string.opac_holdings_summary,
                            displayData.totalAvailable,
                            displayData.totalCopies,
                        ),
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.spacing.medium,
                            vertical = MaterialTheme.spacing.extraSmall,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                repeat(displayData.visibleLocationCount) { index ->
                    OpacHoldingRow(holding = holdings[index])
                }
                if (displayData.hiddenLocationCount > 0) {
                    BookMetadataText(
                        text = stringResource(
                            R.string.opac_more_holding_locations,
                            displayData.hiddenLocationCount,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun OpacHoldingRow(
    holding: OpacHolding,
    modifier: Modifier = Modifier,
) {
    val normalizedLocation = remember(holding.libraryName, holding.locationName) {
        holding.locationDisplayValue()
    }
    val location = normalizedLocation
        ?: stringResource(R.string.opac_unknown_holding_location)
    val callNumber = remember(holding.callNumber) {
        holding.callNumber.metadataValue()
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.spacing.large),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            callNumber?.let { value ->
                Text(
                    text = stringResource(R.string.opac_call_number_value, value),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = stringResource(
                R.string.opac_holding_counts,
                holding.availableCopies,
                holding.totalCopies,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun BookMetadataText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun String?.metadataValue(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)

private data class OpacBookDisplayData(
    val title: String?,
    val authors: String?,
    val publisher: String?,
    val publicationDate: String?,
    val documentType: String?,
    val callNumber: String?,
    val isbn: String?,
)

private fun OpacBook.toDisplayData(authorSeparator: String): OpacBookDisplayData =
    OpacBookDisplayData(
        title = title.metadataValue(),
        authors = authors
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(separator = authorSeparator)
            .takeIf(String::isNotEmpty),
        publisher = publisher.metadataValue(),
        publicationDate = publicationDate.metadataValue(),
        documentType = documentType.metadataValue(),
        callNumber = callNumber.metadataValue(),
        isbn = isbn.metadataValue(),
    )

private data class OpacHoldingsDisplayData(
    val totalAvailable: Int,
    val totalCopies: Int,
    val visibleLocationCount: Int,
    val hiddenLocationCount: Int,
)

private fun List<OpacHolding>.toDisplayData(): OpacHoldingsDisplayData {
    val visibleLocationCount = minOf(size, MAX_VISIBLE_HOLDING_LOCATIONS)
    return OpacHoldingsDisplayData(
        totalAvailable = sumOf(OpacHolding::availableCopies),
        totalCopies = sumOf(OpacHolding::totalCopies),
        visibleLocationCount = visibleLocationCount,
        hiddenLocationCount = size - visibleLocationCount,
    )
}

private fun OpacHolding.locationDisplayValue(): String? =
    listOfNotNull(
        libraryName.metadataValue(),
        locationName.metadataValue(),
    ).distinct().joinToString(" \u00b7 ").takeIf(String::isNotEmpty)

private const val MAX_VISIBLE_HOLDING_LOCATIONS = 3
private const val LOAD_MORE_ITEM_THRESHOLD = 2
