package com.jaspreet.gfeattendancemanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Suppress("unused")
val DarkColorScheme = darkColorScheme(
    primary = Cyan400,
    secondary = Emerald500,
    tertiary = Amber500,
    background = Slate900,
    surface = Slate800,
    onPrimary = Slate900,
    onSecondary = Slate50,
    onTertiary = Slate900,
    onBackground = Slate50,
    onSurface = Slate50
)

@Suppress("unused")
@Composable
fun GFEAttendanceManagerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
