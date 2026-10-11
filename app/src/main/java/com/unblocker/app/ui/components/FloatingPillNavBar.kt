package com.unblocker.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealAccent
import com.unblocker.app.ui.theme.TealPrimary

data class NavItem(
    val title: String,
    val icon: ImageVector
)

/**
 * Floating pill navigation bar with vertically stacked icons and text.
 * Styled with theme-matched teal accents and tactile spring interaction.
 */
@Composable
fun FloatingPillNavBar(
    items: List<NavItem>,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val navBarsInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomMargin = maxOf(16.dp, navBarsInset + 12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = bottomMargin),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = CircleShape,
            color = if (isDark) Slate900.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .widthIn(min = 220.dp, max = 340.dp)
                .height(68.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = if (isDark) listOf(
                            Color.White.copy(alpha = 0.20f),
                            Slate700.copy(alpha = 0.50f)
                        ) else listOf(
                            Color.White,
                            Color(0xFFCBD5E1)
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = selectedItem == index

                    // Brand-aligned container color matching the theme
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            if (isDark) Color(0xFF115E59) else Color(0xFFCCFBF1)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "pillNavContainerColor"
                    )

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            if (isDark) Color(0xFFF0FDFA) else Color(0xFF0F766E)
                        } else {
                            if (isDark) Slate400 else Color(0xFF64748B)
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "pillNavContentColor"
                    )

                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            if (isDark) TealAccent.copy(alpha = 0.65f) else TealPrimary.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        },
                        label = "pillNavBorderColor"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(containerColor)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = { onItemSelected(index) }
                            )
                            .semantics { role = Role.Tab },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.title,
                                color = contentColor,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
