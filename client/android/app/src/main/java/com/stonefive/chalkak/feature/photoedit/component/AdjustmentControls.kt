package com.stonefive.chalkak.feature.photoedit.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustment
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustmentLayers
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustments
import com.stonefive.chalkak.feature.photoedit.PhotoAdjustItem
import com.stonefive.chalkak.feature.photoedit.label
import kotlin.math.roundToInt

/**
 * 자동 보정이 켜져 있으면 항목 눈금자에 자동 보정 몫(자동 보정값 × 강도)을 점으로 표시하고 그 값에서 멈칫한다.
 * 그 자리로 되돌리면 직접 만진 값이 지워져 그 항목은 다시 자동 보정을 따른다([PhotoAdjustmentLayers.withValue]).
 */
@Composable
fun AdjustmentControls(
    adjustmentLayers: PhotoAdjustmentLayers,
    selectedItem: PhotoAdjustItem,
    canReset: Boolean,
    onItemSelect: (PhotoAdjustItem) -> Unit,
    onValueChange: (Float) -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val applied = adjustmentLayers.applied
    fun valueOf(item: PhotoAdjustItem): Float = when (item) {
        PhotoAdjustItem.Auto -> adjustmentLayers.intensity
        is PhotoAdjustItem.Manual -> applied[item.adjustment]
    }
    val value = valueOf(selectedItem)
    val automaticValue = (selectedItem as? PhotoAdjustItem.Manual)
        ?.let { adjustmentLayers.appliedAutomatic?.get(it.adjustment) }
        ?.takeIf { it != 0f }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
        ) {
            Text(
                text = value.toDisplayValue().toString(),
                modifier = Modifier.align(Alignment.Center),
                style = ChalkakTheme.typography.footnote,
                color = ChalkakTheme.colors.photoEditContent,
            )
            Text(
                text = "초기화",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(
                        enabled = canReset,
                        role = Role.Button,
                        onClick = onResetClick,
                    ),
                style = ChalkakTheme.typography.footnote,
                color = if (canReset) {
                    ChalkakTheme.colors.photoEditContent
                } else {
                    ChalkakTheme.colors.photoEditContentMuted
                },
            )
        }

        // 항목을 바꾸면 끌기·관성 상태를 새로 시작한다.
        key(selectedItem) {
            AdjustmentRuler(
                value = value,
                range = selectedItem.range,
                label = selectedItem.label,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                automaticValue = automaticValue,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.md),
        ) {
            PhotoAdjustItem.entries.forEach { item ->
                AdjustmentItem(
                    item = item,
                    isChanged = valueOf(item) != 0f,
                    selected = item == selectedItem,
                    onClick = { onItemSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun AdjustmentItem(
    item: PhotoAdjustItem,
    isChanged: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(min = AdjustmentItemMinWidth)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ).padding(vertical = ChalkakTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xs),
    ) {
        val color = if (selected) ChalkakTheme.colors.photoEditContent else ChalkakTheme.colors.photoEditContentMuted
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            modifier = Modifier.size(AdjustmentIconSize),
            tint = color,
        )
        Text(
            text = item.label,
            style = ChalkakTheme.typography.caption,
            color = color,
        )
        Box(
            modifier = Modifier
                .size(ChangedDotSize)
                .background(
                    color = if (isChanged) ChalkakTheme.colors.photoEditContent else Color.Transparent,
                    shape = CircleShape,
                ),
        )
    }
}

@get:DrawableRes
private val PhotoAdjustItem.iconRes: Int
    get() = when (this) {
        PhotoAdjustItem.Auto -> R.drawable.ic_adjust_auto

        is PhotoAdjustItem.Manual -> when (adjustment) {
            PhotoAdjustment.Exposure -> R.drawable.ic_adjust_exposure
            PhotoAdjustment.Contrast -> R.drawable.ic_adjust_contrast
            PhotoAdjustment.Highlights -> R.drawable.ic_adjust_highlights
            PhotoAdjustment.Shadows -> R.drawable.ic_adjust_shadows
            PhotoAdjustment.BlackPoint -> R.drawable.ic_adjust_black_point
            PhotoAdjustment.Saturation -> R.drawable.ic_adjust_saturation
            PhotoAdjustment.Warmth -> R.drawable.ic_adjust_warmth
        }
    }

private fun Float.toDisplayValue(): Int = (this * DISPLAY_SCALE).roundToInt()

private const val DISPLAY_SCALE = 100f
private val AdjustmentItemMinWidth = 56.dp
private val AdjustmentIconSize = 24.dp
private val ChangedDotSize = 4.dp

@Preview(widthDp = 402)
@Composable
private fun AdjustmentControlsPreview() {
    ChalkakTheme {
        AdjustmentControls(
            adjustmentLayers = PhotoAdjustmentLayers(
                automatic = PhotoAdjustments(exposure = 0.14f, shadows = 0.3f),
                intensity = 1f,
                manual = mapOf(PhotoAdjustment.Exposure to 0.24f),
            ),
            selectedItem = PhotoAdjustItem.Manual(PhotoAdjustment.Exposure),
            canReset = true,
            onItemSelect = {},
            onValueChange = {},
            onResetClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkakTheme.colors.photoEditBackground),
            contentPadding = PaddingValues(ChalkakTheme.spacing.lg),
        )
    }
}
