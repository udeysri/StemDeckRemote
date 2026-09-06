package com.stemdeck.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.stemdeck.remote.playback.ConsoleTheme

/**
 * App-wide Material3 wrapper. The console's hardware-rack look is designed
 * as a single dark theme, not a light/dark pair — same call as iOS's
 * `UIUserInterfaceStyle: Dark` / `.preferredColorScheme(.dark)` — so this
 * always applies the dark scheme regardless of the device's system setting.
 */
private val StemDeckDarkColors = darkColorScheme(
    primary = ConsoleTheme.accent,
    secondary = ConsoleTheme.accent,
    background = ConsoleTheme.background,
    surface = ConsoleTheme.surfaceContainer,
    onBackground = ConsoleTheme.onSurface,
    onSurface = ConsoleTheme.onSurface,
)

@Composable
fun StemDeckRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StemDeckDarkColors, content = content)
}
