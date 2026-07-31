package cn.ahlib.reservation.ui

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.ahlib.reservation.R
import cn.ahlib.reservation.data.ApiErrorKind
import cn.ahlib.reservation.data.ApiException
import cn.ahlib.reservation.data.ApiResult
import cn.ahlib.reservation.data.AppointmentRecord
import cn.ahlib.reservation.data.AvailabilityDay
import cn.ahlib.reservation.data.AvailabilitySlot
import cn.ahlib.reservation.data.Captcha
import cn.ahlib.reservation.data.Category
import cn.ahlib.reservation.data.CreateReservationRequest
import cn.ahlib.reservation.data.ReaderQrCodeFailure
import cn.ahlib.reservation.data.ReaderQrCodeRepository
import cn.ahlib.reservation.data.ReaderQrCodeResult
import cn.ahlib.reservation.data.ReservationRepository
import cn.ahlib.reservation.data.RoomDetail
import cn.ahlib.reservation.data.RoomSignOffRequest
import cn.ahlib.reservation.data.RoomSignRequest
import cn.ahlib.reservation.data.RoomSummary
import cn.ahlib.reservation.data.SIGN_STATE_PENDING
import cn.ahlib.reservation.data.SIGN_STATE_SIGNED_IN
import cn.ahlib.reservation.data.SIGN_STATE_SIGNED_OUT
import cn.ahlib.reservation.data.SessionValidation
import cn.ahlib.reservation.data.UserInfo
import cn.ahlib.reservation.data.isCancellationEligible
import cn.ahlib.reservation.data.isPendingCheckIn
import cn.ahlib.reservation.data.isSelectableForReservation
import cn.ahlib.reservation.data.toSessionValidation
import cn.ahlib.reservation.location.ChinaCoordinateConverter
import cn.ahlib.reservation.location.DeviceLocationProvider
import cn.ahlib.reservation.location.roomSignInCoordinateOrNull
import cn.ahlib.reservation.scanner.ParsedQrCode
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface UiText {
    data class Resource(
        @param:StringRes val id: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : UiText

    data class Dynamic(val value: String) : UiText
}

data class UiMessage(
    val id: Long,
    val text: UiText,
)

enum class AppStage {
    STARTUP,
    LOGIN,
    PHONE_BINDING,
    AUTHENTICATED,
}

enum class AuthenticatedTab {
    ROOMS,
    RESERVATIONS,
    SCANNER,
    PROFILE,
}

enum class ReservationStatusFilter {
    PENDING_CHECK_IN,
    SIGNED_IN,
    SIGNED_OUT,
    OTHER,
    ;

    companion object {
        val DEFAULT_SELECTION: Set<ReservationStatusFilter> = setOf(
            PENDING_CHECK_IN,
            SIGNED_IN,
        )
    }
}

internal fun AppointmentRecord.reservationStatusFilter(): ReservationStatusFilter = when {
    signState == SIGN_STATE_SIGNED_IN -> ReservationStatusFilter.SIGNED_IN
    signState == SIGN_STATE_SIGNED_OUT -> ReservationStatusFilter.SIGNED_OUT
    isPendingCheckIn() -> ReservationStatusFilter.PENDING_CHECK_IN
    else -> ReservationStatusFilter.OTHER
}

enum class LoginRetention(val days: Int) {
    TWO_DAYS(2),
    FIFTEEN_DAYS(15),
    THIRTY_DAYS(30),
}

data class LoginUiState(
    val readerId: String = "",
    val password: String = "",
    val verifyCode: String = "",
    val retention: LoginRetention = LoginRetention.TWO_DAYS,
    val captcha: Captcha? = null,
    val isCaptchaLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val captchaError: UiText? = null,
    val error: UiText? = null,
)

data class PhoneBindingUiState(
    val mobile: String = "",
    val verifyCode: String = "",
    val smsCode: String = "",
    val captcha: Captcha? = null,
    val isCaptchaLoading: Boolean = false,
    val isSendingSms: Boolean = false,
    val isUpdating: Boolean = false,
    val smsCodeSent: Boolean = false,
    val smsResendSeconds: Int = 0,
    val captchaError: UiText? = null,
    val error: UiText? = null,
)

data class RoomListUiState(
    val category: Category? = null,
    val searchQuery: String = "",
    val appliedSearchQuery: String = "",
    val rooms: List<RoomSummary> = emptyList(),
    val pageNum: Int = 0,
    val totalPages: Int = 0,
    val total: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: UiText? = null,
) {
    val canLoadMore: Boolean
        get() = !isLoading &&
            !isRefreshing &&
            !isLoadingMore &&
            pageNum > 0 &&
            pageNum < totalPages
}

data class RoomDetailUiState(
    val isVisible: Boolean = false,
    val roomId: String = "",
    val detail: RoomDetail? = null,
    val availability: List<AvailabilityDay> = emptyList(),
    val selectedDayDate: String? = null,
    val selectedSlotId: String? = null,
    val isLoading: Boolean = false,
    val isAvailabilityRefreshing: Boolean = false,
    val error: UiText? = null,
) {
    val selectedDay: AvailabilityDay?
        get() = availability.firstOrNull { day -> day.date == selectedDayDate }

    val selectedSlot: AvailabilitySlot?
        get() = selectedDay?.list?.firstOrNull { slot -> slot.id == selectedSlotId }

    val selectedDateTime: String?
        get() {
            val day = selectedDay ?: return null
            val slot = selectedSlot?.takeIf(AvailabilitySlot::isSelectableForReservation)
                ?: return null
            return "${day.date} ${slot.startTime}~${slot.endTime}"
        }
}

data class BookingUiState(
    val isVisible: Boolean = false,
    val roomName: String = "",
    val venueName: String = "",
    val dateTime: String = "",
    val bookingId: String = "",
    val requiresName: Boolean = false,
    val requiresMobile: Boolean = false,
    val userName: String = "",
    val mobile: String = "",
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
)

data class ReservationListUiState(
    val reservations: List<AppointmentRecord> = emptyList(),
    val statusFilters: Set<ReservationStatusFilter> = ReservationStatusFilter.DEFAULT_SELECTION,
    val pageNum: Int = 0,
    val totalPages: Int = 0,
    val total: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val cancellingIds: Set<String> = emptySet(),
    val error: UiText? = null,
) {
    val canLoadMore: Boolean
        get() = !isLoading && !isLoadingMore && pageNum > 0 && pageNum < totalPages
}

enum class ScannerPhase {
    SCANNING,
    LOADING,
    READY_TO_SIGN_IN,
    LOCATION_PERMISSION_REQUIRED,
    LOCATING,
    SIGNING_IN,
    READY_TO_SIGN_OUT,
    SIGNING_OUT,
    NO_ACTIVE_RESERVATION,
    NOT_ELIGIBLE,
    COMPLETED,
    ERROR,
}

enum class ScannerAction {
    SIGN_IN,
    SIGN_OUT,
}

data class ScannerUiState(
    val phase: ScannerPhase = ScannerPhase.SCANNING,
    val scannedCode: ParsedQrCode? = null,
    val roomDetail: RoomDetail? = null,
    val reservation: AppointmentRecord? = null,
    val action: ScannerAction? = null,
    val locationRequired: Boolean = false,
    val error: UiText? = null,
) {
    val cameraEnabled: Boolean
        get() = phase == ScannerPhase.SCANNING
}

data class ReaderQrCodeUiState(
    val imageUrl: String? = null,
    val pageUrlInput: String = "",
    val isSaving: Boolean = false,
    val error: UiText? = null,
)

data class ReservationUiState(
    val stage: AppStage = AppStage.STARTUP,
    val isStartupLoading: Boolean = true,
    val startupError: UiText? = null,
    val profile: UserInfo? = null,
    val selectedTab: AuthenticatedTab = AuthenticatedTab.ROOMS,
    val isLoggingOut: Boolean = false,
    val login: LoginUiState = LoginUiState(),
    val phoneBinding: PhoneBindingUiState = PhoneBindingUiState(),
    val roomList: RoomListUiState = RoomListUiState(),
    val roomDetail: RoomDetailUiState = RoomDetailUiState(),
    val booking: BookingUiState = BookingUiState(),
    val reservationList: ReservationListUiState = ReservationListUiState(),
    val scanner: ScannerUiState = ScannerUiState(),
    val readerQrCode: ReaderQrCodeUiState = ReaderQrCodeUiState(),
    val message: UiMessage? = null,
)

internal fun sortReservationsByDate(
    reservations: List<AppointmentRecord>,
): List<AppointmentRecord> = reservations
    .map { record ->
        SortableReservation(
            date = record.reservationDateForSorting() ?: LocalDate.MAX,
            startTime = record.reservationStartTimeForSorting() ?: LocalTime.MAX,
            record = record,
        )
    }
    .sortedWith(
        compareBy<SortableReservation>(
            SortableReservation::date,
            SortableReservation::startTime,
        ),
    )
    .map(SortableReservation::record)

private data class SortableReservation(
    val date: LocalDate,
    val startTime: LocalTime,
    val record: AppointmentRecord,
)

internal fun mergeRefreshedRooms(
    current: List<RoomSummary>,
    refreshed: List<RoomSummary>,
): List<RoomSummary> {
    if (current.isEmpty()) {
        return refreshed
    }
    val currentById = current
        .asSequence()
        .filter { room -> room.id.isNotBlank() }
        .associateBy(RoomSummary::id)
    val merged = refreshed.map { room ->
        currentById[room.id]
            ?.takeIf { currentRoom -> currentRoom == room }
            ?: room
    }
    return if (
        merged.size == current.size &&
        merged.indices.all { index -> merged[index] === current[index] }
    ) {
        current
    } else {
        merged
    }
}

internal fun AppointmentRecord.reservationDateForDisplay(): String? =
    reservationDateForSorting()?.toString()

private fun AppointmentRecord.reservationDateForSorting(): LocalDate? {
    val value = bookDate
        ?.takeIf(String::isNotBlank)
        ?: dateTime?.takeIf(String::isNotBlank)
        ?: return null
    val normalized = value
        .trim()
        .take(10)
        .replace('/', '-')
        .replace('.', '-')
    return runCatching { LocalDate.parse(normalized) }.getOrNull()
}

private fun AppointmentRecord.reservationStartTimeForSorting(): LocalTime? {
    val parts = startTime
        ?.trim()
        ?.split(':')
        ?: return null
    if (parts.size !in 2..3) {
        return null
    }
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
}

class ReservationViewModel(
    private val repository: ReservationRepository,
    private val readerQrCodeRepository: ReaderQrCodeRepository,
    private val locationProvider: DeviceLocationProvider,
    private val shouldUseMockLocation: () -> Boolean,
    private val queueCalendarReminder: (
        roomName: String,
        venueName: String,
        reservationDateTime: String,
        createdAtMillis: Long,
    ) -> Unit,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReservationUiState())
    val uiState: StateFlow<ReservationUiState> = _uiState.asStateFlow()

    private val messageIds = AtomicLong(0L)
    private var sessionGeneration = 0L
    private var roomRequestId = 0L
    private var detailRequestId = 0L
    private var reservationRequestId = 0L
    private var scannerRequestId = 0L
    private var readerQrRequestId = 0L

    private var startupJob: Job? = null
    private var loginCaptchaJob: Job? = null
    private var loginJob: Job? = null
    private var phoneCaptchaJob: Job? = null
    private var phoneSmsJob: Job? = null
    private var smsCountdownJob: Job? = null
    private var phoneUpdateJob: Job? = null
    private var roomsJob: Job? = null
    private var roomDetailJob: Job? = null
    private var bookingJob: Job? = null
    private var reservationsJob: Job? = null
    private val cancellationJobs = mutableMapOf<String, Job>()
    private var scannerJob: Job? = null
    private var readerQrCodeJob: Job? = null
    private var logoutJob: Job? = null

    init {
        restoreSession()
    }

    fun restoreSession() {
        startupJob?.cancel()
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                stage = AppStage.STARTUP,
                isStartupLoading = true,
                startupError = null,
            )
        }
        startupJob = viewModelScope.launch {
            when (val statusResult = restoredSessionStatus()) {
                is ApiResult.Failure -> {
                    if (!isCurrentSession(generation)) {
                        return@launch
                    }
                    if (statusResult.exception.isSessionExpired) {
                        enterLogin()
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                isStartupLoading = false,
                                startupError = statusResult.exception.toUiText(),
                            )
                        }
                    }
                }

                is ApiResult.Success -> {
                    if (!isCurrentSession(generation)) {
                        return@launch
                    }
                    if (!statusResult.data) {
                        enterLogin()
                        return@launch
                    }
                    when (val profileResult = repository.getUserInfo()) {
                        is ApiResult.Failure -> {
                            if (!isCurrentSession(generation)) {
                                return@launch
                            }
                            if (profileResult.exception.isSessionExpired) {
                                expireSession()
                            } else {
                                _uiState.update { state ->
                                    state.copy(
                                        isStartupLoading = false,
                                        startupError = profileResult.exception.toUiText(),
                                    )
                                }
                            }
                        }

                        is ApiResult.Success -> {
                            if (!isCurrentSession(generation)) {
                                return@launch
                            }
                            val profile = profileResult.data
                            if (profile == null) {
                                if (confirmSessionExpired()) {
                                    expireSession()
                                } else {
                                    _uiState.update { state ->
                                        state.copy(
                                            isStartupLoading = false,
                                            startupError = UiText.Resource(
                                                R.string.error_response,
                                            ),
                                        )
                                    }
                                }
                            } else {
                                acceptProfile(profile)
                            }
                        }
                    }
                }
            }
        }
    }

    fun retryStartup() {
        restoreSession()
    }

    fun updateReaderId(value: String) {
        _uiState.update { state ->
            state.copy(login = state.login.copy(readerId = value, error = null))
        }
    }

    fun updateReaderQrPageUrl(value: String) {
        _uiState.update { state ->
            state.copy(
                readerQrCode = state.readerQrCode.copy(
                    pageUrlInput = value,
                    error = null,
                ),
            )
        }
    }

    fun saveReaderQrPageUrl() {
        val state = _uiState.value
        if (
            state.stage != AppStage.AUTHENTICATED ||
            state.readerQrCode.isSaving
        ) {
            return
        }
        val readerId = state.login.readerId.trim()
        val pageUrl = state.readerQrCode.pageUrlInput.trim()
        if (readerId.isEmpty() || pageUrl.isEmpty()) {
            _uiState.update { current ->
                current.copy(
                    readerQrCode = current.readerQrCode.copy(
                        error = UiText.Resource(R.string.reader_qr_error_invalid_url),
                    ),
                )
            }
            return
        }

        readerQrCodeJob?.cancel()
        val requestId = ++readerQrRequestId
        _uiState.update { current ->
            current.copy(
                readerQrCode = current.readerQrCode.copy(
                    isSaving = true,
                    error = null,
                ),
            )
        }
        readerQrCodeJob = viewModelScope.launch {
            when (
                val result = readerQrCodeRepository.resolveAndCache(
                    readerId = readerId,
                    pageUrl = pageUrl,
                )
            ) {
                is ReaderQrCodeResult.Success -> {
                    if (requestId != readerQrRequestId) {
                        return@launch
                    }
                    _uiState.update { current ->
                        current.copy(
                            readerQrCode = ReaderQrCodeUiState(
                                imageUrl = result.imageUrl,
                            ),
                            message = newMessage(R.string.reader_qr_saved),
                        )
                    }
                }

                is ReaderQrCodeResult.Failure -> {
                    if (requestId != readerQrRequestId) {
                        return@launch
                    }
                    _uiState.update { current ->
                        current.copy(
                            readerQrCode = current.readerQrCode.copy(
                                isSaving = false,
                                error = result.reason.toUiText(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun clearReaderQrCodeBinding() {
        val state = _uiState.value
        if (state.stage != AppStage.AUTHENTICATED) {
            return
        }
        val readerId = state.login.readerId.trim()
        if (readerId.isEmpty()) {
            return
        }
        readerQrRequestId++
        readerQrCodeJob?.cancel()
        readerQrCodeRepository.clearCachedImageUrl(readerId)
        _uiState.update { current ->
            current.copy(
                readerQrCode = ReaderQrCodeUiState(),
                message = newMessage(R.string.reader_qr_cleared),
            )
        }
    }

    fun updatePassword(value: String) {
        _uiState.update { state ->
            state.copy(login = state.login.copy(password = value, error = null))
        }
    }

    fun updateLoginVerifyCode(value: String) {
        _uiState.update { state ->
            state.copy(login = state.login.copy(verifyCode = value, error = null))
        }
    }

    fun selectLoginRetention(retention: LoginRetention) {
        _uiState.update { state ->
            state.copy(login = state.login.copy(retention = retention, error = null))
        }
    }

    fun refreshLoginCaptcha() {
        if (_uiState.value.stage != AppStage.LOGIN) {
            return
        }
        loginCaptchaJob?.cancel()
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                login = state.login.copy(
                    captcha = null,
                    isCaptchaLoading = true,
                    captchaError = null,
                ),
            )
        }
        loginCaptchaJob = viewModelScope.launch {
            when (val result = repository.getCaptcha()) {
                is ApiResult.Success -> {
                    if (isCurrentSession(generation) && _uiState.value.stage == AppStage.LOGIN) {
                        _uiState.update { state ->
                            state.copy(
                                login = state.login.copy(
                                    captcha = result.data,
                                    isCaptchaLoading = false,
                                    captchaError = null,
                                ),
                            )
                        }
                    }
                }

                is ApiResult.Failure -> {
                    if (isCurrentSession(generation) && _uiState.value.stage == AppStage.LOGIN) {
                        _uiState.update { state ->
                            state.copy(
                                login = state.login.copy(
                                    isCaptchaLoading = false,
                                    captchaError = result.exception.toUiText(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun submitLogin() {
        val login = _uiState.value.login
        val validationError = when {
            login.readerId.isBlank() || login.password.isEmpty() ->
                UiText.Resource(R.string.login_fields_required)

            login.verifyCode.isBlank() || login.captcha?.uniCode.isNullOrBlank() ->
                UiText.Resource(R.string.captcha_required)

            else -> null
        }
        if (validationError != null) {
            _uiState.update { state ->
                state.copy(login = state.login.copy(error = validationError))
            }
            return
        }
        if (login.isSubmitting) {
            return
        }

        loginJob?.cancel()
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(login = state.login.copy(isSubmitting = true, error = null))
        }
        loginJob = viewModelScope.launch {
            val result = repository.login(
                readerId = login.readerId.trim(),
                password = login.password,
                verifyCode = login.verifyCode.trim(),
                uniCode = checkNotNull(login.captcha).uniCode,
                loginTime = login.retention.days,
            )
            if (!isCurrentSession(generation)) {
                return@launch
            }
            when (result) {
                is ApiResult.Success -> acceptProfile(result.data)

                is ApiResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            login = state.login.copy(
                                verifyCode = "",
                                isSubmitting = false,
                                error = result.exception.toUiText(),
                            ),
                        )
                    }
                    refreshLoginCaptcha()
                }
            }
        }
    }

    fun updateBindingMobile(value: String) {
        _uiState.update { state ->
            state.copy(
                phoneBinding = state.phoneBinding.copy(
                    mobile = value,
                    smsCodeSent = if (value == state.phoneBinding.mobile) {
                        state.phoneBinding.smsCodeSent
                    } else {
                        false
                    },
                    error = null,
                ),
            )
        }
    }

    fun updateBindingVerifyCode(value: String) {
        _uiState.update { state ->
            state.copy(
                phoneBinding = state.phoneBinding.copy(verifyCode = value, error = null),
            )
        }
    }

    fun updateBindingSmsCode(value: String) {
        _uiState.update { state ->
            state.copy(
                phoneBinding = state.phoneBinding.copy(smsCode = value, error = null),
            )
        }
    }

    fun refreshPhoneCaptcha() {
        if (_uiState.value.stage != AppStage.PHONE_BINDING) {
            return
        }
        phoneCaptchaJob?.cancel()
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                phoneBinding = state.phoneBinding.copy(
                    captcha = null,
                    isCaptchaLoading = true,
                    captchaError = null,
                ),
            )
        }
        phoneCaptchaJob = viewModelScope.launch {
            when (val result = repository.getCaptcha()) {
                is ApiResult.Success -> {
                    if (
                        isCurrentSession(generation) &&
                        _uiState.value.stage == AppStage.PHONE_BINDING
                    ) {
                        _uiState.update { state ->
                            state.copy(
                                phoneBinding = state.phoneBinding.copy(
                                    captcha = result.data,
                                    isCaptchaLoading = false,
                                    captchaError = null,
                                ),
                            )
                        }
                    }
                }

                is ApiResult.Failure -> {
                    if (handleSessionFailure(result.exception)) {
                        return@launch
                    }
                    if (
                        isCurrentSession(generation) &&
                        _uiState.value.stage == AppStage.PHONE_BINDING
                    ) {
                        _uiState.update { state ->
                            state.copy(
                                phoneBinding = state.phoneBinding.copy(
                                    isCaptchaLoading = false,
                                    captchaError = result.exception.toUiText(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun sendPhoneSmsCode() {
        val binding = _uiState.value.phoneBinding
        if (binding.smsResendSeconds > 0) {
            return
        }
        val error = when {
            binding.mobile.isBlank() -> UiText.Resource(R.string.mobile_required)
            !isValidMobile(binding.mobile) -> UiText.Resource(R.string.mobile_invalid)
            binding.verifyCode.isBlank() || binding.captcha?.uniCode.isNullOrBlank() ->
                UiText.Resource(R.string.captcha_required)

            else -> null
        }
        if (error != null) {
            _uiState.update { state ->
                state.copy(phoneBinding = state.phoneBinding.copy(error = error))
            }
            return
        }
        if (binding.isSendingSms) {
            return
        }

        phoneSmsJob?.cancel()
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                phoneBinding = state.phoneBinding.copy(isSendingSms = true, error = null),
            )
        }
        phoneSmsJob = viewModelScope.launch {
            val result = repository.sendMessageCode(
                mobile = binding.mobile.trim(),
                verifyCode = binding.verifyCode.trim(),
                uniCode = checkNotNull(binding.captcha).uniCode,
            )
            if (!isCurrentSession(generation)) {
                return@launch
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            phoneBinding = state.phoneBinding.copy(
                                verifyCode = "",
                                isSendingSms = false,
                                smsCodeSent = true,
                                smsResendSeconds = SMS_RESEND_SECONDS,
                                error = null,
                            ),
                            message = newMessage(R.string.sms_code_sent),
                        )
                    }
                    startSmsCountdown()
                    refreshPhoneCaptcha()
                }

                is ApiResult.Failure -> {
                    if (handleSessionFailure(result.exception)) {
                        return@launch
                    }
                    _uiState.update { state ->
                        state.copy(
                            phoneBinding = state.phoneBinding.copy(
                                verifyCode = "",
                                isSendingSms = false,
                                error = result.exception.toUiText(),
                            ),
                        )
                    }
                    refreshPhoneCaptcha()
                }
            }
        }
    }

    fun submitPhoneBinding() {
        val binding = _uiState.value.phoneBinding
        val error = when {
            binding.mobile.isBlank() -> UiText.Resource(R.string.mobile_required)
            !isValidMobile(binding.mobile) -> UiText.Resource(R.string.mobile_invalid)
            binding.smsCode.isBlank() -> UiText.Resource(R.string.sms_code_required)
            binding.smsCode.trim().length !in MIN_SMS_CODE_LENGTH..MAX_SMS_CODE_LENGTH ->
                UiText.Resource(R.string.sms_code_invalid)

            else -> null
        }
        if (error != null) {
            _uiState.update { state ->
                state.copy(phoneBinding = state.phoneBinding.copy(error = error))
            }
            return
        }
        if (binding.isUpdating) {
            return
        }

        phoneUpdateJob?.cancel()
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                phoneBinding = state.phoneBinding.copy(isUpdating = true, error = null),
            )
        }
        phoneUpdateJob = viewModelScope.launch {
            val result = repository.updatePhone(
                mobile = binding.mobile.trim(),
                mobileCode = binding.smsCode.trim(),
            )
            if (!isCurrentSession(generation)) {
                return@launch
            }
            when (result) {
                is ApiResult.Success -> {
                    val currentProfile = _uiState.value.profile
                    if (currentProfile == null) {
                        expireSession()
                        return@launch
                    }
                    val profile = currentProfile.copy(
                        mobile = binding.mobile.trim(),
                        mobileStatus = BOUND_MOBILE_STATUS,
                    )
                    beginAuthenticated(profile, R.string.phone_updated)
                }

                is ApiResult.Failure -> {
                    if (handleSessionFailure(result.exception)) {
                        return@launch
                    }
                    _uiState.update { state ->
                        state.copy(
                            phoneBinding = state.phoneBinding.copy(
                                isUpdating = false,
                                error = result.exception.toUiText(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun closePhoneBinding() {
        logout()
    }

    fun selectTab(tab: AuthenticatedTab) {
        if (_uiState.value.stage != AppStage.AUTHENTICATED) {
            return
        }
        val previousTab = _uiState.value.selectedTab
        if (
            previousTab == AuthenticatedTab.SCANNER ||
            tab == AuthenticatedTab.SCANNER
        ) {
            scannerJob?.cancel()
            scannerRequestId++
        }
        _uiState.update { state ->
            state.copy(
                selectedTab = tab,
                scanner = if (
                    previousTab == AuthenticatedTab.SCANNER ||
                    tab == AuthenticatedTab.SCANNER
                ) {
                    ScannerUiState()
                } else {
                    state.scanner
                },
            )
        }
        when (tab) {
            AuthenticatedTab.ROOMS -> {
                if (
                    _uiState.value.roomList.rooms.isEmpty() &&
                    !_uiState.value.roomList.isLoading
                ) {
                    loadRooms(reset = true)
                }
            }

            AuthenticatedTab.RESERVATIONS -> {
                if (
                    _uiState.value.reservationList.reservations.isEmpty() &&
                    !_uiState.value.reservationList.isLoading
                ) {
                    loadReservations(reset = true)
                }
            }

            AuthenticatedTab.SCANNER,
            AuthenticatedTab.PROFILE,
            -> Unit
        }
    }

    fun updateRoomSearchQuery(value: String) {
        _uiState.update { state ->
            state.copy(
                roomList = state.roomList.copy(searchQuery = value, error = null),
            )
        }
    }

    fun submitRoomSearch() {
        val query = _uiState.value.roomList.searchQuery.trim()
        _uiState.update { state ->
            state.copy(
                roomList = state.roomList.copy(
                    searchQuery = query,
                    appliedSearchQuery = query,
                ),
            )
        }
        loadRooms(reset = true, forceRefresh = true)
    }

    fun refreshRooms() {
        loadRooms(
            reset = true,
            forceRefresh = true,
            retainExistingRooms = true,
        )
    }

    fun retryRooms() {
        loadRooms(
            reset = true,
            forceRefresh = true,
            retainExistingRooms = _uiState.value.roomList.rooms.isNotEmpty(),
        )
    }

    fun loadNextRoomsPage() {
        if (_uiState.value.roomList.canLoadMore) {
            loadRooms(reset = false)
        }
    }

    fun openRoom(roomId: String) {
        if (roomId.isBlank() || _uiState.value.stage != AppStage.AUTHENTICATED) {
            return
        }
        roomDetailJob?.cancel()
        val requestId = ++detailRequestId
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                roomDetail = RoomDetailUiState(
                    isVisible = true,
                    roomId = roomId,
                    isLoading = true,
                ),
                booking = BookingUiState(),
            )
        }
        roomDetailJob = viewModelScope.launch {
            val (detailResult, availabilityResult) = coroutineScope {
                val detail = async { repository.getRoomDetail(roomId) }
                val availability = async { repository.getRoomAvailability(roomId) }
                detail.await() to availability.await()
            }
            if (
                requestId != detailRequestId ||
                !isCurrentSession(generation) ||
                _uiState.value.roomDetail.roomId != roomId
            ) {
                return@launch
            }

            val detailFailure = detailResult as? ApiResult.Failure
            val availabilityFailure = availabilityResult as? ApiResult.Failure
            val sessionFailure = listOfNotNull(detailFailure, availabilityFailure)
                .firstOrNull { failure -> failure.exception.isSessionExpired }
            if (
                sessionFailure != null &&
                handleSessionFailure(sessionFailure.exception)
            ) {
                return@launch
            }

            val detail = (detailResult as? ApiResult.Success<RoomDetail>)?.data
            val availability = (
                availabilityResult as? ApiResult.Success<List<AvailabilityDay>>
            )?.data?.let(::completeAvailabilityDates)
            val initialDayDate = availability
                ?.firstOrNull(AvailabilityDay::isSelectableForReservation)
                ?.date
                ?: availability?.firstOrNull()?.date
            val error = detailFailure?.exception?.toUiText()
                ?: availabilityFailure?.exception?.toUiText()
            _uiState.update { state ->
                state.copy(
                    roomDetail = state.roomDetail.copy(
                        detail = detail,
                        availability = availability.orEmpty(),
                        selectedDayDate = initialDayDate,
                        selectedSlotId = null,
                        isLoading = false,
                        isAvailabilityRefreshing = false,
                        error = error,
                    ),
                )
            }
        }
    }

    fun retryRoomDetail() {
        val roomDetail = _uiState.value.roomDetail
        if (roomDetail.detail == null) {
            roomDetail.roomId
                .takeIf(String::isNotBlank)
                ?.let(::openRoom)
        } else {
            refreshRoomAvailability()
        }
    }

    fun refreshRoomAvailability() {
        val current = _uiState.value.roomDetail
        val roomId = current.roomId
        if (
            !current.isVisible ||
            roomId.isBlank() ||
            current.isLoading ||
            current.isAvailabilityRefreshing
        ) {
            return
        }
        roomDetailJob?.cancel()
        val requestId = ++detailRequestId
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                roomDetail = state.roomDetail.copy(
                    isAvailabilityRefreshing = true,
                    error = null,
                ),
            )
        }
        roomDetailJob = viewModelScope.launch {
            when (
                val result = repository.getRoomAvailability(roomId)
            ) {
                is ApiResult.Success -> {
                    if (
                        requestId != detailRequestId ||
                        !isCurrentSession(generation) ||
                        _uiState.value.roomDetail.roomId != roomId
                    ) {
                        return@launch
                    }
                    val availability = completeAvailabilityDates(result.data)
                    _uiState.update { state ->
                        val detailState = state.roomDetail
                        val selectedDayDate = detailState.selectedDayDate
                            ?.takeIf { date ->
                                availability.any { day -> day.date == date }
                            }
                            ?: availability
                                .firstOrNull(AvailabilityDay::isSelectableForReservation)
                                ?.date
                            ?: availability.firstOrNull()?.date
                        val selectedSlotId = detailState.selectedSlotId
                            ?.takeIf { slotId ->
                                availability
                                    .firstOrNull { day ->
                                        day.date == selectedDayDate
                                    }
                                    ?.list
                                    ?.any { slot ->
                                        slot.id == slotId &&
                                            slot.isSelectableForReservation()
                                    } == true
                            }
                        state.copy(
                            roomDetail = detailState.copy(
                                availability = availability,
                                selectedDayDate = selectedDayDate,
                                selectedSlotId = selectedSlotId,
                                isAvailabilityRefreshing = false,
                                error = null,
                            ),
                        )
                    }
                }

                is ApiResult.Failure -> {
                    if (handleSessionFailure(result.exception)) {
                        return@launch
                    }
                    if (
                        requestId == detailRequestId &&
                        isCurrentSession(generation) &&
                        _uiState.value.roomDetail.roomId == roomId
                    ) {
                        _uiState.update { state ->
                            state.copy(
                                roomDetail = state.roomDetail.copy(
                                    isAvailabilityRefreshing = false,
                                    error = result.exception.toUiText(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun closeRoom() {
        roomDetailJob?.cancel()
        detailRequestId++
        _uiState.update { state ->
            state.copy(
                roomDetail = RoomDetailUiState(),
                booking = BookingUiState(),
            )
        }
    }

    fun selectAvailabilitySlot(dayDate: String, slotId: String) {
        val detailState = _uiState.value.roomDetail
        val day = detailState.availability.firstOrNull { item -> item.date == dayDate }
        val slot = day?.list?.firstOrNull { item -> item.id == slotId }
        if (
            day == null ||
            day.isOpen != 1 ||
            (day.totalLeftNum ?: 0) <= 0 ||
            slot == null ||
            !slot.isSelectableForReservation()
        ) {
            _uiState.update { state ->
                state.copy(message = newMessage(R.string.slot_unavailable))
            }
            return
        }
        _uiState.update { state ->
            state.copy(
                roomDetail = state.roomDetail.copy(
                    selectedDayDate = dayDate,
                    selectedSlotId = slotId,
                    error = null,
                ),
            )
        }
    }

    fun selectAvailabilityDay(dayDate: String) {
        val day = _uiState.value.roomDetail.availability
            .firstOrNull { item -> item.date == dayDate }
        if (day == null || day.date.isBlank()) {
            return
        }
        _uiState.update { state ->
            state.copy(
                roomDetail = state.roomDetail.copy(
                    selectedDayDate = dayDate,
                    selectedSlotId = null,
                    error = null,
                ),
            )
        }
    }

    fun openBookingConfirmation() {
        val state = _uiState.value
        val detail = state.roomDetail.detail
        val dateTime = state.roomDetail.selectedDateTime
        val bookingId = state.roomDetail.selectedSlot?.id
        if (detail == null || dateTime == null || bookingId.isNullOrBlank()) {
            _uiState.update { current ->
                current.copy(message = newMessage(R.string.slot_required))
            }
            return
        }
        val profile = state.profile ?: return expireSession()
        val requiresName = profile.name.isNullOrBlank()
        val requiresMobile = profile.mobile.isNullOrBlank()
        _uiState.update { current ->
            current.copy(
                booking = BookingUiState(
                    isVisible = true,
                    roomName = detail.roomName,
                    venueName = detail.venueName.orEmpty(),
                    dateTime = dateTime,
                    bookingId = bookingId,
                    requiresName = requiresName,
                    requiresMobile = requiresMobile,
                    userName = if (requiresName) "" else profile.name.orEmpty(),
                    mobile = if (requiresMobile) "" else profile.mobile.orEmpty(),
                ),
            )
        }
    }

    fun dismissBookingConfirmation() {
        if (_uiState.value.booking.isSubmitting) {
            return
        }
        _uiState.update { state -> state.copy(booking = BookingUiState()) }
    }

    fun updateBookingName(value: String) {
        _uiState.update { state ->
            state.copy(booking = state.booking.copy(userName = value, error = null))
        }
    }

    fun updateBookingMobile(value: String) {
        _uiState.update { state ->
            state.copy(booking = state.booking.copy(mobile = value, error = null))
        }
    }

    fun submitBooking() {
        val booking = _uiState.value.booking
        if (!booking.isVisible || booking.isSubmitting) {
            return
        }
        val error = when {
            booking.venueName.isBlank() -> UiText.Resource(R.string.venue_missing)
            booking.dateTime.isBlank() || booking.bookingId.isBlank() ->
                UiText.Resource(R.string.slot_required)

            booking.requiresName && booking.userName.isBlank() ->
                UiText.Resource(R.string.name_required)

            booking.requiresName && booking.userName.trim().length > MAX_NAME_LENGTH ->
                UiText.Resource(R.string.name_too_long)

            booking.requiresMobile && booking.mobile.isBlank() ->
                UiText.Resource(R.string.mobile_required)

            booking.requiresMobile && !isValidMobile(booking.mobile) ->
                UiText.Resource(R.string.mobile_invalid)

            else -> null
        }
        if (error != null) {
            _uiState.update { state ->
                state.copy(booking = state.booking.copy(error = error))
            }
            return
        }

        bookingJob?.cancel()
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(booking = state.booking.copy(isSubmitting = true, error = null))
        }
        bookingJob = viewModelScope.launch {
            val result = repository.createReservation(
                CreateReservationRequest(
                    venueName = booking.venueName.trim(),
                    dateTime = booking.dateTime,
                    bookingId = booking.bookingId,
                    userName = booking.userName.trim().takeIf { booking.requiresName },
                    phoneNum = booking.mobile.trim().takeIf { booking.requiresMobile },
                ),
            )
            if (!isCurrentSession(generation)) {
                return@launch
            }
            when (result) {
                is ApiResult.Success -> {
                    queueCalendarReminder(
                        booking.roomName,
                        booking.venueName,
                        booking.dateTime,
                        System.currentTimeMillis(),
                    )
                    _uiState.update { state ->
                        state.copy(
                            selectedTab = AuthenticatedTab.RESERVATIONS,
                            roomDetail = RoomDetailUiState(),
                            booking = BookingUiState(),
                            message = newMessage(R.string.reservation_created),
                        )
                    }
                    loadReservations(reset = true)
                }

                is ApiResult.Failure -> {
                    if (handleSessionFailure(result.exception)) {
                        return@launch
                    }
                    _uiState.update { state ->
                        state.copy(
                            booking = state.booking.copy(
                                isSubmitting = false,
                                error = result.exception.toUiText(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun refreshReservations() {
        loadReservations(reset = true)
    }

    fun toggleReservationStatusFilter(status: ReservationStatusFilter) {
        _uiState.update { state ->
            val current = state.reservationList.statusFilters
            state.copy(
                reservationList = state.reservationList.copy(
                    statusFilters = if (status in current) current - status else current + status,
                ),
            )
        }
    }

    fun retryReservations() {
        loadReservations(reset = true)
    }

    fun loadNextReservationsPage() {
        if (_uiState.value.reservationList.canLoadMore) {
            loadReservations(reset = false)
        }
    }

    fun canCancelReservation(record: AppointmentRecord): Boolean =
        record.isCancellationEligible() &&
            record.id !in _uiState.value.reservationList.cancellingIds

    fun cancelReservation(record: AppointmentRecord) {
        if (!canCancelReservation(record)) {
            _uiState.update { state ->
                state.copy(message = newMessage(R.string.reservation_cancel_not_allowed))
            }
            return
        }
        val id = record.id
        if (cancellationJobs[id]?.isActive == true) {
            return
        }
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                reservationList = state.reservationList.copy(
                    cancellingIds = state.reservationList.cancellingIds + id,
                ),
            )
        }
        cancellationJobs[id] = viewModelScope.launch {
            try {
                when (val result = repository.cancelReservation(id)) {
                    is ApiResult.Success -> {
                        if (!isCurrentSession(generation)) {
                            return@launch
                        }
                        _uiState.update { state ->
                            state.copy(message = newMessage(R.string.reservation_cancelled))
                        }
                        loadReservations(reset = true)
                    }

                    is ApiResult.Failure -> {
                        if (handleSessionFailure(result.exception)) {
                            return@launch
                        }
                        if (isCurrentSession(generation)) {
                            _uiState.update { state ->
                                state.copy(
                                    reservationList = state.reservationList.copy(
                                        error = result.exception.toUiText(),
                                    ),
                                )
                            }
                        }
                    }
                }
            } finally {
                cancellationJobs.remove(id)
                if (isCurrentSession(generation)) {
                    _uiState.update { state ->
                        state.copy(
                            reservationList = state.reservationList.copy(
                                cancellingIds = state.reservationList.cancellingIds - id,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun startScanner() {
        scannerJob?.cancel()
        scannerRequestId++
        _uiState.update { state -> state.copy(scanner = ScannerUiState()) }
    }

    fun onQrScanned(code: ParsedQrCode) {
        if (_uiState.value.stage != AppStage.AUTHENTICATED) {
            return
        }
        scannerJob?.cancel()
        val requestId = ++scannerRequestId
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                scanner = ScannerUiState(
                    phase = ScannerPhase.LOADING,
                    scannedCode = code,
                ),
            )
        }
        scannerJob = viewModelScope.launch {
            val (detailResult, reservationResult) = coroutineScope {
                val detail = async { repository.getRoomDetail(code.roomId) }
                val reservation = async { repository.getCurrentReservation(code.roomId) }
                detail.await() to reservation.await()
            }
            if (requestId != scannerRequestId || !isCurrentSession(generation)) {
                return@launch
            }
            val failures = listOfNotNull(
                detailResult as? ApiResult.Failure,
                reservationResult as? ApiResult.Failure,
            )
            val sessionFailure = failures.firstOrNull { failure ->
                failure.exception.isSessionExpired
            }
            if (
                sessionFailure != null &&
                handleSessionFailure(sessionFailure.exception)
            ) {
                return@launch
            }
            val failure = failures.firstOrNull()
            if (failure != null) {
                _uiState.update { state ->
                    state.copy(
                        scanner = state.scanner.copy(
                            phase = ScannerPhase.ERROR,
                            error = failure.exception.toUiText(),
                        ),
                    )
                }
                return@launch
            }

            val detail = (detailResult as ApiResult.Success<RoomDetail>).data
            val reservation = (
                reservationResult as ApiResult.Success<AppointmentRecord?>
            ).data
            updateScannerForRecord(code, detail, reservation)
        }
    }

    fun retryScanner() {
        val code = _uiState.value.scanner.scannedCode
        if (code == null) {
            startScanner()
        } else {
            onQrScanned(code)
        }
    }

    fun confirmScannerAction() {
        val scanner = _uiState.value.scanner
        when (scanner.phase) {
            ScannerPhase.READY_TO_SIGN_IN -> {
                if (
                    scanner.locationRequired &&
                    !shouldUseMockLocation()
                ) {
                    _uiState.update { state ->
                        state.copy(
                            scanner = state.scanner.copy(
                                phase = ScannerPhase.LOCATION_PERMISSION_REQUIRED,
                                error = null,
                            ),
                        )
                    }
                } else {
                    performSignIn(
                        requiresLocation = scanner.locationRequired,
                    )
                }
            }

            ScannerPhase.READY_TO_SIGN_OUT -> performSignOut()
            else -> Unit
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (_uiState.value.scanner.phase != ScannerPhase.LOCATION_PERMISSION_REQUIRED) {
            return
        }
        if (!granted) {
            _uiState.update { state ->
                state.copy(
                    scanner = state.scanner.copy(
                        error = UiText.Resource(R.string.location_permission_required),
                    ),
                )
            }
            return
        }
        performSignIn(requiresLocation = true)
    }

    fun consumeMessage(messageId: Long) {
        _uiState.update { state ->
            if (state.message?.id == messageId) {
                state.copy(message = null)
            } else {
                state
            }
        }
    }

    fun logout() {
        if (_uiState.value.isLoggingOut) {
            return
        }
        logoutJob?.cancel()
        _uiState.update { state -> state.copy(isLoggingOut = true) }
        logoutJob = viewModelScope.launch {
            withTimeoutOrNull(LOGOUT_TIMEOUT_MILLIS) {
                repository.logout()
            }
            enterLogin(R.string.logged_out)
        }
    }

    private fun acceptProfile(profile: UserInfo) {
        if (profile.mobileStatus?.trim() == UNBOUND_MOBILE_STATUS) {
            beginPhoneBinding(profile)
        } else {
            beginAuthenticated(profile)
        }
    }

    private fun beginPhoneBinding(profile: UserInfo) {
        cancelAuthenticatedJobs()
        val readerId = profile.readerId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: _uiState.value.login.readerId
        _uiState.value = ReservationUiState(
            stage = AppStage.PHONE_BINDING,
            isStartupLoading = false,
            profile = profile,
            login = _uiState.value.login.copy(
                readerId = readerId,
                password = "",
                verifyCode = "",
                isSubmitting = false,
                error = null,
            ),
            phoneBinding = PhoneBindingUiState(mobile = profile.mobile.orEmpty()),
        )
        refreshPhoneCaptcha()
    }

    private fun beginAuthenticated(
        profile: UserInfo,
        @StringRes messageId: Int? = null,
    ) {
        loginCaptchaJob?.cancel()
        phoneCaptchaJob?.cancel()
        smsCountdownJob?.cancel()
        val readerId = profile.readerId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: _uiState.value.login.readerId
        _uiState.value = ReservationUiState(
            stage = AppStage.AUTHENTICATED,
            isStartupLoading = false,
            profile = profile,
            selectedTab = AuthenticatedTab.ROOMS,
            login = LoginUiState(readerId = readerId),
            readerQrCode = ReaderQrCodeUiState(
                imageUrl = readerQrCodeRepository.cachedImageUrl(readerId),
            ),
            message = messageId?.let(::newMessage),
        )
        loadRooms(reset = true)
    }

    private fun enterLogin(@StringRes messageId: Int? = null) {
        sessionGeneration++
        cancelSessionJobs()
        val readerId = _uiState.value.login.readerId
        _uiState.value = ReservationUiState(
            stage = AppStage.LOGIN,
            isStartupLoading = false,
            login = LoginUiState(readerId = readerId),
            message = messageId?.let(::newMessage),
        )
        refreshLoginCaptcha()
    }

    private fun expireSession() {
        if (_uiState.value.stage == AppStage.LOGIN) {
            return
        }
        sessionGeneration++
        cancelSessionJobs()
        val readerId = _uiState.value.login.readerId
        _uiState.value = ReservationUiState(
            stage = AppStage.LOGIN,
            isStartupLoading = false,
            login = LoginUiState(
                readerId = readerId,
                isCaptchaLoading = true,
            ),
            message = newMessage(R.string.session_expired),
        )
        repository.clearLocalSession()
        refreshLoginCaptcha()
    }

    private fun loadRooms(
        reset: Boolean,
        forceRefresh: Boolean = false,
        retainExistingRooms: Boolean = false,
    ) {
        if (_uiState.value.stage != AppStage.AUTHENTICATED) {
            return
        }
        val current = _uiState.value.roomList
        if (reset) {
            roomsJob?.cancel()
        } else if (current.isLoading || current.isLoadingMore || !current.canLoadMore) {
            return
        }
        val requestId = ++roomRequestId
        val generation = sessionGeneration
        val targetPage = if (reset) FIRST_PAGE else current.pageNum + 1
        val isPartialRefresh =
            reset && retainExistingRooms && current.rooms.isNotEmpty()
        _uiState.update { state ->
            state.copy(
                roomList = state.roomList.copy(
                    rooms = if (reset && !isPartialRefresh) {
                        emptyList()
                    } else {
                        state.roomList.rooms
                    },
                    pageNum = if (reset && !isPartialRefresh) {
                        0
                    } else {
                        state.roomList.pageNum
                    },
                    totalPages = if (reset && !isPartialRefresh) {
                        0
                    } else {
                        state.roomList.totalPages
                    },
                    total = if (reset && !isPartialRefresh) {
                        0
                    } else {
                        state.roomList.total
                    },
                    isLoading = reset && !isPartialRefresh,
                    isRefreshing = isPartialRefresh,
                    isLoadingMore = !reset,
                    error = null,
                ),
            )
        }
        roomsJob = viewModelScope.launch {
            val category = _uiState.value.roomList.category ?: when (
                val categoryResult = repository.findReservationCategory(
                    forceRefresh = forceRefresh,
                )
            ) {
                is ApiResult.Success -> categoryResult.data
                is ApiResult.Failure -> {
                    if (handleSessionFailure(categoryResult.exception)) {
                        return@launch
                    }
                    finishRoomLoad(
                        requestId = requestId,
                        reset = reset,
                        retainExistingRooms = isPartialRefresh,
                        error = categoryResult.exception.toUiText(),
                    )
                    return@launch
                }
            }
            if (
                requestId != roomRequestId ||
                !isCurrentSession(generation)
            ) {
                return@launch
            }
            if (category == null || category.id.isBlank()) {
                finishRoomLoad(
                    requestId = requestId,
                    reset = reset,
                    retainExistingRooms = isPartialRefresh,
                    error = UiText.Resource(
                        R.string.reservation_category_missing,
                    ),
                )
                return@launch
            }
            _uiState.update { state ->
                state.copy(roomList = state.roomList.copy(category = category))
            }
            when (
                val result = repository.getRooms(
                    categoryId = category.id,
                    pageNum = targetPage,
                    pageSize = ROOM_PAGE_SIZE,
                    keywords = _uiState.value.roomList.appliedSearchQuery,
                    total = if (reset) 0 else current.total,
                    forceRefresh = forceRefresh,
                )
            ) {
                is ApiResult.Success -> {
                    if (
                        requestId != roomRequestId ||
                        !isCurrentSession(generation)
                    ) {
                        return@launch
                    }
                    _uiState.update { state ->
                        val rooms = if (reset) {
                            if (isPartialRefresh) {
                                mergeRefreshedRooms(
                                    current = state.roomList.rooms,
                                    refreshed = result.data.result,
                                )
                            } else {
                                result.data.result
                            }
                        } else {
                            appendDistinctRooms(
                                state.roomList.rooms,
                                result.data.result,
                            )
                        }
                        state.copy(
                            roomList = state.roomList.copy(
                                rooms = rooms,
                                pageNum = result.data.pageNum,
                                totalPages = result.data.totalPages,
                                total = result.data.total,
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                error = null,
                            ),
                        )
                    }
                }

                is ApiResult.Failure -> {
                    if (handleSessionFailure(result.exception)) {
                        return@launch
                    }
                    finishRoomLoad(
                        requestId = requestId,
                        reset = reset,
                        retainExistingRooms = isPartialRefresh,
                        error = result.exception.toUiText(),
                    )
                }
            }
        }
    }

    private fun finishRoomLoad(
        requestId: Long,
        reset: Boolean,
        retainExistingRooms: Boolean,
        error: UiText,
    ) {
        if (requestId != roomRequestId) {
            return
        }
        _uiState.update { state ->
            state.copy(
                roomList = state.roomList.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = error,
                    rooms = if (reset && !retainExistingRooms) {
                        emptyList()
                    } else {
                        state.roomList.rooms
                    },
                ),
            )
        }
    }

    private fun loadReservations(reset: Boolean) {
        if (_uiState.value.stage != AppStage.AUTHENTICATED) {
            return
        }
        val current = _uiState.value.reservationList
        if (reset) {
            reservationsJob?.cancel()
        } else if (current.isLoading || current.isLoadingMore || !current.canLoadMore) {
            return
        }
        val requestId = ++reservationRequestId
        val generation = sessionGeneration
        val targetPage = if (reset) FIRST_PAGE else current.pageNum + 1
        _uiState.update { state ->
            state.copy(
                reservationList = state.reservationList.copy(
                    reservations = state.reservationList.reservations,
                    pageNum = if (reset) 0 else state.reservationList.pageNum,
                    totalPages = if (reset) 0 else state.reservationList.totalPages,
                    total = if (reset) 0 else state.reservationList.total,
                    isLoading = reset,
                    isLoadingMore = !reset,
                    error = null,
                ),
            )
        }
        reservationsJob = viewModelScope.launch {
            when (
                val result = repository.getMyReservations(
                    pageNum = targetPage,
                    pageSize = RESERVATION_PAGE_SIZE,
                    total = if (reset) 0 else current.total,
                )
            ) {
                is ApiResult.Success -> {
                    if (
                        requestId != reservationRequestId ||
                        !isCurrentSession(generation)
                    ) {
                        return@launch
                    }
                    _uiState.update { state ->
                        val reservations = sortReservationsByDate(
                            if (reset) {
                                result.data.result
                            } else {
                                appendDistinctReservations(
                                    state.reservationList.reservations,
                                    result.data.result,
                                )
                            },
                        )
                        state.copy(
                            reservationList = state.reservationList.copy(
                                reservations = reservations,
                                pageNum = result.data.pageNum,
                                totalPages = result.data.totalPages,
                                total = result.data.total,
                                isLoading = false,
                                isLoadingMore = false,
                                error = null,
                            ),
                        )
                    }
                }

                is ApiResult.Failure -> {
                    if (handleSessionFailure(result.exception)) {
                        return@launch
                    }
                    if (
                        requestId == reservationRequestId &&
                        isCurrentSession(generation)
                    ) {
                        _uiState.update { state ->
                            state.copy(
                                reservationList = state.reservationList.copy(
                                    isLoading = false,
                                    isLoadingMore = false,
                                    error = result.exception.toUiText(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateScannerForRecord(
        code: ParsedQrCode,
        detail: RoomDetail,
        reservation: AppointmentRecord?,
    ) {
        val locationRequired = (detail.distance ?: 0.0) > 0.0
        val scannerState = when {
            reservation == null -> ScannerUiState(
                phase = ScannerPhase.NO_ACTIVE_RESERVATION,
                scannedCode = code,
                roomDetail = detail,
                error = UiText.Resource(R.string.scanner_no_reservation),
            )

            reservation.signState == SIGN_STATE_PENDING &&
                reservation.canSign == CANNOT_SIGN_VALUE -> ScannerUiState(
                phase = ScannerPhase.NOT_ELIGIBLE,
                scannedCode = code,
                roomDetail = detail,
                reservation = reservation,
                error = UiText.Resource(R.string.scanner_not_eligible),
            )

            reservation.signState == SIGN_STATE_PENDING -> ScannerUiState(
                phase = ScannerPhase.READY_TO_SIGN_IN,
                scannedCode = code,
                roomDetail = detail,
                reservation = reservation,
                action = ScannerAction.SIGN_IN,
                locationRequired = locationRequired,
            )

            reservation.signState == SIGN_STATE_SIGNED_IN -> ScannerUiState(
                phase = ScannerPhase.READY_TO_SIGN_OUT,
                scannedCode = code,
                roomDetail = detail,
                reservation = reservation,
                action = ScannerAction.SIGN_OUT,
            )

            else -> ScannerUiState(
                phase = ScannerPhase.NOT_ELIGIBLE,
                scannedCode = code,
                roomDetail = detail,
                reservation = reservation,
                error = UiText.Resource(R.string.scanner_state_unsupported),
            )
        }
        _uiState.update { state -> state.copy(scanner = scannerState) }
    }

    private fun performSignIn(requiresLocation: Boolean) {
        val scanner = _uiState.value.scanner
        val reservation = scanner.reservation ?: return
        if (
            scanner.phase != ScannerPhase.READY_TO_SIGN_IN &&
            scanner.phase != ScannerPhase.LOCATION_PERMISSION_REQUIRED
        ) {
            return
        }
        val useRoomCoordinate = requiresLocation && shouldUseMockLocation()
        val roomCoordinate = if (useRoomCoordinate) {
            scanner.roomDetail?.roomSignInCoordinateOrNull()
        } else {
            null
        }
        if (useRoomCoordinate && roomCoordinate == null) {
            _uiState.update { state ->
                state.copy(
                    scanner = state.scanner.copy(
                        phase = ScannerPhase.READY_TO_SIGN_IN,
                        error = UiText.Resource(
                            R.string.mock_location_coordinates_unavailable,
                        ),
                    ),
                )
            }
            return
        }
        scannerJob?.cancel()
        val requestId = ++scannerRequestId
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                scanner = state.scanner.copy(
                    phase = if (requiresLocation && !useRoomCoordinate) {
                        ScannerPhase.LOCATING
                    } else {
                        ScannerPhase.SIGNING_IN
                    },
                    error = null,
                ),
            )
        }
        scannerJob = viewModelScope.launch {
            val location = if (requiresLocation && !useRoomCoordinate) {
                try {
                    locationProvider.getCurrentLocation()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            if (requestId != scannerRequestId || !isCurrentSession(generation)) {
                return@launch
            }
            if (requiresLocation && !useRoomCoordinate && location == null) {
                _uiState.update { state ->
                    state.copy(
                        scanner = state.scanner.copy(
                            phase = ScannerPhase.READY_TO_SIGN_IN,
                            error = UiText.Resource(R.string.location_unavailable),
                        ),
                    )
                }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    scanner = state.scanner.copy(
                        phase = ScannerPhase.SIGNING_IN,
                        error = null,
                    ),
                )
            }
            val request = when {
                roomCoordinate != null -> RoomSignRequest(
                    id = reservation.id,
                    bookingId = reservation.bookingId,
                    latitude = roomCoordinate.latitude,
                    longitude = roomCoordinate.longitude,
                )

                location != null -> {
                    val coordinate = ChinaCoordinateConverter.wgs84ToGcj02(
                        latitude = location.latitude,
                        longitude = location.longitude,
                    )
                    RoomSignRequest(
                        id = reservation.id,
                        bookingId = reservation.bookingId,
                        latitude = coordinate.latitude,
                        longitude = coordinate.longitude,
                    )
                }

                else -> RoomSignRequest(
                    id = reservation.id,
                    bookingId = reservation.bookingId,
                )
            }
            when (val result = repository.roomSign(request)) {
                is ApiResult.Success -> {
                    if (
                        requestId != scannerRequestId ||
                        !isCurrentSession(generation)
                    ) {
                        return@launch
                    }
                    completeScannerAction(R.string.sign_in_success)
                }

                is ApiResult.Failure -> {
                    if (handleSessionFailure(result.exception)) {
                        return@launch
                    }
                    if (
                        requestId == scannerRequestId &&
                        isCurrentSession(generation)
                    ) {
                        _uiState.update { state ->
                            state.copy(
                                scanner = state.scanner.copy(
                                    phase = ScannerPhase.READY_TO_SIGN_IN,
                                    error = result.exception.toUiText(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun performSignOut() {
        val scanner = _uiState.value.scanner
        val reservation = scanner.reservation ?: return
        if (scanner.phase != ScannerPhase.READY_TO_SIGN_OUT) {
            return
        }
        scannerJob?.cancel()
        val requestId = ++scannerRequestId
        val generation = sessionGeneration
        _uiState.update { state ->
            state.copy(
                scanner = state.scanner.copy(
                    phase = ScannerPhase.SIGNING_OUT,
                    error = null,
                ),
            )
        }
        scannerJob = viewModelScope.launch {
            when (
                val result = repository.roomSignOff(
                    RoomSignOffRequest(
                        id = reservation.id,
                        bookingId = reservation.bookingId,
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    if (
                        requestId != scannerRequestId ||
                        !isCurrentSession(generation)
                    ) {
                        return@launch
                    }
                    completeScannerAction(R.string.sign_out_success)
                }

                is ApiResult.Failure -> {
                    if (handleSessionFailure(result.exception)) {
                        return@launch
                    }
                    if (
                        requestId == scannerRequestId &&
                        isCurrentSession(generation)
                    ) {
                        _uiState.update { state ->
                            state.copy(
                                scanner = state.scanner.copy(
                                    phase = ScannerPhase.READY_TO_SIGN_OUT,
                                    error = result.exception.toUiText(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun completeScannerAction(@StringRes messageId: Int) {
        _uiState.update { state ->
            state.copy(
                scanner = state.scanner.copy(
                    phase = ScannerPhase.COMPLETED,
                    action = null,
                    error = null,
                ),
                message = newMessage(messageId),
            )
        }
        loadReservations(reset = true)
    }

    private fun startSmsCountdown() {
        smsCountdownJob?.cancel()
        val generation = sessionGeneration
        smsCountdownJob = viewModelScope.launch {
            while (
                isCurrentSession(generation) &&
                _uiState.value.stage == AppStage.PHONE_BINDING &&
                _uiState.value.phoneBinding.smsResendSeconds > 0
            ) {
                delay(ONE_SECOND_MILLIS)
                if (
                    !isCurrentSession(generation) ||
                    _uiState.value.stage != AppStage.PHONE_BINDING
                ) {
                    return@launch
                }
                _uiState.update { state ->
                    state.copy(
                        phoneBinding = state.phoneBinding.copy(
                            smsResendSeconds = (
                                state.phoneBinding.smsResendSeconds - 1
                            ).coerceAtLeast(0),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun restoredSessionStatus(): ApiResult<Boolean> {
        val initial = repository.isLoggedIn()
        val initialValidation = initial.toSessionValidation()
        Log.i(SESSION_LOG_TAG, "Initial session validation: $initialValidation")
        if (initialValidation != SessionValidation.EXPIRED) {
            return initial
        }
        delay(SESSION_REVALIDATION_DELAY_MILLIS)
        return repository.isLoggedIn().also { confirmation ->
            Log.i(
                SESSION_LOG_TAG,
                "Restored session confirmation: ${confirmation.toSessionValidation()}",
            )
        }
    }

    private suspend fun confirmSessionExpired(): Boolean {
        delay(SESSION_REVALIDATION_DELAY_MILLIS)
        val confirmation = repository.isLoggedIn().toSessionValidation()
        Log.i(SESSION_LOG_TAG, "Session expiration confirmation: $confirmation")
        return confirmation == SessionValidation.EXPIRED
    }

    private suspend fun handleSessionFailure(exception: ApiException): Boolean {
        if (!exception.isSessionExpired) {
            return false
        }
        if (!confirmSessionExpired()) {
            return false
        }
        expireSession()
        return true
    }

    private fun cancelAuthenticatedJobs() {
        roomsJob?.cancel()
        roomDetailJob?.cancel()
        bookingJob?.cancel()
        reservationsJob?.cancel()
        cancellationJobs.values.forEach { job -> job.cancel() }
        cancellationJobs.clear()
        scannerJob?.cancel()
        roomRequestId++
        detailRequestId++
        reservationRequestId++
        scannerRequestId++
    }

    private fun cancelSessionJobs() {
        startupJob?.cancel()
        loginCaptchaJob?.cancel()
        loginJob?.cancel()
        phoneCaptchaJob?.cancel()
        phoneSmsJob?.cancel()
        smsCountdownJob?.cancel()
        phoneUpdateJob?.cancel()
        readerQrRequestId++
        readerQrCodeJob?.cancel()
        cancelAuthenticatedJobs()
    }

    private fun isCurrentSession(generation: Long): Boolean =
        generation == sessionGeneration

    private fun newMessage(@StringRes id: Int): UiMessage =
        UiMessage(
            id = messageIds.incrementAndGet(),
            text = UiText.Resource(id),
        )

    private fun ApiException.toUiText(): UiText = when (kind) {
        ApiErrorKind.SESSION_EXPIRED -> UiText.Resource(R.string.session_expired)
        ApiErrorKind.NETWORK -> UiText.Resource(R.string.error_network)
        ApiErrorKind.HTTP -> UiText.Resource(R.string.error_http)
        ApiErrorKind.SERIALIZATION -> UiText.Resource(R.string.error_response)
        ApiErrorKind.VALIDATION -> UiText.Resource(R.string.error_invalid_request)
        ApiErrorKind.UNKNOWN -> UiText.Resource(R.string.error_unknown)
        ApiErrorKind.BUSINESS -> message
            ?.takeIf(String::isNotBlank)
            ?.let { value -> UiText.Dynamic(value) }
            ?: UiText.Resource(R.string.error_unknown)
    }

    private fun ReaderQrCodeFailure.toUiText(): UiText =
        UiText.Resource(
            when (this) {
                ReaderQrCodeFailure.INVALID_PAGE_URL ->
                    R.string.reader_qr_error_invalid_url
                ReaderQrCodeFailure.NETWORK -> R.string.reader_qr_error_network
                ReaderQrCodeFailure.HTTP -> R.string.reader_qr_error_http
                ReaderQrCodeFailure.QR_IMAGE_NOT_FOUND ->
                    R.string.reader_qr_error_not_found
            },
        )

    private fun appendDistinctRooms(
        current: List<RoomSummary>,
        next: List<RoomSummary>,
    ): List<RoomSummary> {
        val ids = current.mapNotNullTo(mutableSetOf<String>()) { room ->
            room.id.takeIf(String::isNotBlank)
        }
        return current + next.filter { room -> room.id.isBlank() || ids.add(room.id) }
    }

    private fun appendDistinctReservations(
        current: List<AppointmentRecord>,
        next: List<AppointmentRecord>,
    ): List<AppointmentRecord> {
        val ids = current.mapNotNullTo(mutableSetOf<String>()) { reservation ->
            reservation.id.takeIf(String::isNotBlank)
        }
        return current + next.filter { reservation ->
            reservation.id.isBlank() || ids.add(reservation.id)
        }
    }

    class Factory(
        private val repository: ReservationRepository,
        private val readerQrCodeRepository: ReaderQrCodeRepository,
        private val locationProvider: DeviceLocationProvider,
        private val shouldUseMockLocation: () -> Boolean,
        private val queueCalendarReminder: (
            roomName: String,
            venueName: String,
            reservationDateTime: String,
            createdAtMillis: Long,
        ) -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReservationViewModel::class.java)) {
                return ReservationViewModel(
                    repository = repository,
                    readerQrCodeRepository = readerQrCodeRepository,
                    locationProvider = locationProvider,
                    shouldUseMockLocation = shouldUseMockLocation,
                    queueCalendarReminder = queueCalendarReminder,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val ROOM_PAGE_SIZE = 20
        const val RESERVATION_PAGE_SIZE = 10
        const val MAX_NAME_LENGTH = 20
        const val CANNOT_SIGN_VALUE = 0
        const val MIN_SMS_CODE_LENGTH = 4
        const val MAX_SMS_CODE_LENGTH = 6
        const val SMS_RESEND_SECONDS = 60
        const val ONE_SECOND_MILLIS = 1_000L
        const val UNBOUND_MOBILE_STATUS = "0"
        const val BOUND_MOBILE_STATUS = "1"
        const val LOGOUT_TIMEOUT_MILLIS = 5_000L
        const val SESSION_REVALIDATION_DELAY_MILLIS = 500L
        const val SESSION_LOG_TAG = "AhlibSession"
        val MOBILE_PATTERN = Regex("^1[3-9]\\d{9}$")

        fun isValidMobile(value: String): Boolean =
            MOBILE_PATTERN.matches(value.trim())
    }
}
