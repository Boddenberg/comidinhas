package br.com.boddenb.comidinhas.ui.screen.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary             = Brand,
    onPrimary           = Color.White,
    primaryContainer    = BrandSurface,
    onPrimaryContainer  = BrandDark,

    secondary           = InkMedium,
    onSecondary         = Color.White,
    secondaryContainer  = Surface2,
    onSecondaryContainer= Ink,

    tertiary            = Green,
    onTertiary          = Color.White,
    tertiaryContainer   = GreenSurface,
    onTertiaryContainer = Color(0xFF0A3D28),

    background          = Surface0,
    onBackground        = Ink,

    surface             = Surface0,
    onSurface           = Ink,
    surfaceVariant      = Surface1,
    onSurfaceVariant    = InkMedium,

    outline             = Surface2,
    outlineVariant      = Surface2,

    error               = Color(0xFFBF1B1B),
    onError             = Color.White,
    errorContainer      = Color(0xFFFDE8E8),
    onErrorContainer    = Color(0xFF7A0000),
)

private val DarkColorScheme = darkColorScheme(
    primary             = BrandLight,
    onPrimary           = Color(0xFF5C1800),
    primaryContainer    = BrandDark,
    onPrimaryContainer  = Color(0xFFFFDDD0),

    background          = DarkBg,
    onBackground        = DarkInk,

    surface             = DarkSurface,
    onSurface           = DarkInk,
    surfaceVariant      = DarkCard,
    onSurfaceVariant    = Color(0xFFC8B8AD),

    outline             = Color(0xFF4A3428),
)

@Composable
fun ComidinhasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = AppTypography,
        content     = content
    )
}
