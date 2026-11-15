package com.appybits.eventsnap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(50)
)

private val defaultLight = lightColorScheme(
    primary = Color.Black, 
    background = Color.Transparent, 
    surface = NeutralSurfaceLight,
    onPrimary = Color.White,
    onBackground = NeutralTextLight,
    onSurface = NeutralTextLight,
    onSurfaceVariant = NeutralSecondaryLight,
    outline = NeutralOutlineLight,
    error = ErrorColor
)

private val defaultDark = darkColorScheme(
    primary = Color.White, 
    background = Color.Transparent,
    surface = NeutralSurfaceDark,
    onPrimary = Color.Black,
    onBackground = NeutralTextDark,
    onSurface = NeutralTextDark,
    onSurfaceVariant = NeutralSecondaryDark,
    outline = NeutralOutlineDark,
    error = ErrorColor
)

@Composable
fun AdminTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) defaultDark else defaultLight, typography = Typography, shapes = Shapes, content = content)
}

@Composable
fun PhotographerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) defaultDark else defaultLight, typography = Typography, shapes = Shapes, content = content)
}

@Composable
fun GuestTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) defaultDark else defaultLight, typography = Typography, shapes = Shapes, content = content)
}

@Composable
fun EventSnapTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // Default to admin theme for unassigned screens (like Landing)
    AdminTheme(darkTheme, content)
}
