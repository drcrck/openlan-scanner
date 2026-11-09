package io.github.drcrck.openlan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.drcrck.openlan.R

// Palette del marchio: blu notte + verde acqua, con un accento ambra.
object Brand {
    val Night = Color(0xFF0B1E3A)
    val Deep = Color(0xFF0D3B57)
    val Teal = Color(0xFF14B8A6)
    val TealBright = Color(0xFF5EEAD4)
    val Amber = Color(0xFFFFC75F)
    val Online = Color(0xFF22C55E)
    val Offline = Color(0xFF94A3B8)

    /** Testo secondario sull'intestazione: opaco, contrasto ≥ 4.5:1 su tutta la sfumatura. */
    val HeroText = Color(0xFFD3E2EA)

    /** Sfondo dell'intestazione, uguale in tema chiaro e scuro. */
    val heroGradient = Brush.linearGradient(listOf(Night, Deep, Color(0xFF0C5C5E)))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F3E8),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF2E4A7D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6FF),
    onSecondaryContainer = Color(0xFF0B1E3A),
    tertiary = Color(0xFF8A5100),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF4F7FA),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF52606D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEDF2F7),
    outline = Color(0xFFB4C0CC),
    outlineVariant = Color(0xFFE1E7EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFA7F3E8),
    secondary = Color(0xFFB6C6FF),
    onSecondary = Color(0xFF14295A),
    secondaryContainer = Color(0xFF243A6B),
    onSecondaryContainer = Color(0xFFDCE6FF),
    tertiary = Color(0xFFFFC75F),
    tertiaryContainer = Color(0xFF5C3A00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF070F1D),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF070F1D),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF9AA8BA),
    surfaceContainerLowest = Color(0xFF050B16),
    surfaceContainerLow = Color(0xFF0C1626),
    surfaceContainer = Color(0xFF101C2E),
    surfaceContainerHigh = Color(0xFF142236),
    surfaceContainerHighest = Color(0xFF1B2A40),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF1F2E44),
)

@OptIn(ExperimentalTextApi::class)
private fun grotesk(w: Int) = Font(
    R.font.space_grotesk, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

@OptIn(ExperimentalTextApi::class)
private fun mono(w: Int) = Font(
    R.font.jetbrains_mono, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

/** Font dei titoli (Space Grotesk, licenza OFL). */
val Display = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** Font per IP, MAC e numeri (JetBrains Mono, licenza OFL). */
val Mono = FontFamily(mono(400), mono(500), mono(700))

private val Base = Typography()
private fun TextStyle.display(weight: FontWeight = FontWeight.SemiBold) = copy(fontFamily = Display, fontWeight = weight)

private val AppTypography = Base.copy(
    displaySmall = Base.displaySmall.display(FontWeight.Bold),
    headlineLarge = Base.headlineLarge.display(FontWeight.Bold),
    headlineMedium = Base.headlineMedium.display(FontWeight.Bold),
    headlineSmall = Base.headlineSmall.display(FontWeight.Bold),
    titleLarge = Base.titleLarge.display(FontWeight.Bold),
    titleMedium = Base.titleMedium.display(),
    titleSmall = Base.titleSmall.display(),
    labelLarge = Base.labelLarge.display(),
    labelMedium = Base.labelMedium.copy(letterSpacing = 0.3.sp),
)

/** Tema dell'app: colori del marchio (niente colori dinamici, per un'identità riconoscibile). */
@Composable
fun OpenLanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
