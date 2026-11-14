package app.captunes.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Night = Color(0xFF121212)
val Surface1 = Color(0xFF1C1C1C)
val Surface2 = Color(0xFF2A2A2A)
val Surface3 = Color(0xFF3A3A3A)
val Coral = Color(0xFFFF5A36)
val CoralDeep = Color(0xFFE0391B)
val Violet = Color(0xFF7C5CFF)
val TextDim = Color(0xFFB3B3B3)

private val scheme = darkColorScheme(
    primary = Coral,
    onPrimary = Color.Black,
    secondary = Violet,
    background = Night,
    onBackground = Color.White,
    surface = Night,
    onSurface = Color.White,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerLow = Surface1,
    secondaryContainer = Surface2,
    onSecondaryContainer = Color.White,
    outline = Surface3,
)

private val base = Typography()

private val typography = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
)

/** Dark theme; default text is white so untinted Text is readable on the dark background. */
@Composable
fun CapTunesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}
