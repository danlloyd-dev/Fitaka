package com.pitakaview.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

/**
 * The two springs the app moves with (the owner, 2026-11-27: "improve the animation and transition. make
 * it interactive and smooth"). A spring carries on from wherever a part is and however fast it is going,
 * so a second tap in the middle of a movement never makes it jump, which a timed curve does.
 * The phone's "Remove animations" setting makes both instant, as it does every other movement here.
 */
object FitakaSpring {
    // One of each for the whole app: a spring holds no value of its own, so the same one serves every kind
    // of value, and nothing is made anew each time a part is composed.
    private val Bouncy = spring<Any>(dampingRatio = 0.55f, stiffness = 520f)
    private val Smooth = spring<Any>(dampingRatio = 0.85f, stiffness = 420f)

    /** Lands with one small bounce: a part popping back up under the finger, a tile rising, a knob arriving. */
    @Suppress("UNCHECKED_CAST")
    fun <T> bouncy(): SpringSpec<T> = Bouncy as SpringSpec<T>

    /** Lands without going past: something that slides into place. */
    @Suppress("UNCHECKED_CAST")
    fun <T> smooth(): SpringSpec<T> = Smooth as SpringSpec<T>
}

/** How long a part takes to go down under the finger, in milliseconds. */
const val PRESS_IN = 90

/** How much smaller a part with no shadow to sink into (a chip) gets while it is held. */
const val PRESS_SQUISH = 0.05f

/**
 * How far down a part is under the finger: 0 at rest, 1 held down. It goes down quickly when touched and
 * springs back with a small bounce when let go (so it dips a little below 0 on the way), and a quick tap
 * that lets go before the part is down still takes it all the way down first: every tap is felt.
 *
 * Buttons and tappable cards multiply it by their shadow's depth and sink into the shadow; a chip, which
 * has no shadow, shrinks by [PRESS_SQUISH].
 */
@Composable
fun rememberPressDepth(interaction: InteractionSource, enabled: Boolean = true): State<Float> {
    val depth = remember { Animatable(0f) }
    LaunchedEffect(interaction, enabled) {
        if (!enabled) {
            depth.snapTo(0f)
            return@LaunchedEffect
        }
        // Only presses: a focus or hover event must not cut a press short and leave the part half way down.
        interaction.interactions.filterIsInstance<PressInteraction>().collectLatest { event ->
            if (event is PressInteraction.Press) {
                depth.animateTo(1f, tween(PRESS_IN, easing = LinearOutSlowInEasing))
            } else {
                val left = (PRESS_IN * (1f - depth.value)).toInt()
                if (left > 0) depth.animateTo(1f, tween(left, easing = LinearOutSlowInEasing))
                depth.animateTo(0f, FitakaSpring.bouncy())
            }
        }
    }
    return depth.asState()
}
