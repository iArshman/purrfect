package me.eternal.purrfect.common.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import me.eternal.purrfect.common.ui.theme.Themes

@Composable
fun AppMaterialTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("appearance_preferences", Context.MODE_PRIVATE)
    }

    val themeName by rememberMutableStringPreferenceState(
        sharedPreferences = sharedPreferences,
        key = "app_theme",
        defaultValue = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "dynamic" else "default"
    )
    val amoledMode by rememberMutableBooleanPreferenceState(
        sharedPreferences = sharedPreferences,
        key = "amoled_theme",
        defaultValue = false
    )
    val themeModePref by rememberMutableStringPreferenceState(
        sharedPreferences = sharedPreferences,
        key = "theme_mode",
        defaultValue = ThemeMode.SYSTEM.name
    )

    val effectiveThemeMode = themeMode ?: runCatching { ThemeMode.valueOf(themeModePref) }.getOrDefault(ThemeMode.SYSTEM)
    val useDarkTheme = when (effectiveThemeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isDarkTheme
    }
    val useAmoled = amoledMode || effectiveThemeMode == ThemeMode.AMOLED

    val palettes = remember(context) { Themes.getPalettes(context) }
    val selectedPalette = palettes.find { it.key == themeName } ?: palettes.first()

    val baseScheme: ColorScheme = if (useDarkTheme) {
        selectedPalette.darkColors
    } else {
        selectedPalette.lightColors
    }

    val colorScheme = if (useDarkTheme && useAmoled) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0D0D12),
            surfaceContainer = Color(0xFF14141B),
            surfaceContainerHigh = Color(0xFF1D1D26),
            surfaceContainerHighest = Color(0xFF262632),
        )
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.let { activity ->
                updateSystemBars(activity, useDarkTheme)
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private fun updateSystemBars(activity: Activity, isDarkTheme: Boolean) {
    val window = activity.window
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.isAppearanceLightStatusBars = !isDarkTheme
    insetsController.isAppearanceLightNavigationBars = !isDarkTheme

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}
