package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryBlack,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerGray,
    secondary = SecondaryEmerald,
    onSecondary = OnPrimaryWhite,
    secondaryContainer = SecondaryContainerMint,
    onSecondaryContainer = OnSecondaryContainerEmerald,
    background = SurfaceCanvas,
    onBackground = OnSurfaceDark,
    surface = SurfaceCanvas,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighest,
    onSurfaceVariant = OnSurfaceVariantGray,
    outline = OutlineGray,
    outlineVariant = OutlineVariantLight,
    error = ErrorRed,
    onError = OnPrimaryWhite,
    errorContainer = ErrorContainerRed,
    onErrorContainer = OnErrorContainerRed,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  // Always use light color scheme with light background as required: "底色必须是浅色啊！"
  MaterialTheme(
    colorScheme = LightColorScheme,
    typography = Typography,
    content = content
  )
}


