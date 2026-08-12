package com.stonefive.chalkak.core.designsystem.component.sort

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTextInactive
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

// 26/08/12 (#14) 추후 이동 필요 + 백엔드에 맞게 Enum 수정할 것 + toString과 같은 로직 생각해 볼 것
enum class ChalkakSortOption(val label: String) {
    LATEST("최신순"),
    POPULAR("인기순"),
    RANDOM("랜덤순"),
}

@Composable
fun ChalkakSortSelector(
    selectedOption: ChalkakSortOption,
    onOptionSelected: (ChalkakSortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.selectableGroup(),
    ) {
        ChalkakSortOption.entries.forEach { option ->
            ChalkakSortItem(
                option = option,
                selected = option == selectedOption,
                onClick = { onOptionSelected(option) },
            )
        }
    }
}

@Composable
private fun ChalkakSortItem(
    option: ChalkakSortOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) {
        ChalkakTheme.colors.textPrimary
    } else {
        ChalkakTextInactive
    }

    Box(
        modifier = modifier
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ).padding(10.dp),
    ) {
        Text(
            text = option.label,
            color = color,
            style = ChalkakTheme.typography.subheadline,
            modifier = Modifier.drawBehind {
                if (selected) {
                    val strokeWidth = 1.dp.toPx()
                    val strokeY = size.height + 4.5.dp.toPx() + strokeWidth / 2
                    drawLine(
                        color = color,
                        start = Offset(0f, strokeY),
                        end = Offset(size.width, strokeY),
                        strokeWidth = strokeWidth,
                    )
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChalkakSortSelectorPreview() {
    ChalkakTheme {
        var selectedOption by remember {
            mutableStateOf(ChalkakSortOption.LATEST)
        }

        ChalkakSortSelector(
            selectedOption = selectedOption,
            onOptionSelected = { selectedOption = it },
        )
    }
}
