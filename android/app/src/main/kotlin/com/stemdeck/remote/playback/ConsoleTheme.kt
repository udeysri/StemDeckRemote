package com.stemdeck.remote.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visual language for the mixer screen: a dark hardware-console aesthetic
 * (matte chassis surfaces, recessed fader wells, chalky pastel channel
 * accents, tracked monospaced readouts) — a straight port of the iOS side's
 * `ConsoleTheme` tokens. The system's built-in monospace family stands in
 * for JetBrains Mono / Space Mono, same reasoning as the iOS side: neither
 * is bundled, and this only needs the "tabular readout" feel.
 */
object ConsoleTheme {
    fun color(hex: Long): Color = Color(0xFF000000 or hex)

    val background = color(0x121316)
    val surfaceContainerLow = color(0x1b1b1f)
    val surfaceContainer = color(0x1f1f23)
    val surfaceContainerHigh = color(0x292a2d)
    val recessedWell = color(0x0d0e10)
    val onSurface = color(0xe3e2e6)
    val onSurfaceVariant = color(0xc5c6ca)
    val outline = color(0x8f9194)
    val outlineVariant = color(0x45474a)

    /** The brief's soft-glowing LED / active-state accent. */
    val accent = color(0x93d3c3)

    /**
     * One pastel per stem, matching the brief's "chalky pastel channel
     * accents" — StemDeck's 6 stems (vocals, drums, bass, guitar, piano,
     * other); anything else falls back to [outline].
     */
    fun stemColor(stem: String): Color = when (stem) {
        "vocals" -> color(0xe5989b)
        "drums" -> color(0x8ecae6)
        "bass" -> color(0x98d8c8)
        "guitar" -> color(0xf7c59f)
        "piano" -> color(0xb8a7ea)
        "other" -> accent
        else -> outline
    }

    fun monoFont(size: Int, weight: FontWeight = FontWeight.Normal): TextStyle =
        TextStyle(fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.Monospace)

    /**
     * All-caps, wide-tracked mono label — the brief's "screenprinted panel
     * header" treatment. Callers are expected to pass an already-uppercased
     * string, same as the iOS side's `.textCase(.uppercase)` call sites.
     */
    fun headlineFont(size: Int = 11): TextStyle =
        TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.6.sp)
}

/** A small soft-glowing status lamp — the brief's "physical LED illumination." */
@Composable
fun LEDDot(color: Color, size: Int = 6, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .shadow(elevation = (size * 0.7).dp, shape = androidx.compose.foundation.shape.CircleShape, ambientColor = color, spotColor = color)
            .background(color, androidx.compose.foundation.shape.CircleShape),
    )
}

/**
 * The recessed-well look used for fader tracks and waveform pockets: a dark
 * inset panel with a hairline outer stroke (Compose has no direct
 * "top-only inner highlight" primitive without a custom draw pass, so the
 * outer stroke alone stands in for the iOS side's `RecessedWell` modifier).
 */
fun Modifier.recessedWell(cornerRadius: Int = 8): Modifier {
    val shape: Shape = RoundedCornerShape(cornerRadius.dp)
    return this
        .background(ConsoleTheme.recessedWell, shape)
        .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
}
