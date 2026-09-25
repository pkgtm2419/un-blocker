package com.unblocker.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import com.unblocker.app.services.HealthCheckService
import com.unblocker.app.services.HealthReport
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.CyanGlow
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HealthCheckDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val healthCheckService = remember { HealthCheckService(context) }

    var report by remember { mutableStateOf<HealthReport?>(null) }
    var isChecking by remember { mutableStateOf(true) }

    fun runCheck() {
        isChecking = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                healthCheckService.performHealthCheck()
            }
            report = result
            isChecking = false
        }
    }

    LaunchedEffect(Unit) {
        runCheck()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Slate900,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = CyanGlow,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "System Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isChecking) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = TealAccent)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Evaluating blocking effectiveness & integrity...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate400
                            )
                        }
                    }
                } else if (report != null) {
                    val r = report!!
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            DiagRow(label = "VPN Protection Status", value = if (r.isVpnRunning) "Running" else "Stopped", isOk = r.isVpnRunning)
                            DiagRow(label = "Database Integrity", value = if (r.isDatabaseHealthy) "Verified (OK)" else "Corrupt", isOk = r.isDatabaseHealthy)
                            DiagRow(label = "Effectiveness Benchmark", value = "${"%.1f".format(r.effectivenessScore)}%", isOk = r.effectivenessScore >= 90f)
                            DiagRow(label = "Memory Footprint", value = "${r.memoryUsageMb} MB", isOk = r.memoryUsageMb < 100)
                            DiagRow(label = "Monitored Total", value = "${r.totalConnectionsMonitored} queries", isOk = true)
                            DiagRow(label = "Last Evaluation", value = sdf.format(Date(r.timestamp)), isOk = true)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = r.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (r.isDatabaseHealthy && r.effectivenessScore >= 90f) AllowedGreen else Slate400
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { runCheck() },
                        enabled = !isChecking,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Re-test", color = TealAccent)
                    }

                    TextButton(onClick = onDismiss) {
                        Text(text = "Done", color = TealAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String, isOk: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Slate400)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isOk) AllowedGreen else Color(0xFFEF4444), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
