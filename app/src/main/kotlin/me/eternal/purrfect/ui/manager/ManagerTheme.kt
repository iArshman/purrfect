package me.eternal.purrfect.ui.manager

import androidx.compose.runtime.Composable
import me.eternal.purrfect.common.ui.AppMaterialTheme
import me.eternal.purrfect.common.ui.ThemeMode

@Composable
fun ManagerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    AppMaterialTheme(themeMode = themeMode, content = content)
}
