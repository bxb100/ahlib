package cn.ahlib.reservation.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import cn.ahlib.reservation.R
import cn.ahlib.reservation.automation.AutoBookingTarget
import cn.ahlib.reservation.data.AvailabilityDay
import cn.ahlib.reservation.data.AvailabilitySlot
import cn.ahlib.reservation.data.RoomDetail
import cn.ahlib.reservation.data.RoomSummary
import cn.ahlib.reservation.data.isSelectableForReservation
import cn.ahlib.reservation.ui.theme.ReservationDesignSystem
import cn.ahlib.reservation.ui.theme.spacing
import java.util.GregorianCalendar
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(
    rooms: List<RoomSummary>,
    searchQuery: String,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    errorText: String?,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenRoom: (RoomSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.room_search)) },
                    trailingIcon = {
                        Row {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = stringResource(
                                            R.string.clear_search,
                                        ),
                                    )
                                }
                            }
                            IconButton(
                                onClick = onSearch,
                                enabled = !isLoading && !isRefreshing,
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
                            if (!isLoading && !isRefreshing) {
                                onSearch()
                            }
                        },
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                )
                FilledTonalIconButton(
                    onClick = onRefresh,
                    enabled = !isLoading && !isRefreshing,
                ) {
                    if (isRefreshing) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            when {
                isLoading && rooms.isEmpty() -> {
                    LoadingContent(modifier = Modifier.fillMaxSize())
                }

                errorText != null && rooms.isEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "room-list-error-empty") {
                            ErrorContent(
                                message = errorText,
                                onRetry = onRetry,
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }
                }

                rooms.isEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "room-list-empty") {
                            EmptyContent(
                                text = stringResource(R.string.room_empty),
                                modifier = Modifier.fillParentMaxSize(),
                                actionLabel = stringResource(R.string.refresh),
                                onAction = onRefresh,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = MaterialTheme.spacing.screen,
                            top = MaterialTheme.spacing.extraSmall,
                            end = MaterialTheme.spacing.screen,
                            bottom = MaterialTheme.spacing.section,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    ) {
                        if (errorText != null) {
                            item(key = "room-list-error") {
                                InlineErrorMessage(
                                    message = errorText,
                                    actionLabel = stringResource(R.string.retry),
                                    onAction = onRetry,
                                )
                            }
                        }
                        items(
                            items = rooms,
                            key = { room -> room.id },
                        ) { room ->
                            RoomCard(
                                room = room,
                                onClick = { onOpenRoom(room) },
                            )
                        }
                        if (canLoadMore || isLoadingMore) {
                            item(key = "room-load-more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isLoadingMore) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            LoadingIndicator(
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Text(stringResource(R.string.loading_more))
                                        }
                                    } else {
                                        OutlinedButton(onClick = onLoadMore) {
                                            Text(stringResource(R.string.load_more))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RoomCard(
    room: RoomSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) {
        it.isEnabled = true
    }
    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .styleable(
                styleState,
                ReservationDesignSystem.styles.roomCard,
                style,
            ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        RemoteRoomImage(
            url = room.coverUrl,
            modifier = Modifier.size(width = 116.dp, height = 96.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(
                text = room.roomName.ifBlank {
                    stringResource(R.string.unknown_value)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            room.venueName?.takeIf(String::isNotBlank)?.let { venue ->
                Text(
                    text = venue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            room.address?.takeIf(String::isNotBlank)?.let { address ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                room.totalNum?.let { count ->
                    StatusPill(
                        text = stringResource(R.string.room_capacity_value, count),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                room.ableNum?.let { count ->
                    StatusPill(
                        text = stringResource(R.string.available_left, count),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    roomId: String,
    detail: RoomDetail?,
    availability: List<AvailabilityDay>,
    selectedDate: String?,
    selectedSlotId: String?,
    autoBookingTarget: AutoBookingTarget?,
    isLoading: Boolean,
    isAvailabilityRefreshing: Boolean,
    isBooking: Boolean,
    detailErrorText: String?,
    showBookingDialog: Boolean,
    bookingName: String,
    bookingMobile: String,
    requireBookingName: Boolean,
    requireBookingMobile: Boolean,
    bookingErrorText: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefreshAvailability: () -> Unit,
    onSelectDate: (String) -> Unit,
    onSelectSlot: (String) -> Unit,
    onOpenBookingDialog: () -> Unit,
    onDismissBookingDialog: () -> Unit,
    onBookingNameChange: (String) -> Unit,
    onBookingMobileChange: (String) -> Unit,
    onConfirmBooking: () -> Unit,
    onConfigureAutoBooking: (AvailabilitySlot) -> Unit,
    onClearAutoBookingTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.room_detail),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        when {
            isLoading && detail == null -> {
                LoadingContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            detailErrorText != null && detail == null -> {
                ErrorContent(
                    message = detailErrorText,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            detail == null -> {
                EmptyContent(
                    text = stringResource(R.string.room_detail_load_error),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                )
            }

            else -> {
                RoomDetailBody(
                    roomId = roomId,
                    detail = detail,
                    availability = availability,
                    selectedDate = selectedDate,
                    selectedSlotId = selectedSlotId,
                    autoBookingTarget = autoBookingTarget,
                    isAvailabilityRefreshing = isAvailabilityRefreshing,
                    errorText = detailErrorText,
                    onRetry = onRetry,
                    onRefreshAvailability = onRefreshAvailability,
                    onSelectDate = onSelectDate,
                    onSelectSlot = onSelectSlot,
                    onBook = onOpenBookingDialog,
                    onConfigureAutoBooking = onConfigureAutoBooking,
                    onClearAutoBookingTarget = onClearAutoBookingTarget,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }

    if (showBookingDialog && detail != null) {
        BookingConfirmationDialog(
            detail = detail,
            availability = availability,
            selectedDate = selectedDate,
            selectedSlotId = selectedSlotId,
            bookingName = bookingName,
            bookingMobile = bookingMobile,
            requireBookingName = requireBookingName,
            requireBookingMobile = requireBookingMobile,
            errorText = bookingErrorText,
            isBooking = isBooking,
            onDismiss = onDismissBookingDialog,
            onNameChange = onBookingNameChange,
            onMobileChange = onBookingMobileChange,
            onConfirm = onConfirmBooking,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomDetailBody(
    roomId: String,
    detail: RoomDetail,
    availability: List<AvailabilityDay>,
    selectedDate: String?,
    selectedSlotId: String?,
    autoBookingTarget: AutoBookingTarget?,
    isAvailabilityRefreshing: Boolean,
    errorText: String?,
    onRetry: () -> Unit,
    onRefreshAvailability: () -> Unit,
    onSelectDate: (String) -> Unit,
    onSelectSlot: (String) -> Unit,
    onBook: () -> Unit,
    onConfigureAutoBooking: (AvailabilitySlot) -> Unit,
    onClearAutoBookingTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedDay = availability.firstOrNull { day -> day.date == selectedDate }

    PullToRefreshBox(
        isRefreshing = isAvailabilityRefreshing,
        onRefresh = onRefreshAvailability,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        ) {
            item(key = "detail-availability") {
                ReservationAvailabilitySection(
                    roomId = roomId,
                    availability = availability,
                    selectedDay = selectedDay,
                    selectedSlotId = selectedSlotId,
                    autoBookingTarget = autoBookingTarget,
                    errorText = errorText,
                    onRetry = onRetry,
                    onSelectDate = onSelectDate,
                    onSelectSlot = onSelectSlot,
                    onBook = onBook,
                    onConfigureAutoBooking = onConfigureAutoBooking,
                    onClearAutoBookingTarget = onClearAutoBookingTarget,
                )
            }
            item(key = "detail-image") {
                RemoteRoomImage(
                    url = detail.coverUrl,
                    shareName = detail.roomName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.screen)
                        .aspectRatio(16f / 9f),
                )
            }
            item(key = "detail-header") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.screen),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        Text(
                            text = detail.roomName.ifBlank {
                                stringResource(R.string.unknown_value)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        detail.venueName?.takeIf(String::isNotBlank)?.let { venue ->
                            Text(
                                text = venue,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                vertical = MaterialTheme.spacing.small,
                            ),
                        )
                        detail.address?.takeIf(String::isNotBlank)?.let { address ->
                            LabeledValue(
                                label = stringResource(R.string.address),
                                value = address,
                            )
                        }
                        detail.phoneNum?.takeIf(String::isNotBlank)?.let { phone ->
                            LabeledValue(
                                label = stringResource(R.string.contact_phone),
                                value = phone,
                            )
                        }
                        detail.roomArea?.let { area ->
                            LabeledValue(
                                label = stringResource(R.string.room_area),
                                value = stringResource(R.string.room_area_value, area),
                            )
                        }
                        detail.totalNum?.let { count ->
                            LabeledValue(
                                label = stringResource(R.string.capacity),
                                value = stringResource(R.string.room_capacity_value, count),
                            )
                        }
                    }
                }
            }
            detail.introduction?.takeIf(String::isNotBlank)?.let { introduction ->
                item(key = "detail-introduction") {
                    DetailSection(
                        title = stringResource(R.string.room_description),
                    ) {
                        RoomHtmlContent(
                            html = introduction,
                            imageContentDescription = stringResource(
                                R.string.html_image_description,
                            ),
                            imageShareName = detail.roomName,
                        )
                    }
                }
            }
            detail.appointRule?.takeIf(String::isNotBlank)?.let { rules ->
                item(key = "detail-rules") {
                    DetailSection(
                        title = stringResource(R.string.reservation_rules),
                    ) {
                        RoomHtmlContent(
                            html = rules,
                            imageContentDescription = stringResource(
                                R.string.html_image_description,
                            ),
                            imageShareName = detail.roomName,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationAvailabilitySection(
    roomId: String,
    availability: List<AvailabilityDay>,
    selectedDay: AvailabilityDay?,
    selectedSlotId: String?,
    autoBookingTarget: AutoBookingTarget?,
    errorText: String?,
    onRetry: () -> Unit,
    onSelectDate: (String) -> Unit,
    onSelectSlot: (String) -> Unit,
    onBook: () -> Unit,
    onConfigureAutoBooking: (AvailabilitySlot) -> Unit,
    onClearAutoBookingTarget: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            when {
                errorText != null && availability.isEmpty() -> {
                    ErrorContent(
                        message = errorText,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                availability.isEmpty() -> {
                    EmptyContent(
                        text = stringResource(R.string.no_available_dates),
                        modifier = Modifier.fillMaxWidth(),
                        actionLabel = stringResource(R.string.refresh),
                        onAction = onRetry,
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaterialTheme.spacing.extraLarge),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    ) {
                        errorText?.let { message ->
                            InlineErrorMessage(
                                message = message,
                                actionLabel = stringResource(R.string.retry),
                                onAction = onRetry,
                            )
                        }
                        Text(
                            text = stringResource(R.string.select_date),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            items(
                                items = availability,
                                key = { day -> day.date },
                            ) { day ->
                                AvailabilityDayCard(
                                    day = day,
                                    selected = day.date == selectedDay?.date,
                                    onClick = { onSelectDate(day.date) },
                                )
                            }
                        }
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.select_time),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.auto_booking_swipe_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (selectedDay == null || selectedDay.list.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_available_slots),
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                selectedDay.list.forEach { slot ->
                                    SwipeableAvailabilitySlotCard(
                                        slot = slot,
                                        selected = slot.id == selectedSlotId,
                                        isAutoBookingTarget = autoBookingTarget
                                            ?.matches(roomId, slot) == true,
                                        onClick = { onSelectSlot(slot.id) },
                                        onConfigureAutoBooking = {
                                            onConfigureAutoBooking(slot)
                                        },
                                        onClearAutoBookingTarget =
                                            onClearAutoBookingTarget,
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = onBook,
                            enabled = selectedSlotId != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                        ) {
                            Text(stringResource(R.string.book_now))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailabilityDayCard(
    day: AvailabilityDay,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = remember(day.date) { day.toAvailabilityDateLabel() }
    val selectable = day.isSelectableForReservation()
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary
        selectable -> MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        selectable -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val status = when {
        selectable -> stringResource(R.string.reservation_available)
        day.isOpen == 1 && day.totalLeftNum != null && day.totalLeftNum <= 0 ->
            stringResource(R.string.reservation_full)

        else -> stringResource(R.string.unavailable)
    }

    Surface(
        modifier = Modifier
            .width(84.dp)
            .heightIn(min = 116.dp)
            .selectable(
                selected = selected,
                enabled = day.date.isNotBlank(),
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (label.weekday.isNotEmpty()) {
                Text(
                    text = label.weekday,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (label.month.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.availability_month, label.month),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = label.day,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = if (selectable || selected) {
                    contentColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAvailabilitySlotCard(
    slot: AvailabilitySlot,
    selected: Boolean,
    isAutoBookingTarget: Boolean,
    onClick: () -> Unit,
    onConfigureAutoBooking: () -> Unit,
    onClearAutoBookingTarget: () -> Unit,
) {
    val currentIsTarget by rememberUpdatedState(isAutoBookingTarget)
    val currentConfigureAction by rememberUpdatedState(onConfigureAutoBooking)
    val currentClearAction by rememberUpdatedState(onClearAutoBookingTarget)
    val configureLabel = stringResource(R.string.configure_auto_booking)
    val clearLabel = stringResource(R.string.clear_auto_booking_target)
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                currentConfigureAction()
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }

            SwipeToDismissBoxValue.EndToStart -> {
                if (currentIsTarget) {
                    currentClearAction()
                }
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }

            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .semantics {
                customActions = buildList {
                    add(
                        CustomAccessibilityAction(configureLabel) {
                            onConfigureAutoBooking()
                            true
                        },
                    )
                    if (isAutoBookingTarget) {
                        add(
                            CustomAccessibilityAction(clearLabel) {
                                onClearAutoBookingTarget()
                                true
                            },
                        )
                    }
                }
            },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = isAutoBookingTarget,
        backgroundContent = {
            Row(modifier = Modifier.fillMaxSize()) {
                SwipeActionBackground(
                    label = configureLabel,
                    icon = Icons.Outlined.Check,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f),
                )
                SwipeActionBackground(
                    label = clearLabel,
                    icon = Icons.Outlined.Clear,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        AvailabilitySlotCard(
            slot = slot,
            selected = selected,
            isAutoBookingTarget = isAutoBookingTarget,
            onClick = onClick,
        )
    }
}

@Composable
private fun SwipeActionBackground(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(containerColor)
            .padding(horizontal = 16.dp),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun AvailabilitySlotCard(
    slot: AvailabilitySlot,
    selected: Boolean,
    isAutoBookingTarget: Boolean,
    onClick: () -> Unit,
) {
    val selectable = slot.isSelectableForReservation()
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        isAutoBookingTarget -> MaterialTheme.colorScheme.tertiaryContainer
        selectable -> MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        isAutoBookingTarget -> MaterialTheme.colorScheme.onTertiaryContainer
        selectable -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        isAutoBookingTarget -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val status = slot.bookingStatusName
        ?.takeIf(String::isNotBlank)
        ?: when {
            selectable -> stringResource(R.string.reservation_available)
            slot.leftNum != null && slot.leftNum <= 0 ->
                stringResource(R.string.reservation_full)
            else -> stringResource(R.string.unavailable)
        }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = selectable,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = if (selected || isAutoBookingTarget) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${slot.startTime} - ${slot.endTime}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                slot.leftNum?.let { left ->
                    Text(
                        text = stringResource(R.string.available_left, left),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StatusPill(
                    text = status,
                    containerColor = when {
                        selected -> MaterialTheme.colorScheme.primary
                        selectable -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        selectable -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (isAutoBookingTarget) {
                    StatusPill(
                        text = stringResource(R.string.auto_booking_slot),
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    )
                }
            }
        }
    }
}

private fun AutoBookingTarget.matches(
    roomId: String,
    slot: AvailabilitySlot,
): Boolean =
    this.roomId == roomId &&
        startTime == slot.startTime &&
        endTime == slot.endTime

private data class AvailabilityDateLabel(
    val weekday: String,
    val month: String,
    val day: String,
)

private fun AvailabilityDay.toAvailabilityDateLabel(): AvailabilityDateLabel {
    val normalized = date.substringBefore('T').substringBefore(' ')
    val parts = normalized.split('-')
    if (parts.size != 3) {
        return AvailabilityDateLabel(weekday = "", month = "", day = normalized)
    }
    val year = parts[0].toIntOrNull()
    val month = parts[1].toIntOrNull()
    val day = parts[2].toIntOrNull()
    if (year == null || month == null || day == null) {
        return AvailabilityDateLabel(weekday = "", month = "", day = normalized)
    }
    return try {
        val calendar = GregorianCalendar(year, month - 1, day).apply {
            isLenient = false
            timeInMillis
        }
        AvailabilityDateLabel(
            weekday = DateFormat.format("E", calendar).toString(),
            month = month.toString().padStart(2, '0'),
            day = day.toString().padStart(2, '0'),
        )
    } catch (exception: IllegalArgumentException) {
        AvailabilityDateLabel(weekday = "", month = "", day = normalized)
    }
}

@Composable
private fun BookingConfirmationDialog(
    detail: RoomDetail,
    availability: List<AvailabilityDay>,
    selectedDate: String?,
    selectedSlotId: String?,
    bookingName: String,
    bookingMobile: String,
    requireBookingName: Boolean,
    requireBookingMobile: Boolean,
    errorText: String?,
    isBooking: Boolean,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onMobileChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val slot = remember(availability, selectedSlotId) {
        availability
            .asSequence()
            .flatMap { day -> day.list.asSequence() }
            .firstOrNull { item -> item.id == selectedSlotId }
    }
    val isMobileValid = remember(bookingMobile) { bookingMobile.isValidMobile() }
    val canSubmit = !isBooking &&
        (!requireBookingName || bookingName.isNotBlank()) &&
        (!requireBookingMobile || isMobileValid)

    AlertDialog(
        onDismissRequest = {
            if (!isBooking) {
                onDismiss()
            }
        },
        title = { Text(stringResource(R.string.booking_summary)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                errorText?.let { message ->
                    InlineErrorMessage(message = message)
                }
                LabeledValue(
                    label = stringResource(R.string.booking_room),
                    value = detail.roomName,
                )
                LabeledValue(
                    label = stringResource(R.string.booking_date),
                    value = selectedDate.orEmpty(),
                )
                LabeledValue(
                    label = stringResource(R.string.booking_time),
                    value = slot?.let { item ->
                        "${item.startTime} - ${item.endTime}"
                    }.orEmpty(),
                )
                if (requireBookingName) {
                    OutlinedTextField(
                        value = bookingName,
                        onValueChange = { value -> onNameChange(value.take(20)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.contact_name)) },
                        singleLine = true,
                        enabled = !isBooking,
                    )
                } else {
                    LabeledValue(
                        label = stringResource(R.string.contact_name),
                        value = bookingName,
                    )
                }
                if (requireBookingMobile) {
                    OutlinedTextField(
                        value = bookingMobile,
                        onValueChange = { value ->
                            onMobileChange(value.filter(Char::isDigit).take(11))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.contact_mobile)) },
                        singleLine = true,
                        enabled = !isBooking,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                        ),
                        isError = bookingMobile.isNotEmpty() && !isMobileValid,
                    )
                } else {
                    LabeledValue(
                        label = stringResource(R.string.contact_mobile),
                        value = bookingMobile,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canSubmit,
            ) {
                if (isBooking) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.booking_in_progress))
                } else {
                    Text(stringResource(R.string.confirm_booking))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBooking,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.screen),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.extraLarge)) {
            SectionHeader(title = title)
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
            content()
        }
    }
}

@Composable
private fun RemoteRoomImage(
    url: String?,
    modifier: Modifier = Modifier,
    rounded: Boolean = true,
    shareName: String? = null,
) {
    val shape = if (rounded) MaterialTheme.shapes.medium else MaterialTheme.shapes.extraSmall
    val model = normalizeRemoteImageUrl(url)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ImageNotSupported,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.room_no_image),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            ResilientRoomImage(
                model = model,
                shareName = shareName,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ResilientRoomImage(
    model: String,
    modifier: Modifier = Modifier,
    shareName: String? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val isNetworkImage = model.startsWith("http", ignoreCase = true)
    var retryAttempt by remember(model) { mutableIntStateOf(0) }
    var imageState by remember(model) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val request = remember(context, model, retryAttempt) {
        if (isNetworkImage) {
            buildLibraryImageRequest(
                context = context,
                url = model,
                retryAttempt = retryAttempt,
            )
        } else {
            ImageRequest.Builder(context)
                .data(model.toCoilModel())
                .crossfade(true)
                .allowHardware(false)
                .build()
        }
    }

    LaunchedEffect(imageState, isNetworkImage) {
        if (
            isNetworkImage &&
            imageState is AsyncImagePainter.State.Error &&
            retryAttempt < MAX_LIBRARY_IMAGE_AUTO_RETRIES
        ) {
            delay(350)
            imageState = AsyncImagePainter.State.Empty
            retryAttempt += 1
        }
    }
    val shareDrawable =
        (imageState as? AsyncImagePainter.State.Success)
            ?.result
            ?.image
            ?.asDrawable(resources)

    ShareableRoomImageBox(
        drawable = shareDrawable,
        shareName = shareName,
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.room_image),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onState = { imageState = it },
        )

        when (imageState) {
            AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading,
            -> LoadingIndicator(modifier = Modifier.size(28.dp))

            is AsyncImagePainter.State.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isNetworkImage) {
                        TextButton(
                            onClick = {
                                imageState = AsyncImagePainter.State.Empty
                                retryAttempt += 1
                            },
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            is AsyncImagePainter.State.Success -> Unit
        }
    }
}

private fun String.isValidMobile(): Boolean =
    length == 11 &&
        firstOrNull() == '1' &&
        getOrNull(1) in '3'..'9' &&
        all(Char::isDigit)

private fun normalizeRemoteImageUrl(value: String?): String? {
    val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return when {
        trimmed.startsWith("data:image/", ignoreCase = true) -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "https://www.lib.ah.cn$trimmed"
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) ->
            "https://${trimmed.substringAfter("://")}"
        else -> null
    }
}
