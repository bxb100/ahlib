package cn.ahlib.reservation.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ahlib.reservation.R
import cn.ahlib.reservation.data.CookieCloudConfig
import cn.ahlib.reservation.data.CookieCloudCryptoType
import cn.ahlib.reservation.ui.theme.spacing
import coil3.compose.AsyncImage

private const val AUTH_CONTENT_MAX_WIDTH = 520
private const val MOBILE_LENGTH = 11

private val mobilePattern = Regex("^1[3-9][0-9]{9}$")

private data class RetentionOption(
    val days: Int,
    @param:StringRes val labelRes: Int,
)

private val retentionOptions = listOf(
    RetentionOption(days = 2, labelRes = R.string.stay_2_days),
    RetentionOption(days = 15, labelRes = R.string.stay_15_days),
    RetentionOption(days = 30, labelRes = R.string.stay_30_days),
)

@Composable
fun LoginScreen(
    readerId: String,
    password: String,
    verifyCode: String,
    loginRetentionDays: Int,
    captchaDataUri: String?,
    isCaptchaLoading: Boolean,
    isSubmitting: Boolean,
    errorText: String?,
    onReaderIdChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onVerifyCodeChange: (String) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    cookieCloudServerUrl: String = "",
    cookieCloudUserKey: String = "",
    cookieCloudPassword: String = "",
    cookieCloudCryptoType: CookieCloudCryptoType = CookieCloudCryptoType.LEGACY,
    isCookieCloudConfigured: Boolean = false,
    isCookieCloudSyncing: Boolean = false,
    cookieCloudErrorText: String? = null,
    onSaveCookieCloudAndLogin: (
        serverUrl: String,
        userKey: String,
        password: String,
        cryptoType: CookieCloudCryptoType,
    ) -> Unit = { _, _, _, _ -> },
    onClearCookieCloud: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isCookieCloudDialogVisible by remember { mutableStateOf(false) }
    val hasCaptcha = !captchaDataUri.isNullOrBlank()
    val canLogin = readerId.isNotBlank() &&
        password.isNotBlank() &&
        verifyCode.isNotBlank() &&
        loginRetentionDays in retentionOptions.map(RetentionOption::days) &&
        hasCaptcha &&
        !isCaptchaLoading &&
        !isSubmitting

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            val viewportHeight = maxHeight

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = AUTH_CONTENT_MAX_WIDTH.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(
                        horizontal = MaterialTheme.spacing.extraLarge,
                        vertical = 32.dp,
                    ),
                verticalArrangement = Arrangement.Center,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AuthHeader(
                        icon = Icons.Outlined.LocalLibrary,
                        titleRes = R.string.login_title,
                        descriptionRes = R.string.login_subtitle,
                    )
                    IconButton(
                        onClick = { isCookieCloudDialogVisible = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (isCookieCloudConfigured) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(
                                if (isCookieCloudConfigured) {
                                    R.string.cookie_cloud_settings_configured
                                } else {
                                    R.string.cookie_cloud_settings
                                },
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.section))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.large,
                        ),
                    ) {
                        ErrorMessage(errorText = errorText)

                        OutlinedTextField(
                            value = readerId,
                            onValueChange = onReaderIdChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentType = ContentType.Username
                                },
                            enabled = !isSubmitting,
                            singleLine = true,
                            label = { Text(stringResource(R.string.reader_id)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = null,
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    passwordFocusRequester.requestFocus()
                                },
                            ),
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(passwordFocusRequester)
                                .semantics {
                                    contentType = ContentType.Password
                                },
                            enabled = !isSubmitting,
                            singleLine = true,
                            label = { Text(stringResource(R.string.password)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = {
                                val descriptionRes = if (isPasswordVisible) {
                                    R.string.password_visibility_hide
                                } else {
                                    R.string.password_visibility_show
                                }
                                IconButton(
                                    onClick = {
                                        isPasswordVisible = !isPasswordVisible
                                    },
                                ) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) {
                                            Icons.Outlined.VisibilityOff
                                        } else {
                                            Icons.Outlined.Visibility
                                        },
                                        contentDescription = stringResource(descriptionRes),
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocusDown() },
                            ),
                        )

                        CaptchaInput(
                            verifyCode = verifyCode,
                            onVerifyCodeChange = onVerifyCodeChange,
                            captchaDataUri = captchaDataUri,
                            isCaptchaLoading = isCaptchaLoading,
                            enabled = !isSubmitting,
                            onRefreshCaptcha = onRefreshCaptcha,
                            imeAction = ImeAction.Next,
                            onImeAction = { focusManager.moveFocusDown() },
                        )

                        RetentionSelector(
                            selectedDays = loginRetentionDays,
                            enabled = !isSubmitting,
                            onSelectionChange = onRetentionChange,
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onLogin()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            enabled = canLogin,
                        ) {
                            if (isSubmitting) {
                                LoadingIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(stringResource(R.string.logging_in))
                            } else {
                                Text(stringResource(R.string.login_action))
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCookieCloudDialogVisible) {
        CookieCloudSettingsDialog(
            initialServerUrl = cookieCloudServerUrl,
            initialUserKey = cookieCloudUserKey,
            initialPassword = cookieCloudPassword,
            initialCryptoType = cookieCloudCryptoType,
            isConfigured = isCookieCloudConfigured,
            isSyncing = isCookieCloudSyncing,
            errorText = cookieCloudErrorText,
            onDismiss = { isCookieCloudDialogVisible = false },
            onSaveAndLogin = onSaveCookieCloudAndLogin,
            onClear = {
                onClearCookieCloud()
                isCookieCloudDialogVisible = false
            },
        )
    }
}

@Composable
private fun CookieCloudSettingsDialog(
    initialServerUrl: String,
    initialUserKey: String,
    initialPassword: String,
    initialCryptoType: CookieCloudCryptoType,
    isConfigured: Boolean,
    isSyncing: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onSaveAndLogin: (
        serverUrl: String,
        userKey: String,
        password: String,
        cryptoType: CookieCloudCryptoType,
    ) -> Unit,
    onClear: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var userKey by remember { mutableStateOf(initialUserKey) }
    var password by remember { mutableStateOf(initialPassword) }
    var cryptoType by remember { mutableStateOf(initialCryptoType) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var hasDraftChanges by remember { mutableStateOf(false) }

    LaunchedEffect(
        initialServerUrl,
        initialUserKey,
        initialPassword,
        initialCryptoType,
    ) {
        if (!hasDraftChanges && !isSyncing) {
            serverUrl = initialServerUrl
            userKey = initialUserKey
            password = initialPassword
            cryptoType = initialCryptoType
        }
    }

    val trimmedServerUrl = serverUrl.trim()
    val serverUrlErrorRes = when {
        trimmedServerUrl.isEmpty() -> R.string.cookie_cloud_server_url_required
        !isValidCookieCloudServerUrl(trimmedServerUrl) ->
            R.string.cookie_cloud_server_url_https_required

        else -> null
    }
    val userKeyErrorRes = when {
        userKey.isBlank() -> R.string.cookie_cloud_user_key_required
        !isValidCookieCloudUserKey(userKey) -> R.string.cookie_cloud_user_key_invalid
        else -> null
    }
    val passwordErrorRes = when {
        password.isEmpty() -> R.string.cookie_cloud_password_required
        !isValidCookieCloudPassword(password) -> R.string.cookie_cloud_password_invalid
        else -> null
    }
    val serverUrlError = serverUrlErrorRes?.let { stringResource(it) }
    val userKeyError = userKeyErrorRes?.let { stringResource(it) }
    val passwordError = passwordErrorRes?.let { stringResource(it) }
    val canSave = serverUrlErrorRes == null &&
        userKeyErrorRes == null &&
        passwordErrorRes == null &&
        !isSyncing

    fun clearSensitiveDraftAndDismiss() {
        password = ""
        isPasswordVisible = false
        onDismiss()
    }

    fun saveAndLogin() {
        focusManager.clearFocus()
        onSaveAndLogin(
            trimmedServerUrl,
            userKey.trim(),
            password,
            cryptoType,
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSyncing) {
                clearSensitiveDraftAndDismiss()
            }
        },
        modifier = Modifier.imePadding(),
        title = {
            Text(stringResource(R.string.cookie_cloud_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            ) {
                Text(
                    text = stringResource(R.string.cookie_cloud_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ErrorMessage(errorText = errorText)

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        hasDraftChanges = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing,
                    singleLine = true,
                    isError = serverUrlError != null,
                    label = { Text(stringResource(R.string.cookie_cloud_server_url)) },
                    supportingText = serverUrlError?.let { message ->
                        { Text(message) }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocusDown() },
                    ),
                )

                OutlinedTextField(
                    value = userKey,
                    onValueChange = {
                        userKey = it
                        hasDraftChanges = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing,
                    singleLine = true,
                    isError = userKeyError != null,
                    label = { Text(stringResource(R.string.cookie_cloud_user_key)) },
                    supportingText = userKeyError?.let { message ->
                        { Text(message) }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocusDown() },
                    ),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        hasDraftChanges = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing,
                    singleLine = true,
                    isError = passwordError != null,
                    label = { Text(stringResource(R.string.cookie_cloud_password)) },
                    supportingText = passwordError?.let { message ->
                        { Text(message) }
                    },
                    trailingIcon = {
                        val descriptionRes = if (isPasswordVisible) {
                            R.string.password_visibility_hide
                        } else {
                            R.string.password_visibility_show
                        }
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible },
                            enabled = !isSyncing,
                        ) {
                            Icon(
                                imageVector = if (isPasswordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(descriptionRes),
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSave) {
                                saveAndLogin()
                            }
                        },
                    ),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.cookie_cloud_crypto_type),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = cryptoType == CookieCloudCryptoType.LEGACY,
                            onClick = {
                                cryptoType = CookieCloudCryptoType.LEGACY
                                hasDraftChanges = true
                            },
                            enabled = !isSyncing,
                            label = {
                                Text(stringResource(R.string.cookie_cloud_crypto_legacy))
                            },
                        )
                        FilterChip(
                            selected = cryptoType ==
                                CookieCloudCryptoType.AES_128_CBC_FIXED,
                            onClick = {
                                cryptoType = CookieCloudCryptoType.AES_128_CBC_FIXED
                                hasDraftChanges = true
                            },
                            enabled = !isSyncing,
                            label = {
                                Text(stringResource(R.string.cookie_cloud_crypto_aes_fixed))
                            },
                        )
                    }
                    Text(
                        text = stringResource(R.string.cookie_cloud_crypto_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::saveAndLogin,
                enabled = canSave,
            ) {
                if (isSyncing) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.cookie_cloud_syncing))
                } else {
                    Text(stringResource(R.string.cookie_cloud_save_and_login))
                }
            }
        },
        dismissButton = {
            Row {
                if (isConfigured) {
                    TextButton(
                        onClick = {
                            password = ""
                            isPasswordVisible = false
                            onClear()
                        },
                        enabled = !isSyncing,
                    ) {
                        Text(stringResource(R.string.cookie_cloud_clear))
                    }
                }
                TextButton(
                    onClick = ::clearSensitiveDraftAndDismiss,
                    enabled = !isSyncing,
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

private fun isValidCookieCloudServerUrl(value: String): Boolean =
    CookieCloudConfig.normalizedOrNull(
        serverUrl = value,
        userKey = "validation-key",
        password = "validation-password",
        cryptoType = CookieCloudCryptoType.LEGACY,
    ) != null

private fun isValidCookieCloudUserKey(value: String): Boolean =
    CookieCloudConfig.normalizedOrNull(
        serverUrl = "https://cookiecloud.invalid",
        userKey = value,
        password = "validation-password",
        cryptoType = CookieCloudCryptoType.LEGACY,
    ) != null

private fun isValidCookieCloudPassword(value: String): Boolean =
    CookieCloudConfig.normalizedOrNull(
        serverUrl = "https://cookiecloud.invalid",
        userKey = "validation-key",
        password = value,
        cryptoType = CookieCloudCryptoType.LEGACY,
    ) != null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneBindingScreen(
    mobile: String,
    verifyCode: String,
    smsCode: String,
    captchaDataUri: String?,
    isCaptchaLoading: Boolean,
    isSendingCode: Boolean,
    smsResendSeconds: Int,
    isSubmitting: Boolean,
    errorText: String?,
    onMobileChange: (String) -> Unit,
    onVerifyCodeChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val isBusy = isSendingCode || isSubmitting
    val hasValidMobile = mobile.length == MOBILE_LENGTH && mobilePattern.matches(mobile)
    val hasCaptcha = !captchaDataUri.isNullOrBlank()
    val canSendCode = hasValidMobile &&
        verifyCode.isNotBlank() &&
        hasCaptcha &&
        !isCaptchaLoading &&
        smsResendSeconds == 0 &&
        !isBusy
    val canSubmit = hasValidMobile && smsCode.length in 4..6 && !isBusy

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.phone_binding_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        enabled = !isBusy,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close_and_logout),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding(),
        ) {
            val viewportHeight = maxHeight

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = AUTH_CONTENT_MAX_WIDTH.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(MaterialTheme.spacing.extraLarge),
                verticalArrangement = Arrangement.Center,
            ) {
                SupportingHeader(
                    icon = Icons.Outlined.PhoneAndroid,
                    descriptionRes = R.string.phone_binding_description,
                )

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.section))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.large,
                        ),
                    ) {
                        ErrorMessage(errorText = errorText)

                        OutlinedTextField(
                            value = mobile,
                            onValueChange = onMobileChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy,
                            singleLine = true,
                            label = { Text(stringResource(R.string.mobile_number)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PhoneAndroid,
                                    contentDescription = null,
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocusDown() },
                            ),
                        )

                        CaptchaInput(
                            verifyCode = verifyCode,
                            onVerifyCodeChange = onVerifyCodeChange,
                            captchaDataUri = captchaDataUri,
                            isCaptchaLoading = isCaptchaLoading,
                            enabled = !isBusy,
                            onRefreshCaptcha = onRefreshCaptcha,
                            imeAction = ImeAction.Next,
                            onImeAction = { focusManager.moveFocusDown() },
                        )

                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                onSendCode()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            enabled = canSendCode,
                        ) {
                            if (isSendingCode) {
                                LoadingIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(stringResource(R.string.sending_sms_code))
                            } else if (smsResendSeconds > 0) {
                                Text(
                                    stringResource(
                                        R.string.sms_resend_countdown,
                                        smsResendSeconds,
                                    ),
                                )
                            } else {
                                Text(stringResource(R.string.send_sms_code))
                            }
                        }

                        OutlinedTextField(
                            value = smsCode,
                            onValueChange = onSmsCodeChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy,
                            singleLine = true,
                            label = { Text(stringResource(R.string.sms_code)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Sms,
                                    contentDescription = null,
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (canSubmit) {
                                        focusManager.clearFocus()
                                        onSubmit()
                                    }
                                },
                            ),
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSubmit()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            enabled = canSubmit,
                        ) {
                            if (isSubmitting) {
                                LoadingIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(stringResource(R.string.binding_phone))
                            } else {
                                Text(stringResource(R.string.complete_binding))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthHeader(
    icon: ImageVector,
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderIcon(icon = icon)
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SupportingHeader(
    icon: ImageVector,
    @StringRes descriptionRes: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderIcon(icon = icon)
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun CaptchaInput(
    verifyCode: String,
    onVerifyCodeChange: (String) -> Unit,
    captchaDataUri: String?,
    isCaptchaLoading: Boolean,
    enabled: Boolean,
    onRefreshCaptcha: () -> Unit,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = verifyCode,
            onValueChange = onVerifyCodeChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(R.string.image_code)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = imeAction,
            ),
            keyboardActions = when (imeAction) {
                ImeAction.Done -> KeyboardActions(onDone = { onImeAction() })
                else -> KeyboardActions(onNext = { onImeAction() })
            },
        )

        CaptchaPreview(
            captchaDataUri = captchaDataUri,
            isCaptchaLoading = isCaptchaLoading,
            enabled = enabled,
            onRefreshCaptcha = onRefreshCaptcha,
        )
    }
}

@Composable
private fun CaptchaPreview(
    captchaDataUri: String?,
    isCaptchaLoading: Boolean,
    enabled: Boolean,
    onRefreshCaptcha: () -> Unit,
) {
    val captchaDescription = stringResource(R.string.captcha_description)
    val refreshDescription = stringResource(R.string.refresh_captcha)
    val loadingDescription = stringResource(R.string.refreshing)
    val shape = MaterialTheme.shapes.medium
    val captchaModel = remember(captchaDataUri) {
        captchaDataUri?.toCoilModel()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                )
                .clickable(
                    enabled = enabled && !isCaptchaLoading,
                    role = Role.Button,
                    onClickLabel = refreshDescription,
                    onClick = onRefreshCaptcha,
                )
                .semantics {
                    contentDescription = if (isCaptchaLoading) {
                        loadingDescription
                    } else {
                        captchaDescription
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isCaptchaLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                )
            } else if (captchaModel != null) {
                AsyncImage(
                    model = captchaModel,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.refresh_captcha),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

        }

        FilledTonalIconButton(
            onClick = onRefreshCaptcha,
            modifier = Modifier.size(48.dp),
            enabled = enabled && !isCaptchaLoading,
        ) {
            if (isCaptchaLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = refreshDescription,
                )
            }
        }
    }
}

@Composable
private fun RetentionSelector(
    selectedDays: Int,
    enabled: Boolean,
    onSelectionChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.stay_signed_in),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            retentionOptions.forEach { option ->
                FilterChip(
                    selected = option.days == selectedDays,
                    onClick = { onSelectionChange(option.days) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    label = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(option.labelRes))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ErrorMessage(errorText: String?) {
    errorText?.takeIf(String::isNotBlank)?.let { message ->
        InlineErrorMessage(
            message = message,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private fun androidx.compose.ui.focus.FocusManager.moveFocusDown() {
    moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
}
