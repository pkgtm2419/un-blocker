package com.unblocker.app.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.sp
import com.unblocker.app.data.database.AppDatabase
import com.unblocker.app.data.model.ConnectionLog
import com.unblocker.app.data.model.ContentType
import com.unblocker.app.ui.adaptive.LocalWindowDimensions
import com.unblocker.app.ui.adaptive.WindowWidthClass
import com.unblocker.app.ui.theme.AdBlockedRed
import com.unblocker.app.ui.theme.AdBlockedRedBg
import com.unblocker.app.ui.theme.AdultBlockedPurple
import com.unblocker.app.ui.theme.AdultBlockedPurpleBg
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.AllowedGreenBg
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogFilterType {
    ALL,
    ADS_ONLY,
    ADULT_ONLY,
    BLOCKED_ONLY,
    ALLOWED_ONLY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    database: AppDatabase
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dimensions = LocalWindowDimensions.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(LogFilterType.ALL) }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedLogForDetails by remember { mutableStateOf<ConnectionLog?>(null) }

    // Map UI filter to query parameters
    val categoryFilter = when (selectedFilter) {
        LogFilterType.ADS_ONLY -> ContentType.AD.name
        LogFilterType.ADULT_ONLY -> ContentType.ADULT_CONTENT.name
        else -> null
    }

    val isBlockedFilter = when (selectedFilter) {
        LogFilterType.BLOCKED_ONLY -> true
        LogFilterType.ALLOWED_ONLY -> false
        else -> null
    }

    val logs by database.connectionDao().getFilteredLogsFlow(
        searchQuery = searchQuery,
        category = categoryFilter,
        isBlocked = isBlockedFilter,
        limit = 300
    ).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (dimensions.widthClass == WindowWidthClass.COMPACT) 16.dp else 32.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Title and Actions Row (Export & Clear)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Connection Logs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${logs.size} connections logged",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400
                )
            }

            Row {
                IconButton(
                    onClick = {
                        exportLogsToCsv(context, logs)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Logs",
                        tint = TealAccent
                    )
                }

                IconButton(
                    onClick = { showClearDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Logs",
                        tint = Slate400
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(text = "Search domain or app name...", color = Slate400) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Slate400) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search", tint = Slate400)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Slate800,
                unfocusedContainerColor = Slate800,
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = Slate700
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(LogFilterType.values()) { filter ->
                val label = when (filter) {
                    LogFilterType.ALL -> "All"
                    LogFilterType.ADS_ONLY -> "Ads Only"
                    LogFilterType.ADULT_ONLY -> "18+ Adult"
                    LogFilterType.BLOCKED_ONLY -> "Blocked"
                    LogFilterType.ALLOWED_ONLY -> "Allowed"
                }

                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(text = label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealAccent,
                        selectedLabelColor = Color.Black,
                        containerColor = Slate800,
                        labelColor = Slate400
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == filter,
                        borderColor = Slate700,
                        selectedBorderColor = TealAccent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Logs List
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No connection logs match the current filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    LogCard(
                        log = log,
                        onClick = { selectedLogForDetails = log }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Clear Logs Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Logs?") },
            text = { Text("This will permanently delete all logged network connections stored on this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                database.connectionDao().clearAll()
                            }
                            showClearDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdBlockedRed)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Log Detail Bottom Sheet
    selectedLogForDetails?.let { log ->
        ModalBottomSheet(
            onDismissRequest = { selectedLogForDetails = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Slate900
        ) {
            LogDetailSheet(log = log, onClose = { selectedLogForDetails = null })
        }
    }
}

@Composable
private fun LogCard(
    log: ConnectionLog,
    onClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val isBlocked = log.isBlocked
    val isAdult = log.category == ContentType.ADULT_CONTENT.name

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.domain,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${log.sourceApp} • ${timeFormat.format(Date(log.timestamp))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400
                    )
                    if (log.detectionMethod.isNotEmpty() && log.detectionMethod != "NONE") {
                        Text(
                            text = " • ${log.detectionMethod}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent
                        )
                    }
                }
            }

            Surface(
                color = when {
                    !isBlocked -> AllowedGreenBg
                    isAdult -> AdultBlockedPurpleBg
                    else -> AdBlockedRedBg
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = when {
                        !isBlocked -> "ALLOWED"
                        isAdult -> "18+ BLOCKED"
                        else -> "AD BLOCKED"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !isBlocked -> AllowedGreen
                        isAdult -> AdultBlockedPurple
                        else -> AdBlockedRed
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LogDetailSheet(
    log: ConnectionLog,
    onClose: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Connection Details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        DetailRow(label = "Domain", value = log.domain)
        DetailRow(label = "Status", value = if (log.isBlocked) "BLOCKED" else "ALLOWED")
        DetailRow(label = "Category", value = log.category)
        DetailRow(label = "Detection Method", value = log.detectionMethod)
        DetailRow(label = "Reason", value = log.reason)
        DetailRow(label = "Confidence", value = "${(log.confidence * 100).toInt()}%")
        DetailRow(label = "Timestamp", value = dateFormat.format(Date(log.timestamp)))
        DetailRow(label = "Source App", value = log.sourceApp)
        DetailRow(label = "Protocol / Port", value = "${log.protocol} : ${log.port}")

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Slate700)
        ) {
            Text(text = "Close")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Slate400)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun exportLogsToCsv(context: Context, logs: List<ConnectionLog>) {
    if (logs.isEmpty()) return
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val csvBuilder = StringBuilder()
    csvBuilder.append("Timestamp,Domain,SourceApp,Category,IsBlocked,DetectionMethod,Reason\n")
    for (l in logs) {
        csvBuilder.append("\"${sdf.format(Date(l.timestamp))}\",\"${l.domain}\",\"${l.sourceApp}\",\"${l.category}\",\"${l.isBlocked}\",\"${l.detectionMethod}\",\"${l.reason}\"\n")
    }

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, csvBuilder.toString())
        type = "text/csv"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Export unblocker Logs")
    context.startActivity(shareIntent)
}
