package com.unblocker.app.ui.extensions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unblocker.app.data.model.ExtensionFilter
import com.unblocker.app.ui.theme.CyanGlow
import com.unblocker.app.ui.theme.Slate300
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.Slate950
import com.unblocker.app.ui.theme.TealAccent
import com.unblocker.app.ui.theme.TealPrimary
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExtensionsViewModel = viewModel()
) {
    BackHandler { onNavigateBack() }

    val filteredExtensions by viewModel.filteredExtensions.collectAsState()
    val activeFilterCount by viewModel.activeFilterCount.collectAsState()
    val isUpdating by viewModel.isUpdating.collectAsState()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Infinite rotation animation when updating
    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    Scaffold(
        containerColor = Slate950,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = { Text("Search filters…", color = Slate400, fontSize = 14.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = TealAccent,
                                unfocusedBorderColor = Slate700,
                                cursorColor = TealAccent
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.onSearchQueryChange("")
                                    isSearchActive = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search", tint = Slate400)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        )
                    } else {
                        Text(
                            text = "Hosts & filters",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to home",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search filters", tint = Color.White)
                        }
                    }

                    // Refresh / Update all button
                    IconButton(
                        onClick = { viewModel.updateAll() },
                        enabled = !isUpdating
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Update all filters from internet",
                            tint = if (isUpdating) TealAccent else Color.White,
                            modifier = if (isUpdating) Modifier.rotate(spinRotation) else Modifier
                        )
                    }

                    // Add Custom Filter button
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add custom filter", tint = Color.White)
                    }

                    // Overflow Menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Slate900)
                        ) {
                            // 1. Auto-update extensions checkbox option
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Auto-update extensions",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Checkbox(
                                            checked = autoUpdateEnabled,
                                            onCheckedChange = { checked ->
                                                viewModel.setAutoUpdateEnabled(checked)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = TealAccent,
                                                checkmarkColor = Slate950,
                                                uncheckedColor = Slate400
                                            )
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setAutoUpdateEnabled(!autoUpdateEnabled)
                                }
                            )

                            // 2. Update all extensions
                            DropdownMenuItem(
                                text = { Text("Update all extensions", color = Color.White, fontSize = 14.sp) },
                                onClick = {
                                    showMenu = false
                                    viewModel.updateAll()
                                }
                            )

                            // 3. Enable all
                            DropdownMenuItem(
                                text = { Text("Enable all", color = Color.White, fontSize = 14.sp) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setAllEnabled(true)
                                }
                            )

                            // 4. Disable all
                            DropdownMenuItem(
                                text = { Text("Disable all", color = Color.White, fontSize = 14.sp) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setAllEnabled(false)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate950
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.updateAll() },
                containerColor = TealPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save & Sync Filters",
                    tint = Color.White
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Subheader: Total active filters
            Text(
                text = "Total active filters: $activeFilterCount",
                fontSize = 13.sp,
                color = Slate400,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Extension Lists
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredExtensions, key = { it.id }) { item ->
                    ExtensionCard(
                        item = item,
                        onToggle = { enabled -> viewModel.toggleExtension(item.id, enabled) },
                        onUpdate = { viewModel.updateExtension(item.id) },
                        onDelete = { viewModel.removeCustomFilter(item.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    // Dialog for adding custom filter
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add custom filter", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Filter Name", color = Slate400) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = Slate700
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Filter URL (optional)", color = Slate400) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = Slate700
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.addCustomFilter(name.trim(), url.trim())
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add", color = TealAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Slate400)
                }
            },
            containerColor = Slate900
        )
    }
}

@Composable
fun ExtensionCard(
    item: ExtensionFilter,
    onToggle: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    var showItemMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Slate900,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate800, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Icon (Pencil for custom / manual filters, Globe for others)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Slate800, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isCustom) Icons.Default.Edit else Icons.Default.Public,
                    contentDescription = null,
                    tint = Slate300,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (item.isCustom && item.filterCount == 0 && item.sizeBytes == 0L) {
                        "Filters: 0   N/A"
                    } else {
                        "Filters: ${item.filterCount}   Size: ${item.formattedSize()}"
                    },
                    color = Slate400,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.formattedLastUpdated(),
                    color = Slate400,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Switch toggle
            Switch(
                checked = item.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2DD4BF),
                    checkedTrackColor = Color(0xFF115E59),
                    uncheckedThumbColor = Slate400,
                    uncheckedTrackColor = Slate800
                )
            )

            // Item overflow menu
            Box {
                IconButton(
                    onClick = { showItemMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Item actions",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showItemMenu,
                    onDismissRequest = { showItemMenu = false },
                    modifier = Modifier.background(Slate900)
                ) {
                    if (item.url.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("Update", color = Color.White) },
                            onClick = {
                                showItemMenu = false
                                onUpdate()
                            }
                        )
                    }
                    if (item.isCustom) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFEF4444)) },
                            onClick = {
                                showItemMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
