package com.greenslocks.iptv

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

private fun t(weight: FontWeight, size: Int, line: Int, spacing: Float = 0f) =
    TextStyle(fontFamily = InterFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

/** One type scale for the whole app: Inter, five weights, fixed sizes and line heights. */
val VanceTypography = Typography(
    displayLarge = t(FontWeight.ExtraBold, 56, 64, -1f),
    displayMedium = t(FontWeight.ExtraBold, 44, 52, -0.5f),
    displaySmall = t(FontWeight.Bold, 36, 44, -0.25f),
    headlineLarge = t(FontWeight.Bold, 34, 42, -0.6f),
    headlineMedium = t(FontWeight.SemiBold, 28, 36, -0.35f),
    headlineSmall = t(FontWeight.SemiBold, 22, 30, -0.2f),
    titleLarge = t(FontWeight.SemiBold, 20, 28),
    titleMedium = t(FontWeight.SemiBold, 17, 24),
    titleSmall = t(FontWeight.SemiBold, 15, 22),
    bodyLarge = t(FontWeight.Normal, 16, 24),
    bodyMedium = t(FontWeight.Normal, 14, 22, 0.1f),
    bodySmall = t(FontWeight.Normal, 12, 18, 0.1f),
    labelLarge = t(FontWeight.SemiBold, 14, 20, 0.15f),
    labelMedium = t(FontWeight.Medium, 12, 16, 0.2f),
    labelSmall = t(FontWeight.Medium, 11, 16, 0.3f),
)
