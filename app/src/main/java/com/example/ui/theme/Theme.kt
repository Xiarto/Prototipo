package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = OnForestGreen,
    primaryContainer = SageContainer,
    onPrimaryContainer = OnSageContainer,
    secondary = Terracotta,
    onSecondary = OnTerracotta,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = OnTerracottaContainer,
    tertiary = CitrusAmber,
    onTertiary = OnCitrusAmber,
    tertiaryContainer = CitrusContainer,
    onTertiaryContainer = OnCitrusContainer,
    background = AlabasterBackground,
    onBackground = EspressoOnSurface,
    surface = CreamSurface,
    onSurface = EspressoOnSurface,
    surfaceVariant = OatSurfaceVariant,
    onSurfaceVariant = MutedOliveOnSurfaceVariant,
    outline = WarmOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = SoftMint,
    onPrimary = OnSoftMint,
    primaryContainer = SoftMintContainer,
    onPrimaryContainer = OnSoftMintContainer,
    secondary = SoftCoral,
    onSecondary = OnSoftCoral,
    secondaryContainer = SoftCoralContainer,
    onSecondaryContainer = OnSoftCoralContainer,
    tertiary = CitrusAmber,
    onTertiary = OnCitrusAmber,
    background = DarkPineBackground,
    onBackground = DarkWarmText,
    surface = DarkPineSurface,
    onSurface = DarkWarmText,
    surfaceVariant = DarkPineSurfaceVariant,
    onSurfaceVariant = DarkMutedText,
    outline = DarkOutline
)

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ChefNeveraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
