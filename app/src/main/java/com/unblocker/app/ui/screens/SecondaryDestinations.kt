package com.unblocker.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Destination for viewing and testing user allow/block rules and My domains exceptions (WP-11; full CRUD in WP-20).
 */
@Composable
fun ExceptionsScreen(
    onNavigateBack: () -> Unit,
    onOpenLogs: () -> Unit = {},
) {
    SecondaryDestinationScaffold(
        title = "Exceptions & Rules",
        icon = Icons.AutoMirrored.Filled.Rule,
        headline = "User Rules & Protected Domains",
        description =
            "View and manage custom Allow/Block overrides, My domains protected sites, " +
                "and evaluate domains with the rule tester. You can also manage exceptions directly per domain in Logs.",
        primaryActionLabel = "Open Logs to Filter Domains",
        onPrimaryAction = onOpenLogs,
        onNavigateBack = onNavigateBack,
    )
}

/**
 * Destination for local query statistics and rule-source attribution (WP-11; full charts in WP-40).
 */
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    onOpenLogs: () -> Unit = {},
) {
    SecondaryDestinationScaffold(
        title = "Statistics",
        icon = Icons.Default.BarChart,
        headline = "On-Device Protection Summary",
        description =
            "Daily query trends, block rate percentages, top blocked domains, and per-list hit counters " +
                "computed strictly on your device within local retention limits.",
        primaryActionLabel = "View Detailed Domain Logs",
        onPrimaryAction = onOpenLogs,
        onNavigateBack = onNavigateBack,
    )
}

/**
 * Destination for the guided onboarding flow (WP-11; 7-step setup in WP-41).
 */
@Composable
fun OnboardingScreen(
    onNavigateBack: () -> Unit,
    onOpenExtensions: () -> Unit = {},
) {
    SecondaryDestinationScaffold(
        title = "Guided Setup",
        icon = Icons.Default.RocketLaunch,
        headline = "Welcome to Un-Blocker",
        description =
            "On-device DNS protection with zero cloud servers and zero telemetry. " +
                "Choose a curated filter preset in Extensions or start protection from Home.",
        primaryActionLabel = "Browse Curated Presets",
        onPrimaryAction = onOpenExtensions,
        onNavigateBack = onNavigateBack,
    )
}

/**
 * Dedicated About destination showing dynamic package metadata, privacy contract, and source links (WP-11).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val versionName =
        remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "2.7.2"
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Un-Blocker", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Un-Blocker v$versionName",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "100% On-Device • Zero-Cloud • Zero-Telemetry",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text =
                            "Un-Blocker inspects only DNS queries locally through Android's VpnService " +
                                "without inspecting HTTPS bodies or transmitting telemetry off your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/pkgtm2419/un-blocker"),
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            },
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("GitHub Repository & Apache-2.0 License")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryDestinationScaffold(
    title: String,
    icon: ImageVector,
    headline: String,
    description: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = headline, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onPrimaryAction,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(primaryActionLabel)
            }
        }
    }
}
