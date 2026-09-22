package com.stonefive.chalkak.feature.reminder.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun ReminderTimeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val contentColor = if (selected) {
        ChalkakTheme.colors.onActionPrimary
    } else {
        ChalkakTheme.colors.textPrimary
    }

    Surface(
        modifier = modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        shape = ChalkakTheme.shapes.input,
        color = if (selected) {
            ChalkakTheme.colors.actionPrimary
        } else {
            ChalkakTheme.colors.surfaceElevated
        },
        contentColor = contentColor,
        border = if (selected) {
            null
        } else {
            BorderStroke(
                width = 1.dp,
                color = ChalkakTheme.colors.border,
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = TIME_OPTION_HORIZONTAL_PADDING,
                    vertical = TIME_OPTION_VERTICAL_PADDING,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = contentColor,
                style = ChalkakTheme.typography.body,
            )

            description?.let { text ->
                Text(
                    text = text,
                    color = if (selected) {
                        ChalkakTheme.colors.onActionPrimary
                    } else {
                        ChalkakTheme.colors.textInactive
                    },
                    style = ChalkakTheme.typography.subheadline,
                )
            }
        }
    }
}

private val TIME_OPTION_HORIZONTAL_PADDING = 19.dp
private val TIME_OPTION_VERTICAL_PADDING = 18.dp

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ReminderTimeOptionRowPreview() {
    ChalkakTheme {
        ReminderTimeOptionRow(
            label = "저녁 18:00",
            description = "해 질 무렵",
            selected = true,
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
