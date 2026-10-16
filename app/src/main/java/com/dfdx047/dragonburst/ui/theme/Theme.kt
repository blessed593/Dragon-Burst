package com.dfdx047.dragonburst.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dfdx047.dragonburst.data.DarkMode
import com.dfdx047.dragonburst.data.ThemeMode

/**
 * Paleta Dragon Burst, tirada dos menus do proprio Burst Limit: painel violeta com borda
 * creme, titulos dourados, selecao lavanda; o laranja das auras e o vermelho para avisos.
 * Os nomes antigos (Navy, BlueDeep...) ficam, agora com os tons violeta do jogo.
 */
object DfColors {
    val Night = Color(0xFF160A26)
    val Panel = Color(0xFF2A1442)
    val Royal = Color(0xFF5C348C)
    val Lavender = Color(0xFF9678D6)
    val Gold = Color(0xFFFFD63C)
    val Cream = Color(0xFFE8DEC4)
    val Ember = Color(0xFFFF8A1F)

    val Orange = Ember
    val OrangeDeep = Color(0xFFE0560B)
    val Yellow = Gold
    val Blue = Lavender
    val BlueDeep = Royal
    val Navy = Night
    val StarRed = Color(0xFFD7261E)
    val Ok = Color(0xFF3FBF73)
    val Warn = Color(0xFFFFB020)
    val Error = Color(0xFFFF5A4E)
}

private val DragonLight = lightColorScheme(
    primary = DfColors.Royal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADCFF),
    onPrimaryContainer = Color(0xFF26104A),
    secondary = Color(0xFF8A6100),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9A8),
    onSecondaryContainer = Color(0xFF2B1D00),
    tertiary = DfColors.OrangeDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF331200),
    background = Color(0xFFF7F2FA),
    onBackground = Color(0xFF1E1527),
    surface = Color(0xFFF7F2FA),
    onSurface = Color(0xFF1E1527),
    surfaceVariant = Color(0xFFE8DEF0),
    onSurfaceVariant = Color(0xFF4C4057),
    surfaceContainer = Color(0xFFEFE6F5),
    surfaceContainerHigh = Color(0xFFE7DCEF),
    outline = Color(0xFF7D6F88),
    outlineVariant = Color(0xFFCFC2D9),
    error = Color(0xFFC0281E),
)

private val DragonDark = darkColorScheme(
    primary = DfColors.Gold,
    onPrimary = Color(0xFF2A1442),
    primaryContainer = DfColors.Royal,
    onPrimaryContainer = Color(0xFFF4ECFF),
    secondary = DfColors.Lavender,
    onSecondary = Color(0xFF1E0B36),
    secondaryContainer = Color(0xFF3D2160),
    onSecondaryContainer = Color(0xFFEADCFF),
    tertiary = DfColors.Ember,
    onTertiary = Color(0xFF3A1500),
    tertiaryContainer = Color(0xFF7A3000),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = DfColors.Night,
    onBackground = Color(0xFFF2EAF7),
    surface = DfColors.Night,
    onSurface = Color(0xFFF2EAF7),
    surfaceVariant = Color(0xFF33204A),
    onSurfaceVariant = Color(0xFFD3C5E0),
    surfaceContainer = Color(0xFF211035),
    surfaceContainerHigh = Color(0xFF2C1844),
    surfaceContainerHighest = Color(0xFF372052),
    outline = Color(0xFFA493B4),
    outlineVariant = Color(0xFF4A3462),
    error = DfColors.Error,
)

private val DfTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
    )
}

val LogoStyle = TextStyle(
    fontWeight = FontWeight.Black,
    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
    fontSize = 30.sp,
    letterSpacing = 1.5.sp,
)

@Composable
fun DragonBurstTheme(themeMode: ThemeMode, darkMode: DarkMode, content: @Composable () -> Unit) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme: ColorScheme = if (themeMode == ThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) DragonDark else DragonLight
    }
    MaterialTheme(colorScheme = scheme, typography = DfTypography, content = content)
}
