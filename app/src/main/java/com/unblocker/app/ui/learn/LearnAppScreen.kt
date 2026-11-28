package com.unblocker.app.ui.learn

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unblocker.app.R

/**
 * Renders app selection, a DNS recording session, and domain-rule review.
 *
 * @param onNavigateBack Callback used when leaving the app-selection state.
 * @param viewModel Owner of app discovery, session state, and reviewed rule application.
 * @param contentPadding Parent content insets added to the scaffold padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnAppScreen(
    onNavigateBack: () -> Unit,
    viewModel: LearnViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    BackHandler {
        when (state) {
            is LearnUiState.Recording -> viewModel.cancelSession()
            is LearnUiState.Reviewing -> viewModel.cancelSession()
            is LearnUiState.SelectingApp -> onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            when (state) {
                                is LearnUiState.SelectingApp -> stringResource(R.string.learn_topbar_selecting)
                                is LearnUiState.Recording -> stringResource(R.string.learn_topbar_recording)
                                is LearnUiState.Reviewing -> stringResource(R.string.learn_topbar_reviewing)
                            },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (state) {
                            is LearnUiState.Recording -> viewModel.cancelSession()
                            is LearnUiState.Reviewing -> viewModel.cancelSession()
                            is LearnUiState.SelectingApp -> onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(contentPadding),
        ) {
            when (val s = state) {
                is LearnUiState.SelectingApp -> {
                    AppSelectionContent(
                        apps = s.filteredApps,
                        query = s.query,
                        isLoading = s.isLoading,
                        onQueryChange = viewModel::searchApps,
                        onSelectApp = viewModel::startRecording,
                    )
                }

                is LearnUiState.Recording -> {
                    RecordingContent(
                        app = s.app,
                        elapsedSeconds = s.elapsedSeconds,
                        domains = s.recordedDomains,
                        onOpenApp = {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(s.app.packageName)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        },
                        onStopRecording = viewModel::stopRecording,
                    )
                }

                is LearnUiState.Reviewing -> {
                    ReviewDomainsContent(
                        app = s.app,
                        domains = s.domains,
                        appliedSuccess = s.appliedSuccess,
                        onSetAction = viewModel::setDomainAction,
                        onToggleWholeSite = viewModel::setAllowWholeSite,
                        onSelectAllListMatches = viewModel::selectAllListMatches,
                        onApplyRules = viewModel::applyRules,
                        onDone = viewModel::cancelSession,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSelectionContent(
    apps: List<AppItem>,
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectApp: (AppItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.learn_pick_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.learn_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.learn_loading_apps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (apps.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (query.isEmpty()) "No installed launcher apps found" else "No matching apps found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectApp(app) },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Button(
                                onClick = { onSelectApp(app) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.learn_action_learn), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingContent(
    app: AppItem,
    elapsedSeconds: Int,
    domains: List<com.unblocker.app.services.SessionDomain>,
    onOpenApp: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val context = LocalContext.current
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeFormatted = "%02d:%02d".format(minutes, seconds)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.learn_isolation_banner, app.label),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.learn_recording_title, app.label),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.learn_time_elapsed, timeFormatted),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenApp,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Launch,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.learn_open_app, app.label))
                    }
                    OutlinedButton(
                        onClick = onStopRecording,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.learn_stop_review))
                    }
                }
            }
        }

        if (elapsedSeconds >= 60 && domains.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.learn_idle_encrypted_title),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "https://github.com/pkgtm2419/un-blocker/blob/main/docs/dns-bypass-matrix.md",
                                        ),
                                    ).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    },
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.learn_idle_encrypted_link),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.learn_captured_domains, domains.size),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (domains.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.learn_waiting_queries, app.label, app.label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(domains, key = { it.domain }) { d ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = d.domain,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.learn_domain_count, d.count),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewDomainsContent(
    app: AppItem,
    domains: List<ReviewedDomain>,
    appliedSuccess: Boolean,
    onSetAction: (String, ReviewAction) -> Unit,
    onToggleWholeSite: (String, Boolean) -> Unit,
    onSelectAllListMatches: () -> Unit,
    onApplyRules: () -> Unit,
    onDone: () -> Unit,
) {
    val trackerCount = domains.count { it.category == DomainCategory.TRACKER_AD }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.learn_results_title, app.label),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.learn_results_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trackerCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSelectAllListMatches,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(stringResource(R.string.learn_select_all_trackers, trackerCount), fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (appliedSuccess) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.learn_rules_saved_toast),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(domains, key = { it.domain }) { item ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = item.domain,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val catBadge =
                                when (item.category) {
                                    DomainCategory.FIRST_PARTY -> stringResource(R.string.learn_cat_first_party)
                                    DomainCategory.CDN_INFRA -> stringResource(R.string.learn_cat_cdn_infra)
                                    DomainCategory.TRACKER_AD -> stringResource(R.string.learn_cat_tracker_ad)
                                    DomainCategory.OTHER -> stringResource(R.string.learn_cat_other)
                                }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color =
                                    when (item.category) {
                                        DomainCategory.FIRST_PARTY -> {
                                            MaterialTheme.colorScheme.primary.copy(
                                                alpha = 0.15f,
                                            )
                                        }

                                        DomainCategory.CDN_INFRA -> {
                                            MaterialTheme.colorScheme.secondary.copy(
                                                alpha = 0.15f,
                                            )
                                        }

                                        DomainCategory.TRACKER_AD -> {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                        }

                                        DomainCategory.OTHER -> {
                                            MaterialTheme.colorScheme.outlineVariant.copy(
                                                alpha = 0.3f,
                                            )
                                        }
                                    },
                            ) {
                                Text(
                                    text = catBadge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        if (item.alsoSeenIn.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.learn_also_seen_in, item.alsoSeenIn.joinToString(", ")),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (item.pendingAllowConfirmation && item.allowWarningMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = item.allowWarningMessage,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = item.action == ReviewAction.ALLOW,
                                onClick = { onSetAction(item.domain, ReviewAction.ALLOW) },
                                label = {
                                    Text(
                                        text =
                                            if (item.pendingAllowConfirmation) {
                                                stringResource(R.string.learn_choice_confirm_allow)
                                            } else {
                                                stringResource(R.string.learn_choice_allow)
                                            },
                                        fontSize = 11.sp,
                                    )
                                },
                            )
                            FilterChip(
                                selected = item.action == ReviewAction.BLOCK,
                                onClick = { onSetAction(item.domain, ReviewAction.BLOCK) },
                                label = { Text(stringResource(R.string.learn_choice_block), fontSize = 11.sp) },
                            )
                            FilterChip(
                                selected = item.action == ReviewAction.KEEP_DEFAULT,
                                onClick = { onSetAction(item.domain, ReviewAction.KEEP_DEFAULT) },
                                label = { Text(stringResource(R.string.learn_choice_default), fontSize = 11.sp) },
                            )
                        }

                        if (item.action == ReviewAction.ALLOW) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text =
                                        if (item.canAllowWholeSite) {
                                            stringResource(R.string.learn_whole_site_toggle)
                                        } else {
                                            stringResource(R.string.learn_shared_platform_note)
                                        },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Switch(
                                    checked = item.allowWholeSite,
                                    enabled = item.canAllowWholeSite,
                                    onCheckedChange = { checked ->
                                        onToggleWholeSite(item.domain, checked)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onApplyRules,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.learn_apply_rules))
            }
            OutlinedButton(
                onClick = onDone,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}
