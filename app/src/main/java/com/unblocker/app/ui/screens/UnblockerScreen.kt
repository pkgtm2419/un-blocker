package com.unblocker.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.analysis.DeviceLearning
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.services.UnblockerVpnService
import com.unblocker.app.ui.adaptive.LocalWindowDimensions
import com.unblocker.app.ui.adaptive.WindowWidthClass
import com.unblocker.app.ui.components.AgentStatusPanel
import com.unblocker.app.ui.components.HaikeiAtmosphericBackground
import com.unblocker.app.ui.components.SkeuomorphicCard
import com.unblocker.app.ui.motion.rememberBreathingPulse
import com.unblocker.app.ui.theme.AdultBlockedPurple
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.LocalVpnStateColors
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealAccent
import com.unblocker.app.ui.theme.rememberVpnStateColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Redesigned Unblocker main screen featuring:
 *  - Motion primitives: Reusable breathing pulses, physics-based springs, fluid transitions.
 *  - Haikei-style generated background waves and organic mesh curves.
 *  - Real-time state-driven dynamic color theming.
 *  - Replit Agent-inspired stepped workflow diagnostic panel.
 *  - Skeuomorphic layout design: Realistic specular rims, tactile depth, and dual-shadow affordances.
 *  - 100% on-device, zero-log, zero-cloud architecture.
 */
@Composable
fun UnblockerScreen(
    preferences: FilteringPreferences,
    quickStartManager: QuickStartManager,
    onRequestVpnPermission: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
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
    val isPrivateDnsActive by UnblockerVpnService.isPrivateDnsActive.collectAsState()
    val privateDnsServerName by UnblockerVpnService.privateDnsServerName.collectAsState()

    LaunchedEffect(Unit) {
        UnblockerVpnService.checkPrivateDns(context)
    }

    // Dynamic state-reactive colors
    val stateColors = rememberVpnStateColors(
        serviceStatus = serviceStatus,
        isDarkTheme = isSystemInDarkTheme()
    )

    // Breathing pulse motion primitive
    val pulseGlowScale = rememberBreathingPulse(
        minScale = 0.95f,
        maxScale = 1.08f,
        durationMillis = 1800
    )

    // Press interaction state and physics-based spring bounce
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

    CompositionLocalProvider(LocalVpnStateColors provides stateColors) {
        HaikeiAtmosphericBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        horizontal = if (dimensions.widthClass == WindowWidthClass.COMPACT) 24.dp else 48.dp,
                        vertical = 32.dp
                    )
                    .padding(bottom = contentPadding.calculateBottomPadding() + 64.dp),
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

                Spacer(modifier = Modifier.height(36.dp))

                // 1. SKEUOMORPHIC CENTRAL POWER BUTTON
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(outerGlowSize)
                ) {
                    // Ambient atmospheric glow ring (morphs and breathes with state)
                    Box(
                        modifier = Modifier
                            .size(outerGlowSize)
                            .scale(if (isRunning) pulseGlowScale else 1.0f)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        stateColors.glowColor,
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // Primary Interactive Tactile Power Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(buttonSize)
                            .scale(pressScale)
                            .shadow(
                                elevation = if (isRunning) 18.dp else 8.dp,
                                shape = CircleShape,
                                ambientColor = stateColors.coreAccent,
                                spotColor = stateColors.surfaceShadow
                            )
                            .clip(CircleShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        stateColors.coreGradientStart,
                                        stateColors.coreGradientEnd
                                    )
                                )
                            )
                            .border(
                                width = if (isRunning) 3.dp else 2.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        stateColors.coreAccent,
                                        stateColors.coreAccent.copy(alpha = 0.3f)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .clickable(
                                enabled = !isStarting && !isStopping && !resetting,
                                interactionSource = interactionSource,
                                indication = ripple(bounded = true, radius = buttonSize / 2),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                    if (isRunning) {
                                        preferences.setAdBlockingEnabled(false)
                                        quickStartManager.stopBlockingServices()
                                    } else {
                                        preferences.setAdBlockingEnabled(true)
                                        quickStartManager.startBlockingServices(onPermissionRequired = onRequestVpnPermission)
                                    }
                                }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = if (isRunning) "Disable Blocker" else "Enable Blocker",
                            tint = stateColors.coreAccent,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 2. REPLIT AGENT-INSPIRED STATUS & WORKFLOW PANEL
                AgentStatusPanel(
                    serviceStatus = serviceStatus,
                    isPrivateDnsActive = isPrivateDnsActive,
                    privateDnsServerName = privateDnsServerName
                )

                // Standalone Private DNS Warning Card if detected
                if (UnblockerVpnService.shouldWarn(isPrivateDnsActive, privateDnsServerName)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Private DNS is active — encrypted queries may bypass local filtering",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFDE68A),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        })
                                    }
                                }
                            ) {
                                Text(
                                    text = "Open Network Settings",
                                    color = Color(0xFFFBBF24),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. SKEUOMORPHIC 18+ ADULT CONTENT TOGGLE
                SkeuomorphicCard(
                    modifier = Modifier.widthIn(max = 420.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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

                Spacer(modifier = Modifier.height(16.dp))

                // 4. SKEUOMORPHIC COVERAGE & LIMITATIONS CARD
                SkeuomorphicCard(
                    modifier = Modifier.widthIn(max = 420.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Coverage & limits",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand",
                                tint = Slate400
                            )
                        }
                        if (expanded) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "✅ Blocks ad-network domains in apps, games, and browsers\n" +
                                "✅ Blocks tracker, telemetry, and analytics domains\n" +
                                "✅ Blocks pop-under ad domains\n" +
                                "❌ Cannot block ads served from the same domain as the content " +
                                "(e.g. YouTube in-stream ads)\n" +
                                "❌ Cannot block JavaScript-injected ads inside browser pages " +
                                "— use a browser extension (uBlock Origin) for that\n" +
                                "❌ Cannot block ads when Private DNS strict mode is on " +
                                "(see warning above)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. SKEUOMORPHIC LOCAL DOMAIN RULE BUILDER
                SkeuomorphicCard(
                    modifier = Modifier.widthIn(max = 420.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column {
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
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = stateColors.coreAccent,
                                unfocusedBorderColor = Slate700
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                enabled = !updatingException,
                                onClick = {
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
                                }
                            ) {
                                Text("Clear all", color = Slate400)
                            }
                            Row {
                                TextButton(
                                    enabled = !updatingException,
                                    onClick = {
                                        val domain = DomainName.normalize(exceptionDomain)
                                        if (domain == null) {
                                            exceptionMessage = "Enter a valid domain name."
                                        } else {
                                            updatingException = true
                                            scope.launch {
                                                val removed = withContext(Dispatchers.IO) {
                                                    val appContext = context.applicationContext
                                                    DeviceLearning.setUserRule(appContext, domain, com.unblocker.app.logic.analysis.UserFeedback.NONE)
                                                }
                                                exceptionMessage = if (removed) "Local rule removed." else "No saved rule matched."
                                                exceptionDomain = ""
                                                updatingException = false
                                            }
                                        }
                                    }
                                ) {
                                    Text("Remove")
                                }
                                TextButton(
                                    enabled = !updatingException,
                                    onClick = {
                                        val domain = DomainName.normalize(exceptionDomain)
                                        if (domain == null) {
                                            exceptionMessage = "Enter a valid domain name."
                                        } else {
                                            updatingException = true
                                            scope.launch {
                                                val added = withContext(Dispatchers.IO) {
                                                    val appContext = context.applicationContext
                                                    DeviceLearning.setUserRule(appContext, domain, com.unblocker.app.logic.analysis.UserFeedback.BLOCK)
                                                }
                                                exceptionMessage = if (added) "Block rule saved locally." else "Block rule already saved."
                                                exceptionDomain = ""
                                                updatingException = false
                                            }
                                        }
                                    }
                                ) {
                                    Text("Block", color = Color(0xFFEF4444))
                                }
                                TextButton(
                                    enabled = !updatingException,
                                    onClick = {
                                        val domain = DomainName.normalize(exceptionDomain)
                                        if (domain == null) {
                                            exceptionMessage = "Enter a valid domain name."
                                        } else {
                                            updatingException = true
                                            scope.launch {
                                                val added = withContext(Dispatchers.IO) {
                                                    val appContext = context.applicationContext
                                                    DeviceLearning.setUserRule(appContext, domain, com.unblocker.app.logic.analysis.UserFeedback.ALLOW)
                                                }
                                                exceptionMessage = if (added) "Allow rule saved locally." else "Allow rule already saved."
                                                exceptionDomain = ""
                                                updatingException = false
                                            }
                                        }
                                    }
                                ) {
                                    Text("Allow", color = AllowedGreen)
                                }
                            }
                        }
                        if (exceptionMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(exceptionMessage, style = MaterialTheme.typography.bodySmall, color = Slate400)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Minimal Privacy Footnote & System Links
                Text(
                    text = "Learning stays on this device. No telemetry.\nAllowed DNS queries go to your resolver.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                }) {
                    Text("Always-on VPN settings")
                }
                Text(
                    text = "Enable Always-on VPN for system-managed restart. Leave “Block connections without VPN” off: this app routes DNS only.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Slate400
                )
                TextButton(
                    enabled = !isRunning && !isStarting && !isStopping && !resetting,
                    onClick = {
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
                    }
                ) {
                    Text("Clear local learning (stop protection first)")
                }
                if (resetMessage.isNotEmpty()) {
                    Text(resetMessage, style = MaterialTheme.typography.bodySmall, color = Slate400)
                }
            }
        }
    }
}
