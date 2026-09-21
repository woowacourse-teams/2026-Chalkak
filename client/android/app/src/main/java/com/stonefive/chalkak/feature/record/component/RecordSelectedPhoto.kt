package com.stonefive.chalkak.feature.record.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SelectedPhotoDateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
private val StatusTopPadding = 8.dp
private val StatusTrailingPadding = 12.dp
private val StatusMinimumTouchHeight = 44.dp
private val StatusIndicatorSize = 16.dp
private val StatusBubbleWidth = 300.dp
private val StatusBubbleTailWidth = 14.dp
private val StatusBubbleTailHeight = 7.dp
private val StatusBubbleTailTrailingPadding = 24.dp
private const val STATUS_BACKGROUND_OPACITY = 0.88f
private const val PENDING_STATUS_LABEL = "사진 반영 중"
private const val PENDING_STATUS_HINT = "자세한 안내 보기"
private const val PENDING_STATUS_CLOSE_HINT = "자세한 안내 닫기"
private const val PENDING_STATUS_MESSAGE_DISPLAY =
    "사진을 반영하고 있어요.\n표시되기까지 조금 시간이 걸릴 수도 있어요!"
private const val PENDING_STATUS_COLLAPSED_STATE = "안내 메시지 숨겨짐"
private const val PENDING_STATUS_EXPANDED_STATE = "안내 메시지 표시됨"

@Composable
fun RecordSelectedPhoto(
    post: PostCalendarItem?,
    modifier: Modifier = Modifier,
) {
    if (post == null) return

    Box(modifier = modifier) {
        ChalkakImage(
            model = post.thumbnailImageUrl,
            contentDescription = "${post.topicDate.format(SelectedPhotoDateFormatter)} 기록 사진",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = post.topicDate.format(SelectedPhotoDateFormatter),
            color = ChalkakTheme.colors.textOnImage,
            style = ChalkakTheme.typography.body,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = 15.dp,
                    top = 15.dp,
                ),
        )
        if (post.status == PostStatus.PENDING) {
            PendingStatus(
                postId = post.postId,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = StatusTopPadding,
                        end = StatusTrailingPadding,
                    ),
            )
        }
    }
}

@Composable
private fun PendingStatus(
    postId: String,
    modifier: Modifier = Modifier,
) {
    var isStatusMessageVisible by remember(postId) { mutableStateOf(false) }

    Layout(
        content = {
            Box(modifier = Modifier.wrapContentSize()) {
                AnimatedVisibility(
                    visible = isStatusMessageVisible,
                    enter = fadeIn() + scaleIn(
                        initialScale = 0.96f,
                        transformOrigin = TransformOrigin(1f, 1f),
                    ),
                    exit = fadeOut() + scaleOut(
                        targetScale = 0.96f,
                        transformOrigin = TransformOrigin(1f, 1f),
                    ),
                ) {
                    PendingStatusMessage()
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = StatusMinimumTouchHeight)
                    .clip(ChalkakTheme.shapes.pill)
                    .background(
                        color = ChalkakTheme.colors.actionPrimary.copy(
                            alpha = STATUS_BACKGROUND_OPACITY,
                        ),
                    ).clickable { isStatusMessageVisible = !isStatusMessageVisible }
                    .clearAndSetSemantics {
                        contentDescription = PENDING_STATUS_LABEL
                        stateDescription = if (isStatusMessageVisible) {
                            PENDING_STATUS_EXPANDED_STATE
                        } else {
                            PENDING_STATUS_COLLAPSED_STATE
                        }
                        role = Role.Button
                        onClick(
                            label = if (isStatusMessageVisible) {
                                PENDING_STATUS_CLOSE_HINT
                            } else {
                                PENDING_STATUS_HINT
                            },
                        ) {
                            isStatusMessageVisible = !isStatusMessageVisible
                            true
                        }
                    }.padding(horizontal = ChalkakTheme.spacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
                ) {
                    CircularProgressIndicator(
                        color = ChalkakTheme.colors.onActionPrimary,
                        modifier = Modifier
                            .size(StatusIndicatorSize)
                            .clearAndSetSemantics {},
                    )
                    Text(
                        text = PENDING_STATUS_LABEL,
                        color = ChalkakTheme.colors.onActionPrimary,
                        style = ChalkakTheme.typography.subheadline,
                    )
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val message = measurables[0].measure(childConstraints)
        val status = measurables[1].measure(childConstraints)

        layout(status.width, status.height) {
            if (message.width > 0 && message.height > 0) {
                message.placeRelative(
                    x = status.width - message.width,
                    y = -message.height,
                )
            }
            status.placeRelative(0, 0)
        }
    }
}

@Composable
private fun PendingStatusMessage() {
    val statusColor = ChalkakTheme.colors.actionPrimary

    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = PENDING_STATUS_MESSAGE_DISPLAY,
            color = ChalkakTheme.colors.onActionPrimary,
            style = ChalkakTheme.typography.footnote,
            modifier = Modifier
                .width(StatusBubbleWidth)
                .background(
                    color = statusColor,
                    shape = ChalkakTheme.shapes.button,
                ).padding(ChalkakTheme.spacing.md),
        )
        Canvas(
            modifier = Modifier
                .padding(end = StatusBubbleTailTrailingPadding)
                .size(
                    width = StatusBubbleTailWidth,
                    height = StatusBubbleTailHeight,
                ),
        ) {
            drawPath(
                path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width / 2, size.height)
                    lineTo(size.width, 0f)
                    close()
                },
                color = statusColor,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun RecordSelectedPhotoPreview() {
    ChalkakTheme {
        RecordSelectedPhoto(
            post = PostCalendarItem(
                postId = "preview-post",
                topicDate = LocalDate.of(2026, 8, 2),
                thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
                status = PostStatus.APPROVED,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
