package com.example.ui.theme

import androidx.compose.ui.graphics.Color

object InkPalette {
    val Lavender = Color(0xFFD0BCFF)
    val White = Color(0xFFE6E1E5)
    val Coral = Color(0xFFFFB4AB)
    val IceBlue = Color(0xFFC2E7FF)
    val Mint = Color(0xFFA8DAB5)
    val Amber = Color(0xFFFFD8A8)
    val Violet = Color(0xFFA78BFA)
    val Slate = Color(0xFF938F99)

    val CuratedColors = listOf(
        Lavender to "Lavender",
        White to "Chalk White",
        Coral to "Coral Rose",
        IceBlue to "Ice Blue",
        Mint to "Mint Sage",
        Amber to "Amber Glow",
        Violet to "Violet",
        Slate to "Muted Slate"
    )

    val NotebookCovers = listOf(
        Color(0xFF25232A) to "Sophisticated Dark",
        Color(0xFF2E1B4D) to "Midnight Violet",
        Color(0xFF132A4A) to "Abyssal Navy",
        Color(0xFF143D2B) to "Cypress Green",
        Color(0xFF4A1919) to "Wine Red",
        Color(0xFF3B2B1B) to "Dark Bronze",
        Color(0xFF1C1B1F) to "Onyx Black"
    )

    val StrokeWidthPresets = listOf(
        2.5f to "Fine (2.5pt)",
        5.0f to "Medium (5pt)",
        9.0f to "Bold (9pt)",
        18.0f to "Marker (18pt)"
    )
}
