package cn.ahlib.reservation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ahlib.reservation.R
import cn.ahlib.reservation.ui.theme.spacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    appVersionName: String,
    isCheckingUpdate: Boolean,
    isAdvancedSettingsEnabled: Boolean,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onEnableAdvancedSettings: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    modifier: Modifier = Modifier,
    requiredUnlockClicks: Int = DEFAULT_ADVANCED_SETTINGS_UNLOCK_CLICKS,
) {
    BackHandler(onBack = onBack)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val remainingFeedbackMessages = (1..3).associateWith {
        stringResource(R.string.advanced_settings_clicks_remaining, it)
    }
    val enabledFeedbackMessage = stringResource(
        R.string.advanced_settings_enabled,
    )
    var feedbackJob by remember { mutableStateOf<Job?>(null) }
    var versionClickCount by rememberSaveable { mutableIntStateOf(0) }
    var isEnabledLocally by rememberSaveable {
        mutableStateOf(isAdvancedSettingsEnabled)
    }

    LaunchedEffect(isAdvancedSettingsEnabled) {
        if (isAdvancedSettingsEnabled) {
            isEnabledLocally = true
        }
    }

    fun showFeedback(feedback: AdvancedSettingsUnlockFeedback) {
        val message = when (feedback) {
            is AdvancedSettingsUnlockFeedback.Remaining ->
                checkNotNull(remainingFeedbackMessages[feedback.clicks])

            AdvancedSettingsUnlockFeedback.Enabled -> enabledFeedbackMessage
        }
        feedbackJob?.cancel()
        snackbarHostState.currentSnackbarData?.dismiss()
        feedbackJob = coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    val advancedSettingsVisible =
        isAdvancedSettingsEnabled || isEnabledLocally

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
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
                ),
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(MaterialTheme.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        ) {
            item(key = "app-info") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClick = {
                                val result = AdvancedSettingsUnlockState(
                                    clickCount = versionClickCount,
                                    isEnabled = advancedSettingsVisible,
                                ).onVersionClick(requiredUnlockClicks)
                                versionClickCount = result.state.clickCount
                                isEnabledLocally = result.state.isEnabled
                                if (result.didEnable) {
                                    onEnableAdvancedSettings()
                                }
                                result.feedback?.let(::showFeedback)
                            },
                        ),
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
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.medium,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.app_update_current_version,
                                appVersionName,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaterialTheme.spacing.medium),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (advancedSettingsVisible) {
                item(key = "advanced-settings") {
                    FilledTonalButton(
                        onClick = onOpenAdvancedSettings,
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
            }
            item(key = "app-update") {
                FilledTonalButton(
                    onClick = onCheckUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCheckingUpdate,
                ) {
                    if (isCheckingUpdate) {
                        LoadingIndicator(modifier = Modifier.size(18.dp))
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
        }
    }
}
