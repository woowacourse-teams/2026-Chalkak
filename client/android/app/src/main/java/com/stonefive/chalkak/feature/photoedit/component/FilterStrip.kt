package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PhotoAdjustments
import com.stonefive.chalkak.domain.model.PhotoEdit
import com.stonefive.chalkak.domain.model.PhotoFilter
import com.stonefive.chalkak.feature.photoedit.label
import com.stonefive.chalkak.feature.photoedit.toColorFilter

/** 필터 썸네일 목록. 같은 작은 이미지에 지금 조정 값과 각 필터를 합친 색만 바꿔 그린다. */
@Composable
fun FilterStrip(
    imageModel: Any?,
    selectedFilter: PhotoFilter,
    onFilterSelect: (PhotoFilter) -> Unit,
    modifier: Modifier = Modifier,
    adjustments: PhotoAdjustments = PhotoAdjustments(),
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyRow(
        modifier = modifier.selectableGroup(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.md),
    ) {
        items(PhotoFilter.entries, key = { it.name }) { filter ->
            FilterThumbnail(
                imageModel = imageModel,
                filter = filter,
                adjustments = adjustments,
                selected = filter == selectedFilter,
                onClick = { onFilterSelect(filter) },
            )
        }
    }
}

@Composable
private fun FilterThumbnail(
    imageModel: Any?,
    filter: PhotoFilter,
    adjustments: PhotoAdjustments,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorFilter =
        remember(filter, adjustments) { PhotoEdit(filter = filter, adjustments = adjustments).toColorFilter() }
    Column(
        modifier = Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChalkakImage(
            model = imageModel,
            contentDescription = null,
            modifier = Modifier
                .size(FilterThumbnailSize)
                .clip(ChalkakTheme.shapes.small)
                .border(
                    width = FilterSelectionBorderWidth,
                    color = if (selected) ChalkakTheme.colors.photoEditContent else Color.Transparent,
                    shape = ChalkakTheme.shapes.small,
                ),
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
        )
        Text(
            text = filter.label,
            modifier = Modifier.padding(top = ChalkakTheme.spacing.xs),
            style = ChalkakTheme.typography.caption,
            color = if (selected) ChalkakTheme.colors.photoEditContent else ChalkakTheme.colors.photoEditContentMuted,
        )
    }
}

private val FilterThumbnailSize = 64.dp
private val FilterSelectionBorderWidth = 2.dp

@Preview(widthDp = 360)
@Composable
private fun FilterStripPreview() {
    ChalkakTheme {
        FilterStrip(
            imageModel = R.drawable.preview_photo,
            selectedFilter = PhotoFilter.Mono,
            onFilterSelect = {},
            modifier = Modifier.background(ChalkakTheme.colors.photoEditBackground),
            contentPadding = PaddingValues(ChalkakTheme.spacing.lg),
        )
    }
}
