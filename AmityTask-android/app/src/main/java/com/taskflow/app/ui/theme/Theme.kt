package com.taskflow.app.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ─── Quiet House ───────────────────────────────────────────────
//
// A stone ground, deep ink text, one accent (the brand violet), and three
// quiet signal colours: sage for done, amber for "past its day", slate for
// away. Nothing in the palette is red except `error`, which is reserved for
// real errors and destructive confirms — never for a turn that is late
// (constraint 3).
//
// Every Material role is set on purpose. The old scheme left the …Container
// roles to Material's baseline, where tertiaryContainer is a dark red, and
// that is how a red disc once appeared in the household switcher with nobody
// having chosen it (CLAUDE.md §7).

private val Violet = Color(0xFF7C3AED)
private val Ink = Color(0xFF17201B)
private val Stone = Color(0xFFF2F3EE)

private val LightColorScheme = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFE7FD),
    onPrimaryContainer = Color(0xFF4C1D95),
    inversePrimary = Color(0xFFC4B5FD),
    secondary = Ink,
    onSecondary = Color.White,
    // What Material paints a *selected* chip, segment or toggle in: the violet
    // tint, so a choice reads as chosen and not merely greyed.
    secondaryContainer = Color(0xFFEFE7FD),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = Color(0xFF2F7A55),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2EFE7),
    onTertiaryContainer = Color(0xFF24603F),
    background = Stone,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEEE8),
    onSurfaceVariant = Color(0xFF4F5852),
    surfaceTint = Color.Transparent,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE6E8E1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8F4),
    surfaceContainer = Stone,
    // Dialogs draw on this one: white, so a dialog reads as a clean card.
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE6E8E1),
    inverseSurface = Ink,
    inverseOnSurface = Stone,
    outline = Color(0xFFDDE0D7),
    outlineVariant = Color(0xFFECEEE8),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFDE8E4),
    onErrorContainer = Color(0xFF7A1A10),
    scrim = Color(0xFF17201B),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB79CFA),
    onPrimary = Color(0xFF2A0D5E),
    primaryContainer = Color(0xFF3B2470),
    onPrimaryContainer = Color(0xFFE9DDFF),
    inversePrimary = Violet,
    secondary = Color(0xFF2A332E),
    onSecondary = Color(0xFFEEF1EC),
    secondaryContainer = Color(0xFF3B2470),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = Color(0xFF6FC498),
    onTertiary = Color(0xFF0D2A1B),
    tertiaryContainer = Color(0xFF1E3A2B),
    onTertiaryContainer = Color(0xFFBFE6CF),
    background = Color(0xFF101512),
    onBackground = Color(0xFFEEF1EC),
    surface = Color(0xFF1A211D),
    onSurface = Color(0xFFEEF1EC),
    surfaceVariant = Color(0xFF232B26),
    onSurfaceVariant = Color(0xFFAEB6B0),
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFF2A322D),
    surfaceDim = Color(0xFF101512),
    surfaceContainerLowest = Color(0xFF0C100E),
    surfaceContainerLow = Color(0xFF151B17),
    surfaceContainer = Color(0xFF1A211D),
    surfaceContainerHigh = Color(0xFF212924),
    surfaceContainerHighest = Color(0xFF29322C),
    inverseSurface = Color(0xFFEEF1EC),
    inverseOnSurface = Ink,
    outline = Color(0xFF34403A),
    outlineVariant = Color(0xFF29322C),
    error = Color(0xFFF2A598),
    onError = Color(0xFF5C1209),
    errorContainer = Color(0xFF5C1209),
    onErrorContainer = Color(0xFFFDE8E4),
    scrim = Color.Black,
)

/**
 * The colours Material has no role for: the three signals, the dock, and the
 * soft fills behind the signal chips. Read through [Quiet.colors].
 */
@Immutable
data class QuietColors(
    /** The floating dock and the toast: the darkest thing on a light screen. */
    val dock: Color,
    val dockActive: Color,
    val onDock: Color,
    val onDockMuted: Color,
    /** Done. A thing that happened, never a reward. */
    val done: Color,
    val doneSoft: Color,
    val onDoneSoft: Color,
    /** Past its day. Amber, and only ever paired with a date — never a count. */
    val pastDot: Color,
    val pastText: Color,
    val pastSoft: Color,
    /** Away. */
    val awaySoft: Color,
    val onAwaySoft: Color,
)

private val LightQuiet = QuietColors(
    dock = Ink,
    dockActive = Color(0xFF2C3832),
    onDock = Color.White,
    onDockMuted = Color(0xFFAEB6B0),
    done = Color(0xFF2F7A55),
    doneSoft = Color(0xFFE2EFE7),
    onDoneSoft = Color(0xFF24603F),
    pastDot = Color(0xFFD99A1E),
    pastText = Color(0xFF7A5200),
    pastSoft = Color(0xFFFBF1DC),
    awaySoft = Color(0xFFEAECF1),
    onAwaySoft = Color(0xFF3B4352),
)

private val DarkQuiet = QuietColors(
    dock = Color(0xFF2A332E),
    dockActive = Color(0xFF3A4640),
    onDock = Color(0xFFEEF1EC),
    onDockMuted = Color(0xFF9AA49D),
    done = Color(0xFF6FC498),
    doneSoft = Color(0xFF1E3A2B),
    onDoneSoft = Color(0xFFBFE6CF),
    pastDot = Color(0xFFE8B44F),
    pastText = Color(0xFFF0C878),
    pastSoft = Color(0xFF3A2E14),
    awaySoft = Color(0xFF262C36),
    onAwaySoft = Color(0xFFC3CAD6),
)

private val LocalQuiet = staticCompositionLocalOf { LightQuiet }

object Quiet {
    val colors: QuietColors
        @Composable get() = LocalQuiet.current
}

private val QuietShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TaskFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Material You dynamic colour stays off, deliberately: a wallpaper-derived
    // scheme can put a red primary next to the amber, which reads as exactly
    // the shame signal constraint 3 rules out.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // The status and navigation bar icons follow the theme the app is actually
    // drawing, not the phone's. enableEdgeToEdge() picks them from the system
    // setting, so Appearance → Dark on a phone in light mode left dark icons on
    // the dark background — the clock and battery all but gone.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var context = view.context
            while (context is ContextWrapper && context !is Activity) context = context.baseContext
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalQuiet provides if (darkTheme) DarkQuiet else LightQuiet) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = QuietTypography,
            shapes = QuietShapes,
            content = content,
        )
    }
}
