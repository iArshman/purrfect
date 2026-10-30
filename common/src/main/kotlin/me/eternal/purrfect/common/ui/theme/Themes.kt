package me.eternal.purrfect.common.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import me.eternal.purrfect.common.ui.theme.palettes.GreenApple
import me.eternal.purrfect.common.ui.theme.palettes.Monochrome
import me.eternal.purrfect.common.ui.theme.palettes.PurrAurora
import me.eternal.purrfect.common.ui.theme.palettes.StrawberryDaiquiri
import me.eternal.purrfect.common.ui.theme.palettes.TidalWave

object Themes {
    private val staticPalettes = listOf(
        PurrAurora,
        GreenApple,
        Monochrome,
        StrawberryDaiquiri,
        TidalWave
    )

    fun getPalettes(context: Context): List<AppPalette> {
        val dynamicTheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AppPalette(
                key = "dynamic",
                lightColors = dynamicLightColorScheme(context),
                darkColors = dynamicDarkColorScheme(context)
            )
        } else {
            AppPalette(
                key = "default",
                lightColors = lightColorScheme(),
                darkColors = darkColorScheme()
            )
        }
        return listOf(dynamicTheme) + staticPalettes
    }
}
