package com.taskflow.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.taskflow.app.R

// Two variable fonts, both SIL Open Font License (app/OFL_*.txt):
// Bricolage Grotesque for anything that names a screen or a chore, Instrument
// Sans for everything read. Each weight is one instance of the same file.

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int) = Font(
    R.font.bricolage_grotesque,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        // The display optical size: tighter, more character at heading sizes.
        FontVariation.Setting("opsz", 36f),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun instrument(weight: Int) = Font(
    R.font.instrument_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Bricolage = FontFamily(bricolage(600), bricolage(700), bricolage(800))

val InstrumentSans = FontFamily(
    instrument(400), instrument(500), instrument(600), instrument(700),
)

private val base = Typography()

private fun display(style: TextStyle, size: Int, weight: Int = 700) = style.copy(
    fontFamily = Bricolage,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = (size * 1.08).sp,
    letterSpacing = (-0.02).em,
)

private fun text(style: TextStyle, size: Int, weight: Int, line: Double = 1.4) = style.copy(
    fontFamily = InstrumentSans,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = (size * line).sp,
    letterSpacing = 0.sp,
)

val QuietTypography = Typography(
    displayLarge = display(base.displayLarge, 52, 800),
    displayMedium = display(base.displayMedium, 44, 750),
    displaySmall = display(base.displaySmall, 38),
    // Screen titles.
    headlineLarge = display(base.headlineLarge, 34),
    // Chore titles in sheets.
    headlineMedium = display(base.headlineMedium, 30),
    headlineSmall = display(base.headlineSmall, 26),
    titleLarge = display(base.titleLarge, 22),
    titleMedium = text(base.titleMedium, 17, 600, 1.3),
    titleSmall = text(base.titleSmall, 15, 600, 1.3),
    bodyLarge = text(base.bodyLarge, 16, 400),
    bodyMedium = text(base.bodyMedium, 15, 400),
    bodySmall = text(base.bodySmall, 13, 400),
    labelLarge = text(base.labelLarge, 15, 600, 1.2),
    labelMedium = text(base.labelMedium, 12, 600, 1.2).copy(letterSpacing = 0.08.em),
    labelSmall = text(base.labelSmall, 11, 600, 1.2),
)
