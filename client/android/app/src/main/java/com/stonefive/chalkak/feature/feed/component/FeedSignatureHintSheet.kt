package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.aireview.component.AiReviewSheetDragHandle

internal const val SIGNATURE_HINT_TITLE = "사인을 눌러보세요!"
internal const val SIGNATURE_HINT_MESSAGE = "이제 사인별로 작가님들의 사진을 모아볼 수 있어요"

private const val HINT_ILLUSTRATION_ASPECT_RATIO = 2.35f
private const val HINT_PHOTO_DIM_ALPHA = 0.25f

// 사인 표시는 그림 폭의 약 36%를 차지하고, 사인 벡터(140×80) 비율을 유지한다.
private const val HINT_MARK_WIDTH_FRACTION = 0.36f
private const val HINT_MARK_ASPECT_RATIO = 140f / 80f

/**
 * 사진 상세에 처음 들어왔을 때(내 사진 포함) 사인으로 작가 사진을 모아볼 수 있다고 알리는 바텀시트다.
 * 버튼 없이 쓸어내리기·바깥 탭·뒤로 가기·드래그 핸들 탭으로 닫으며, 닫히면 [onDismiss]를 한 번 호출한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSignatureHintSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = modifier,
    ) {
        AiReviewSheetDragHandle(sheetState = sheetState, onDismiss = onDismiss, modifier = Modifier.fillMaxWidth())
        FeedSignatureHintContent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ChalkakTheme.spacing.xl,
                    end = ChalkakTheme.spacing.xl,
                    bottom = ChalkakTheme.spacing.xl,
                ),
        )
    }
}

@Composable
private fun FeedSignatureHintContent(modifier: Modifier = Modifier) {
    // 그림의 좌우 끝을 가운데 정렬된 본문 한 줄의 좌우 끝에 맞춘다. 본문이 두 줄이 되면 그림도 가득 찬다.
    var messageWidth by remember { mutableIntStateOf(0) }
    val illustrationWidth = with(LocalDensity.current) { messageWidth.toDp() }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FeedSignatureHintIllustration(
            modifier = if (messageWidth > 0) Modifier.width(illustrationWidth) else Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(ChalkakTheme.spacing.xl))
        Text(
            text = SIGNATURE_HINT_TITLE,
            color = ChalkakTheme.colors.textPrimary,
            // AI 피드백 결과 요약 문장과 같은 굵기·크기
            style = ChalkakTheme.typography.title3,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ChalkakTheme.spacing.sm))
        Text(
            text = SIGNATURE_HINT_MESSAGE,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.onSizeChanged { messageWidth = it.width },
        )
    }
}

/**
 * 안내 그림: 로그인(온보딩) 배경 사진을 살짝 어둡게 깔고, 오른쪽 아래에 손으로 동그라미 친 "사인" 글씨를 얹어 누를 곳을 가리킨다.
 * 사진과 사인은 실제 회원의 것이 아닌 앱 리소스다(사인 벡터의 출처는 img_signature_hint_sample.xml 주석 참고).
 */
@Composable
private fun FeedSignatureHintIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(HINT_ILLUSTRATION_ASPECT_RATIO)
            .clip(ChalkakTheme.shapes.large),
    ) {
        Image(
            painter = painterResource(R.drawable.img_login_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // 흰 사인과 동그라미가 사진 위에서 잘 보이도록 사진 전체를 살짝 어둡게 한다.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    ChalkakTheme.colors.scrim
                        .copy(alpha = HINT_PHOTO_DIM_ALPHA),
                ),
        )
        Image(
            painter = painterResource(R.drawable.img_signature_hint_sample),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ChalkakTheme.spacing.sm, bottom = ChalkakTheme.spacing.xs)
                .fillMaxWidth(HINT_MARK_WIDTH_FRACTION)
                .aspectRatio(HINT_MARK_ASPECT_RATIO),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun FeedSignatureHintContentPreview() {
    ChalkakTheme {
        FeedSignatureHintContent(modifier = Modifier.padding(ChalkakTheme.spacing.xl))
    }
}
