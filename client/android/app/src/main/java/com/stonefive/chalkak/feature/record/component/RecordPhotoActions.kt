package com.stonefive.chalkak.feature.record.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakOutlinedButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

private val RecordPhotoActionsHorizontalPadding = 20.dp

@Composable
fun RecordPhotoActions(
    onFeedClick: () -> Unit,
    onDisplayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(
            start = RecordPhotoActionsHorizontalPadding,
            top = 24.dp,
            end = RecordPhotoActionsHorizontalPadding,
            bottom = 32.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ChalkakOutlinedButton(
            text = "피드에서 보기",
            onClick = onFeedClick,
            modifier = Modifier.weight(1f),
        )
        ChalkakOutlinedButton(
            text = "전시 보러가기",
            onClick = onDisplayClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun RecordPhotoActionsPreview() {
    ChalkakTheme {
        RecordPhotoActions(
            onFeedClick = {},
            onDisplayClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
