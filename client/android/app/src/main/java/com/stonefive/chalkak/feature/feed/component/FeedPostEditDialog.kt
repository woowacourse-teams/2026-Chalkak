package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextField
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextLengthMetric
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.feed.FeedPostEditState

/**
 * 피드 상세의 '수정'으로 여는 게시물 수정 화면.
 *
 * 전시하기 글 쓰기와 같은 배치(사진 썸네일 옆 제목, 그 아래 '이야기' 칸, 아래 고정 버튼)로 제목과 설명을 함께 고친다.
 * 입력값은 [edit]으로 받기만 하고, 바뀐 글은 [onTitleChange]·[onDescriptionChange]로 ViewModel에 올린다.
 * 저장 중([isSaving])에는 입력과 닫기를 막는다. 응답이 오기 전에 닫으면 결과를 볼 곳이 없기 때문이다.
 *
 * 예: 제목 "제목", 설명 "이야기"인 게시물이면 두 칸에 그 글이 채워진 채 열린다.
 */
@Composable
fun FeedPostEditDialog(
    edit: FeedPostEditState,
    thumbnailImageUrl: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
) {
    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        // 이슈 그림처럼 화면 전체를 덮고, 키보드 높이는 아래 imePadding으로 직접 비킨다.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        FeedPostEditContent(
            edit = edit,
            thumbnailImageUrl = thumbnailImageUrl,
            onTitleChange = onTitleChange,
            onDescriptionChange = onDescriptionChange,
            onSave = onSave,
            onBackClick = { if (!isSaving) onDismiss() },
            isSaving = isSaving,
            modifier = modifier,
        )
    }
}

@Composable
private fun FeedPostEditContent(
    edit: FeedPostEditState,
    thumbnailImageUrl: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onBackClick: () -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FeedPostEditTopBar(
                onBackClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        start = ChalkakTheme.spacing.sm,
                        end = ChalkakTheme.spacing.md,
                        top = ChalkakTheme.spacing.sm,
                        bottom = ChalkakTheme.spacing.sm,
                    ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .imePadding()
                    // 아래 고정 버튼에 마지막 줄이 가리지 않게 버튼 자리만큼 비운다.
                    .padding(bottom = FeedPostEditBottomButtonSpace)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FeedPostEditContentHorizontalPadding)
                    .padding(top = ChalkakTheme.spacing.lg),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    ChalkakImage(
                        model = thumbnailImageUrl,
                        contentDescription = "수정할 사진",
                        modifier = Modifier
                            .size(FeedPostEditThumbnailSize)
                            .clip(ChalkakTheme.shapes.photoCard),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(ChalkakTheme.spacing.lg))
                    Column(modifier = Modifier.weight(1f)) {
                        FeedPostEditSectionLabel(text = "제목")
                        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xs))
                        ChalkakTextField(
                            value = edit.title,
                            onValueChange = onTitleChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(FEED_POST_EDIT_TITLE_TAG),
                            placeholder = "사진의 제목을 적어 주세요. (선택)",
                            enabled = !isSaving,
                            textStyle = ChalkakTheme.typography.headline,
                            singleLine = true,
                            maxLength = POST_TITLE_MAX_LENGTH,
                            // 서버가 제목·설명 길이를 code point로 검증하므로(POST-005) 같은 단위로 센다.
                            lengthMetric = ChalkakTextLengthMetric.CODE_POINTS,
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = ChalkakTheme.spacing.lg),
                    color = ChalkakTheme.colors.border,
                )

                FeedPostEditSectionLabel(text = "이야기")
                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
                ChalkakTextField(
                    value = edit.description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FEED_POST_EDIT_DESCRIPTION_TAG),
                    placeholder = "사진에 담긴 이야기를 적어 주세요. (선택)",
                    enabled = !isSaving,
                    textStyle = ChalkakTheme.typography.body,
                    minLines = DESCRIPTION_MIN_LINES,
                    maxLines = DESCRIPTION_MAX_LINES,
                    maxLength = POST_DESCRIPTION_MAX_LENGTH,
                    lengthMetric = ChalkakTextLengthMetric.CODE_POINTS,
                )
            }
        }

        ChalkakButton(
            text = if (isSaving) "저장 중..." else "저장",
            onClick = onSave,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = FeedPostEditButtonHorizontalPadding)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = FeedPostEditButtonHorizontalPadding)
                .testTag(FEED_POST_EDIT_SAVE_TAG),
            enabled = !isSaving,
        )
    }
}

@Composable
private fun FeedPostEditTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(FeedPostEditTopBarTouchSize)
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
                modifier = Modifier.size(FeedPostEditTopBarIconSize),
            )
        }
        Text(
            text = "게시물 수정",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(FeedPostEditTopBarTouchSize))
    }
}

@Composable
private fun FeedPostEditSectionLabel(text: String) {
    Text(
        text = text,
        color = ChalkakTheme.colors.textPrimary,
        style = ChalkakTheme.typography.caption,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun FeedPostEditContentPreview() {
    ChalkakTheme {
        FeedPostEditContent(
            edit = FeedPostEditState(
                title = "늦은 오후의 골목",
                description = "퇴근길에 빛이 벽을 타고 내려오는 걸 보고 멈춰 섰어요.",
            ),
            thumbnailImageUrl = "",
            onTitleChange = {},
            onDescriptionChange = {},
            onSave = {},
            onBackClick = {},
            isSaving = false,
        )
    }
}

const val FEED_POST_EDIT_TITLE_TAG = "feed_post_edit_title"
const val FEED_POST_EDIT_DESCRIPTION_TAG = "feed_post_edit_description"
const val FEED_POST_EDIT_SAVE_TAG = "feed_post_edit_save"

// 서버 입력 규칙(POST-005)과 같은 최대 길이. 전시하기의 이야기는 150자지만 수정은 서버 기준 300자로 받는다(#938 계획).
private const val POST_TITLE_MAX_LENGTH = 10
private const val POST_DESCRIPTION_MAX_LENGTH = 300

// 아래 값은 전시하기 글 쓰기 화면(PhotoUploadScreen)과 같은 배치를 맞추기 위한 고정값이다.
private const val DESCRIPTION_MIN_LINES = 8
private const val DESCRIPTION_MAX_LINES = 16
private val FeedPostEditContentHorizontalPadding = 22.dp
private val FeedPostEditButtonHorizontalPadding = 26.dp
private val FeedPostEditBottomButtonSpace = 104.dp
private val FeedPostEditThumbnailSize = 84.dp
private val FeedPostEditTopBarTouchSize = 44.dp
private val FeedPostEditTopBarIconSize = 24.dp
