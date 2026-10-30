package me.eternal.purrfect.common.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun lazyColumnContentPadding(
    horizontal: Dp = 10.dp,
    vertical: Dp = 10.dp,
    hasSubAction: Boolean = false,
    hasFAB: Boolean = false
): PaddingValues {
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = vertical,
        bottom = vertical + if (hasSubAction) {
            80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        } else if (hasFAB) {
            85.dp
        } else {
            0.dp
        }
    )
}

@Composable
fun screenContentPadding(
    horizontal: Dp = 10.dp,
    vertical: Dp = 10.dp,
    hasSubAction: Boolean = false,
    hasFAB: Boolean = false
): PaddingValues {
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = 75.dp + vertical + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        bottom = 80.dp + vertical + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            + if (hasSubAction) 60.dp else 0.dp
            + if (hasFAB) 75.dp else 0.dp
    )
}

val columnPadding = 10.dp
val componentPadding = 16.dp
