package com.pocketforge.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Cyberpunk Theme Primary Accents
val CyberCyan = Color(0xFF00F0FF)
val CyberAmber = Color(0xFFFF9900)
val CyberCrimson = Color(0xFFFF0055)
val CyberEmerald = Color(0xFF00FF88)

val PocketOrange = CyberCyan
val PocketBlue = CyberCyan
val PocketGreen = CyberEmerald
val PocketBackground = Color(0xFF050B14)
val PocketSurface = Color(0xFF0A1526)
val PocketSurfaceVariant = Color(0xFF10223B)
val PocketOutline = Color(0xFF1E3A5F)

// 1. J.A.R.V.I.S (CYAN) - Futuristic Arc Reactor Glow
val JarvisColors = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF001F29),
    primaryContainer = Color(0xFF06384C),
    onPrimaryContainer = Color(0xFFC0F5FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF00223A),
    tertiary = Color(0xFF00E5FF),
    onTertiary = Color(0xFF002A36),
    background = Color(0xFF050C16),
    onBackground = Color(0xFFE2F3FF),
    surface = Color(0xFF0A1626),
    onSurface = Color(0xFFE2F3FF),
    surfaceVariant = Color(0xFF10233B),
    onSurfaceVariant = Color(0xFF8BB5D6),
    outline = Color(0xFF1A4168),
    outlineVariant = Color(0xFF112F4D),
)

// 2. STARK IND (AMBER) - Mark Protocol Holographic Gold/Orange
val StarkColors = darkColorScheme(
    primary = CyberAmber,
    onPrimary = Color(0xFF2B1400),
    primaryContainer = Color(0xFF4C2703),
    onPrimaryContainer = Color(0xFFFFE0B8),
    secondary = Color(0xFFFFB800),
    onSecondary = Color(0xFF2E1C00),
    tertiary = Color(0xFFFF5E00),
    onTertiary = Color(0xFF331000),
    background = Color(0xFF0E0B08),
    onBackground = Color(0xFFFFEED9),
    surface = Color(0xFF18130E),
    onSurface = Color(0xFFFFEED9),
    surfaceVariant = Color(0xFF271E16),
    onSurfaceVariant = Color(0xFFD6B594),
    outline = Color(0xFF4E3725),
    outlineVariant = Color(0xFF382517),
)

// 3. VERONICA (CRIMSON) - Veronica Protocol Crimson Lasers
val VeronicaColors = darkColorScheme(
    primary = CyberCrimson,
    onPrimary = Color(0xFF2E000C),
    primaryContainer = Color(0xFF4E071A),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFFF2A6D),
    onSecondary = Color(0xFF300010),
    tertiary = Color(0xFFFF4081),
    onTertiary = Color(0xFF380016),
    background = Color(0xFF0F0509),
    onBackground = Color(0xFFFFE8EE),
    surface = Color(0xFF1D0912),
    onSurface = Color(0xFFFFE8EE),
    surfaceVariant = Color(0xFF2E101E),
    onSurfaceVariant = Color(0xFFD996AD),
    outline = Color(0xFF5E1E36),
    outlineVariant = Color(0xFF3E1222),
)

// 4. CYBER MATRIX (EMERALD) - Neural Terminal Matrix Green
val MatrixColors = darkColorScheme(
    primary = CyberEmerald,
    onPrimary = Color(0xFF002A14),
    primaryContainer = Color(0xFF064324),
    onPrimaryContainer = Color(0xFFB8FFE0),
    secondary = Color(0xFF05FFA1),
    onSecondary = Color(0xFF002E19),
    tertiary = Color(0xFF10B981),
    onTertiary = Color(0xFF00331C),
    background = Color(0xFF040E08),
    onBackground = Color(0xFFE0FFE8),
    surface = Color(0xFF081C10),
    onSurface = Color(0xFFE0FFE8),
    surfaceVariant = Color(0xFF0F2E1B),
    onSurfaceVariant = Color(0xFF8AD4A6),
    outline = Color(0xFF18522E),
    outlineVariant = Color(0xFF103A20),
)

// Classic Dark / Light for fallback
val ClassicDarkColors = JarvisColors

val LightColors = lightColorScheme(
    primary = Color(0xFF007799),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCEBFF),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFF94A3B8),
)

// Extra Optional Light Theme: Claude Style (Warm terracotta & ivory cream)
val ClaudeLightColors = lightColorScheme(
    primary = Color(0xFFD97757),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFDEEE9),
    onPrimaryContainer = Color(0xFF4A1A0B),
    secondary = Color(0xFF9A5E44),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E6DF),
    onSecondaryContainer = Color(0xFF3A1F13),
    tertiary = Color(0xFF78716C),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF1F1E1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1E1B),
    surfaceVariant = Color(0xFFF3F0E8),
    onSurfaceVariant = Color(0xFF6B665E),
    outline = Color(0xFFE5E0D5),
    outlineVariant = Color(0xFFEDE9DF),
)

enum class AppThemeMode(
    val title: String,
    val subtitle: String,
    val hexColor: Long,
) {
    DARK("Dark", "Default dark", 0xFF00F0FF),
    LIGHT("Light", "Default light", 0xFF0284C7),
    SYSTEM("System", "Follow OS", 0xFF38BDF8),

    // 4 Optional Cyberpunk Themes
    JARVIS("J.A.R.V.I.S", "Arc Reactor Cyan", 0xFF00F0FF),
    STARK("STARK IND", "Mark Holographic Amber", 0xFFFF9900),
    VERONICA("VERONICA", "Veronica Crimson", 0xFFFF0055),
    MATRIX("CYBER MATRIX", "Neural Matrix Emerald", 0xFF00FF88),

    // Extra Optional Light Theme
    CLAUDE_LIGHT("Claude Style", "Warm terracotta & ivory", 0xFFD97757);

    val isLightVariant: Boolean
        get() = this == LIGHT || this == CLAUDE_LIGHT

    val isDarkVariant: Boolean
        get() = this == DARK || this == JARVIS || this == STARK || this == VERONICA || this == MATRIX
}

@Composable
fun PocketTheme(themeMode: AppThemeMode = AppThemeMode.JARVIS, content: @Composable () -> Unit) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.LIGHT, AppThemeMode.CLAUDE_LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemDark
        else -> true
    }

    val colorScheme = when (themeMode) {
        AppThemeMode.JARVIS -> JarvisColors
        AppThemeMode.STARK -> StarkColors
        AppThemeMode.VERONICA -> VeronicaColors
        AppThemeMode.MATRIX -> MatrixColors
        AppThemeMode.DARK -> ClassicDarkColors
        AppThemeMode.LIGHT -> LightColors
        AppThemeMode.CLAUDE_LIGHT -> ClaudeLightColors
        AppThemeMode.SYSTEM -> if (isSystemDark) JarvisColors else LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
