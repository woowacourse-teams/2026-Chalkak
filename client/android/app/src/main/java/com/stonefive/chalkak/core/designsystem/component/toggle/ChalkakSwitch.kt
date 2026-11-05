package com.stonefive.chalkak.core.designsystem.component.toggle

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import kotlin.math.roundToInt

/**
 * 찰칵 공통 스위치. 머티리얼 스위치를 [CHALKAK_SWITCH_SCALE] 비율로 줄이고, 줄인 크기만큼만 자리를 차지한다.
 * 설정의 푸시 알림 스위치와 전시하기·게시물 수정의 인스타그램 소개 허용 스위치가 같은 모양을 쓴다.
 * 행 전체를 눌러도 켜고 끌 수 있게 하려면 부모 행에서 [onCheckedChange]를 함께 부른다.
 */
@Composable
fun ChalkakSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 행 높이를 스위치가 늘리지 않도록 최소 터치 크기 확장을 끈다. 터치 영역은 부모 행이 맡는다.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val width = (placeable.width * CHALKAK_SWITCH_SCALE).roundToInt()
                    val height = (placeable.height * CHALKAK_SWITCH_SCALE).roundToInt()
                    layout(width, height) {
                        placeable.placeRelative(
                            x = (width - placeable.width) / 2,
                            y = (height - placeable.height) / 2,
                        )
                    }
                }.scale(CHALKAK_SWITCH_SCALE),
            colors = SwitchDefaults.colors(
                uncheckedBorderColor = ChalkakTheme.colors.textMuted,
                disabledUncheckedBorderColor = ChalkakTheme.colors.textMuted,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChalkakSwitchPreview() {
    ChalkakTheme {
        ChalkakSwitch(checked = true, onCheckedChange = {})
    }
}

private const val CHALKAK_SWITCH_SCALE = 0.75f
