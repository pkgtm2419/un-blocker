package com.unblocker.app.ui.logs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.R
import com.unblocker.app.ui.theme.AdBlockedRed
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealPrimary
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modal dialog presenting a structured table view of connection metrics and timestamped DNS query events.
 *
 * @param stat Aggregate metrics for the domain connection.
 * @param events Individual timestamped query events observed for the domain.
 * @param onDismiss Callback invoked when the user closes or dismisses the dialog.
 */
@Composable
fun ConnectionHistoryDialog(
    stat: DomainStatEntity,
    events: List<DomainEventEntity>,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val fullDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = CircleShape,
                    color = TealPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stat.domain,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.logs_history_dialog_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Connection Overview Summary Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Slate800 else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (isDark) Slate700 else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.logs_history_overview),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MetricCell(label = stringResource(R.string.logs_history_total_queries), value = "${stat.callCount}")
                            MetricCell(label = stringResource(R.string.logs_filter_allowed), value = "${stat.allowedCount}", color = AllowedGreen)
                            MetricCell(label = stringResource(R.string.logs_filter_blocked), value = "${stat.blockedCount}", color = AdBlockedRed)
                            val blockRate =
                                if (stat.callCount > 0) {
                                    stat.blockedCount * 100 / stat.callCount
                                } else {
                                    0
                                }
                            MetricCell(label = stringResource(R.string.logs_history_block_rate), value = "$blockRate%")
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = if (isDark) Slate700 else Color(0xFFE2E8F0),
                        )

                        val firstSeenText =
                            if (stat.firstSeen > 0) {
                                shortDateFormat.format(Date(stat.firstSeen))
                            } else {
                                stringResource(R.string.logs_history_unknown)
                            }
                        val lastSeenText =
                            if (stat.lastSeen > 0) {
                                shortDateFormat.format(Date(stat.lastSeen))
                            } else {
                                stringResource(R.string.logs_history_unknown)
                            }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.logs_history_first_seen, firstSeenText),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.logs_history_last_seen, lastSeenText),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Table View of Events
                Text(
                    text = stringResource(R.string.logs_history_table_title, events.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (events.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Slate900 else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, if (isDark) Slate700 else Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.logs_history_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    // Table Layout Container
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isDark) Slate700 else Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Table Header Row
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(if (isDark) Slate800 else Color(0xFFE2E8F0))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "#",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(26.dp),
                                )
                                Text(
                                    text = stringResource(R.string.logs_history_col_datetime),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = stringResource(R.string.logs_history_col_decision),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(68.dp),
                                    textAlign = TextAlign.End,
                                )
                            }

                            HorizontalDivider(color = if (isDark) Slate700 else Color(0xFFCBD5E1))

                            // Table Data Rows
                            events.forEachIndexed { index, event ->
                                val isEven = index % 2 == 0
                                val rowBg =
                                    if (isEven) {
                                        if (isDark) Slate900 else Color.White
                                    } else {
                                        if (isDark) Slate800.copy(alpha = 0.5f) else Color(0xFFF8FAFC)
                                    }

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(rowBg)
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(26.dp),
                                    )
                                    Text(
                                        text = fullDateFormat.format(Date(event.ts)),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val isBlocked = event.decision == 1
                                    val badgeColor = if (isBlocked) AdBlockedRed else AllowedGreen
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = badgeColor.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
                                        modifier = Modifier.width(68.dp),
                                    ) {
                                        Text(
                                            text =
                                                if (isBlocked) {
                                                    stringResource(R.string.logs_history_decision_blocked)
                                                } else {
                                                    stringResource(R.string.logs_history_decision_allowed)
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp,
                                            color = badgeColor,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                        )
                                    }
                                }
                                if (index < events.lastIndex) {
                                    HorizontalDivider(
                                        color = if (isDark) Slate800 else Color(0xFFF1F5F9),
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_close),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/**
 * Small KPI metric cell in the connection summary table.
 *
 * @param label Metric descriptor.
 * @param value Computed metric value.
 * @param color Text color for the metric value.
 */
@Composable
private fun MetricCell(
    label: String,
    value: String,
    color: Color = Color.Unspecified,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
