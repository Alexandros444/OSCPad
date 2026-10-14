package com.oscpad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oscpad.model.NumberStep
import java.math.BigDecimal

/**
 * Numeric text field with ▲/▼ buttons that add or subtract [step] (1 for integers, 0.1 for
 * floats), clamped to [min]..[max]. Typing stays possible for exact values.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    step: BigDecimal,
    modifier: Modifier = Modifier,
    integer: Boolean = false,
    min: BigDecimal? = null,
    max: BigDecimal? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
        trailingIcon = {
            Column {
                StepButton("▲") { onValueChange(NumberStep.step(value, step, up = true, min = min, max = max)) }
                StepButton("▼") { onValueChange(NumberStep.step(value, step, up = false, min = min, max = max)) }
            }
        },
    )
}

@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 44.dp, height = 26.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 13.sp)
    }
}
