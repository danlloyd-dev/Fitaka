package com.pitakaview.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.theme.PitakaTheme

/**
 * One set of motion values for the whole app, so every transition feels related.
 * Compose scales these by the phone's animator duration setting ("Remove animations" = instant).
 */
object PitakaMotion {
    const val SHORT = 150
    const val MEDIUM = 300
    const val LONG = 450

    /** Fast start, gentle landing: used for anything entering the screen. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Quick exit. */
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * A peso amount that counts smoothly from its previous value to [centavos].
 * Interpolates a 0→1 progress, so the value it settles on is always exactly [centavos].
 */
@Composable
fun AnimatedAmountText(
    centavos: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    signed: Boolean = false,
) {
    var from by remember { mutableLongStateOf(centavos) }
    var to by remember { mutableLongStateOf(centavos) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(centavos) {
        if (centavos != to) {
            from = from + ((to - from) * progress.value).toLong()
            to = centavos
            progress.snapTo(0f)
            progress.animateTo(1f, tween(PitakaMotion.LONG + 150, easing = PitakaMotion.Emphasized))
        }
    }
    val shown = if (progress.value >= 1f) to else from + ((to - from) * progress.value).toLong()
    val text = if (signed && shown > 0) "+${Peso.format(shown)}" else Peso.format(shown)
    Text(text, modifier = modifier, style = style, color = PitakaTheme.colors.ink, maxLines = 1)
}
