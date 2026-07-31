package cn.ahlib.reservation.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import cn.ahlib.reservation.R
import cn.ahlib.reservation.automation.AUTOMATION_LOG_MAX_ENTRIES
import cn.ahlib.reservation.automation.AutomationLogEntry
import cn.ahlib.reservation.automation.AutomationLogLevel
import cn.ahlib.reservation.automation.AutomationSettings
import cn.ahlib.reservation.automation.MAX_CANCELLATION_LEAD_MINUTES
import cn.ahlib.reservation.automation.MIN_CANCELLATION_LEAD_MINUTES
import cn.ahlib.reservation.ui.theme.spacing
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsScreen(
    settings: AutomationSettings,
    onBack: () -> Unit,
    onAutoBookingEnabledChange: (Boolean) -> Unit,
    onCancellationEnabledChange: (Boolean) -> Unit,
    onCancellationLeadMinutesChange: (Int) -> Unit,
    onMockLocationEnabledChange: (Boolean) -> Unit,
    onOpenLogs: () -> Unit,
    canScheduleExactAlarms: () -> Boolean,
    onSystemAccessChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var backgroundAccessGranted by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations())
    }
    var exactAlarmAccessGranted by remember {
        mutableStateOf(canScheduleExactAlarms())
    }
    var leadInput by rememberSaveable {
        mutableStateOf(settings.cancellationLeadMinutes.toString())
    }

    LaunchedEffect(settings.cancellationLeadMinutes) {
        if (leadInput.toIntOrNull() != settings.cancellationLeadMinutes) {
            leadInput = settings.cancellationLeadMinutes.toString()
        }
    }
    val leadValue = leadInput.toIntOrNull()
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onSystemAccessChanged()
                backgroundAccessGranted = context.isIgnoringBatteryOptimizations()
                exactAlarmAccessGranted = canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.automation_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenLogs) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = stringResource(R.string.automation_logs),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(MaterialTheme.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        ) {
            item(key = "auto-booking") {
                SettingsCard {
                    SettingSwitchRow(
                        title = stringResource(R.string.auto_booking_title),
                        description = stringResource(R.string.auto_booking_description),
                        checked = settings.autoBookingEnabled,
                        onCheckedChange = onAutoBookingEnabledChange,
                    )
                    HorizontalDivider()
                    val target = settings.target
                    Text(
                        text = if (target == null) {
                            stringResource(R.string.auto_booking_target_missing)
                        } else {
                            stringResource(
                                R.string.auto_booking_target_value,
                                target.roomName,
                                target.startTime,
                                target.endTime,
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.auto_booking_target_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "auto-cancellation") {
                SettingsCard {
                    SettingSwitchRow(
                        title = stringResource(R.string.auto_cancellation_title),
                        description = stringResource(
                            R.string.auto_cancellation_description,
                        ),
                        checked = settings.cancellationEnabled,
                        onCheckedChange = onCancellationEnabledChange,
                    )
                    HorizontalDivider()
                    OutlinedTextField(
                        value = leadInput,
                        onValueChange = { value ->
                            val filtered = value.filter(Char::isDigit).take(2)
                            leadInput = filtered
                            filtered.toIntOrNull()
                                ?.takeIf { minutes ->
                                    minutes in MIN_CANCELLATION_LEAD_MINUTES..
                                        MAX_CANCELLATION_LEAD_MINUTES
                                }
                                ?.let(onCancellationLeadMinutesChange)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.cancellation_lead_minutes))
                        },
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.cancellation_lead_range,
                                    MIN_CANCELLATION_LEAD_MINUTES,
                                    MAX_CANCELLATION_LEAD_MINUTES,
                                ),
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        singleLine = true,
                        isError = leadValue == null ||
                            leadValue !in MIN_CANCELLATION_LEAD_MINUTES..
                            MAX_CANCELLATION_LEAD_MINUTES,
                    )
                }
            }

            item(key = "mock-location") {
                SettingsCard {
                    SettingSwitchRow(
                        title = stringResource(R.string.mock_location_title),
                        description = stringResource(R.string.mock_location_description),
                        checked = settings.mockLocationEnabled,
                        onCheckedChange = onMockLocationEnabledChange,
                    )
                }
            }

            item(key = "background-access") {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.background_execution_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.background_execution_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BackgroundStatusRow(
                        label = stringResource(R.string.battery_optimization),
                        granted = backgroundAccessGranted,
                    )
                    if (!backgroundAccessGranted) {
                        Button(
                            onClick = context::requestIgnoreBatteryOptimizations,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.allow_background_execution))
                        }
                    }
                    BackgroundStatusRow(
                        label = stringResource(R.string.exact_alarm_access),
                        granted = exactAlarmAccessGranted,
                    )
                    if (!exactAlarmAccessGranted) {
                        FilledTonalButton(
                            onClick = context::requestExactAlarmAccess,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.allow_exact_alarm))
                        }
                    }
                    Text(
                        text = stringResource(R.string.background_execution_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = context::openBackgroundExecutionHelp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.background_execution_help))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationLogScreen(
    entries: List<AutomationLogEntry>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.automation_logs)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.clear_logs),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            EmptyContent(
                text = stringResource(R.string.automation_logs_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(MaterialTheme.spacing.screen),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                item(key = "notice") {
                    Text(
                        text = stringResource(
                            R.string.automation_logs_notice,
                            AUTOMATION_LOG_MAX_ENTRIES,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(entries, key = AutomationLogEntry::id) { entry ->
                    AutomationLogCard(entry)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            content = content,
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun BackgroundStatusRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.WarningAmber
            },
            contentDescription = null,
            tint = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                if (granted) {
                    R.string.access_granted
                } else {
                    R.string.access_not_granted
                },
            ),
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AutomationLogCard(entry: AutomationLogEntry) {
    val icon = entry.level.icon()
    val color = when (entry.level) {
        AutomationLogLevel.INFO -> MaterialTheme.colorScheme.primary
        AutomationLogLevel.SUCCESS -> MaterialTheme.colorScheme.tertiary
        AutomationLogLevel.WARNING -> MaterialTheme.colorScheme.secondary
        AutomationLogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val formattedTimestamp = remember(entry.timestampMillis) {
                    DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.MEDIUM,
                    ).format(Date(entry.timestampMillis))
                }
                Text(
                    text = formattedTimestamp,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun AutomationLogLevel.icon(): ImageVector = when (this) {
    AutomationLogLevel.INFO -> Icons.Outlined.Info
    AutomationLogLevel.SUCCESS -> Icons.Outlined.CheckCircle
    AutomationLogLevel.WARNING -> Icons.Outlined.WarningAmber
    AutomationLogLevel.ERROR -> Icons.Outlined.ErrorOutline
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(PowerManager::class.java)
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

@SuppressLint("BatteryLife")
private fun Context.requestIgnoreBatteryOptimizations() {
    val packageUri = "package:$packageName".toUri()
    val request = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        packageUri,
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(request)
    } catch (_: ActivityNotFoundException) {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun Context.requestExactAlarmAccess() {
    val packageUri = "package:$packageName".toUri()
    val request = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        packageUri,
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(request)
    } catch (_: ActivityNotFoundException) {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun Context.openBackgroundExecutionHelp() {
    runCatching {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "https://dontkillmyapp.com/".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
