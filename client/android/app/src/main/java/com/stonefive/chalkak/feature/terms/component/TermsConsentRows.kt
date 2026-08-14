package com.stonefive.chalkak.feature.terms.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.component.checkbox.ChalkakCheckbox
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

private val TermsCardShape = RoundedCornerShape(16.dp)
private val TermsCardBorder = Color(0xFFE3E1DD)

@Composable
internal fun TermsAllConsentRow(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TermsCardShape)
            .border(BorderStroke(1.dp, TermsCardBorder), TermsCardShape)
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            ).padding(
                horizontal = 16.dp,
                vertical = 19.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ChalkakCheckbox(checked = checked)
        Text(
            text = "전체 동의",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.body,
        )
    }
}

@Composable
internal fun TermsRequiredConsentRow(
    text: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    onViewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .toggleable(
                    value = checked,
                    interactionSource = null,
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = { onCheckedChange() },
                ).padding(
                    start = 4.dp,
                    top = 16.dp,
                    bottom = 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChalkakCheckbox(checked = checked)
            Text(
                text = text,
                color = ChalkakTheme.colors.textPrimary
                    .copy(alpha = 0.85f),
                style = ChalkakTheme.typography.body,
            )
        }

        Text(
            text = "보기",
            color = ChalkakTheme.colors.textMuted,
            style = ChalkakTheme.typography.callout,
            modifier = Modifier
                .clickable(
                    interactionSource = viewInteractionSource,
                    indication = null,
                    onClick = onViewClick,
                ).padding(
                    start = 12.dp,
                    top = 16.dp,
                    end = 4.dp,
                    bottom = 16.dp,
                ).semantics { role = Role.Button },
        )
    }
}

@Composable
internal fun TermsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ChalkakTheme.colors.border),
    )
}
