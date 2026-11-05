package com.zl.qiewash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * Countdown text with a real axis: [weight] is a continuous target, and every time
 * [introKey] changes the weight sweeps down from [introFrom] once — the "look at me"
 * flourish before it settles into the slow progress-driven drift.
 */
@Composable
fun ClockText(
    text: String,
    weight: Float,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    introKey: Any? = null,
    introFrom: Float = 950f,
    introMs: Int = 340,
) {
    val steady by animateFloatAsState(
        targetValue = weight,
        animationSpec = tween(600),
        label = "clockWeight",
    )
    val intro = remember { Animatable(0f) }
    LaunchedEffect(introKey) {
        intro.snapTo(1f)
        intro.animateTo(0f, tween(introMs, easing = FastOutSlowInEasing))
    }
    val shown = lerp(steady, introFrom, intro.value)
    Text(
        text,
        modifier = modifier,
        color = color,
        maxLines = 1,
        softWrap = false,
        style = style.copy(
            fontFamily = ClockFamily,
            fontWeight = FontWeight(((shown / 10f).roundToInt() * 10).coerceIn(200, 950)),
            // Digits have no descender, so the stock leading pushes the glyphs up in the
            // row. Trimming both ends makes the visual centre match the layout centre.
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
}
