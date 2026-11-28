package com.unblocker.app.ui.logs

import android.content.res.Resources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unblocker.app.R
import com.unblocker.app.ui.components.HaikeiAtmosphericBackground
import com.unblocker.app.ui.components.SkeuomorphicCard
import com.unblocker.app.ui.theme.AdBlockedRed
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders local aggregate DNS logs, filters, optional event details, export, and history controls.
 *
 * @param contentPadding Parent navigation padding whose bottom inset is applied to the list and snackbar.
 * @param initialDomain Optional initial domain query supplied by navigation/deep-link arguments.
 * @param viewModel Owner of log queries, rule feedback, and local-history operations.
 */
@Composable
fun LogViewerScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    initialDomain: String? = null,
    viewModel: LogViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialDomain) {
        if (!initialDomain.isNullOrBlank()) {
            viewModel.setSearchQuery(initialDomain)
        }
    }
    val domainStats by viewModel.domainStats.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val expandedDomain by viewModel.expandedDomain.collectAsState()
    val domainEvents by viewModel.domainEvents.collectAsState()
    val isLoggingPaused by viewModel.isLoggingPaused.collectAsState()
    val selectedDomains by viewModel.selectedDomains.collectAsState()

    val totalDomains by viewModel.totalDomains.collectAsState()
    val totalCalls by viewModel.totalCalls.collectAsState()
    val totalBlocked by viewModel.totalBlocked.collectAsState()
    val summary by viewModel.protectionSummary.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .CreateDocument("text/csv"),
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            viewModel.exportLogsToCsv(os)
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Export failed: ${e.message}")
                    }
                }
            }
        }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(stringResource(R.string.logs_clear_dialog_title)) },
            text = {
                Text(
                    stringResource(R.string.logs_clear_dialog_body),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLogs()
                        showClearConfirmDialog = false
                    },
                ) {
                    Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    HaikeiAtmosphericBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = statusBarPadding + 16.dp,
                        bottom = contentPadding.calculateBottomPadding() + 80.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Skeuomorphic Header Card
                item {
                    SkeuomorphicCard(
                        shape = RoundedCornerShape(24.dp),
                        elevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.logs_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 0.5.sp,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text =
                                        stringResource(
                                            R.string.logs_counts_header,
                                            totalDomains,
                                            totalCalls,
                                            totalBlocked,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color =
                                        if (isLoggingPaused) {
                                            Color(
                                                0x33EF4444,
                                            )
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    border =
                                        BorderStroke(
                                            1.dp,
                                            if (isLoggingPaused) {
                                                Color(
                                                    0x66EF4444,
                                                )
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                        ),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    IconButton(
                                        onClick = { viewModel.toggleLoggingPaused() },
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Icon(
                                            imageVector = if (isLoggingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            contentDescription =
                                                stringResource(
                                                    if (isLoggingPaused) {
                                                        R.string.logs_resume_desc
                                                    } else {
                                                        R.string.logs_pause_desc
                                                    },
                                                ),
                                            tint =
                                                if (isLoggingPaused) {
                                                    Color(
                                                        0xFFEF4444,
                                                    )
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            val timestamp =
                                                SimpleDateFormat(
                                                    "yyyyMMdd_HHmmss",
                                                    Locale.US,
                                                ).format(Date())
                                            exportLauncher.launch("unblocker_export_$timestamp.csv")
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = stringResource(R.string.logs_export_csv),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    IconButton(
                                        onClick = { viewModel.pruneExpiredLogs() },
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CleaningServices,
                                            contentDescription = stringResource(R.string.logs_prune_expired_desc),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    IconButton(
                                        onClick = { showClearConfirmDialog = true },
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = stringResource(R.string.logs_clear_history),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.logs_protection_summary,
                                        summary.dnsQueries,
                                        summary.blockedDnsQueries,
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp),
                            )
                        }

                        if (isLoggingPaused) {
                            val isDark = isSystemInDarkTheme()
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0x26EF4444) else Color(0xFFFEE2E2),
                                border = BorderStroke(1.dp, if (isDark) Color(0x66EF4444) else Color(0xFFFCA5A5)),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.logs_logging_paused),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B),
                                        fontWeight = FontWeight.SemiBold,
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
                        elevation = 4.dp,
                    ) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = {
                                Text(
                                    stringResource(R.string.logs_search_placeholder),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.action_clear_search),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                ),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Filter & Sort Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = filter == LogFilter.ALL,
                                    onClick = { viewModel.setFilter(LogFilter.ALL) },
                                    label = { Text(stringResource(R.string.logs_filter_all)) },
                                    shape = CircleShape,
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    border =
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = filter == LogFilter.ALL,
                                            borderColor =
                                                if (filter ==
                                                    LogFilter.ALL
                                                ) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                        ),
                                )
                                FilterChip(
                                    selected = filter == LogFilter.BLOCKED,
                                    onClick = { viewModel.setFilter(LogFilter.BLOCKED) },
                                    label = { Text(stringResource(R.string.logs_filter_blocked)) },
                                    shape = CircleShape,
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AdBlockedRed.copy(alpha = 0.25f),
                                            selectedLabelColor = AdBlockedRed,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    border =
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = filter == LogFilter.BLOCKED,
                                            borderColor =
                                                if (filter ==
                                                    LogFilter.BLOCKED
                                                ) {
                                                    AdBlockedRed
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                        ),
                                )
                                FilterChip(
                                    selected = filter == LogFilter.ALLOWED,
                                    onClick = { viewModel.setFilter(LogFilter.ALLOWED) },
                                    label = { Text(stringResource(R.string.logs_filter_allowed)) },
                                    shape = CircleShape,
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AllowedGreen.copy(alpha = 0.25f),
                                            selectedLabelColor = AllowedGreen,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    border =
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = filter == LogFilter.ALLOWED,
                                            borderColor =
                                                if (filter ==
                                                    LogFilter.ALLOWED
                                                ) {
                                                    AllowedGreen
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                        ),
                                )
                                FilterChip(
                                    selected = filter == LogFilter.FAILED,
                                    onClick = { viewModel.setFilter(LogFilter.FAILED) },
                                    label = { Text(stringResource(R.string.logs_filter_failed)) },
                                    shape = CircleShape,
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0x33F59E0B),
                                            selectedLabelColor = Color(0xFFB45309),
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    border =
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = filter == LogFilter.FAILED,
                                            borderColor =
                                                if (filter == LogFilter.FAILED) {
                                                    Color(0xFFF59E0B)
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                        ),
                                )
                            }

                            // Sort toggle button
                            TextButton(
                                onClick = {
                                    viewModel.setSort(
                                        if (sort == LogSort.RECENT) LogSort.MOST_CALLS else LogSort.RECENT,
                                    )
                                },
                            ) {
                                Text(
                                    text = if (sort == LogSort.RECENT) "Sort: Recent" else "Sort: Most calls",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }

                // Domain List Rows
                if (selectedDomains.isNotEmpty()) {
                    item {
                        val visibleDomains = domainStats.take(150).map { it.domain }
                        SkeuomorphicCard(shape = RoundedCornerShape(18.dp), elevation = 4.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.logs_selected_count, selectedDomains.size),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { viewModel.selectVisibleDomains(visibleDomains) }) {
                                            Text(stringResource(R.string.logs_select_all))
                                        }
                                        TextButton(onClick = { viewModel.clearSelection() }) {
                                            Text(stringResource(R.string.logs_clear_selection))
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.batchAllowSelected() },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.logs_batch_allow))
                                    }
                                    Button(
                                        onClick = { viewModel.batchBlockSelected() },
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                            ),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.logs_batch_block), color = Color.White)
                                    }
                                    TextButton(
                                        onClick = { viewModel.batchResetSelected() },
                                    ) {
                                        Text(stringResource(R.string.logs_batch_reset))
                                    }
                                }
                            }
                        }
                    }
                }

                if (domainStats.isEmpty()) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        if (searchQuery.isEmpty()) {
                                            R.string.logs_empty_all
                                        } else {
                                            R.string.logs_empty_search
                                        },
                                    ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    val selectionMode = selectedDomains.isNotEmpty()
                    items(domainStats.take(150), key = { it.domain }) { stat ->
                        DomainStatCard(
                            stat = stat,
                            isExpanded = expandedDomain == stat.domain,
                            events = if (expandedDomain == stat.domain) domainEvents else emptyList(),
                            onToggleExpand = { viewModel.toggleExpandDomain(stat.domain) },
                            onAllow = { viewModel.allowDomain(stat.domain) },
                            onBlock = { viewModel.blockDomain(stat.domain) },
                            onReset = { viewModel.resetDomain(stat.domain) },
                            onAllowWholeSite = { viewModel.allowWholeSite(stat.domain) },
                            onProtectDomain = { viewModel.protectDomain(stat.domain) },
                            isSelected = selectedDomains.contains(stat.domain),
                            selectionMode = selectionMode,
                            onToggleSelect = { viewModel.toggleSelectDomain(stat.domain) },
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp),
            )
        }
    }
}

/**
 * Renders one domain aggregate and its expandable event and rule-action section.
 *
 * @param stat Aggregate counts and timestamps for the readable domain.
 * @param isExpanded Whether details should be visible.
 * @param events Available recent events supplied by the caller; may be empty in aggregate-only mode.
 * @param onToggleExpand Callback requesting expansion or collapse.
 * @param onAllow Callback requesting an exact-domain allow override.
 * @param onBlock Callback requesting an exact-domain block override.
 * @param onReset Callback requesting removal of the exact-domain override.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DomainStatCard(
    stat: DomainStatEntity,
    isExpanded: Boolean,
    events: List<DomainEventEntity>,
    onToggleExpand: () -> Unit,
    onAllow: () -> Unit,
    onBlock: () -> Unit,
    onReset: () -> Unit = {},
    onAllowWholeSite: () -> Unit = {},
    onProtectDomain: () -> Unit = {},
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
) {
    val resources = LocalResources.current
    val relativeTime =
        remember(stat.lastSeen, resources) {
            formatRelativeTime(resources, stat.lastSeen)
        }
    var showHistoryPopup by remember { mutableStateOf(false) }

    SkeuomorphicCard(
        shape = RoundedCornerShape(20.dp),
        elevation = 3.dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(200)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggleSelect() else onToggleExpand() },
                        onLongClick = onToggleSelect,
                    ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stat.domain,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text =
                            stringResource(
                                R.string.logs_item_meta,
                                stat.callCount,
                                stat.blockedCount,
                                relativeTime,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        blockedCount = stat.blockedCount,
                        allowedCount = stat.allowedCount,
                        failCount = stat.failCount,
                        lastOutcome = stat.lastOutcome,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription =
                            if (isExpanded) {
                                stringResource(R.string.action_collapse)
                            } else {
                                stringResource(R.string.action_expand)
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Expanded Detail Section
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.logs_recent_activity_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(
                            onClick = { showHistoryPopup = true },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.logs_history_button_desc),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (events.isEmpty()) {
                        Text(
                            text =
                                stringResource(
                                    R.string.logs_aggregated_empty,
                                    stat.callCount,
                                    stat.blockedCount,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            events.forEach { event ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${dateFormat.format(
                                            Date(event.ts),
                                        )} ${timeFormat.format(Date(event.ts))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val isDark = isSystemInDarkTheme()
                                    Text(
                                        text = if (event.decision == 1) "BLOCKED" else "ALLOWED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color =
                                            if (event.decision == 1) {
                                                if (isDark) AdBlockedRed else Color(0xFFDC2626)
                                            } else {
                                                if (isDark) AllowedGreen else Color(0xFF047857)
                                            },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Override Action Buttons
                    val isDark = isSystemInDarkTheme()
                    val allowColor = if (isDark) AllowedGreen else Color(0xFF047857)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { showHistoryPopup = true },
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text(stringResource(R.string.logs_action_why), color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(
                            onClick = onReset,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text(stringResource(R.string.logs_action_reset), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = onAllow,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, allowColor),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(stringResource(R.string.logs_action_always_allow), color = allowColor)
                        }
                        Button(
                            onClick = onBlock,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.logs_action_block_domain), color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showHistoryPopup) {
        ConnectionHistoryDialog(
            stat = stat,
            events = events,
            onDismiss = { showHistoryPopup = false },
            onAllow = onAllow,
            onBlock = onBlock,
            onAllowWholeSite = onAllowWholeSite,
            onProtectDomain = onProtectDomain,
            onReset = onReset,
        )
    }
}

/**
 * Renders failed, mixed, blocked, or allowed status from aggregate decision counts and latest outcome.
 *
 * @param blockedCount Number of blocked observations.
 * @param allowedCount Number of allowed observations.
 * @param failCount Number of upstream DNS resolution failures.
 * @param lastOutcome Latest recorded outcome code.
 */
@Composable
fun StatusChip(
    blockedCount: Long,
    allowedCount: Long,
    failCount: Long = 0L,
    lastOutcome: Int = DomainStatEntity.OUTCOME_ALLOWED,
) {
    val isDark = isSystemInDarkTheme()
    val (label, backgroundColor, textColor) =
        when {
            lastOutcome == DomainStatEntity.OUTCOME_UPSTREAM_FAIL ||
                (failCount > 0L && blockedCount == 0L && allowedCount == 0L) -> {
                Triple(
                    stringResource(R.string.logs_filter_failed),
                    if (isDark) Color(0x33F59E0B) else Color(0xFFFEF3C7),
                    if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                )
            }

            blockedCount > 0 && allowedCount > 0 -> {
                Triple(
                    stringResource(R.string.logs_filter_mixed),
                    if (isDark) Color(0x33F59E0B) else Color(0xFFFEF3C7),
                    if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                )
            }

            blockedCount > 0 -> {
                Triple(
                    stringResource(R.string.logs_filter_blocked),
                    if (isDark) Color(0x33EF4444) else Color(0xFFFEE2E2),
                    if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                )
            }

            else -> {
                Triple(
                    stringResource(R.string.logs_filter_allowed),
                    if (isDark) Color(0x3310B981) else Color(0xFFD1FAE5),
                    if (isDark) AllowedGreen else Color(0xFF047857),
                )
            }
        }

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(1.dp, if (isDark) textColor.copy(alpha = 0.4f) else textColor.copy(alpha = 0.25f)),
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun formatRelativeTime(
    resources: Resources,
    epochMillis: Long,
): String {
    val elapsedMillis = System.currentTimeMillis() - epochMillis
    if (elapsedMillis < 0) return resources.getString(R.string.time_just_now)
    val seconds = elapsedMillis / 1000
    if (seconds < 60) return resources.getString(R.string.time_seconds_ago, seconds)
    val minutes = seconds / 60
    if (minutes < 60) return resources.getString(R.string.time_minutes_ago, minutes)
    val hours = minutes / 60
    if (hours < 24) return resources.getString(R.string.time_hours_ago, hours)
    val days = hours / 24
    return resources.getString(R.string.time_days_ago, days)
}
