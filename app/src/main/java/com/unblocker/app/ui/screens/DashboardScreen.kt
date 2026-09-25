package com.unblocker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.data.database.AppDatabase
import com.unblocker.app.data.model.ConnectionLog
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.ui.adaptive.LocalWindowDimensions
import com.unblocker.app.ui.adaptive.WindowWidthClass
import com.unblocker.app.ui.theme.AdBlockedRed
import com.unblocker.app.ui.theme.AdBlockedRedBg
import com.unblocker.app.ui.theme.AdultBlockedPurple
import com.unblocker.app.ui.theme.AdultBlockedPurpleBg
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.AllowedGreenBg
import com.unblocker.app.ui.theme.CyanGlow
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealAccent
import com.unblocker.app.ui.theme.TealPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    database: AppDatabase,
    preferences: FilteringPreferences,
    quickStartManager: QuickStartManager,
    onNavigateToLogs: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onVpnPermissionNeeded: () -> Unit
) {
    val dimensions = LocalWindowDimensions.current
    val serviceStatus by quickStartManager.status.collectAsState()
    val isRunning = serviceStatus == ServiceStatus.RUNNING

    val adBlockingEnabled by preferences.adBlockingEnabled.collectAsState()
    val adultBlockingEnabled by preferences.adultBlockingEnabled.collectAsState()
    val effectivenessScore by preferences.healthEffectivenessScore.collectAsState()

    // Query stats
    val totalConnections by database.connectionDao().countTotalConnectionsFlow().collectAsState(initial = 0L)
    val totalAdsBlocked by database.connectionDao().countTotalAdsBlockedFlow().collectAsState(initial = 0L)
    val totalAdultBlocked by database.connectionDao().countTotalAdultBlockedFlow().collectAsState(initial = 0L)

    // Calculate today's start timestamp
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfDay = calendar.timeInMillis

    val adsBlockedToday by database.connectionDao().countAdsBlockedSinceFlow(startOfDay).collectAsState(initial = 0L)
    val adultBlockedToday by database.connectionDao().countAdultBlockedSinceFlow(startOfDay).collectAsState(initial = 0L)
    val queriesToday by database.connectionDao().countConnectionsSinceFlow(startOfDay).collectAsState(initial = 0L)

    val recentLogs by database.connectionDao().getFilteredLogsFlow(
        searchQuery = "",
        category = null,
        isBlocked = true,
        limit = 5
    ).collectAsState(initial = emptyList())

    // Est. data saved: average ad/tracker payload saved is ~80KB per blocked domain
    val totalDataSavedMb = ((totalAdsBlocked + totalAdultBlocked) * 80L) / 1024L

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (dimensions.widthClass == WindowWidthClass.COMPACT) 16.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            DashboardHeader(
                isRunning = isRunning,
                onToggleService = {
                    if (isRunning) {
                        quickStartManager.stopBlockingServices()
                    } else {
                        quickStartManager.startBlockingServices(onPermissionRequired = onVpnPermissionNeeded)
                    }
                }
            )
        }

        // Quick Toggles Row (Ads & 18+ Content)
        item {
            QuickFiltersCard(
                adBlockingEnabled = adBlockingEnabled,
                onToggleAdBlocking = { preferences.setAdBlockingEnabled(it) },
                adultBlockingEnabled = adultBlockingEnabled,
                onToggleAdultBlocking = { preferences.setAdultBlockingEnabled(it) },
                adsBlockedToday = adsBlockedToday,
                adultBlockedToday = adultBlockedToday
            )
        }

        // Responsive Metric Cards
        item {
            ResponsiveMetricGrid(
                dimensions = dimensions,
                adsBlockedToday = adsBlockedToday,
                totalAdsBlocked = totalAdsBlocked,
                adultBlockedToday = adultBlockedToday,
                totalAdultBlocked = totalAdultBlocked,
                queriesToday = queriesToday,
                totalQueries = totalConnections,
                dataSavedMb = totalDataSavedMb
            )
        }

        // Health & Diagnostics Card
        item {
            HealthStatusCard(
                effectivenessScore = effectivenessScore,
                isRunning = isRunning,
                onOpenDiagnostics = onOpenDiagnostics
            )
        }

        // Recent Activity Preview
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Blocked Traffic",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = onNavigateToLogs) {
                    Text(text = "View All Logs", color = TealAccent)
                }
            }
        }

        if (recentLogs.isEmpty()) {
            item {
                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isRunning) "Listening for unwanted network connections..." else "Start unblocker to begin monitoring and blocking",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate400
                        )
                    }
                }
            }
        } else {
            items(recentLogs) { log ->
                RecentLogItem(log = log)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DashboardHeader(
    isRunning: Boolean,
    onToggleService: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isRunning) Color(0xFF064E3B) else Slate900
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isRunning) AllowedGreen else Slate700,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Shield else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (isRunning) "SHIELD ACTIVE" else "SHIELD INACTIVE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isRunning) "Protecting all device traffic locally" else "Tap power to activate protection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isRunning) AllowedGreen.copy(alpha = 0.9f) else Slate400
                    )
                }
            }

            Switch(
                checked = isRunning,
                onCheckedChange = { onToggleService() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AllowedGreen,
                    uncheckedThumbColor = Slate400,
                    uncheckedTrackColor = Slate800
                )
            )
        }
    }
}

@Composable
private fun QuickFiltersCard(
    adBlockingEnabled: Boolean,
    onToggleAdBlocking: (Boolean) -> Unit,
    adultBlockingEnabled: Boolean,
    onToggleAdultBlocking: (Boolean) -> Unit,
    adsBlockedToday: Long,
    adultBlockedToday: Long
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Live Protection Filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Ad Blocking Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Block Advertisements & Trackers",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "$adsBlockedToday ads blocked today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (adBlockingEnabled) AdBlockedRed else Slate400
                    )
                }
                Switch(
                    checked = adBlockingEnabled,
                    onCheckedChange = onToggleAdBlocking,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TealAccent
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Adult Content Blocking Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Block 18+ Adult Content",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "$adultBlockedToday adult requests blocked today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (adultBlockingEnabled) AdultBlockedPurple else Slate400
                    )
                }
                Switch(
                    checked = adultBlockingEnabled,
                    onCheckedChange = onToggleAdultBlocking,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AdultBlockedPurple
                    )
                )
            }
        }
    }
}

@Composable
private fun ResponsiveMetricGrid(
    dimensions: com.unblocker.app.ui.adaptive.WindowDimensions,
    adsBlockedToday: Long,
    totalAdsBlocked: Long,
    adultBlockedToday: Long,
    totalAdultBlocked: Long,
    queriesToday: Long,
    totalQueries: Long,
    dataSavedMb: Long
) {
    if (dimensions.widthClass == WindowWidthClass.EXPANDED) {
        // 4 Columns in single row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Ads Blocked",
                todayCount = adsBlockedToday,
                totalCount = totalAdsBlocked,
                icon = Icons.Default.Block,
                accentColor = AdBlockedRed,
                bgColor = AdBlockedRedBg,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Adult Blocked",
                todayCount = adultBlockedToday,
                totalCount = totalAdultBlocked,
                icon = Icons.Default.Shield,
                accentColor = AdultBlockedPurple,
                bgColor = AdultBlockedPurpleBg,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Queries Monitored",
                todayCount = queriesToday,
                totalCount = totalQueries,
                icon = Icons.Default.NetworkCheck,
                accentColor = CyanGlow,
                bgColor = Slate700,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Est. Data Saved",
                todayCount = dataSavedMb,
                unit = "MB",
                icon = Icons.Default.DataUsage,
                accentColor = AllowedGreen,
                bgColor = AllowedGreenBg,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        // 2 Columns x 2 Rows
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "Ads Blocked",
                    todayCount = adsBlockedToday,
                    totalCount = totalAdsBlocked,
                    icon = Icons.Default.Block,
                    accentColor = AdBlockedRed,
                    bgColor = AdBlockedRedBg,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Adult Blocked",
                    todayCount = adultBlockedToday,
                    totalCount = totalAdultBlocked,
                    icon = Icons.Default.Shield,
                    accentColor = AdultBlockedPurple,
                    bgColor = AdultBlockedPurpleBg,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "Queries Monitored",
                    todayCount = queriesToday,
                    totalCount = totalQueries,
                    icon = Icons.Default.NetworkCheck,
                    accentColor = CyanGlow,
                    bgColor = Slate700,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Est. Data Saved",
                    todayCount = dataSavedMb,
                    unit = "MB",
                    icon = Icons.Default.DataUsage,
                    accentColor = AllowedGreen,
                    bgColor = AllowedGreenBg,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    todayCount: Long,
    totalCount: Long? = null,
    unit: String? = null,
    icon: ImageVector,
    accentColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Slate400
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(bgColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (unit != null) "$todayCount $unit" else "$todayCount",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (totalCount != null) {
                Text(
                    text = "$totalCount all-time",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400
                )
            }
        }
    }
}

@Composable
private fun HealthStatusCard(
    effectivenessScore: Float,
    isRunning: Boolean,
    onOpenDiagnostics: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HealthAndSafety,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "System Health & Effectiveness",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Effectiveness: ${"%.1f".format(effectivenessScore)}% | Integrity: OK",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AllowedGreen
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenDiagnostics,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TealAccent)
            ) {
                Text(text = "Diagnostics")
            }
        }
    }
}

@Composable
private fun RecentLogItem(log: ConnectionLog) {
    val isAdult = log.category == com.unblocker.app.data.model.ContentType.ADULT_CONTENT.name
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Surface(
        color = Slate800,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.domain,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${log.sourceApp} • ${timeFormat.format(Date(log.timestamp))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400
                )
            }

            Surface(
                color = if (isAdult) AdultBlockedPurpleBg else AdBlockedRedBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isAdult) "18+ BLOCKED" else "AD BLOCKED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isAdult) AdultBlockedPurple else AdBlockedRed,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
