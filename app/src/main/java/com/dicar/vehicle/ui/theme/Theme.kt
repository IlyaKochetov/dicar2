package com.dicar.vehicle.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal daylight palette: light gray background, white cards, thin outlines and blue accents.
 * Battery is green, fuel orange and alerts red consistently across screens.
 *
 * The night palette keeps the same structure with a darker background, lighter cards,
 * lower-contrast outlines and brighter accents suited to dark surfaces.
 */
private object Day {
    val Accent = Color(0xFF0A84FF)       // Accent: speed, RPM, drive power
    val Green = Color(0xFF34C759)        // Battery level
    val Orange = Color(0xFFFF9F0A)       // Fuel level
    val Red = Color(0xFFFF3B30)          // Alert

    val Background = Color(0xFFEEF1F5)
    val Card = Color(0xFFFFFFFF)
    val CardAlt = Color(0xFFF7F9FB)
    val Track = Color(0xFFE8ECF1)        // Progress bar / gauge track
    val TrackDeep = Color(0xFFDDE3EA)
    val Text = Color(0xFF0F1419)
    val Dim = Color(0xFF8A94A3)          // Labels, units and secondary text
    val Border = Color(0xFFE2E6EC)
    val BorderStrong = Color(0xFFC7CEd8)
}

private object Night {
    val Accent = Color(0xFF4DA3FF)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFFB340)
    val Red = Color(0xFFFF6961)

    val Background = Color(0xFF121417)
    val Card = Color(0xFF1C1F23)
    val CardAlt = Color(0xFF16191D)
    val Track = Color(0xFF2A2F36)
    val TrackDeep = Color(0xFF343A42)
    val Text = Color(0xFFECEDEE)
    val Dim = Color(0xFF9AA1AC)
    val Border = Color(0xFF2E3238)
    val BorderStrong = Color(0xFF454C55)
}

private val LightColors = lightColorScheme(
    primary = Day.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF00345F),
    secondary = Day.Green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F5E0),
    onSecondaryContainer = Color(0xFF0B3D1E),
    tertiary = Day.Orange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFECCF),
    onTertiaryContainer = Color(0xFF4A2D00),
    error = Day.Red,
    onError = Color.White,
    errorContainer = Color(0xFFFFE0DE),
    onErrorContainer = Color(0xFF5C0F0B),
    background = Day.Background,
    onBackground = Day.Text,
    surface = Day.Background,
    onSurface = Day.Text,
    surfaceVariant = Day.Track,
    onSurfaceVariant = Day.Dim,
    surfaceContainerLowest = Day.Card,
    surfaceContainerLow = Day.CardAlt,
    surfaceContainer = Day.Card,
    surfaceContainerHigh = Day.Track,
    surfaceContainerHighest = Day.TrackDeep,
    outline = Day.BorderStrong,
    outlineVariant = Day.Border,
)

private val DarkColors = darkColorScheme(
    primary = Night.Accent,
    onPrimary = Color(0xFF00243F),
    primaryContainer = Color(0xFF123A5E),
    onPrimaryContainer = Color(0xFFCFE4FF),
    secondary = Night.Green,
    onSecondary = Color(0xFF00210E),
    secondaryContainer = Color(0xFF143C23),
    onSecondaryContainer = Color(0xFFCCF2D7),
    tertiary = Night.Orange,
    onTertiary = Color(0xFF3A2300),
    tertiaryContainer = Color(0xFF4E3410),
    onTertiaryContainer = Color(0xFFFFE6C2),
    error = Night.Red,
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF4D1F1D),
    onErrorContainer = Color(0xFFFFDAD7),
    background = Night.Background,
    onBackground = Night.Text,
    surface = Night.Background,
    onSurface = Night.Text,
    surfaceVariant = Night.Track,
    onSurfaceVariant = Night.Dim,
    surfaceContainerLowest = Color(0xFF101316),
    surfaceContainerLow = Night.CardAlt,
    surfaceContainer = Night.Card,
    surfaceContainerHigh = Night.Track,
    surfaceContainerHighest = Night.TrackDeep,
    outline = Night.BorderStrong,
    outlineVariant = Night.Border,
)

/** Larger corners and text improve touch and readability on the head unit. */
private val CarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Important numbers use large light text; labels are smaller and subdued.
 * Display and headline styles enable `tnum` so changing digits do not shift adjacent units.
 * The digits retain their normal sans-serif appearance.
 */
private const val TABULAR = "tnum"

private val CarTypography = Typography().run {
    copy(
        displayLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 64.sp, fontFeatureSettings = TABULAR),
        displayMedium = TextStyle(fontWeight = FontWeight.Light, fontSize = 46.sp, fontFeatureSettings = TABULAR),
        displaySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 34.sp, fontFeatureSettings = TABULAR),
        headlineMedium = headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR),
        headlineSmall = headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR),
        titleLarge = titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, fontFeatureSettings = TABULAR),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp),
        labelMedium = labelMedium.copy(fontSize = 13.sp),
        labelSmall = labelSmall.copy(fontSize = 11.sp),
    )
}

/** Light and dark themes follow the system UI mode, including DiLink day/night changes. */
@Composable
fun DiCarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = CarShapes,
        typography = CarTypography,
        content = content,
    )
}

/** Low-contrast outline color for cards and other containers. */
val MaterialTheme.hairline: Color
    @Composable get() = colorScheme.outlineVariant

/** Twin-view body fill: white by day and dark gray slightly lighter than cards at night. */
val MaterialTheme.carBody: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2C3238) else Color.White

/** Glass (windscreens, sunroof and side windows): neutral gray darker than the body. */
val MaterialTheme.carGlass: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF454D56) else Color(0xFFD4DBE3)

/**
 * Tires are the darkest part in both themes so all four corners remain identifiable.
 * At night they are slightly lighter than the background and outlined (see CarDiagram)
 * to keep their outer edges visible.
 */
val MaterialTheme.carTyre: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF05070A) else Color(0xFF2B3038)
