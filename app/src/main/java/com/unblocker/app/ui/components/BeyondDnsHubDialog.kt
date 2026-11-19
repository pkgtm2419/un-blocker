package com.unblocker.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.services.SystemHardeningChecker
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.TealPrimary

/**
 * Educational dialog explaining DNS filtering boundaries and recommended companion tools for stream ads.
 *
 * @param onDismiss Callback invoked when the user dismisses the dialog.
 */
@Composable
fun BeyondDnsHubDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Column {
                    Text(
                        text = "Beyond DNS: Streams & Feeds",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Defense-in-depth for video & in-app ads",
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
                Text(
                    text =
                        "DNS filtering blocks 80–90% of ads and telemetry before connections start. " +
                            "However, some ads share identical domains with media content. " +
                            "Here is how to defeat them at other layers:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Section 1: YouTube & Video Streams
                CompanionSectionCard(
                    icon = Icons.Default.PlayCircleOutline,
                    title = "YouTube & Media Streams",
                    whyText =
                        "Video and ads are served from the same CDN (googlevideo.com). " +
                            "Blocking DNS crashes the player.",
                    solutionText =
                        "Use Firefox + uBlock Origin for web playback, or open-source " +
                            "players (NewPipe, Tubular, ReVanced) with SponsorBlock.",
                    actionLabel = "Install Firefox",
                    onAction = {
                        runCatching {
                            context.startActivity(SystemHardeningChecker.createPlayStoreIntent("org.mozilla.firefox"))
                        }
                    },
                )

                // Section 2: Browser In-Page Cosmetic Ads
                CompanionSectionCard(
                    icon = Icons.Default.Web,
                    title = "Web Blank Frames & Popups",
                    whyText = "DNS drops ad connections, but websites leave empty blank frames behind.",
                    solutionText =
                        "Install Firefox with uBlock Origin or use Brave Browser with Shields. " +
                            "They inject CSS rules to collapse blank ad spaces and remove cookie popups.",
                    actionLabel = "Install Brave",
                    onAction = {
                        runCatching {
                            context.startActivity(SystemHardeningChecker.createPlayStoreIntent("com.brave.browser"))
                        }
                    },
                )

                // Section 3: In-Feed Social Ads (Instagram / Reddit)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Slate800 else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (isDark) Slate700 else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Social Media In-Feed Promos",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                "Instagram, Facebook, and Reddit feed ads arrive inside encrypted API " +
                                    "responses. The safest solution without account ban risk is installing them " +
                                    "as Progressive Web Apps (PWAs) inside Brave or Firefox with uBlock Origin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/**
 * Information card detailing one limitation of DNS and the corresponding browser or app solution.
 *
 * @param icon Section leading icon.
 * @param title Problem category title.
 * @param whyText Technical explanation of why DNS filtering cannot distinguish content from ads.
 * @param solutionText Recommended client-side or cosmetic solution.
 * @param actionLabel Button label for opening relevant application stores.
 * @param onAction Action invoked when clicking the companion install button.
 */
@Composable
private fun CompanionSectionCard(
    icon: ImageVector,
    title: String,
    whyText: String,
    solutionText: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) Slate800 else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (isDark) Slate700 else Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = whyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "✓ Solution: $solutionText",
                style = MaterialTheme.typography.bodySmall,
                color = TealPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            )
        }
    }
}
