package com.stemdeck.remote.playback

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.stemdeck.remote.R

/**
 * Icon, color, and display name for the 6 stems StemDeck produces
 * (`STEM_NAMES` in the desktop app's `app/core/config.py`: vocals, drums,
 * bass, guitar, piano, other). Any unrecognized name — e.g. a future stem
 * StemDeck adds — falls back to a generic icon/color and its capitalized
 * name rather than failing.
 *
 * Material's classic icon set has no literal drum/guitar/piano glyphs, so
 * (unlike the iOS side's SF Symbols, which does) these are the closest
 * available stand-ins rather than 1:1 equivalents.
 */
object StemIcon {
    fun icon(stem: String): ImageVector = when (stem) {
        "vocals" -> Icons.Filled.Mic
        "drums" -> Icons.Filled.Album
        "bass" -> Icons.Filled.GraphicEq
        "guitar" -> Icons.Filled.Audiotrack
        "piano" -> Icons.Filled.LibraryMusic
        "other" -> Icons.Filled.MusicNote
        else -> Icons.Filled.Equalizer
    }

    /**
     * Full-color stem artwork (from iconpacks.net — free for commercial/personal
     * use, see the root README's Acknowledgments) for the one or two spots per
     * screen where the icon is the hero rather than a small state indicator.
     * Unlike [icon], these are never tinted — they carry their own color.
     */
    @DrawableRes
    fun imageRes(stem: String): Int = when (stem) {
        "vocals" -> R.drawable.ic_stem_vocals
        "drums" -> R.drawable.ic_stem_drums
        "bass" -> R.drawable.ic_stem_bass
        "guitar" -> R.drawable.ic_stem_guitar
        "piano" -> R.drawable.ic_stem_piano
        "other" -> R.drawable.ic_stem_other
        else -> R.drawable.ic_stem_other
    }

    fun color(stem: String): Color = ConsoleTheme.stemColor(stem)

    fun displayName(stem: String): String = when (stem) {
        "vocals" -> "Vocals"
        "drums" -> "Drums"
        "bass" -> "Bass"
        "guitar" -> "Guitar"
        "piano" -> "Piano"
        "other" -> "Other"
        else -> stem.replaceFirstChar { it.uppercase() }
    }
}
