package com.pitakaview.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.theme.PitakaDimens

/**
 * The sketch look in one modifier: a solid shadow offset down-right, the fill, and a thick outline.
 * The shadow is drawn outside this node's bounds, so callers reserve room for it with
 * `padding(end = offset, bottom = offset)` *before* this modifier.
 */
fun Modifier.sketchBox(
    shape: Shape,
    fill: Color,
    border: Color,
    borderWidth: Dp = PitakaDimens.border,
    shadow: Color = Color.Unspecified,
    shadowOffset: Dp = 0.dp,
): Modifier = this
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val offset = shadowOffset.toPx()
        if (offset > 0f && shadow.isSpecified) {
            translate(offset, offset) { drawOutline(outline, shadow) }
        }
        drawOutline(outline, fill)
    }
    .border(borderWidth, border, shape)
