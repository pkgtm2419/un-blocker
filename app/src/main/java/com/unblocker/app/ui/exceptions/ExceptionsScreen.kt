package com.unblocker.app.ui.exceptions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unblocker.app.R
import com.unblocker.app.ui.theme.AdBlockedRed
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.TealPrimary
import com.unblocker.data.db.entity.UserRuleEntity

/**
 * Full Exceptions & Rule Tester screen backed by the readable `user_rule` Room table and HMAC stores (WP-20 / F29).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExceptionsScreen(
    onNavigateBack: () -> Unit,
    onOpenLogs: () -> Unit = {},
    viewModel: ExceptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    var testQuery by rememberSaveable { mutableStateOf("") }
    var newDomainInput by rememberSaveable { mutableStateOf("") }
    var newWholeSite by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.feedbackMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.exceptions_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenLogs) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.exceptions_action),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        val activeList =
            if (state.selectedTab == ExceptionTab.ALLOW) {
                state.allowRules
            } else {
                state.blockRules
            }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Test a domain card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.exceptions_test_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = testQuery,
                                onValueChange = { testQuery = it },
                                placeholder = { Text(stringResource(R.string.exceptions_test_placeholder)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { viewModel.testDomain(testQuery) },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.exceptions_test_button))
                            }
                        }

                        state.testResult?.let { tested ->
                            val isBlocked = tested.shouldBlock
                            val accent = if (isBlocked) AdBlockedRed else AllowedGreen
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = accent.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (isBlocked) Icons.Default.Shield else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text =
                                                if (isBlocked) {
                                                    stringResource(R.string.exceptions_test_verdict_blocked)
                                                } else {
                                                    stringResource(R.string.exceptions_test_verdict_allowed)
                                                },
                                            fontWeight = FontWeight.Bold,
                                            color = accent,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            text = tested.domain,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = tested.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Legacy/hashed rules notice when unlisted HMAC entries exist
            if (state.unlistedHmacCount > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.exceptions_legacy_banner, state.unlistedHmacCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            // 3. Add exception form
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newDomainInput,
                            onValueChange = { newDomainInput = it },
                            placeholder = { Text(stringResource(R.string.exceptions_add_placeholder)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = newWholeSite,
                                onCheckedChange = { newWholeSite = it },
                            )
                            Text(
                                text = stringResource(R.string.exceptions_whole_site_checkbox),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.addRule(newDomainInput, isBlock = false, wholeSite = newWholeSite)
                                    newDomainInput = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, TealPrimary),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.exceptions_add_allow_button), color = TealPrimary)
                            }
                            Button(
                                onClick = {
                                    viewModel.addRule(newDomainInput, isBlock = true, wholeSite = newWholeSite)
                                    newDomainInput = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.exceptions_add_block_button), color = Color.White)
                            }
                        }
                    }
                }
            }

            // 4. Search & Allow/Block tabs
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text(stringResource(R.string.exceptions_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.action_clear_search),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                TabRow(selectedTabIndex = if (state.selectedTab == ExceptionTab.ALLOW) 0 else 1) {
                    Tab(
                        selected = state.selectedTab == ExceptionTab.ALLOW,
                        onClick = { viewModel.selectTab(ExceptionTab.ALLOW) },
                        text = { Text(stringResource(R.string.exceptions_tab_allow, state.totalAllowCount)) },
                    )
                    Tab(
                        selected = state.selectedTab == ExceptionTab.BLOCK,
                        onClick = { viewModel.selectTab(ExceptionTab.BLOCK) },
                        text = { Text(stringResource(R.string.exceptions_tab_block, state.totalBlockCount)) },
                    )
                }
            }

            if (activeList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text =
                                if (state.selectedTab == ExceptionTab.ALLOW) {
                                    stringResource(R.string.exceptions_empty_allow)
                                } else {
                                    stringResource(R.string.exceptions_empty_block)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(activeList, key = { "${it.domain}:${it.action}" }) { rule ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.removeRule(rule.domain)
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    ) {
                        ExceptionRuleRow(
                            rule = rule,
                            onDelete = {
                                viewModel.removeRule(rule.domain)
                                resources.getString(R.string.exceptions_rule_removed_toast, rule.domain)
                            },
                        )
                    }
                }
            }

            if (state.protectedDomains.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.exceptions_protected_title, state.protectedDomains.size),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(state.protectedDomains, key = { "protected:$it" }) { protectedHost ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = protectedHost,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.removeRule(protectedHost) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExceptionRuleRow(
    rule: UserRuleEntity,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = rule.domain,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = rule.isWholeSite,
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                text =
                                    if (rule.isWholeSite) {
                                        stringResource(R.string.exceptions_badge_whole_site)
                                    } else {
                                        stringResource(R.string.exceptions_badge_exact)
                                    },
                                fontSize = 11.sp,
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
