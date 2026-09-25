package com.unblocker.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unblocker.app.data.dao.AppCount
import com.unblocker.app.data.dao.DomainCount
import com.unblocker.app.data.database.AppDatabase
import com.unblocker.app.ui.adaptive.LocalWindowDimensions
import com.unblocker.app.ui.adaptive.WindowWidthClass
import com.unblocker.app.ui.theme.AdBlockedRed
import com.unblocker.app.ui.theme.AdultBlockedPurple
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.CyanGlow
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealAccent

@Composable
fun StatisticsScreen(
    database: AppDatabase
) {
    val dimensions = LocalWindowDimensions.current

    val totalConnections by database.connectionDao().countTotalConnectionsFlow().collectAsState(initial = 0L)
    val totalAdsBlocked by database.connectionDao().countTotalAdsBlockedFlow().collectAsState(initial = 0L)
    val totalAdultBlocked by database.connectionDao().countTotalAdultBlockedFlow().collectAsState(initial = 0L)

    val topAdDomains by database.connectionDao().getTopBlockedAdDomainsFlow(5).collectAsState(initial = emptyList())
    val topAdultDomains by database.connectionDao().getTopBlockedAdultDomainsFlow(5).collectAsState(initial = emptyList())
    val topApps by database.connectionDao().getTopAppsFlow(5).collectAsState(initial = emptyList())

    val totalBlocked = totalAdsBlocked + totalAdultBlocked
    val totalAllowed = (totalConnections - totalBlocked).coerceAtLeast(0L)

    val adPercentage = if (totalConnections > 0) (totalAdsBlocked.toFloat() / totalConnections) else 0f
    val adultPercentage = if (totalConnections > 0) (totalAdultBlocked.toFloat() / totalConnections) else 0f
    val allowedPercentage = if (totalConnections > 0) (totalAllowed.toFloat() / totalConnections) else 1f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (dimensions.widthClass == WindowWidthClass.COMPACT) 16.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Network Analytics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Comprehensive device traffic & protection breakdown",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate400
            )
        }

        // Traffic Distribution Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = CyanGlow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Traffic Classification",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Multi-Segment Bar Representation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Slate700)
                    ) {
                        if (adPercentage > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(adPercentage.coerceAtLeast(0.01f))
                                    .height(14.dp)
                                    .background(AdBlockedRed)
                            )
                        }
                        if (adultPercentage > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(adultPercentage.coerceAtLeast(0.01f))
                                    .height(14.dp)
                                    .background(AdultBlockedPurple)
                            )
                        }
                        if (allowedPercentage > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(allowedPercentage.coerceAtLeast(0.01f))
                                    .height(14.dp)
                                    .background(AllowedGreen)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendItem(label = "Ads", count = totalAdsBlocked, color = AdBlockedRed)
                        LegendItem(label = "Adult (18+)", count = totalAdultBlocked, color = AdultBlockedPurple)
                        LegendItem(label = "Allowed", count = totalAllowed, color = AllowedGreen)
                    }
                }
            }
        }

        // Top Blocked Domains - Responsive split if screen is wide
        item {
            if (dimensions.widthClass == WindowWidthClass.EXPANDED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TopListCard(
                        title = "Top Blocked Ad Domains",
                        icon = Icons.Default.Block,
                        iconColor = AdBlockedRed,
                        items = topAdDomains,
                        emptyMessage = "No ads blocked yet",
                        modifier = Modifier.weight(1f)
                    )
                    TopListCard(
                        title = "Top Blocked 18+ Domains",
                        icon = Icons.Default.Shield,
                        iconColor = AdultBlockedPurple,
                        items = topAdultDomains,
                        emptyMessage = "No adult content blocked yet",
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TopListCard(
                        title = "Top Blocked Ad Domains",
                        icon = Icons.Default.Block,
                        iconColor = AdBlockedRed,
                        items = topAdDomains,
                        emptyMessage = "No ads blocked yet"
                    )
                    TopListCard(
                        title = "Top Blocked 18+ Domains",
                        icon = Icons.Default.Shield,
                        iconColor = AdultBlockedPurple,
                        items = topAdultDomains,
                        emptyMessage = "No adult content blocked yet"
                    )
                }
            }
        }

        // Top Monitored Apps
        item {
            TopAppsCard(apps = topApps)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegendItem(label: String, count: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Slate400)
            Text(text = "$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun TopListCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    items: List<DomainCount>,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (items.isEmpty()) {
                Text(text = emptyMessage, style = MaterialTheme.typography.bodyMedium, color = Slate400)
            } else {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. ${item.domain}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${item.count} blocks",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = iconColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopAppsCard(apps: List<AppCount>) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Most Active Applications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (apps.isEmpty()) {
                Text(text = "No applications logged yet", style = MaterialTheme.typography.bodyMedium, color = Slate400)
            } else {
                apps.forEachIndexed { index, app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. ${app.source_app}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${app.count} queries",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TealAccent
                        )
                    }
                }
            }
        }
    }
}
