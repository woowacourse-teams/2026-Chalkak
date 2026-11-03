package com.stonefive.chalkak.core.designsystem.component.input

import android.icu.text.BreakIterator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/**
 * 텍스트 길이를 어떤 단위로 세고 제한할지 정한다.
 *
 * 서버 검증 기준과 UI 제한·카운터를 맞추기 위한 값이다. 기본값은 사용자가 눈으로 세는 글자 수(grapheme)이며,
 * 서버가 다른 단위로 검증하는 화면은 그 단위를 골라 맞춘다.
 */
enum class ChalkakTextLengthMetric {
    /** 사람이 인식하는 글자(grapheme cluster) 수. */
    CHARACTERS,

    /** Unicode code point 수. 서버가 code point로 길이를 검증할 때 사용한다. */
    CODE_POINTS,
}

/**
 * 입력칸 모양. [OUTLINED]는 배경과 테두리가 있는 기본 모양이고,
 * [PLAIN]은 배경·테두리·안쪽 여백 없이 글자와 글자 수만 그려 글 쓰기 화면처럼 넓게 쓴다.
 */
enum class ChalkakTextFieldStyle {
    OUTLINED,
    PLAIN,
}

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
    lengthMetric: ChalkakTextLengthMetric = ChalkakTextLengthMetric.CHARACTERS,
    showCharacterCount: Boolean = maxLength != null,
    style: ChalkakTextFieldStyle = ChalkakTextFieldStyle.OUTLINED,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val characterCount = remember(value, lengthMetric) { value.length(lengthMetric) }
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
                maxLength?.let { changedValue.limited(it, lengthMetric) } ?: changedValue,
            )
        },
        modifier = when (style) {
            ChalkakTextFieldStyle.OUTLINED ->
                modifier
                    .background(
                        color = ChalkakTheme.colors.inputBackground,
                        shape = ChalkakTheme.shapes.input,
                    ).border(
                        width = 1.dp,
                        color = borderColor,
                        shape = ChalkakTheme.shapes.input,
                    ).padding(16.dp)

            ChalkakTextFieldStyle.PLAIN -> modifier
        },
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
        cursorBrush = SolidColor(ChalkakTheme.colors.inputCursor),
        decorationBox = { innerTextField ->
            val inputContent: @Composable (Modifier) -> Unit = { inputModifier ->
                Box(modifier = inputModifier) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = ChalkakTheme.colors.textInactive,
                            style = resolvedTextStyle,
                        )
                    }
                    innerTextField()
                }
            }
            val counter: @Composable (Modifier) -> Unit = { counterModifier ->
                if (showCharacterCount && maxLength != null) {
                    Text(
                        text = "$characterCount / $maxLength",
                        modifier = counterModifier,
                        color = ChalkakTheme.colors.textInactive,
                        style = if (style == ChalkakTextFieldStyle.PLAIN) {
                            ChalkakTheme.typography.caption
                        } else {
                            ChalkakTheme.typography.subheadline
                        },
                    )
                }
            }

            if (style == ChalkakTextFieldStyle.PLAIN) {
                // 글자 수는 입력 아래 오른쪽에 둔다.
                Column(horizontalAlignment = Alignment.End) {
                    inputContent(Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
                    counter(Modifier)
                }
            } else if (singleLine) {
                // 한 줄 입력은 글자 수를 같은 줄 오른쪽에 두어 한 줄 높이를 유지한다.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    inputContent(Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(ChalkakTheme.spacing.sm))
                    counter(Modifier)
                }
            } else {
                Box {
                    inputContent(
                        if (showCharacterCount) Modifier.padding(bottom = 24.dp) else Modifier,
                    )
                    counter(Modifier.align(Alignment.BottomEnd))
                }
            }
        },
    )
}

internal fun String.length(metric: ChalkakTextLengthMetric): Int = when (metric) {
    ChalkakTextLengthMetric.CHARACTERS -> graphemeCount()
    ChalkakTextLengthMetric.CODE_POINTS -> codePointCount(0, length)
}

internal fun String.limited(
    maxLength: Int,
    metric: ChalkakTextLengthMetric,
): String = when (metric) {
    ChalkakTextLengthMetric.CHARACTERS -> takeGraphemes(maxLength)
    ChalkakTextLengthMetric.CODE_POINTS -> takeCodePoints(maxLength)
}

private fun String.takeCodePoints(count: Int): String {
    if (codePointCount(0, length) <= count) return this

    // 글자 중간에서 자르면 국기·피부색 이모지가 깨지므로, 글자 단위로 담다가 합이 넘기 전에 멈춘다.
    // JVM 단위 테스트에서도 쓸 수 있도록 java.text 구현을 쓴다. 기기에서는 ICU 기반으로 동작한다.
    val iterator = java.text.BreakIterator.getCharacterInstance().apply {
        setText(this@takeCodePoints)
    }
    var start = iterator.first()
    var end = iterator.next()
    var usedCount = 0
    while (end != java.text.BreakIterator.DONE) {
        val size = codePointCount(start, end)
        if (usedCount + size > count) break
        usedCount += size
        start = end
        end = iterator.next()
    }
    return substring(0, start)
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
