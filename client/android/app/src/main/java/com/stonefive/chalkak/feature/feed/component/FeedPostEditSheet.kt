package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextField
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextLengthMetric
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.feed.FeedPostEditState

/**
 * 피드 상세의 '수정'으로 여는 게시물 수정 바텀시트.
 *
 * 맨 위 줄 왼쪽에 '게시물 수정', 오른쪽에 '저장'을 두고, 그 아래 '제목'(한 줄)과 '이야기'(3–8줄) 칸으로 제목과 설명을 함께 고친다.
 * 입력값은 [edit]으로 받기만 하고, 바뀐 글은 [onTitleChange]·[onDescriptionChange]로 ViewModel에 올린다.
 * 아래로 내리기·바깥 누르기·뒤로 가기는 저장하지 않고 [onDismiss]를 부른다.
 * 저장 중([isSaving])에는 입력과 닫기를 모두 막는다. 응답이 오기 전에 닫으면 결과를 볼 곳이 없기 때문이다.
 * 키보드가 올라오면 ModalBottomSheet가 시트를 키보드 위로 올려 준다(시트 안에서 따로 비키지 않는다).
 *
 * 예: 제목 "제목", 설명 "이야기"인 게시물이면 두 칸에 그 글이 채워진 채 올라온다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedPostEditSheet(
    edit: FeedPostEditState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        // 화면 아래 끝과 좌우를 꽉 채우도록 위쪽 모서리만 둥글게 하고 기본 최대 너비(640dp) 제한을 푼다.
        sheetMaxWidth = Dp.Unspecified,
        // 저장 중에는 끌어내리기·바깥 누르기·뒤로 가기로 닫히지 않게 한다.
        sheetGesturesEnabled = !isSaving,
        shape = ChalkakTheme.shapes.sheet
            .copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        containerColor = ChalkakTheme.colors.surfaceElevated,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !isSaving,
            shouldDismissOnClickOutside = !isSaving,
        ),
    ) {
        FeedPostEditContent(
            edit = edit,
            onTitleChange = onTitleChange,
            onDescriptionChange = onDescriptionChange,
            onSave = onSave,
            isSaving = isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ChalkakTheme.spacing.xl,
                    end = ChalkakTheme.spacing.xl,
                    bottom = ChalkakTheme.spacing.xl,
                ),
        )
    }
}

@Composable
private fun FeedPostEditContent(
    edit: FeedPostEditState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FeedPostEditHeader(
            onSave = onSave,
            isSaving = isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.md))

        FeedPostEditSectionLabel(text = "제목")
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
        ChalkakTextField(
            value = edit.title,
            onValueChange = onTitleChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FEED_POST_EDIT_TITLE_TAG),
            placeholder = "작품 제목은 선택이에요.",
            enabled = !isSaving,
            textStyle = ChalkakTheme.typography.subheadline,
            singleLine = true,
            maxLength = POST_TITLE_MAX_LENGTH,
            // 서버가 제목·설명 길이를 code point로 검증하므로(POST-005) 같은 단위로 센다.
            lengthMetric = ChalkakTextLengthMetric.CODE_POINTS,
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.lg))

        FeedPostEditSectionLabel(text = "이야기")
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
        ChalkakTextField(
            value = edit.description,
            onValueChange = onDescriptionChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FEED_POST_EDIT_DESCRIPTION_TAG),
            placeholder = "작품 이야기는 선택이에요.",
            enabled = !isSaving,
            textStyle = ChalkakTheme.typography.subheadline,
            minLines = DESCRIPTION_MIN_LINES,
            maxLines = DESCRIPTION_MAX_LINES,
            maxLength = POST_DESCRIPTION_MAX_LENGTH,
            lengthMetric = ChalkakTextLengthMetric.CODE_POINTS,
        )
    }
}

/** 시트 맨 위 줄: 왼쪽 '게시물 수정', 오른쪽 '저장'. 저장 중에는 "저장 중..."으로 바꾸고 누를 수 없게 한다. */
@Composable
private fun FeedPostEditHeader(
    onSave: () -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "게시물 수정",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = FeedPostEditTouchSize, minHeight = FeedPostEditTouchSize)
                .clickable(
                    enabled = !isSaving,
                    role = Role.Button,
                    interactionSource = null,
                    indication = null,
                    onClick = onSave,
                ).testTag(FEED_POST_EDIT_SAVE_TAG),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = if (isSaving) "저장 중..." else "저장",
                color = if (isSaving) ChalkakTheme.colors.textMuted else ChalkakTheme.colors.actionPrimary,
                style = ChalkakTheme.typography.headline,
            )
        }
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

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun FeedPostEditContentPreview() {
    ChalkakTheme {
        FeedPostEditContent(
            edit = FeedPostEditState(
                title = "늦은 오후의 골목",
                description = "퇴근길에 빛이 벽을 타고 내려오는 걸 보고 멈춰 섰어요.",
            ),
            onTitleChange = {},
            onDescriptionChange = {},
            onSave = {},
            isSaving = false,
            modifier = Modifier
                .background(ChalkakTheme.colors.surfaceElevated)
                .padding(ChalkakTheme.spacing.xl),
        )
    }
}

const val FEED_POST_EDIT_TITLE_TAG = "feed_post_edit_title"
const val FEED_POST_EDIT_DESCRIPTION_TAG = "feed_post_edit_description"
const val FEED_POST_EDIT_SAVE_TAG = "feed_post_edit_save"

// 서버 입력 규칙(POST-005)과 같은 최대 길이. 전시하기의 이야기는 150자지만 수정은 서버 기준 300자로 받는다(#938 계획).
private const val POST_TITLE_MAX_LENGTH = 10
private const val POST_DESCRIPTION_MAX_LENGTH = 300

// 이야기 칸은 3줄 높이로 시작해 8줄까지 늘어난다. 키보드가 올라와도 두 칸과 '저장'이 한 화면에 보이게 하는 높이다.
private const val DESCRIPTION_MIN_LINES = 3
private const val DESCRIPTION_MAX_LINES = 8
private val FeedPostEditTouchSize = 44.dp
