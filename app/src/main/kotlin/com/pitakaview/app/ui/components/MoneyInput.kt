package com.pitakaview.app.ui.components

import androidx.compose.foundation.text.input.InputTransformation

private val MoneyPattern = Regex("""^\d{0,9}(\.\d{0,2})?$""")

/** For every peso field: digits with at most one dot and two decimals. Anything else typed or pasted is refused. */
val MoneyInput = InputTransformation { if (!MoneyPattern.matches(asCharSequence())) revertAllChanges() }
