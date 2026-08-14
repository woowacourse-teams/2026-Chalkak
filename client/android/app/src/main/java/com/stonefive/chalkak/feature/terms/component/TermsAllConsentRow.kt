package com.stonefive.chalkak.feature.terms.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
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

@Preview(showBackground = true, widthDp = 352)
@Composable
private fun TermsAllConsentRowPreview() {
    ChalkakTheme {
        TermsAllConsentRow(
            modifier = Modifier.fillMaxWidth(),
            checked = false,
            onClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 352)
@Composable
private fun TermsAllConsentRowCheckedPreview() {
    ChalkakTheme {
        TermsAllConsentRow(
            modifier = Modifier.fillMaxWidth(),
            checked = true,
            onClick = {},
        )
    }
}
