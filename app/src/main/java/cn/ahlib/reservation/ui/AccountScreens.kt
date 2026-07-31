package cn.ahlib.reservation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import cn.ahlib.reservation.R
import cn.ahlib.reservation.automation.signInDeadlineForDisplay
import cn.ahlib.reservation.data.AppointmentRecord
import cn.ahlib.reservation.data.SIGN_STATE_PENDING
import cn.ahlib.reservation.data.SIGN_STATE_SIGNED_IN
import cn.ahlib.reservation.data.SIGN_STATE_SIGNED_OUT
import cn.ahlib.reservation.data.UserInfo
import cn.ahlib.reservation.data.isCancellationEligible
import cn.ahlib.reservation.data.isPendingCheckIn
import cn.ahlib.reservation.scanner.ParsedQrCode
import cn.ahlib.reservation.scanner.QrCodeScanner
import cn.ahlib.reservation.scanner.QrImageHistoryEntry
import cn.ahlib.reservation.scanner.QrImageHistoryStore
import cn.ahlib.reservation.scanner.QrImageScanError
import cn.ahlib.reservation.scanner.QrImageScanResult
import cn.ahlib.reservation.scanner.QrScannerError
import cn.ahlib.reservation.scanner.messageResource
import cn.ahlib.reservation.scanner.scanQrCodeFromImage
import cn.ahlib.reservation.ui.theme.spacing
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationsScreen(
    reservations: List<AppointmentRecord>,
    selectedStatusFilters: Set<ReservationStatusFilter>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    cancellingIds: Set<String>,
    errorText: String?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onCancel: (AppointmentRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingCancellation by remember {
        mutableStateOf<AppointmentRecord?>(null)
    }
    val visibleReservations = remember(reservations, selectedStatusFilters) {
        reservations.filter { record ->
            record.reservationStatusFilter() in selectedStatusFilters
        }
    }
    val listState = rememberLazyListState()
    val latestCanLoadMore by rememberUpdatedState(canLoadMore)
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(selectedStatusFilters) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(listState) {
        var loadedDuringCurrentScroll = false
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            ReservationLoadMoreObservation(
                isScrollInProgress = listState.isScrollInProgress,
                canLoadMore = latestCanLoadMore,
                totalItemsCount = layoutInfo.totalItemsCount,
                lastVisibleIndex =
                    layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
            )
        }
            .collect { observation ->
                if (!observation.isScrollInProgress) {
                    loadedDuringCurrentScroll = false
                } else if (
                    !loadedDuringCurrentScroll &&
                    observation.shouldLoadMore()
                ) {
                    loadedDuringCurrentScroll = true
                    latestOnLoadMore()
                }
            }
    }

    PullToRefreshBox(
        isRefreshing = isLoading && reservations.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            isLoading && reservations.isEmpty() -> {
                LoadingContent(modifier = Modifier.fillMaxSize())
            }

            errorText != null && reservations.isEmpty() -> {
                ErrorContent(
                    message = errorText,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            visibleReservations.isEmpty() -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "reservation-empty") {
                        EmptyContent(
                            text = stringResource(
                                if (reservations.isEmpty()) {
                                    R.string.reservation_empty
                                } else {
                                    R.string.reservation_filtered_empty
                                },
                            ),
                            modifier = Modifier.fillParentMaxSize(),
                            icon = Icons.Outlined.EventBusy,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(MaterialTheme.spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    if (errorText != null) {
                        item(key = "reservation-error") {
                            InlineErrorMessage(
                                message = errorText,
                                actionLabel = stringResource(R.string.retry),
                                onAction = onRetry,
                            )
                        }
                    }
                    itemsIndexed(
                        items = visibleReservations,
                        key = { index, record ->
                            record.id.ifBlank {
                                "${record.bookingId}:${record.bookDate}:${record.startTime}:$index"
                            }
                        },
                    ) { _, record ->
                        ReservationCard(
                            record = record,
                            isCancelling = record.id in cancellingIds,
                            onCancel = { pendingCancellation = record },
                        )
                    }
                    if (isLoadingMore) {
                        item(key = "reservation-footer") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(
                                    8.dp,
                                    Alignment.CenterHorizontally,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(stringResource(R.string.loading_more))
                            }
                        }
                    }
                }
            }
        }
    }

    pendingCancellation?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingCancellation = null },
            title = { Text(stringResource(R.string.cancel_reservation)) },
            text = { Text(stringResource(R.string.cancel_reservation_question)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingCancellation = null
                        onCancel(record)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancellation = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private data class ReservationLoadMoreObservation(
    val isScrollInProgress: Boolean,
    val canLoadMore: Boolean,
    val totalItemsCount: Int,
    val lastVisibleIndex: Int,
)

private fun ReservationLoadMoreObservation.shouldLoadMore(): Boolean =
    isScrollInProgress &&
        canLoadMore &&
        totalItemsCount > 0 &&
        lastVisibleIndex >= totalItemsCount - 2

private fun ReservationStatusFilter.labelResource(): Int = when (this) {
    ReservationStatusFilter.PENDING_CHECK_IN -> R.string.sign_pending
    ReservationStatusFilter.SIGNED_IN -> R.string.signed_in
    ReservationStatusFilter.SIGNED_OUT -> R.string.signed_out
    ReservationStatusFilter.OTHER -> R.string.reservation_filter_other
}

@Composable
fun ReservationStatusFilterAction(
    selectedStatusFilters: Set<ReservationStatusFilter>,
    onToggleStatusFilter: (ReservationStatusFilter) -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isMenuExpanded = true }) {
            BadgedBox(
                badge = {
                    if (selectedStatusFilters.size < ReservationStatusFilter.entries.size) {
                        Badge()
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = stringResource(R.string.reservation_filter_action),
                )
            }
        }
        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = { isMenuExpanded = false },
        ) {
            ReservationStatusFilter.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(stringResource(status.labelResource())) },
                    onClick = { onToggleStatusFilter(status) },
                    leadingIcon = {
                        Checkbox(
                            checked = status in selectedStatusFilters,
                            onCheckedChange = null,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReservationCard(
    record: AppointmentRecord,
    isCancelling: Boolean,
    onCancel: () -> Unit,
) {
    var isExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(
                        horizontal = MaterialTheme.spacing.large,
                        vertical = MaterialTheme.spacing.medium,
                    ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.reservationDateForDisplay()
                        ?: stringResource(R.string.unknown_value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = record.roomName
                        ?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.unknown_value),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val deadlineText = when {
                    record.isPendingCheckIn() -> record.signInDeadlineForDisplay()?.let {
                        stringResource(R.string.reservation_sign_in_deadline_compact, it)
                    }
                    record.signState == SIGN_STATE_SIGNED_IN -> {
                        stringResource(R.string.reservation_sign_out_deadline_compact)
                    }
                    else -> null
                }
                deadlineText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                StatusPill(
                    text = record.statusMergeName
                        ?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.status_unknown),
                )
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = stringResource(
                        if (isExpanded) {
                            R.string.reservation_collapse_details
                        } else {
                            R.string.reservation_expand_details
                        },
                    ),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isExpanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                )
                Column(
                    modifier = Modifier.padding(
                        start = MaterialTheme.spacing.large,
                        top = MaterialTheme.spacing.medium,
                        end = MaterialTheme.spacing.large,
                        bottom = MaterialTheme.spacing.large,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    record.venueName?.takeIf(String::isNotBlank)?.let { venue ->
                        LabeledValue(
                            label = stringResource(R.string.venue),
                            value = venue,
                        )
                    }
                    record.userName?.takeIf(String::isNotBlank)?.let { name ->
                        LabeledValue(
                            label = stringResource(R.string.reservation_person),
                            value = name,
                        )
                    }
                    record.phoneNum?.takeIf(String::isNotBlank)?.let { phone ->
                        LabeledValue(
                            label = stringResource(R.string.reservation_phone),
                            value = phone,
                        )
                    }
                    record.bookNum?.let { count ->
                        LabeledValue(
                            label = stringResource(R.string.reservation_number),
                            value = stringResource(R.string.reservation_count_value, count),
                        )
                    }
                    LabeledValue(
                        label = stringResource(R.string.sign_status),
                        value = signStateLabel(record.signState),
                    )
                    if (record.isCancellationEligible()) {
                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !isCancelling,
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isCancelling) {
                                    MaterialTheme.colorScheme.outlineVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            ),
                        ) {
                            if (isCancelling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            Text(stringResource(R.string.cancel_reservation))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun signStateLabel(signState: Int?): String = when (signState) {
    SIGN_STATE_PENDING -> stringResource(R.string.sign_pending)
    SIGN_STATE_SIGNED_IN -> stringResource(R.string.signed_in)
    SIGN_STATE_SIGNED_OUT -> stringResource(R.string.signed_out)
    else -> stringResource(R.string.status_unknown)
}

@Composable
fun ScannerScreen(
    scannerEnabled: Boolean,
    restartKey: Int,
    isChecking: Boolean,
    isLocating: Boolean,
    isSigningIn: Boolean,
    isSigningOut: Boolean,
    feedbackText: String?,
    onScan: (ParsedQrCode) -> Unit,
    onScannerError: (QrScannerError) -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasRequestedCamera by rememberSaveable { mutableStateOf(false) }
    var cameraPermissionPermanentlyDenied by rememberSaveable {
        mutableStateOf(false)
    }
    var isImagePickerOpen by remember { mutableStateOf(false) }
    var isImageScanning by remember { mutableStateOf(false) }
    var imageScanError by remember {
        mutableStateOf<QrImageScanError?>(null)
    }
    var cameraScanError by remember {
        mutableStateOf<QrScannerError?>(null)
    }
    var imageScanJob by remember { mutableStateOf<Job?>(null) }
    val imageHistoryStore = remember(context) {
        QrImageHistoryStore(context)
    }
    var imageHistory by remember(imageHistoryStore) {
        mutableStateOf(imageHistoryStore.load())
    }
    var activeImageUriString by remember {
        mutableStateOf<String?>(null)
    }
    val scanImage: (android.net.Uri) -> Unit = { uri ->
        imageHistory = imageHistoryStore.record(uri)
        imageScanJob?.cancel()
        imageScanJob = coroutineScope.launch {
            imageScanError = null
            cameraScanError = null
            activeImageUriString = uri.toString()
            isImageScanning = true
            try {
                when (val result = scanQrCodeFromImage(context, uri)) {
                    is QrImageScanResult.Success -> onScan(result.code)

                    is QrImageScanResult.Failure -> {
                        if (
                            result.error is QrImageScanError.ImageFailure
                        ) {
                            imageHistory = imageHistoryStore.remove(uri.toString())
                        }
                        imageScanError = result.error
                    }
                }
            } finally {
                isImageScanning = false
                if (activeImageUriString == uri.toString()) {
                    activeImageUriString = null
                }
            }
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        isImagePickerOpen = false
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scanImage(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRequestedCamera = true
        hasCameraPermission = granted
        cameraPermissionPermanentlyDenied = !granted &&
            context.arePermissionsPermanentlyDenied(
                listOf(Manifest.permission.CAMERA),
            )
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (hasCameraPermission) {
                    cameraPermissionPermanentlyDenied = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasCameraPermission, hasRequestedCamera) {
        if (!hasCameraPermission && !hasRequestedCamera) {
            hasRequestedCamera = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val operationActive = isChecking || isLocating || isSigningIn || isSigningOut
    val scannerInteractionBlocked =
        operationActive ||
            isImageScanning ||
            isImagePickerOpen ||
            imageScanError != null ||
            cameraScanError != null
    val imageErrorText = imageScanError?.let { error ->
        stringResource(error.messageResource())
    }
    val cameraErrorText = cameraScanError?.let { error ->
        stringResource(error.messageResource())
    }
    val showFeedback =
        operationActive || isImageScanning || imageErrorText != null ||
            cameraErrorText != null || feedbackText != null
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (hasCameraPermission) {
                    MaterialTheme.colorScheme.scrim
                } else {
                    MaterialTheme.colorScheme.background
                },
            ),
    ) {
        if (hasCameraPermission) {
            key(restartKey) {
                QrCodeScanner(
                    enabled = scannerEnabled && !scannerInteractionBlocked,
                    onScan = { code, capturedImage ->
                        onScan(code)
                        capturedImage?.let { image ->
                            lifecycleOwner.lifecycleScope.launch {
                                try {
                                    val uri = imageHistoryStore
                                        .saveCapturedQrImage(image)
                                    imageHistory = imageHistoryStore.record(uri)
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (_: Exception) {
                                    // Scan completion must not depend on history storage.
                                } finally {
                                    image.recycle()
                                }
                            }
                        }
                    },
                    onError = { error ->
                        cameraScanError = error
                        onScannerError(error)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            EmptyContent(
                text = stringResource(
                    if (hasRequestedCamera) {
                        R.string.camera_permission_denied
                    } else {
                        R.string.camera_permission_body
                    },
                ),
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Outlined.CameraAlt,
                actionLabel = stringResource(
                    if (cameraPermissionPermanentlyDenied) {
                        R.string.open_settings
                    } else {
                        R.string.allow_camera
                    },
                ),
                onAction = {
                    if (cameraPermissionPermanentlyDenied) {
                        context.openApplicationSettings()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(MaterialTheme.spacing.screen)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.extraLarge,
                    vertical = MaterialTheme.spacing.large,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(
                        if (hasCameraPermission) {
                            R.string.scanner_instructions
                        } else {
                            R.string.scanner_image_option
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(
                    onClick = {
                        imageScanError = null
                        isImagePickerOpen = true
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    enabled = !operationActive &&
                        !isImageScanning &&
                        !isImagePickerOpen,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (imageScanError == null) {
                                R.string.scanner_select_image
                            } else {
                                R.string.scanner_select_another_image
                            },
                        ),
                    )
                }
            }
        }

        // The feedback card is stacked above the recent-image strip so a
        // prompt can never be covered by (or hide) the history panel.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(MaterialTheme.spacing.screen)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            if (showFeedback) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (operationActive || isImageScanning) {
                            CircularProgressIndicator()
                        }
                        Text(
                            text = when {
                                isImageScanning -> {
                                    stringResource(R.string.scanner_image_processing)
                                }
                                isChecking -> stringResource(R.string.scanner_checking)
                                isLocating -> stringResource(R.string.locating)
                                isSigningIn -> stringResource(R.string.signing_in)
                                isSigningOut -> stringResource(R.string.signing_out)
                                imageErrorText != null -> imageErrorText
                                cameraErrorText != null -> cameraErrorText
                                else -> feedbackText.orEmpty()
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        when {
                            operationActive || isImageScanning -> Unit

                            cameraScanError != null -> {
                                Button(onClick = { cameraScanError = null }) {
                                    Text(stringResource(R.string.scan_again))
                                }
                            }

                            imageScanError != null -> {
                                if (hasCameraPermission) {
                                    Button(
                                        onClick = {
                                            imageScanError = null
                                            onScanAgain()
                                        },
                                    ) {
                                        Text(
                                            stringResource(
                                                R.string.scanner_continue_camera,
                                            ),
                                        )
                                    }
                                }
                            }

                            feedbackText != null -> {
                                Button(onClick = onScanAgain) {
                                    Text(stringResource(R.string.scan_another))
                                }
                            }

                            else -> Unit
                        }
                    }
                }
            }

            if (imageHistory.isNotEmpty()) {
                val historyEnabled =
                    !isImagePickerOpen && !isImageScanning && !operationActive
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.spacing.large,
                            vertical = MaterialTheme.spacing.medium,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        R.string.scanner_image_history_title,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.scanner_image_history_hint,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    imageHistoryStore.clear()
                                    imageHistory = emptyList()
                                },
                                enabled = historyEnabled,
                            ) {
                                Text(stringResource(R.string.clear_history))
                            }
                        }
                        ScannerImageHistory(
                            entries = imageHistory,
                            activeUriString = activeImageUriString,
                            enabled = historyEnabled,
                            onEntryClick = { entry ->
                                scanImage(entry.uriString.toUri())
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerImageHistory(
    entries: List<QrImageHistoryEntry>,
    activeUriString: String?,
    enabled: Boolean,
    onEntryClick: (QrImageHistoryEntry) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        contentPadding = PaddingValues(vertical = MaterialTheme.spacing.extraSmall),
    ) {
        itemsIndexed(
            items = entries,
            key = { _, entry -> entry.uriString },
        ) { index, entry ->
            Surface(
                onClick = { onEntryClick(entry) },
                enabled = enabled,
                modifier = Modifier
                    .size(72.dp)
                    .aspectRatio(1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (activeUriString == entry.uriString) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = entry.uriString.toUri(),
                        contentDescription = stringResource(
                            R.string.scanner_image_history_item,
                            index + 1,
                        ),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (activeUriString == entry.uriString) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f),
                        ) {}
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.inversePrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    profile: UserInfo?,
    readerId: String,
    readerQrImageUrl: String?,
    readerQrPageUrl: String,
    appVersionName: String,
    isSavingReaderQr: Boolean,
    isCheckingUpdate: Boolean,
    readerQrErrorText: String?,
    isLoggingOut: Boolean,
    onReaderQrPageUrlChange: (String) -> Unit,
    onSaveReaderQrPageUrl: () -> Unit,
    onClearReaderQrBinding: () -> Unit,
    onOpenAutomation: () -> Unit,
    onCheckUpdate: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var showReaderQrEditor by rememberSaveable { mutableStateOf(false) }
    var wasSavingReaderQr by remember { mutableStateOf(false) }
    val missingValue = stringResource(R.string.not_provided)
    val readerStatusResource = readerStatusLabelResource(profile?.readerStatus)
    val readerStatusValue = if (readerStatusResource != null) {
        stringResource(readerStatusResource)
    } else {
        profile?.readerStatus
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: missingValue
    }
    val normalizedReaderId = readerId.trim()
    val readerIdValue = normalizedReaderId
        .takeIf(String::isNotEmpty)
        ?: missingValue

    LaunchedEffect(
        isSavingReaderQr,
        readerQrImageUrl,
        readerQrErrorText,
    ) {
        if (
            wasSavingReaderQr &&
            !isSavingReaderQr &&
            readerQrImageUrl != null &&
            readerQrErrorText == null
        ) {
            showReaderQrEditor = false
        }
        wasSavingReaderQr = isSavingReaderQr
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        item(key = "profile-card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.extraLarge),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.large,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(
                                MaterialTheme.spacing.large,
                            ),
                        ) {
                            LabeledValue(
                                label = stringResource(R.string.mobile),
                                value = profile?.mobile
                                    ?.takeIf(String::isNotBlank)
                                    ?: missingValue,
                            )
                            LabeledValue(
                                label = stringResource(R.string.reader_card),
                                value = readerIdValue,
                            )
                            LabeledValue(
                                label = stringResource(R.string.reader_status),
                                value = readerStatusValue,
                            )
                        }
                        if (readerQrImageUrl == null) {
                            ReaderQrCodeUnbound(
                                onClick = { showReaderQrEditor = true },
                            )
                        } else {
                            ReaderQrCodeImage(
                                imageUrl = readerQrImageUrl,
                                onClearBinding = onClearReaderQrBinding,
                            )
                        }
                    }
                }
            }
        }
        item(key = "automation") {
            FilledTonalButton(
                onClick = onOpenAutomation,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.automation_settings_title))
            }
        }
        item(key = "app-update") {
            FilledTonalButton(
                onClick = onCheckUpdate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCheckingUpdate,
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.app_update_checking))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.app_update_check))
                }
            }
        }
        item(key = "logout") {
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoggingOut,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isLoggingOut) {
                        MaterialTheme.colorScheme.outlineVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                ),
            ) {
                if (isLoggingOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.logging_out))
                } else {
                    Text(stringResource(R.string.logout))
                }
            }
        }
        item(key = "app-version") {
            Text(
                text = stringResource(
                    R.string.app_update_current_version,
                    appVersionName,
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showReaderQrEditor) {
        ReaderQrBindingDialog(
            pageUrl = readerQrPageUrl,
            isSaving = isSavingReaderQr,
            errorText = readerQrErrorText,
            onPageUrlChange = onReaderQrPageUrlChange,
            onSave = onSaveReaderQrPageUrl,
            onDismiss = { showReaderQrEditor = false },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isLoggingOut) {
                    showLogoutDialog = false
                }
            },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

internal fun readerStatusLabelResource(readerStatus: String?): Int? =
    when (readerStatus?.trim()) {
        "1" -> R.string.reader_status_valid
        "2" -> R.string.reader_status_verifying
        "3" -> R.string.reader_status_lost
        "4" -> R.string.reader_status_suspended
        "5" -> R.string.reader_status_cancelled
        else -> null
    }
