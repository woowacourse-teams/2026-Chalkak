package com.stonefive.chalkak.feature.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakOutlinedButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.feedback.component.FeedbackListItem
import com.stonefive.chalkak.feature.feedback.component.FeedbackTopBar
import com.stonefive.chalkak.feature.feedback.component.FeedbackTopBarPadding
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 고객센터(내 문의 목록) Route.
 *
 * @param refreshRequested 접수·삭제·404로 이 화면에 돌아왔을 때 true. 첫 페이지부터 다시 불러온 뒤 [onRefreshConsumed]를 부른다.
 * @param onInquiryClick 목록 항목을 눌렀을 때 문의 ID를 넘긴다.
 * @param onReauthenticationRequired 목록 요청이 401일 때.
 */
@Composable
fun FeedbackListRoute(
    refreshRequested: Boolean,
    onRefreshConsumed: () -> Unit,
    onBackClick: () -> Unit,
    onComposeClick: () -> Unit,
    onInquiryClick: (String) -> Unit,
    onReauthenticationRequired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedbackListViewModel = viewModel(factory = FeedbackListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            viewModel.refresh()
            onRefreshConsumed()
        }
    }

    LaunchedEffect(uiState.isReauthenticationRequired) {
        if (uiState.isReauthenticationRequired) {
            onReauthenticationRequired()
            viewModel.reauthenticationHandled()
        }
    }

    FeedbackListScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onComposeClick = onComposeClick,
        onInquiryClick = onInquiryClick,
        onRefresh = viewModel::refresh,
        onLoadNextPage = viewModel::loadNextPage,
        modifier = modifier,
    )
}

/** 고객센터 화면. 위 제목 줄, 가운데 목록(또는 로딩·빈·실패 안내), 아래 고정 "문의하기" 버튼. */
@Composable
fun FeedbackListScreen(
    uiState: FeedbackListUiState,
    onBackClick: () -> Unit,
    onComposeClick: () -> Unit,
    onInquiryClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        FeedbackTopBar(
            title = "고객센터",
            onBackClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeedbackTopBarPadding),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (uiState.status) {
                FeedbackListStatus.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = ChalkakTheme.colors.iconPrimary,
                )

                FeedbackListStatus.Failed -> FeedbackListFailure(
                    onRetryClick = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                )

                FeedbackListStatus.Empty -> FeedbackListEmpty(modifier = Modifier.fillMaxSize())

                FeedbackListStatus.Content -> FeedbackListContent(
                    uiState = uiState,
                    onInquiryClick = onInquiryClick,
                    onRefresh = onRefresh,
                    onLoadNextPage = onLoadNextPage,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        ChalkakButton(
            text = "문의하기",
            onClick = onComposeClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal)
                .padding(top = ChalkakTheme.spacing.sm, bottom = ChalkakTheme.spacing.lg),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackListContent(
    uiState: FeedbackListUiState,
    onInquiryClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LoadNextPageNearEnd(
        listState = listState,
        itemCount = uiState.items.size,
        canLoadMore = uiState.hasNext && !uiState.isLoadingMore && !uiState.loadMoreFailed && !uiState.isRefreshing,
        onLoadNextPage = onLoadNextPage,
    )

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            items(items = uiState.items, key = { item -> item.id }) { item ->
                FeedbackListItem(
                    item = item,
                    onClick = dropUnlessResumed { onInquiryClick(item.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                )
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                    thickness = DividerDefaults.Thickness,
                    color = ChalkakTheme.colors.border,
                )
            }

            if (uiState.isLoadingMore || uiState.loadMoreFailed) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ChalkakTheme.spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.isLoadingMore) {
                            CircularProgressIndicator(color = ChalkakTheme.colors.iconPrimary)
                        } else {
                            // 실패한 뒤에는 사람이 눌렀을 때만 다시 요청한다.
                            ChalkakOutlinedButton(text = "더 불러오기", onClick = onLoadNextPage)
                        }
                    }
                }
            }
        }
    }
}

/** 마지막 항목 근처까지 스크롤하면 [onLoadNextPage]를 부른다. [canLoadMore]가 false면 부르지 않는다. */
@Composable
private fun LoadNextPageNearEnd(
    listState: LazyListState,
    itemCount: Int,
    canLoadMore: Boolean,
    onLoadNextPage: () -> Unit,
) {
    val currentCanLoadMore by rememberUpdatedState(canLoadMore)
    val currentOnLoadNextPage by rememberUpdatedState(onLoadNextPage)
    LaunchedEffect(listState, itemCount, canLoadMore) {
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
            lastVisibleIndex != null && lastVisibleIndex >= itemCount - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && currentCanLoadMore) currentOnLoadNextPage()
            }
    }
}

@Composable
private fun FeedbackListEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "아직 보낸 문의가 없어요",
            style = ChalkakTheme.typography.body,
            color = ChalkakTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "불편한 점이나 궁금한 점을 문의하기로 알려 주세요",
            style = ChalkakTheme.typography.subheadline,
            color = ChalkakTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeedbackListFailure(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "문의를 불러오지 못했어요",
            style = ChalkakTheme.typography.subheadline,
            color = ChalkakTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        ChalkakOutlinedButton(text = "다시 시도", onClick = onRetryClick)
    }
}

private const val LOAD_MORE_THRESHOLD = 4

private val previewItems = listOf(
    FeedbackListItemUiState(
        id = "1",
        content = "전시한 사진이 피드에 안 보여요. 전시하기를 눌렀는데 피드에서 찾을 수가 없어요.",
        timeText = "09:30",
        answerStatus = FeedbackAnswerStatus.Waiting,
    ),
    FeedbackListItemUiState(
        id = "2",
        content = "사진 업로드 후 화면이 멈춰요. 업로드 버튼을 누르면 한참 동안 아무 반응이 없다가 앱이 꺼져요.",
        timeText = "11월 3일 14:20",
        answerStatus = FeedbackAnswerStatus.Answered,
    ),
    FeedbackListItemUiState(
        id = "3",
        content = "알림이 늦게 와요. 주제 알림이 하루 지나서 도착했어요.",
        timeText = "11월 1일 09:12",
        answerStatus = FeedbackAnswerStatus.Waiting,
    ),
    FeedbackListItemUiState(
        id = "4",
        content = "사인을 바꾸고 싶은데 어디서 바꾸나요?",
        timeText = "10월 28일 21:40",
        answerStatus = FeedbackAnswerStatus.Answered,
    ),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FeedbackListScreenContentPreview() {
    ChalkakTheme {
        FeedbackListScreen(
            uiState = FeedbackListUiState(status = FeedbackListStatus.Content, items = previewItems, hasNext = true),
            onBackClick = {},
            onComposeClick = {},
            onInquiryClick = {},
            onRefresh = {},
            onLoadNextPage = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FeedbackListScreenEmptyPreview() {
    ChalkakTheme {
        FeedbackListScreen(
            uiState = FeedbackListUiState(status = FeedbackListStatus.Empty),
            onBackClick = {},
            onComposeClick = {},
            onInquiryClick = {},
            onRefresh = {},
            onLoadNextPage = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FeedbackListScreenFailedPreview() {
    ChalkakTheme {
        FeedbackListScreen(
            uiState = FeedbackListUiState(status = FeedbackListStatus.Failed),
            onBackClick = {},
            onComposeClick = {},
            onInquiryClick = {},
            onRefresh = {},
            onLoadNextPage = {},
        )
    }
}
