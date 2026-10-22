package com.stonefive.chalkak.core.designsystem.component.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import kotlinx.coroutines.launch

/**
 * 제목, 휠([ChalkakWheelPicker]) 여러 개, 확인 버튼으로 된 바텀시트.
 *
 * - [wheels]에는 휠을 가로로 나란히 넣는다. 각 휠의 폭은 호출부가 `Modifier.weight(1f)` 등으로 정한다.
 * - 확인을 누르면 시트가 내려간 뒤 [onConfirm]을 부른다. 바깥을 누르거나 아래로 내리면 [onDismiss]만 부른다.
 * - 휠을 굴리는 동안의 임시 선택값은 호출부가 들고 있다가 [onConfirm]에서 적용한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChalkakPickerBottomSheet(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "확인",
    wheels: @Composable RowScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        // 화면 아래 끝과 좌우를 꽉 채우도록 위쪽 모서리만 둥글게 하고 기본 최대 너비(640dp) 제한을 푼다.
        sheetMaxWidth = Dp.Unspecified,
        shape = ChalkakTheme.shapes.sheet
            .copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        containerColor = ChalkakTheme.colors.surfaceElevated,
    ) {
        PickerSheetContent(
            title = title,
            confirmText = confirmText,
            onConfirm = {
                // 시트가 내려가는 애니메이션을 마친 뒤 적용해야 시트가 뚝 끊기지 않는다.
                scope.launch {
                    sheetState.hide()
                    onConfirm()
                }
            },
            wheels = wheels,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ChalkakTheme.spacing.xl,
                    top = ChalkakTheme.spacing.sm,
                    end = ChalkakTheme.spacing.xl,
                    bottom = ChalkakTheme.spacing.xl,
                ),
        )
    }
}

@Composable
private fun PickerSheetContent(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    wheels: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
    ) {
        Text(
            text = title,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title3,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
            modifier = Modifier.fillMaxWidth(),
            content = wheels,
        )
        ChalkakButton(
            text = confirmText,
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ChalkakTheme.spacing.sm),
        )
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PickerSheetContentPreview() {
    ChalkakTheme {
        PickerSheetContent(
            title = "날짜 선택",
            confirmText = "확인",
            onConfirm = {},
            wheels = {
                ChalkakWheelPicker(
                    items = listOf(2026),
                    selectedItem = 2026,
                    onSelectedItemChange = {},
                    itemLabel = { "${it}년" },
                    contentDescription = "연도",
                    modifier = Modifier.weight(1f),
                )
                ChalkakWheelPicker(
                    items = listOf(9, 10),
                    selectedItem = 10,
                    onSelectedItemChange = {},
                    itemLabel = { "${it}월" },
                    contentDescription = "월",
                    modifier = Modifier.weight(1f),
                )
            },
            modifier = Modifier
                .background(ChalkakTheme.colors.surfaceElevated)
                .padding(ChalkakTheme.spacing.xl),
        )
    }
}
