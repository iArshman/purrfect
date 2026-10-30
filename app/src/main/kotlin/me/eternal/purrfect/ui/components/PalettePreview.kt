package me.eternal.purrfect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.eternal.purrfect.common.ui.ThemeMode
import me.eternal.purrfect.common.ui.theme.AppPalette

@Composable
fun ThemeBox(
    selected: Boolean,
    theme: AppPalette,
    themeMode: ThemeMode,
    isAmoled: Boolean,
    onClick: () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val baseColors = if (isDark) theme.darkColors else theme.lightColors
    val colorScheme = if (isDark && (isAmoled || themeMode == ThemeMode.AMOLED)) {
        baseColors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF14141B)
        )
    } else {
        baseColors
    }

    PalettePreview(
        colorScheme = colorScheme,
        selected = selected,
        onClick = onClick
    )
}

@Composable
fun PalettePreview(
    colorScheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(105.dp)
            .height(165.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colorScheme.background)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) colorScheme.primary else colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colorScheme.secondaryContainer)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.surfaceContainer)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.tertiaryContainer)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { idx ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (idx == 0) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}
