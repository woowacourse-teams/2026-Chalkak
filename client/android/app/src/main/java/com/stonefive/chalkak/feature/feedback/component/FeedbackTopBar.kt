package com.stonefive.chalkak.feature.feedback.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/** 고객센터·문의 작성·문의 상세 화면이 [FeedbackTopBar] 바깥에 주는 여백. 세 화면의 제목 위치를 맞춘다. */
val FeedbackTopBarPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)

/** 고객센터·문의 작성·문의 상세 위의 제목 줄. 왼쪽 뒤로 가기와 가운데 [title]만 있다. */
@Composable
fun FeedbackTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "뒤로 가기" }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onBackClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier.size(24.dp),
            )
        }

        Text(
            text = title,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.size(44.dp))
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun FeedbackTopBarPreview() {
    ChalkakTheme {
        FeedbackTopBar(
            title = "고객센터",
            onBackClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = 8.dp,
                    end = 12.dp,
                    bottom = 8.dp,
                ),
        )
    }
}
