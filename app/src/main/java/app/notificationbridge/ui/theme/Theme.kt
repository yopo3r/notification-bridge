/** Material 3 color and type system for built-in and imported themes. */
package app.notificationbridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.notificationbridge.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBEBDD),
    onPrimaryContainer = Color(0xFF06251B),
    secondary = Color(0xFF48645A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E9DF),
    onSecondaryContainer = Color(0xFF15271F),
    background = Color(0xFFF5F8F6),
    onBackground = Color(0xFF18211D),
    surface = Color(0xFFFCFDFC),
    onSurface = Color(0xFF18211D),
    surfaceVariant = Color(0xFFE7EEEA),
    onSurfaceVariant = Color(0xFF46534D),
    outline = Color(0xFF75847C),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88D5B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF164F3D),
    onPrimaryContainer = Color(0xFFA6F2D2),
    secondary = Color(0xFFB6CCBF),
    onSecondary = Color(0xFF24382E),
    secondaryContainer = Color(0xFF344B3F),
    onSecondaryContainer = Color(0xFFD2E8DA),
    background = Color(0xFF101714),
    onBackground = Color(0xFFE1E9E4),
    surface = Color(0xFF171F1B),
    onSurface = Color(0xFFE1E9E4),
    surfaceVariant = Color(0xFF27332D),
    onSurfaceVariant = Color(0xFFB9C8BF),
    outline = Color(0xFF84958B),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5F1713),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun NotificationBridgeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    customThemeSource: String? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val customTheme = customThemeSource?.let { runCatching { CustomThemeParser.parse(it) }.getOrNull() }
    val customPalette = if (darkTheme) customTheme?.dark else customTheme?.light
    val scheme = customPalette?.let { palette ->
        if (darkTheme) darkColorScheme(
            primary = palette.highlight,
            onPrimary = palette.highlightForeground,
            primaryContainer = palette.highlight.copy(alpha = 0.24f),
            onPrimaryContainer = palette.foreground,
            secondary = palette.secondary,
            secondaryContainer = palette.secondary.copy(alpha = 0.20f),
            onSecondaryContainer = palette.foreground,
            background = palette.background,
            onBackground = palette.foreground,
            surface = palette.surface,
            onSurface = palette.foreground,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.foreground,
            outline = palette.foreground.copy(alpha = 0.55f),
            outlineVariant = palette.foreground.copy(alpha = 0.24f),
            error = palette.error
        ) else lightColorScheme(
            primary = palette.highlight,
            onPrimary = palette.highlightForeground,
            primaryContainer = palette.highlight.copy(alpha = 0.16f),
            onPrimaryContainer = palette.foreground,
            secondary = palette.secondary,
            secondaryContainer = palette.secondary.copy(alpha = 0.14f),
            onSecondaryContainer = palette.foreground,
            background = palette.background,
            onBackground = palette.foreground,
            surface = palette.surface,
            onSurface = palette.foreground,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.foreground,
            outline = palette.foreground.copy(alpha = 0.55f),
            outlineVariant = palette.foreground.copy(alpha = 0.24f),
            error = palette.error
        )
    } ?: if (darkTheme) DarkColors else LightColors

    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
