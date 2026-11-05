package com.zl.qiewash.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.zl.qiewash.R

private val Mint = Color(0xFF006B58)

/** Deep brand green, always dark regardless of theme, for the running banner. */
val QwDeep = Color(0xFF0B3B2E)
private val MintLight = Color(0xFF4FD1B0)
private val Ink = Color(0xFF0B1F1A)

private val LightColors = lightColorScheme(
    primary = Mint,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF7CF8D5),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4A635B),
    secondaryContainer = Color(0xFFCCE8DE),
    onSecondaryContainer = Color(0xFF06201A),
    surface = Color(0xFFF5FBF8),
    surfaceVariant = Color(0xFFDBE5E1),
    onSurfaceVariant = Color(0xFF3F4945),
    background = Color(0xFFF5FBF8),
)

private val DarkColors = darkColorScheme(
    primary = MintLight,
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF00513E),
    onPrimaryContainer = Color(0xFF7CF8D5),
    secondary = Color(0xFFB1CCC2),
    secondaryContainer = Color(0xFF334B44),
    onSecondaryContainer = Color(0xFFCCE8DE),
    surface = Color(0xFF0E1513),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    background = Color(0xFF0B1F1A),
    onBackground = Color(0xFFE1E3DF),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Expressive corner radii follow element nesting: bigger for outer surfaces, smaller
 * for the pieces inside them (concentric: inner = outer - inset).
 */
object QwShape {
    val hero = RoundedCornerShape(32.dp)      // top-level hero surface
    val card = RoundedCornerShape(24.dp)      // list cards / device cards
    val inner = RoundedCornerShape(16.dp)     // containers inside a card
    val chip = RoundedCornerShape(10.dp)      // labels, status chips, badges
    val button = RoundedCornerShape(20.dp)

    /**
     * Grouped-list corners: only the first row keeps the big top radius and only the
     * last row the big bottom radius; rows in between get a small radius, so the group
     * reads as one surface split into rows.
     */
    fun group(index: Int, count: Int, big: Dp = 24.dp, small: Dp = 6.dp): Shape {
        val top = if (index == 0) big else small
        val bottom = if (index == count - 1) big else small
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }
}

@Composable
fun QieWashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}

/**
 * Roboto Flex subset (digits + colon) with the wght axis kept. One entry per 10 units
 * makes the weight read as continuous while still letting Compose cache typefaces.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val ClockFamily = FontFamily(
    (200..900 step 10).map { w ->
        Font(
            R.font.clock_var,
            weight = FontWeight(w),
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    }
)
