package com.unblocker.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.provider.Settings
import com.unblocker.app.logic.analysis.DeviceLearning
import com.unblocker.app.logic.DomainName
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.ui.adaptive.LocalWindowDimensions
import com.unblocker.app.ui.adaptive.WindowWidthClass
import com.unblocker.app.ui.theme.AdultBlockedPurple
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.AllowedGreenBg
import com.unblocker.app.ui.theme.CyanGlow
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealAccent

/**
 * Ultra-clean, distraction-free main screen.
 * Features:
 *  1. One large interactive central power icon for enabling/disabling the blocker with haptic feedback & bounce animation.
 *  2. Directly below: Real-time status indicator pill.
 *  3. Directly below: Clean, compact toggle for blocking 18+ content.
 *  4. 100% on-device, zero-log, zero-cloud architecture.
 */
@Composable
fun UnblockerScreen(
    preferences: FilteringPreferences,
    quickStartManager: QuickStartManager,
    onRequestVpnPermission: () -> Unit
) {
    val dimensions = LocalWindowDimensions.current
    val haptic = LocalHapticFeedback.current

    val serviceStatus by quickStartManager.status.collectAsState()
    val isRunning = serviceStatus == ServiceStatus.RUNNING
    val isStarting = serviceStatus == ServiceStatus.STARTING
    val isStopping = serviceStatus == ServiceStatus.STOPPING
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resetMessage by remember { mutableStateOf("") }
    var resetting by remember { mutableStateOf(false) }
    var exceptionDomain by remember { mutableStateOf("") }
    var exceptionMessage by remember { mutableStateOf("") }
    var updatingException by remember { mutableStateOf(false) }

    val adultBlockingEnabled by preferences.adultBlockingEnabled.collectAsState()

    // Smooth color transitions
    val buttonGlowColor by animateColorAsState(
        targetValue = if (isRunning) AllowedGreen.copy(alpha = 0.35f) else Color.Transparent,
        animationSpec = tween(500),
        label = "glowColor"
    )
    val buttonBorderColor by animateColorAsState(
        targetValue = if (isRunning) AllowedGreen else Slate700,
        animationSpec = tween(400),
        label = "borderColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isRunning) AllowedGreen else Slate400,
        animationSpec = tween(400),
        label = "iconColor"
    )

    // Gentle breathing pulse animation when protection is active
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    // Press interaction state and responsive spring bounce animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressScale"
    )

    val buttonSize = if (dimensions.widthClass == WindowWidthClass.COMPACT) 140.dp else 160.dp
    val outerGlowSize = if (dimensions.widthClass == WindowWidthClass.COMPACT) 200.dp else 230.dp
    val iconSize = if (dimensions.widthClass == WindowWidthClass.COMPACT) 68.dp else 78.dp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    horizontal = if (dimensions.widthClass == WindowWidthClass.COMPACT) 24.dp else 48.dp,
                    vertical = 36.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Minimal Header Branding with App Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = com.unblocker.app.R.drawable.app_logo),
                    contentDescription = "unblocker logo",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "unblocker",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "on-device network shield",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                color = TealAccent
            )

            Spacer(modifier = Modifier.height(44.dp))

            // 1. ONE BIG ICON FOR ENABLING / DISABLING THE BLOCKER
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(outerGlowSize)
            ) {
                // Background ambient glow ring (pulses when active)
                Box(
                    modifier = Modifier
                        .size(outerGlowSize)
                        .scale(if (isRunning) pulseGlowScale else 1.0f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    buttonGlowColor,
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // The Primary Interactive Power Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(buttonSize)
                        .scale(pressScale)
                        .shadow(
                            elevation = if (isRunning) 16.dp else 6.dp,
                            shape = CircleShape,
                            ambientColor = if (isRunning) AllowedGreen else Color.Black,
                            spotColor = if (isRunning) AllowedGreen else Color.Black
                        )
                        .clip(CircleShape)
                        .background(
                            brush = if (isRunning) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF064E3B),
                                        Slate900
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Slate800,
                                        Slate900
                                    )
                                )
                            }
                        )
                        .border(
                            width = if (isRunning) 3.dp else 2.dp,
                            color = buttonBorderColor,
                            shape = CircleShape
                        )
                        .clickable(
                            enabled = !isStarting && !isStopping && !resetting,
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true, radius = buttonSize / 2),
                            onClick = {
                                // Satisfying tactile haptic vibration
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                if (isRunning) {
                                    // Turn OFF
                                    preferences.setAdBlockingEnabled(false)
                                    quickStartManager.stopBlockingServices()
                                } else {
                                    // Turn ON
                                    preferences.setAdBlockingEnabled(true)
                                    quickStartManager.startBlockingServices(onPermissionRequired = onRequestVpnPermission)
                                }
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = if (isRunning) "Disable Blocker" else "Enable Blocker",
                        tint = iconColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 2. DIRECTLY BELOW: REAL-TIME STATUS INDICATOR
            Surface(
                color = if (isRunning) AllowedGreenBg else Slate800,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isRunning) AllowedGreen.copy(alpha = 0.5f) else Slate700
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(
                                color = if (isRunning) AllowedGreen else Slate400,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    val daysActive = preferences.getDaysSinceInstall()
                    val activeStatusText = if (daysActive < 14) {
                        "SHIELD ACTIVE • DAY $daysActive LEARNING"
                    } else {
                        "SHIELD ACTIVE • AUTONOMOUS SHIELD"
                    }

                    Text(
                        text = when (serviceStatus) {
                            ServiceStatus.RUNNING -> activeStatusText
                            ServiceStatus.STARTING -> "STARTING PROTECTION…"
                            ServiceStatus.STOPPING -> "STOPPING PROTECTION…"
                            ServiceStatus.ERROR -> "CONNECTION FAILED • TAP TO RETRY"
                            ServiceStatus.STOPPED -> "SHIELD IDLE • TAP ICON TO START"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = if (isRunning) AllowedGreen else Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // 3. DIRECTLY BELOW: SMALL TOGGLE FOR BLOCK 18+ CONTENT
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Block 18+ Content",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Filter adult websites & explicit domains",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Switch(
                        checked = adultBlockingEnabled,
                        onCheckedChange = { enable ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            preferences.setAdultBlockingEnabled(enable)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AdultBlockedPurple,
                            uncheckedThumbColor = Slate400,
                            uncheckedTrackColor = Slate800
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        text = "Local domain rule",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Allow or block one exact domain. Only device-keyed fingerprints are saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = exceptionDomain,
                        onValueChange = { exceptionDomain = it; exceptionMessage = "" },
                        enabled = !updatingException,
                        singleLine = true,
                        label = { Text("example.com") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(enabled = !updatingException, onClick = {
                            updatingException = true
                            scope.launch {
                                exceptionMessage = withContext(Dispatchers.IO) {
                                    try {
                                        DeviceLearning.clearRules(context.applicationContext)
                                        "All local rules cleared."
                                    } catch (_: Exception) { "Could not clear local rules. Please retry." }
                                }
                                exceptionDomain = ""
                                updatingException = false
                            }
                        }) { Text("Clear all") }
                        Row {
                            TextButton(enabled = !updatingException, onClick = {
                                val domain = DomainName.normalize(exceptionDomain)
                                if (domain == null) {
                                    exceptionMessage = "Enter a valid domain name."
                                } else {
                                    updatingException = true
                                    scope.launch {
                                        val removed = withContext(Dispatchers.IO) {
                                            val appContext = context.applicationContext
                                            val allowed = DeviceLearning.allowlist(appContext).remove(domain)
                                            val blocked = DeviceLearning.blocklist(appContext).remove(domain)
                                            allowed || blocked
                                        }
                                        exceptionMessage = if (removed) "Local rule removed." else "No saved rule matched."
                                        exceptionDomain = ""
                                        updatingException = false
                                    }
                                }
                            }) { Text("Remove") }
                            TextButton(enabled = !updatingException, onClick = {
                                val domain = DomainName.normalize(exceptionDomain)
                                if (domain == null) {
                                    exceptionMessage = "Enter a valid domain name."
                                } else {
                                    updatingException = true
                                    scope.launch {
                                        val added = withContext(Dispatchers.IO) {
                                            val appContext = context.applicationContext
                                            DeviceLearning.allowlist(appContext).remove(domain)
                                            DeviceLearning.blocklist(appContext).add(domain)
                                        }
                                        exceptionMessage = if (added) "Block rule saved locally." else "Block rule already saved."
                                        exceptionDomain = ""
                                        updatingException = false
                                    }
                                }
                            }) { Text("Block") }
                            TextButton(enabled = !updatingException, onClick = {
                                val domain = DomainName.normalize(exceptionDomain)
                                if (domain == null) {
                                    exceptionMessage = "Enter a valid domain name."
                                } else {
                                    updatingException = true
                                    scope.launch {
                                        val added = withContext(Dispatchers.IO) {
                                            val appContext = context.applicationContext
                                            DeviceLearning.blocklist(appContext).remove(domain)
                                            DeviceLearning.allowlist(appContext).add(domain)
                                        }
                                        exceptionMessage = if (added) "Allow rule saved locally." else "Allow rule already saved."
                                        exceptionDomain = ""
                                        updatingException = false
                                    }
                                }
                            }) { Text("Allow") }
                        }
                    }
                    if (exceptionMessage.isNotEmpty()) {
                        Text(exceptionMessage, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Minimal Privacy Footnote
            Text(
                text = "Learning stays on this device. No telemetry.\nAllowed DNS queries go to your resolver.",
                style = MaterialTheme.typography.labelSmall,
                color = Slate400.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            }) { Text("Always-on VPN settings") }
            Text(
                "Enable Always-on VPN for system-managed restart. Leave “Block connections without VPN” off: this app routes DNS only.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            TextButton(enabled = !isRunning && !isStarting && !isStopping && !resetting, onClick = {
                resetting = true
                scope.launch {
                    resetMessage = withContext(Dispatchers.IO) {
                        try {
                            DeviceLearning.clear(context.applicationContext)
                            preferences.resetLearning()
                            "Local learning cleared."
                        } catch (_: Exception) { "Could not clear learning. Please retry." }
                    }
                    resetting = false
                }
            }) { Text("Clear local learning (stop protection first)") }
            if (resetMessage.isNotEmpty()) Text(resetMessage, style = MaterialTheme.typography.bodySmall)
        }
    }
}
