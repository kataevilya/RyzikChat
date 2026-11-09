package app.ryzik.chat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.ryzik.chat.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import app.ryzik.chat.data.AppSettings
import app.ryzik.chat.data.ThemeMode

/** Цвета, из которых можно собрать тему, если Material You (цвета обоев) выключен. */
val SeedColors = listOf(
    Color(0xFFFF8A3D) to "Рыжик",
    Color(0xFF6750A4) to "Лаванда",
    Color(0xFF006A6A) to "Бирюза",
    Color(0xFF2E6BE6) to "Небо",
    Color(0xFF3A7D2C) to "Лес",
    Color(0xFFD0346A) to "Малина",
    Color(0xFF8B5E3C) to "Какао",
    Color(0xFF5C5F66) to "Графит",
    // Цвета ниже — только для Премиума
    Color(0xFF9C4DFF) to "Аметист",
    Color(0xFFFF4F8B) to "Неон",
    Color(0xFFC9A227) to "Золото",
    Color(0xFF00B8D4) to "Лёд",
)

/** С этого индекса в [SeedColors] начинаются премиум-цвета. */
const val PREMIUM_SEEDS_FROM = 8

private fun tone(seed: Color, lightness: Float, satMul: Float = 1f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[1] = (hsl[1] * satMul).coerceIn(0f, 1f)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun shiftHue(seed: Color, deg: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[0] = (hsl[0] + deg + 360f) % 360f
    return Color(ColorUtils.HSLToColor(hsl))
}

fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val secondary = tone(seed, 0.5f, 0.35f)
    val tertiary = shiftHue(seed, 60f)
    return if (!dark) lightColorScheme(
        primary = tone(seed, 0.40f),
        onPrimary = Color.White,
        primaryContainer = tone(seed, 0.88f),
        onPrimaryContainer = tone(seed, 0.12f),
        secondary = tone(secondary, 0.40f),
        onSecondary = Color.White,
        secondaryContainer = tone(seed, 0.90f, 0.4f),
        onSecondaryContainer = tone(seed, 0.12f, 0.4f),
        tertiary = tone(tertiary, 0.40f),
        tertiaryContainer = tone(tertiary, 0.88f),
        onTertiaryContainer = tone(tertiary, 0.12f),
        background = tone(seed, 0.985f, 0.3f),
        surface = tone(seed, 0.985f, 0.3f),
        surfaceVariant = tone(seed, 0.90f, 0.15f),
        onSurfaceVariant = tone(seed, 0.30f, 0.1f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = tone(seed, 0.96f, 0.3f),
        surfaceContainer = tone(seed, 0.945f, 0.3f),
        surfaceContainerHigh = tone(seed, 0.925f, 0.3f),
        surfaceContainerHighest = tone(seed, 0.90f, 0.3f),
        outline = tone(seed, 0.50f, 0.1f),
        outlineVariant = tone(seed, 0.80f, 0.1f),
    ) else darkColorScheme(
        primary = tone(seed, 0.78f),
        onPrimary = tone(seed, 0.20f),
        primaryContainer = tone(seed, 0.30f),
        onPrimaryContainer = tone(seed, 0.90f),
        secondary = tone(secondary, 0.78f),
        secondaryContainer = tone(seed, 0.25f, 0.4f),
        onSecondaryContainer = tone(seed, 0.90f, 0.4f),
        tertiary = tone(tertiary, 0.78f),
        tertiaryContainer = tone(tertiary, 0.30f),
        onTertiaryContainer = tone(tertiary, 0.90f),
        background = tone(seed, 0.07f, 0.3f),
        surface = tone(seed, 0.07f, 0.3f),
        surfaceVariant = tone(seed, 0.25f, 0.15f),
        onSurfaceVariant = tone(seed, 0.80f, 0.1f),
        surfaceContainerLowest = tone(seed, 0.04f, 0.3f),
        surfaceContainerLow = tone(seed, 0.09f, 0.3f),
        surfaceContainer = tone(seed, 0.11f, 0.3f),
        surfaceContainerHigh = tone(seed, 0.14f, 0.3f),
        surfaceContainerHighest = tone(seed, 0.17f, 0.3f),
        outline = tone(seed, 0.55f, 0.1f),
        outlineVariant = tone(seed, 0.30f, 0.1f),
    )
}

/** Nunito — мягкий округлый шрифт (OFL). Один вариативный файл, толщины задаём осью wght. */
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Rounded = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800))

private fun TextStyle.rounded() = copy(fontFamily = Rounded)

private val AppTypography = Typography().let { base ->
    val t = base.copy(
        displayLarge = base.displayLarge.rounded(), displayMedium = base.displayMedium.rounded(), displaySmall = base.displaySmall.rounded(),
        headlineLarge = base.headlineLarge.rounded(), headlineMedium = base.headlineMedium.rounded(), headlineSmall = base.headlineSmall.rounded(),
        titleLarge = base.titleLarge.rounded(), titleMedium = base.titleMedium.rounded(), titleSmall = base.titleSmall.rounded(),
        bodyLarge = base.bodyLarge.rounded(), bodyMedium = base.bodyMedium.rounded(), bodySmall = base.bodySmall.rounded(),
        labelLarge = base.labelLarge.rounded(), labelMedium = base.labelMedium.rounded(), labelSmall = base.labelSmall.rounded(),
    )
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.copy(fontWeight = FontWeight.Medium),
        bodyMedium = t.bodyMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.1.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun RyzikTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    var scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> schemeFromSeed(SeedColors[settings.seedColor.coerceIn(SeedColors.indices)].first, dark)
    }
    if (dark && settings.amoled) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF111111),
        )
    }
    val animated = scheme.animated()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = animated, typography = AppTypography, shapes = AppShapes) {
        // Цвет текста по умолчанию берём из темы: иначе текст вне Surface/Scaffold
        // (экран входа, приветствие) остаётся чёрным даже в тёмной теме.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides animated.onSurface,
            content = content,
        )
    }
}

/** Плавная смена цветов при переключении темы. */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = tween<Color>(500)
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "scheme").value
    return copy(
        primary = a(primary), onPrimary = a(onPrimary),
        primaryContainer = a(primaryContainer), onPrimaryContainer = a(onPrimaryContainer),
        secondary = a(secondary), secondaryContainer = a(secondaryContainer), onSecondaryContainer = a(onSecondaryContainer),
        tertiary = a(tertiary), tertiaryContainer = a(tertiaryContainer), onTertiaryContainer = a(onTertiaryContainer),
        background = a(background), onBackground = a(onBackground),
        surface = a(surface), onSurface = a(onSurface),
        surfaceVariant = a(surfaceVariant), onSurfaceVariant = a(onSurfaceVariant),
        surfaceContainerLowest = a(surfaceContainerLowest), surfaceContainerLow = a(surfaceContainerLow),
        surfaceContainer = a(surfaceContainer), surfaceContainerHigh = a(surfaceContainerHigh),
        surfaceContainerHighest = a(surfaceContainerHighest),
        outline = a(outline), outlineVariant = a(outlineVariant),
    )
}

/** Обои чата: мягкие градиенты в цветах темы. */
@Composable
fun wallpaperColors(index: Int): List<Color> {
    val c = MaterialTheme.colorScheme
    return when (index) {
        1 -> listOf(c.primaryContainer.copy(alpha = 0.55f), c.tertiaryContainer.copy(alpha = 0.55f))
        2 -> listOf(c.secondaryContainer.copy(alpha = 0.6f), c.surface)
        3 -> listOf(c.tertiaryContainer.copy(alpha = 0.5f), c.surface, c.primaryContainer.copy(alpha = 0.5f))
        4 -> listOf(c.surfaceContainerHighest, c.surfaceContainerLowest)
        else -> listOf(c.surface, c.surface)
    }
}

val WallpaperNames = listOf("Без обоев", "Закат", "Туман", "Аврора", "Графит")
