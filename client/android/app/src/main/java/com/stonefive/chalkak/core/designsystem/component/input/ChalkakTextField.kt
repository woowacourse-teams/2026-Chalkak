package com.stonefive.chalkak.core.designsystem.component.input

import android.icu.text.BreakIterator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakInputBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTextInactive
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun ChalkakTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    maxLength: Int? = null,
    showCharacterCount: Boolean = maxLength != null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val characterCount = remember(value) { value.graphemeCount() }
    val resolvedTextStyle = (textStyle ?: ChalkakTheme.typography.body).copy(
        color = if (enabled) {
            ChalkakTheme.colors.textPrimary
        } else {
            ChalkakTheme.colors.textMuted
        },
    )
    val borderColor = if (isFocused) {
        ChalkakTheme.colors.actionPrimary
    } else {
        ChalkakTheme.colors.border
    }

    BasicTextField(
        value = value,
        onValueChange = { changedValue ->
            onValueChange(
                maxLength?.let(changedValue::takeGraphemes) ?: changedValue,
            )
        },
        modifier = modifier
            .background(
                color = ChalkakInputBackground,
                shape = ChalkakTheme.shapes.input,
            ).border(
                width = 1.dp,
                color = borderColor,
                shape = ChalkakTheme.shapes.input,
            ).padding(16.dp),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = resolvedTextStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(Color(0xFFB0563B)),
        decorationBox = { innerTextField ->
            Box {
                Box(
                    modifier = if (showCharacterCount) {
                        Modifier.padding(bottom = 24.dp)
                    } else {
                        Modifier
                    },
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = ChalkakTextInactive,
                            style = resolvedTextStyle,
                        )
                    }
                    innerTextField()
                }

                if (showCharacterCount && maxLength != null) {
                    Text(
                        text = "$characterCount / $maxLength",
                        modifier = Modifier.align(Alignment.BottomEnd),
                        color = ChalkakTextInactive,
                        style = ChalkakTheme.typography.subheadline,
                    )
                }
            }
        },
    )
}

private fun String.graphemeCount(): Int {
    val iterator = BreakIterator.getCharacterInstance().apply {
        setText(this@graphemeCount)
    }
    var count = 0

    while (iterator.next() != BreakIterator.DONE) {
        count++
    }

    return count
}

private fun String.takeGraphemes(count: Int): String {
    if (count == 0 || isEmpty()) return ""

    val iterator = BreakIterator.getCharacterInstance().apply {
        setText(this@takeGraphemes)
    }
    var endIndex = iterator.first()

    repeat(count) {
        val nextIndex = iterator.next()
        if (nextIndex == BreakIterator.DONE) return this
        endIndex = nextIndex
    }

    return substring(0, endIndex)
}

@Preview(showBackground = true)
@Composable
private fun ChalkakTextFieldPreview() {
    ChalkakTheme {
        ChalkakTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.size(
                width = 320.dp,
                height = 148.dp,
            ),
            placeholder = "한 줄은 선택이에요.",
            maxLength = 50,
        )
    }
}
