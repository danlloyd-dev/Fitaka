package com.pitakaview.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section

/**
 * A labelled text field in the sketch style, on Compose's state-based text API ([TextFieldState]).
 * It sits flat until focused, then lifts onto its shadow. Errors show a Gastos-red outline plus "!"
 * text in ink, so the state never relies on colour alone.
 */
@Composable
fun PitakaTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    inputTransformation: InputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shadow = PitakaDimens.shadow
    val lift by animateDpAsState(if (focused) shadow else 0.dp, label = "lift")
    val outline = if (isError) c.fill(Section.GASTOS) else c.ink

    Column(modifier.semantics(mergeDescendants = true) {}) {
        // The label is drawn above the box for the eye. A screen reader gets it from the box itself (below),
        // so it hears "Name, edit box" once instead of a loose word followed by a nameless box.
        Text(
            label, style = type.labelLarge, color = c.ink,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).semantics { hideFromAccessibility() },
        )
        BasicTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = shadow, bottom = shadow)
                .semantics {
                    contentDescription = label
                    // What is wrong is spoken with the box, not only shown under it.
                    if (isError && supportingText != null) error(supportingText)
                },
            inputTransformation = inputTransformation,
            textStyle = type.bodyLarge.copy(color = c.ink),
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            lineLimits = lineLimits,
            interactionSource = interaction,
            cursorBrush = SolidColor(c.ink),
            decorator = TextFieldDecorator { innerTextField ->
                Row(
                    modifier = Modifier
                        .sketchBox(
                            shape = RoundedCornerShape(PitakaDimens.corner),
                            fill = c.card,
                            border = outline,
                            borderWidth = if (focused || isError) 3.dp else PitakaDimens.border,
                            shadow = c.shadow,
                            shadowOffset = lift,
                            glass = true,
                        )
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (prefix != null) {
                        Text(
                            prefix,
                            style = type.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = c.ink,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        if (state.text.isEmpty() && placeholder != null) {
                            Text(placeholder, style = type.bodyLarge, color = c.inkMuted)
                        }
                        innerTextField()
                    }
                }
            },
        )
        if (supportingText != null) {
            Text(
                text = if (isError) "! $supportingText" else supportingText,
                style = type.bodySmall.copy(fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal),
                color = if (isError) c.ink else c.inkMuted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
