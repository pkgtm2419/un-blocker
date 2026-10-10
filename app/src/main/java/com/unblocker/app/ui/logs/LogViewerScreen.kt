package com.unblocker.app.ui.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Samsung One UI 9 style Logs tab.
 *
 * Implements WP-3 and WP-4:
 * - Shows each unique domain once with total call counts.
 * - Expanding shows timestamped history (up to 50 events) grouped chronologically.
 * - Filter chips (All / Blocked / Allowed) and sort options (Recent / Most calls).
 * - Live aggregate counters, history wiping, and pause/resume logging.
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // One UI 9 Header Card
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Connection Logs",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$totalDomains domains • $totalCalls queries • $totalBlocked blocked",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row {
                            IconButton(
                                onClick = { viewModel.toggleLoggingPaused() }
                            ) {
                                Icon(
                                    imageVector = if (isLoggingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isLoggingPaused) "Resume Logging" else "Pause Logging",
                                    tint = if (isLoggingPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { showClearConfirmDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear History",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isLoggingPaused) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "Logging is currently paused",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Search and Filters Card
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search domains…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                                shape = CircleShape
                            )
                            FilterChip(
                                selected = filter == LogFilter.BLOCKED,
                                onClick = { viewModel.setFilter(LogFilter.BLOCKED) },
                                label = { Text("Blocked") },
                                shape = CircleShape
                            )
                            FilterChip(
                                selected = filter == LogFilter.ALLOWED,
                                onClick = { viewModel.setFilter(LogFilter.ALLOWED) },
                                label = { Text("Allowed") },
                                shape = CircleShape
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
                                text = if (sort == LogSort.RECENT) "Recent" else "Most calls",
                                fontSize = 12.sp
                            )
                        }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(16.dp)
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${stat.callCount} calls • ${stat.blockedCount} blocked • $relativeTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(blockedCount = stat.blockedCount, allowedCount = stat.allowedCount)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                    Text(
                        text = "Recent Activity (Latest 50)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (events.isEmpty()) {
                        Text(
                            text = "Loading events…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (event.decision == 1) "BLOCKED" else "ALLOWED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (event.decision == 1) MaterialTheme.colorScheme.error else Color(0xFF10B981)
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
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Always Allow")
                        }
                        Button(
                            onClick = onBlock,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Block Domain")
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
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        blockedCount > 0 -> Triple(
            "Blocked",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Triple(
            "Allowed",
            Color(0xFFD1FAE5),
            Color(0xFF065F46)
        )
    }

    Surface(
        shape = CircleShape,
        color = bg
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
