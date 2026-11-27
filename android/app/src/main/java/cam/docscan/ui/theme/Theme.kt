package cam.docscan.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cybernetic Scanner Minimal color tokens (from Stitch design system).
 */
object DocScanColors {
    val SurfaceBase = Color(0xFF0B132B)
    val Background = Color(0xFF0A122A)
    val SurfaceLowest = Color(0xFF050D25)
    val SurfaceRaised = Color(0xFF0F172A)
    val SurfaceLow = Color(0xFF131A33)
    val SurfaceCard = Color(0xFF162032)
    val SurfaceContainer = Color(0xFF171E37)
    val SurfaceOverlay = Color(0xFF1E293B)
    val SurfaceHigh = Color(0xFF212942)
    val SurfaceHighest = Color(0xFF2C344D)

    val Primary = Color(0xFF44E5C2)
    val OnPrimary = Color(0xFF00382D)
    val ScanGlow = Color(0xFF00C9A7)
    val OnPrimaryContainer = Color(0xFF004E40)

    val Secondary = Color(0xFF4FDBC8)
    val SecondaryContainer = Color(0xFF14B8A6)
    val Tertiary = Color(0xFF38BDF8)
    val TertiarySoft = Color(0xFF90D6FF)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    val OnSurface = Color(0xFFDBE1FF)
    val OnSurfaceVariant = Color(0xFFBACAC3)

    val BorderSubtle = Color(0x14FFFFFF)
    val BorderMedium = Color(0x29FFFFFF)

    val WarningAmber = Color(0xFFF59E0B)
    val DangerCrimson = Color(0xFFEF4444)
    val Error = Color(0xFFFFB4AB)
    val ErrorContainer = Color(0xFF93000A)
}

private val DocScanColorScheme = darkColorScheme(
    primary = DocScanColors.Primary,
    onPrimary = DocScanColors.OnPrimary,
    primaryContainer = DocScanColors.ScanGlow,
    onPrimaryContainer = DocScanColors.OnPrimaryContainer,
    secondary = DocScanColors.Secondary,
    onSecondary = Color(0xFF003731),
    secondaryContainer = DocScanColors.SecondaryContainer,
    tertiary = DocScanColors.Tertiary,
    onTertiary = Color(0xFF00354A),
    background = DocScanColors.SurfaceBase,
    onBackground = DocScanColors.TextPrimary,
    surface = DocScanColors.SurfaceCard,
    onSurface = DocScanColors.TextPrimary,
    surfaceVariant = DocScanColors.SurfaceOverlay,
    onSurfaceVariant = DocScanColors.TextSecondary,
    outline = DocScanColors.BorderMedium,
    outlineVariant = DocScanColors.BorderSubtle,
    error = DocScanColors.DangerCrimson,
    onError = Color.White,
    errorContainer = DocScanColors.ErrorContainer,
)

val DocScanTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),
)

val DocScanShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun DocScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DocScanColorScheme,
        typography = DocScanTypography,
        shapes = DocScanShapes,
        content = content,
    )
}
