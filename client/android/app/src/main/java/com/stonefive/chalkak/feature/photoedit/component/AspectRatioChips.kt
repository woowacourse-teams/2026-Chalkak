package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.photoedit.CropAspectRatio
import com.stonefive.chalkak.feature.photoedit.label

@Composable
fun AspectRatioChips(
    selectedAspectRatio: CropAspectRatio,
    onAspectRatioSelect: (CropAspectRatio) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
    ) {
        CropAspectRatio.entries.forEach { aspectRatio ->
            val selected = aspectRatio == selectedAspectRatio
            Text(
                text = aspectRatio.label,
                modifier = Modifier
                    .clip(ChalkakTheme.shapes.pill)
                    .background(if (selected) ChalkakTheme.colors.photoEditContent else Color.Transparent)
                    .border(ChipBorderWidth, ChalkakTheme.colors.photoEditContentMuted, ChalkakTheme.shapes.pill)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onAspectRatioSelect(aspectRatio) },
                    ).padding(horizontal = ChalkakTheme.spacing.md, vertical = ChalkakTheme.spacing.xs),
                style = ChalkakTheme.typography.footnote,
                color = if (selected) ChalkakTheme.colors.photoEditBackground else ChalkakTheme.colors.photoEditContent,
            )
        }
    }
}

private val ChipBorderWidth = 1.dp

@Preview(widthDp = 360)
@Composable
private fun AspectRatioChipsPreview() {
    ChalkakTheme {
        AspectRatioChips(
            selectedAspectRatio = CropAspectRatio.Square,
            onAspectRatioSelect = {},
            modifier = Modifier
                .background(ChalkakTheme.colors.photoEditBackground)
                .padding(ChalkakTheme.spacing.sm),
        )
    }
}
