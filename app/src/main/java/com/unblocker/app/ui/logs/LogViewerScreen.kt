package com.unblocker.app.ui.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unblocker.data.db.entity.DnsLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GroupedLogEntry(
    val domain: String,
    val count: Int,
    val latestTimestamp: Long,
    val isBlocked: Boolean
)

@Composable
fun LogViewerScreen(
    viewModel: LogViewModel = hiltViewModel()
) {
    val logs by viewModel.recentLogs.collectAsState()
    
    val groupedLogs = remember(logs) {
        logs.groupBy { it.domain }.map { (domain, logsForDomain) ->
            val latestLog = logsForDomain.maxByOrNull { it.timestamp } ?: logsForDomain.first()
            GroupedLogEntry(
                domain = domain,
                count = logsForDomain.size,
                latestTimestamp = latestLog.timestamp,
                isBlocked = latestLog.isBlocked
            )
        }.sortedByDescending { it.latestTimestamp }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Recently Intercepted Connections",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        if (groupedLogs.isEmpty()) {
            Text("No network logs found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(groupedLogs) { log ->
                    LogEntryRow(
                        log = log,
                        onWhitelistClick = { viewModel.unblockDomain(log.domain) }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun LogEntryRow(
    log: GroupedLogEntry,
    onWhitelistClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(log.latestTimestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.domain,
                style = MaterialTheme.typography.bodyLarge,
                color = if (log.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$formattedTime • ${if (log.isBlocked) "BLOCKED" else "ALLOWED"} • Calls: ${log.count}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (log.isBlocked) {
            Button(
                onClick = onWhitelistClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            ) {
                Text("Allow")
            }
        }
    }
}
