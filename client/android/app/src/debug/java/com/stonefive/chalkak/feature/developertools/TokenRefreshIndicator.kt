package com.stonefive.chalkak.feature.developertools

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/**
 * 토큰 갱신이 성공한 순간 메인 화면 위쪽에 잠깐 띄우는 작은 표시(debug 빌드 전용).
 * 시연 영상에서 "Access 토큰이 무효였다가 갱신됐다"를 눈으로 보이게 하려고 둔다. 위치·보이는 시간은 부모가 정한다.
 */
@Composable
fun TokenRefreshIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = ChalkakTheme.shapes.large,
        color = ChalkakTheme.colors.actionPrimary,
    ) {
        Text(
            text = stringResource(R.string.developer_tools_token_refreshed),
            modifier = Modifier.padding(
                horizontal = ChalkakTheme.spacing.lg,
                vertical = ChalkakTheme.spacing.sm,
            ),
            color = ChalkakTheme.colors.onActionPrimary,
            style = ChalkakTheme.typography.footnote,
        )
    }
}

@Preview(name = "갱신 표시", showBackground = true)
@Composable
private fun TokenRefreshIndicatorPreview() {
    ChalkakTheme {
        TokenRefreshIndicator(modifier = Modifier.padding(ChalkakTheme.spacing.lg))
    }
}
