package com.unblocker.app.ui.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unblocker.app.ui.components.HaikeiAtmosphericBackground
import com.unblocker.app.ui.components.SkeuomorphicCard
import com.unblocker.app.ui.theme.AdBlockedRed
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.Slate100
import com.unblocker.app.ui.theme.Slate300
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealAccent
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Redesigned Connection Logs Screen.
 *
 * Implements:
 * - Haikei atmospheric background layer
 * - Skeuomorphic tactile depth cards with specular rim lighting
 * - Unique domain aggregation with call counts & timestamped event records (up to 50 events)
 * - Filter chips (All / Blocked / Allowed) and sort options (Recent / Most calls)
 * - Live aggregate counters, history wiping, and pause/resume logging
 */
@Composable
fun LogViewerScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: LogViewModel = hiltViewModel()
) {
    val domainStats by viewModel.domainStats.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val expandedDomain by viewModel.expandedDomain.collectAsState()
    val domainEvents by viewModel.domainEvents.collectAsState()
    val isLoggingPaused by viewModel.isLoggingPaused.collectAsState()

    val totalDomains by viewModel.totalDomains.collectAsState()
    val totalCalls by viewModel.totalCalls.collectAsState()
    val totalBlocked by viewModel.totalBlocked.collectAsState()

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All History?") },
            text = { Text("This will permanently remove all intercepted domain statistics and timestamped event records.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLogs()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    HaikeiAtmosphericBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusBarPadding + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Skeuomorphic Header Card
            item {
                SkeuomorphicCard(
                    shape = RoundedCornerShape(24.dp),
                    elevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Connection Logs",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Slate100,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$totalDomains domains • $totalCalls queries • $totalBlocked blocked",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Slate400
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isLoggingPaused) Color(0x33EF4444) else Slate800,
                                border = BorderStroke(1.dp, if (isLoggingPaused) Color(0x66EF4444) else Slate700),
                                modifier = Modifier.size(42.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleLoggingPaused() },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = if (isLoggingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = if (isLoggingPaused) "Resume Logging" else "Pause Logging",
                                        tint = if (isLoggingPaused) Color(0xFFEF4444) else TealAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Slate800,
                                border = BorderStroke(1.dp, Slate700),
                                modifier = Modifier.size(42.dp)
                            ) {
                                IconButton(
                                    onClick = { showClearConfirmDialog = true },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Clear History",
                                        tint = Slate400,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isLoggingPaused) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x26EF4444),
                            border = BorderStroke(1.dp, Color(0x66EF4444))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Logging is currently paused",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFFCA5A5),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Skeuomorphic Search and Filters Card
            item {
                SkeuomorphicCard(
                    shape = RoundedCornerShape(22.dp),
                    elevation = 4.dp
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search domains…", color = Slate400) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Slate400)
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate100,
                            unfocusedTextColor = Slate100,
                            focusedPlaceholderColor = Slate400,
                            unfocusedPlaceholderColor = Slate400,
                            focusedLeadingIconColor = TealAccent,
                            unfocusedLeadingIconColor = Slate400,
                            focusedTrailingIconColor = Slate100,
                            unfocusedTrailingIconColor = Slate400,
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = Slate700.copy(alpha = 0.8f),
                            cursorColor = TealAccent
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Filter & Sort Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = filter == LogFilter.ALL,
                                onClick = { viewModel.setFilter(LogFilter.ALL) },
                                label = { Text("All") },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TealAccent.copy(alpha = 0.25f),
                                    selectedLabelColor = TealAccent,
                                    containerColor = Slate800,
                                    labelColor = Slate400
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = filter == LogFilter.ALL,
                                    borderColor = if (filter == LogFilter.ALL) TealAccent else Slate700
                                )
                            )
                            FilterChip(
                                selected = filter == LogFilter.BLOCKED,
                                onClick = { viewModel.setFilter(LogFilter.BLOCKED) },
                                label = { Text("Blocked") },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AdBlockedRed.copy(alpha = 0.25f),
                                    selectedLabelColor = AdBlockedRed,
                                    containerColor = Slate800,
                                    labelColor = Slate400
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = filter == LogFilter.BLOCKED,
                                    borderColor = if (filter == LogFilter.BLOCKED) AdBlockedRed else Slate700
                                )
                            )
                            FilterChip(
                                selected = filter == LogFilter.ALLOWED,
                                onClick = { viewModel.setFilter(LogFilter.ALLOWED) },
                                label = { Text("Allowed") },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AllowedGreen.copy(alpha = 0.25f),
                                    selectedLabelColor = AllowedGreen,
                                    containerColor = Slate800,
                                    labelColor = Slate400
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = filter == LogFilter.ALLOWED,
                                    borderColor = if (filter == LogFilter.ALLOWED) AllowedGreen else Slate700
                                )
                            )
                        }

                        // Sort toggle button
                        TextButton(
                            onClick = {
                                viewModel.setSort(
                                    if (sort == LogSort.RECENT) LogSort.MOST_CALLS else LogSort.RECENT
                                )
                            }
                        ) {
                            Text(
                                text = if (sort == LogSort.RECENT) "Sort: Recent" else "Sort: Most calls",
                                color = TealAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Domain List Rows
            if (domainStats.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No connections logged yet" else "No matching domains found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Slate400
                        )
                    }
                }
            } else {
                items(domainStats, key = { it.domain }) { stat ->
                    DomainStatCard(
                        stat = stat,
                        isExpanded = expandedDomain == stat.domain,
                        events = if (expandedDomain == stat.domain) domainEvents else emptyList(),
                        onToggleExpand = { viewModel.toggleExpandDomain(stat.domain) },
                        onAllow = { viewModel.allowDomain(stat.domain) },
                        onBlock = { viewModel.blockDomain(stat.domain) }
                    )
                }
            }
        }
    }
}

@Composable
fun DomainStatCard(
    stat: DomainStatEntity,
    isExpanded: Boolean,
    events: List<DomainEventEntity>,
    onToggleExpand: () -> Unit,
    onAllow: () -> Unit,
    onBlock: () -> Unit
) {
    val relativeTime = remember(stat.lastSeen) {
        formatRelativeTime(stat.lastSeen)
    }

    SkeuomorphicCard(
        shape = RoundedCornerShape(20.dp),
        elevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stat.domain,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate100,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${stat.callCount} calls • ${stat.blockedCount} blocked • $relativeTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(blockedCount = stat.blockedCount, allowedCount = stat.allowedCount)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Slate400
                    )
                }
            }

            // Expanded Detail Section
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = Slate700
                    )

                    Text(
                        text = "Recent Activity (Latest 50)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TealAccent
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (events.isEmpty()) {
                        Text(
                            text = "Loading events…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    } else {
                        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            events.forEach { event ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${dateFormat.format(Date(event.ts))} ${timeFormat.format(Date(event.ts))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400
                                    )
                                    Text(
                                        text = if (event.decision == 1) "BLOCKED" else "ALLOWED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (event.decision == 1) AdBlockedRed else AllowedGreen
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Override Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onAllow,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, AllowedGreen),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Always Allow", color = AllowedGreen)
                        }
                        Button(
                            onClick = onBlock,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Block Domain", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(blockedCount: Long, allowedCount: Long) {
    val (label, bg, text) = when {
        blockedCount > 0 && allowedCount > 0 -> Triple(
            "Mixed",
            Color(0x33F59E0B),
            Color(0xFFFBBF24)
        )
        blockedCount > 0 -> Triple(
            "Blocked",
            Color(0x33EF4444),
            Color(0xFFF87171)
        )
        else -> Triple(
            "Allowed",
            Color(0x3310B981),
            AllowedGreen
        )
    }

    Surface(
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, text.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            color = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun formatRelativeTime(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    if (diff < 0) return "just now"
    val seconds = diff / 1000
    if (seconds < 60) return "${seconds}s ago"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}
