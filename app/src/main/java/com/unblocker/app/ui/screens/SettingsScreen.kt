package com.unblocker.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unblocker.app.data.database.AppDatabase
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.ui.adaptive.LocalWindowDimensions
import com.unblocker.app.ui.adaptive.WindowWidthClass
import com.unblocker.app.ui.components.BlacklistManagerDialog
import com.unblocker.app.ui.components.HealthCheckDialog
import com.unblocker.app.ui.components.WhitelistManagerDialog
import com.unblocker.app.ui.theme.AdultBlockedPurple
import com.unblocker.app.ui.theme.CyanGlow
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.TealAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    preferences: FilteringPreferences,
    database: AppDatabase
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dimensions = LocalWindowDimensions.current

    val adBlockingEnabled by preferences.adBlockingEnabled.collectAsState()
    val adultBlockingEnabled by preferences.adultBlockingEnabled.collectAsState()
    val parentalControlEnabled by preferences.parentalControlEnabled.collectAsState()
    val autoRestartOnBoot by preferences.autoRestartOnBoot.collectAsState()
    val healthCheckInterval by preferences.healthCheckIntervalMinutes.collectAsState()
    val logRetentionDays by preferences.logRetentionDays.collectAsState()

    var showWhitelistDialog by remember { mutableStateOf(false) }
    var showBlacklistDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showCleanDbDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (dimensions.widthClass == WindowWidthClass.COMPACT) 16.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Preferences & Rules",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Configure blocking levels, rules, and background persistence",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate400
            )
        }

        // Section 1: Content Filtering
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "CONTENT FILTERING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchItem(
                        title = "Block Advertisements",
                        description = "Intercept ad networks, analytics, and telemetry",
                        checked = adBlockingEnabled,
                        onCheckedChange = { preferences.setAdBlockingEnabled(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchItem(
                        title = "Block 18+ Adult Content",
                        description = "Prevent explicit sites and streaming content",
                        checked = adultBlockingEnabled,
                        activeColor = AdultBlockedPurple,
                        onCheckedChange = { preferences.setAdultBlockingEnabled(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchItem(
                        title = "Parental Control Mode",
                        description = "Strict enforcement for safe device browsing",
                        checked = parentalControlEnabled,
                        onCheckedChange = { preferences.setParentalControlEnabled(it) }
                    )
                }
            }
        }

        // Section 2: Custom Rules
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "CUSTOM RULES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingActionItem(
                        title = "Manage Whitelist",
                        description = "Domains that will always bypass blocking",
                        icon = Icons.Default.List,
                        onClick = { showWhitelistDialog = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingActionItem(
                        title = "Custom Blacklist",
                        description = "Add personal domains to block unconditionally",
                        icon = Icons.Default.Security,
                        onClick = { showBlacklistDialog = true }
                    )
                }
            }
        }

        // Section 3: System & Persistence
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "SYSTEM & AUTOMATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchItem(
                        title = "Auto-Restart on Boot",
                        description = "Automatically resumes protection when device turns on",
                        checked = autoRestartOnBoot,
                        onCheckedChange = { preferences.setAutoRestartOnBoot(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingActionItem(
                        title = "Health Check Interval",
                        description = "Currently runs every $healthCheckInterval minutes",
                        icon = Icons.Default.HealthAndSafety,
                        onClick = {
                            val nextInterval = when (healthCheckInterval) {
                                5 -> 15
                                15 -> 30
                                30 -> 60
                                else -> 5
                            }
                            preferences.setHealthCheckIntervalMinutes(nextInterval)
                            Toast.makeText(context, "Health check interval set to $nextInterval min", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingActionItem(
                        title = "Log Retention Policy",
                        description = "Keep connections for $logRetentionDays days",
                        icon = Icons.Default.CleaningServices,
                        onClick = {
                            val nextRetention = when (logRetentionDays) {
                                7 -> 30
                                30 -> 90
                                else -> 7
                            }
                            preferences.setLogRetentionDays(nextRetention)
                            Toast.makeText(context, "Retention period set to $nextRetention days", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingActionItem(
                        title = "Diagnostics & Health Benchmark",
                        description = "Test filtering engine and check database integrity",
                        icon = Icons.Default.HealthAndSafety,
                        onClick = { showDiagnosticsDialog = true }
                    )
                }
            }
        }

        // Section 4: Privacy & About
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "PRIVACY & ABOUT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Zero-Server Privacy Guarantee",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "unblocker operates 100% locally on your device. No traffic queries, analytics, or domain logs are ever uploaded to any external server. All ad lists and adult databases are self-contained.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "unblocker v1.0.0 (Build 2026.09)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showWhitelistDialog) {
        WhitelistManagerDialog(database = database, onDismiss = { showWhitelistDialog = false })
    }

    if (showBlacklistDialog) {
        BlacklistManagerDialog(database = database, onDismiss = { showBlacklistDialog = false })
    }

    if (showDiagnosticsDialog) {
        HealthCheckDialog(onDismiss = { showDiagnosticsDialog = false })
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    activeColor: Color = TealAccent,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate400
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = activeColor
            )
        )
    }
}

@Composable
private fun SettingActionItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400
                )
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
    }
}
