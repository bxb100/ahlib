package cn.ahlib.reservation.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.ahlib.reservation.BuildConfig
import cn.ahlib.reservation.R
import cn.ahlib.reservation.automation.AUTOMATION_TIME_ZONE
import cn.ahlib.reservation.automation.AutomationManager
import cn.ahlib.reservation.calendar.ReservationCalendarReminder
import cn.ahlib.reservation.update.AppUpdateManager
import cn.ahlib.reservation.update.UpdateNotice
import java.text.DateFormat
import java.util.Date

@Composable
fun ReservationApp(
    viewModel: ReservationViewModel,
    automationManager: AutomationManager,
    appUpdateManager: AppUpdateManager,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by appUpdateManager.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.text?.resolve()
    val context = LocalContext.current
    val calendarReminderUnavailableText =
        stringResource(R.string.calendar_reminder_unavailable)
    val pendingCalendarReminder by
        automationManager.pendingCalendarReminder.collectAsStateWithLifecycle()

    LaunchedEffect(state.message?.id) {
        val message = state.message ?: return@LaunchedEffect
        val resolved = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resolved)
        viewModel.consumeMessage(message.id)
    }
    LaunchedEffect(Unit) {
        appUpdateManager.checkForUpdates(userInitiated = false)
    }
    LaunchedEffect(updateState.notice) {
        val notice = updateState.notice ?: return@LaunchedEffect
        val messageRes = when (notice) {
            UpdateNotice.UP_TO_DATE -> R.string.app_update_up_to_date
            UpdateNotice.CHECK_FAILED -> R.string.app_update_check_failed
        }
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        appUpdateManager.consumeNotice()
    }
    Box(modifier = modifier.fillMaxSize()) {
        when (state.stage) {
            AppStage.STARTUP -> StartupContent(
                isLoading = state.isStartupLoading,
                errorText = state.startupError?.resolve(),
                onRetry = viewModel::retryStartup,
                modifier = Modifier.fillMaxSize(),
            )

            AppStage.LOGIN -> LoginScreen(
                readerId = state.login.readerId,
                password = state.login.password,
                verifyCode = state.login.verifyCode,
                loginRetentionDays = state.login.retention.days,
                captchaDataUri = state.login.captcha?.img,
                isCaptchaLoading = state.login.isCaptchaLoading,
                isSubmitting = state.login.isSubmitting,
                errorText = (state.login.error ?: state.login.captchaError)?.resolve(),
                onReaderIdChange = viewModel::updateReaderId,
                onPasswordChange = viewModel::updatePassword,
                onVerifyCodeChange = viewModel::updateLoginVerifyCode,
                onRetentionChange = { days ->
                    LoginRetention.entries
                        .firstOrNull { retention -> retention.days == days }
                        ?.let(viewModel::selectLoginRetention)
                },
                onRefreshCaptcha = viewModel::refreshLoginCaptcha,
                onLogin = viewModel::submitLogin,
                modifier = Modifier.fillMaxSize(),
            )

            AppStage.PHONE_BINDING -> PhoneBindingScreen(
                mobile = state.phoneBinding.mobile,
                verifyCode = state.phoneBinding.verifyCode,
                smsCode = state.phoneBinding.smsCode,
                captchaDataUri = state.phoneBinding.captcha?.img,
                isCaptchaLoading = state.phoneBinding.isCaptchaLoading,
                isSendingCode = state.phoneBinding.isSendingSms,
                smsResendSeconds = state.phoneBinding.smsResendSeconds,
                isSubmitting = state.phoneBinding.isUpdating || state.isLoggingOut,
                errorText = (
                    state.phoneBinding.error ?: state.phoneBinding.captchaError
                    )?.resolve(),
                onMobileChange = { value ->
                    viewModel.updateBindingMobile(
                        value.filter(Char::isDigit).take(11),
                    )
                },
                onVerifyCodeChange = viewModel::updateBindingVerifyCode,
                onSmsCodeChange = { value ->
                    viewModel.updateBindingSmsCode(
                        value.filter(Char::isDigit).take(6),
                    )
                },
                onRefreshCaptcha = viewModel::refreshPhoneCaptcha,
                onSendCode = viewModel::sendPhoneSmsCode,
                onSubmit = viewModel::submitPhoneBinding,
                onClose = viewModel::closePhoneBinding,
                modifier = Modifier.fillMaxSize(),
            )

            AppStage.AUTHENTICATED -> AuthenticatedContent(
                state = state,
                viewModel = viewModel,
                automationManager = automationManager,
                isCheckingUpdate = updateState.isChecking,
                onCheckUpdate = {
                    appUpdateManager.checkForUpdates(userInitiated = true)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding(),
        )
    }

    if (
        state.stage == AppStage.AUTHENTICATED &&
        pendingCalendarReminder != null
    ) {
        val reminder = checkNotNull(pendingCalendarReminder)
        CalendarReminderDialog(
            reminder = reminder,
            onAdd = {
                if (openCalendarReminderEditor(context, reminder)) {
                    automationManager.dismissCalendarReminder(reminder.id)
                } else {
                    Toast.makeText(
                        context,
                        calendarReminderUnavailableText,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDismiss = {
                automationManager.dismissCalendarReminder(reminder.id)
            },
        )
    }

    updateState.dialog?.let { updateDialog ->
        AppUpdateDialog(
            dialogState = updateDialog,
            onStartDownload = appUpdateManager::startDownload,
            onInstall = appUpdateManager::installReadyApk,
            onDismiss = appUpdateManager::dismissDialog,
        )
    }
}

@Composable
private fun CalendarReminderDialog(
    reminder: ReservationCalendarReminder,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val displayName = reminder.roomName
        .takeIf(String::isNotBlank)
        ?: reminder.venueName
    val reminderTime = formatCalendarTime(reminder.eventStartAtMillis)
    val deadline = formatCalendarTime(reminder.deadlineAtMillis)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.calendar_reminder_prompt_title))
        },
        text = {
            Text(
                stringResource(
                    R.string.calendar_reminder_prompt_body,
                    displayName,
                    reminderTime,
                    deadline,
                ),
            )
        },
        confirmButton = {
            Button(onClick = onAdd) {
                Text(stringResource(R.string.calendar_reminder_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.calendar_reminder_skip))
            }
        },
    )
}

private fun formatCalendarTime(timestampMillis: Long): String =
    DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
    ).apply {
        timeZone = AUTOMATION_TIME_ZONE
    }.format(Date(timestampMillis))

@Composable
private fun StartupContent(
    isLoading: Boolean,
    errorText: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        LoadingContent(
            modifier = modifier,
            label = stringResource(R.string.restoring_session),
        )
    } else {
        ErrorContent(
            message = errorText ?: stringResource(R.string.restore_failed),
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedContent(
    state: ReservationUiState,
    viewModel: ReservationViewModel,
    automationManager: AutomationManager,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val autoBookingConfiguredText = stringResource(R.string.auto_booking_configured)
    val autoBookingTargetClearedText =
        stringResource(R.string.auto_booking_target_cleared)
    val automationSettings by automationManager.settings.collectAsStateWithLifecycle()
    val automationLogs by automationManager.logs.collectAsStateWithLifecycle()
    var profilePage by rememberSaveable { mutableStateOf(ProfilePage.PROFILE) }

    LaunchedEffect(state.selectedTab) {
        if (state.selectedTab != AuthenticatedTab.PROFILE) {
            profilePage = ProfilePage.PROFILE
        }
    }
    LaunchedEffect(state.reservationList.reservations) {
        if (automationSettings.cancellationEnabled) {
            automationManager.refreshCancellationSchedule()
        }
    }

    if (state.selectedTab == AuthenticatedTab.PROFILE) {
        when (profilePage) {
            ProfilePage.AUTOMATION -> {
                AutomationSettingsScreen(
                    settings = automationSettings,
                    onBack = { profilePage = ProfilePage.PROFILE },
                    onAutoBookingEnabledChange =
                        automationManager::setAutoBookingEnabled,
                    onCancellationEnabledChange =
                        automationManager::setCancellationEnabled,
                    onCancellationLeadMinutesChange =
                        automationManager::setCancellationLeadMinutes,
                    onMockLocationEnabledChange =
                        automationManager::setMockLocationEnabled,
                    onOpenLogs = { profilePage = ProfilePage.LOGS },
                    canScheduleExactAlarms =
                        automationManager::canScheduleExactAlarms,
                    onSystemAccessChanged = automationManager::sync,
                    modifier = modifier,
                )
                return
            }

            ProfilePage.LOGS -> {
                AutomationLogScreen(
                    entries = automationLogs,
                    onBack = { profilePage = ProfilePage.AUTOMATION },
                    onClear = automationManager::clearLogs,
                    modifier = modifier,
                )
                return
            }

            ProfilePage.PROFILE -> Unit
        }
    }

    if (
        state.selectedTab == AuthenticatedTab.ROOMS &&
        state.roomDetail.isVisible
    ) {
        val detailError = state.roomDetail.error?.resolve()
        RoomDetailScreen(
            roomId = state.roomDetail.roomId,
            detail = state.roomDetail.detail,
            availability = state.roomDetail.availability,
            selectedDate = state.roomDetail.selectedDayDate,
            selectedSlotId = state.roomDetail.selectedSlotId,
            autoBookingTarget = automationSettings.target,
            isLoading = state.roomDetail.isLoading,
            isAvailabilityRefreshing =
                state.roomDetail.isAvailabilityRefreshing,
            isBooking = state.booking.isSubmitting,
            detailErrorText = detailError,
            showBookingDialog = state.booking.isVisible,
            bookingName = state.booking.userName,
            bookingMobile = state.booking.mobile,
            requireBookingName = state.booking.requiresName,
            requireBookingMobile = state.booking.requiresMobile,
            bookingErrorText = state.booking.error?.resolve(),
            onBack = viewModel::closeRoom,
            onRetry = viewModel::retryRoomDetail,
            onRefreshAvailability = viewModel::refreshRoomAvailability,
            onSelectDate = viewModel::selectAvailabilityDay,
            onSelectSlot = { slotId ->
                state.roomDetail.selectedDayDate?.let { dayDate ->
                    viewModel.selectAvailabilitySlot(dayDate, slotId)
                }
            },
            onOpenBookingDialog = viewModel::openBookingConfirmation,
            onDismissBookingDialog = viewModel::dismissBookingConfirmation,
            onBookingNameChange = viewModel::updateBookingName,
            onBookingMobileChange = { value ->
                viewModel.updateBookingMobile(
                    value.filter(Char::isDigit).take(11),
                )
            },
            onConfirmBooking = viewModel::submitBooking,
            onConfigureAutoBooking = { slot ->
                val detail = state.roomDetail.detail
                if (detail != null) {
                    automationManager.configureAutoBooking(
                        roomId = state.roomDetail.roomId,
                        detail = detail,
                        slot = slot,
                    )
                    Toast.makeText(
                        context,
                        autoBookingConfiguredText,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onClearAutoBookingTarget = {
                automationManager.clearAutoBookingTarget()
                Toast.makeText(
                    context,
                    autoBookingTargetClearedText,
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(state.selectedTab.titleResource()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (state.selectedTab == AuthenticatedTab.RESERVATIONS) {
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(context, LibraryWebViewActivity::class.java),
                                )
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Public,
                                contentDescription = stringResource(
                                    R.string.library_web_view_open,
                                ),
                            )
                        }
                        ReservationStatusFilterAction(
                            selectedStatusFilters = state.reservationList.statusFilters,
                            onToggleStatusFilter = viewModel::toggleReservationStatusFilter,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                AuthenticatedTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        icon = {
                            Icon(
                                imageVector = tab.icon(),
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(stringResource(tab.titleResource()))
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        when (state.selectedTab) {
            AuthenticatedTab.ROOMS -> RoomsScreen(
                rooms = state.roomList.rooms,
                searchQuery = state.roomList.searchQuery,
                isLoading = state.roomList.isLoading,
                isRefreshing = state.roomList.isRefreshing,
                isLoadingMore = state.roomList.isLoadingMore,
                canLoadMore = state.roomList.canLoadMore,
                errorText = state.roomList.error?.resolve(),
                onSearchQueryChange = viewModel::updateRoomSearchQuery,
                onSearch = viewModel::submitRoomSearch,
                onRefresh = viewModel::refreshRooms,
                onRetry = viewModel::retryRooms,
                onLoadMore = viewModel::loadNextRoomsPage,
                onOpenRoom = { room -> viewModel.openRoom(room.id) },
                modifier = contentModifier,
            )

            AuthenticatedTab.RESERVATIONS -> ReservationsScreen(
                reservations = state.reservationList.reservations,
                selectedStatusFilters = state.reservationList.statusFilters,
                isLoading = state.reservationList.isLoading,
                isLoadingMore = state.reservationList.isLoadingMore,
                canLoadMore = state.reservationList.canLoadMore,
                cancellingIds = state.reservationList.cancellingIds,
                errorText = state.reservationList.error?.resolve(),
                onRefresh = viewModel::refreshReservations,
                onRetry = viewModel::retryReservations,
                onLoadMore = viewModel::loadNextReservationsPage,
                onCancel = viewModel::cancelReservation,
                modifier = contentModifier,
            )

            AuthenticatedTab.SCANNER -> ScannerRoute(
                state = state,
                viewModel = viewModel,
                modifier = contentModifier,
            )

            AuthenticatedTab.PROFILE -> ProfileScreen(
                profile = state.profile,
                readerId = state.login.readerId,
                readerQrImageUrl = state.readerQrCode.imageUrl,
                readerQrPageUrl = state.readerQrCode.pageUrlInput,
                appVersionName = BuildConfig.VERSION_NAME,
                isSavingReaderQr = state.readerQrCode.isSaving,
                isCheckingUpdate = isCheckingUpdate,
                readerQrErrorText = state.readerQrCode.error?.resolve(),
                isLoggingOut = state.isLoggingOut,
                onReaderQrPageUrlChange = viewModel::updateReaderQrPageUrl,
                onSaveReaderQrPageUrl = viewModel::saveReaderQrPageUrl,
                onClearReaderQrBinding = viewModel::clearReaderQrCodeBinding,
                onOpenAutomation = { profilePage = ProfilePage.AUTOMATION },
                onCheckUpdate = onCheckUpdate,
                onLogout = viewModel::logout,
                modifier = contentModifier,
            )
        }
    }
}

private enum class ProfilePage {
    PROFILE,
    AUTOMATION,
    LOGS,
}

@Composable
private fun ScannerRoute(
    state: ReservationUiState,
    viewModel: ReservationViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = state.scanner
    var hasLocationPermission by remember {
        mutableStateOf(context.hasLocationPermission())
    }
    var locationPermissionPermanentlyDenied by rememberSaveable {
        mutableStateOf(false)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationPermissionPermanentlyDenied = !hasLocationPermission &&
            context.arePermissionsPermanentlyDenied(LOCATION_PERMISSIONS.asList())
        viewModel.onLocationPermissionResult(hasLocationPermission)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = context.hasLocationPermission()
                if (hasLocationPermission) {
                    locationPermissionPermanentlyDenied = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(scanner.phase, scanner.error, hasLocationPermission) {
        if (scanner.phase == ScannerPhase.LOCATION_PERMISSION_REQUIRED) {
            if (hasLocationPermission) {
                viewModel.onLocationPermissionResult(true)
            } else if (scanner.error == null) {
                locationPermissionLauncher.launch(
                    LOCATION_PERMISSIONS,
                )
            }
        }
    }

    val feedbackText = scanner.error?.resolve() ?: when (scanner.phase) {
        ScannerPhase.COMPLETED -> stringResource(R.string.operation_success)
        ScannerPhase.NO_ACTIVE_RESERVATION ->
            stringResource(R.string.current_reservation_missing)

        ScannerPhase.NOT_ELIGIBLE -> stringResource(R.string.not_in_sign_time)
        else -> null
    }

    ScannerScreen(
        scannerEnabled = scanner.cameraEnabled,
        restartKey = scanner.scannedCode?.rawValue?.hashCode() ?: 0,
        isChecking = scanner.phase == ScannerPhase.LOADING,
        isLocating = scanner.phase == ScannerPhase.LOCATING,
        isSigningIn = scanner.phase == ScannerPhase.SIGNING_IN,
        isSigningOut = scanner.phase == ScannerPhase.SIGNING_OUT,
        feedbackText = feedbackText,
        onScan = viewModel::onQrScanned,
        onScannerError = {},
        onScanAgain = viewModel::startScanner,
        modifier = modifier,
    )

    when {
        scanner.phase == ScannerPhase.READY_TO_SIGN_IN && scanner.error == null -> {
            ScannerConfirmationDialog(
                title = stringResource(R.string.confirm_sign_in),
                body = stringResource(R.string.confirm_sign_in_body),
                onConfirm = viewModel::confirmScannerAction,
                onDismiss = viewModel::startScanner,
            )
        }

        scanner.phase == ScannerPhase.READY_TO_SIGN_OUT && scanner.error == null -> {
            ScannerConfirmationDialog(
                title = stringResource(R.string.confirm_sign_out),
                body = stringResource(R.string.confirm_sign_out_body),
                onConfirm = viewModel::confirmScannerAction,
                onDismiss = viewModel::startScanner,
            )
        }

        scanner.phase == ScannerPhase.LOCATION_PERMISSION_REQUIRED &&
            scanner.error != null -> {
            AlertDialog(
                onDismissRequest = viewModel::startScanner,
                title = { Text(stringResource(R.string.location_permission_title)) },
                text = {
                    Text(
                        scanner.error.resolve(),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (locationPermissionPermanentlyDenied) {
                                context.openApplicationSettings()
                            } else {
                                locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                if (locationPermissionPermanentlyDenied) {
                                    R.string.open_settings
                                } else {
                                    R.string.allow_location
                                },
                            ),
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::startScanner) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun ScannerConfirmationDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun UiText.resolve(): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.Resource -> stringResource(id, *formatArgs.toTypedArray())
}

private fun AuthenticatedTab.titleResource(): Int = when (this) {
    AuthenticatedTab.ROOMS -> R.string.nav_rooms
    AuthenticatedTab.RESERVATIONS -> R.string.nav_reservations
    AuthenticatedTab.SCANNER -> R.string.nav_scanner
    AuthenticatedTab.PROFILE -> R.string.nav_profile
}

private fun AuthenticatedTab.icon(): ImageVector = when (this) {
    AuthenticatedTab.ROOMS -> Icons.Outlined.MeetingRoom
    AuthenticatedTab.RESERVATIONS -> Icons.AutoMirrored.Outlined.EventNote
    AuthenticatedTab.SCANNER -> Icons.Outlined.QrCodeScanner
    AuthenticatedTab.PROFILE -> Icons.Outlined.Person
}

private fun android.content.Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
