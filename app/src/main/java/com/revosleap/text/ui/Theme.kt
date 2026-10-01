package com.revosleap.text.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.revosleap.text.R

private val vazirmatn = FontFamily(Font(R.font.vazirmatn))
private val base = Typography()
private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = vazirmatn),
    displayMedium = base.displayMedium.copy(fontFamily = vazirmatn),
    displaySmall = base.displaySmall.copy(fontFamily = vazirmatn),
    headlineLarge = base.headlineLarge.copy(fontFamily = vazirmatn),
    headlineMedium = base.headlineMedium.copy(fontFamily = vazirmatn),
    headlineSmall = base.headlineSmall.copy(fontFamily = vazirmatn),
    titleLarge = base.titleLarge.copy(fontFamily = vazirmatn),
    titleMedium = base.titleMedium.copy(fontFamily = vazirmatn),
    titleSmall = base.titleSmall.copy(fontFamily = vazirmatn),
    bodyLarge = base.bodyLarge.copy(fontFamily = vazirmatn),
    bodyMedium = base.bodyMedium.copy(fontFamily = vazirmatn),
    bodySmall = base.bodySmall.copy(fontFamily = vazirmatn),
    labelLarge = base.labelLarge.copy(fontFamily = vazirmatn),
    labelMedium = base.labelMedium.copy(fontFamily = vazirmatn),
    labelSmall = base.labelSmall.copy(fontFamily = vazirmatn)
)

@Composable
fun FarakhvanTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF65DAC8), onPrimary = Color(0xFF00382F),
        background = Color(0xFF101C1A), surface = Color(0xFF142320),
        secondaryContainer = Color(0xFF204D43), error = Color(0xFFFFB4AB)
    ) else lightColorScheme(
        primary = Color(0xFF00796B), onPrimary = Color.White,
        background = Color(0xFFF4FBF8), surface = Color(0xFFF4FBF8),
        secondaryContainer = Color(0xFFD0EDE3), error = Color(0xFFBA1A1A)
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
