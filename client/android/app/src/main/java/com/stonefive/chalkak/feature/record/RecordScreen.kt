package com.stonefive.chalkak.feature.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakScreenBottomBar
import com.stonefive.chalkak.core.designsystem.component.image.LocalPhotoTransitionCoordinator
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionKey
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.core.ui.photoColor
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.feature.record.component.RecordCalendarGrid
import com.stonefive.chalkak.feature.record.component.RecordFloatingIconButton
import com.stonefive.chalkak.feature.record.component.RecordPhotoBook
import com.stonefive.chalkak.feature.record.component.RecordPhotoPillars
import com.stonefive.chalkak.feature.record.component.RecordPhotoStackOrder
import com.stonefive.chalkak.feature.record.component.RecordPhotoStackStyle
import com.stonefive.chalkak.feature.record.component.RecordStackMetrics
import com.stonefive.chalkak.feature.record.component.RecordStackMonthButton
import com.stonefive.chalkak.feature.record.component.RecordWeekdayHeader
import com.stonefive.chalkak.feature.record.component.RecordYearCalendar
import com.stonefive.chalkak.feature.record.component.recordTransitionSourceId
import com.stonefive.chalkak.feature.record.component.weekIndexOf
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RecordHorizontalPadding = 20.dp

/** 맨 위에서 묶음이 기본 높이(300dp)보다 커져 있는 높이. 기둥은 126dp에서 236dp가 되고, 책자는 화면 폭이 허락하는 만큼 커진다. */
private val StackExpansion = 110.dp
private val StackControlsPadding = 12.dp
private val StackToWeekday = 8.dp
private val WeekdayToGrid = 14.dp
private val ContentBottomPadding = 32.dp
private val ErrorTopPadding = 24.dp

/** 달력에서 고른 날로 묶음을 옮기는 0.5초 응답의 스프링. 강성은 (2π / 0.5)²이다. */
private val BringToFrontSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 158f)

/** 책에서 펼친 날 앞뒤로 주제를 미리 불러올 날 수. */
private const val BOOK_TOPIC_RANGE = 3

/** 여러 장을 빠르게 넘기는 동안에는 주제를 부르지 않는다. */
private const val BOOK_TOPIC_DEBOUNCE_MILLIS = 250L

@Composable
fun RecordRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    onNavigateToLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory),
    onOpenFeed: (String, String) -> Unit = { _, _ -> },
    deletedPostId: String? = null,
    onDeletedPostConsumed: () -> Unit = {},
    requestedDate: LocalDate? = null,
    onRequestedDateConsumed: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val images = rememberRecordStackImages(uiState.posts)
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(requestedDate) {
        requestedDate?.let {
            viewModel.openDate(it)
            onRequestedDateConsumed()
        }
    }

    LaunchedEffect(deletedPostId) {
        deletedPostId?.let {
            viewModel.removeDeletedPost(it)
            onDeletedPostConsumed()
        }
    }

    RecordScreen(
        uiState = uiState,
        onOpenPhotoUpload = onOpenPhotoUpload,
        onNavigateToBottomBar = onNavigateToBottomBar,
        images = images,
        onNavigateToLogin = onNavigateToLogin,
        onRetryClick = viewModel::retryCurrentMonth,
        onOpenFeed = onOpenFeed,
        onPendingPhotoClick = viewModel::onPendingPhotoSelected,
        onToggleStackStyle = viewModel::togglePhotoStackStyle,
        onShowMonth = viewModel::showMonth,
        onLoadYear = viewModel::loadYear,
        onLoadTopicTitles = viewModel::loadTopicTitles,
        onLoadSignature = viewModel::loadSignature,
        modifier = modifier,
    )
}

/**
 * 기록 탭.
 * - 월간: 위에 사진 묶음, 아래에 달력. 달력의 사진을 누르면 묶음이 그날로 옮겨 가고, 묶음의 사진을 누르면 그 사진의 피드로 간다.
 * - 묶음은 기둥과 눈금자와 월간 책자 두 모양이 있고, 오른쪽 위 버튼으로 바꿔 A/B로 비교한다.
 * - 연간: 월간 왼쪽 위의 연월을 눌러 나가 다른 달을 고른다.
 *
 * @param images 묶음에 그릴 게시물별 썸네일. 사진 원래 비율로 자리를 잡는 데도 쓴다.
 */
@Composable
fun RecordScreen(
    uiState: RecordUiState,
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
    images: Map<String, ImageBitmap> = emptyMap(),
    onNavigateToLogin: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onOpenFeed: (String, String) -> Unit = { _, _ -> },
    onPendingPhotoClick: () -> Unit = {},
    onToggleStackStyle: () -> Unit = {},
    onShowMonth: (YearMonth) -> Unit = {},
    onLoadYear: (Int) -> Unit = {},
    onLoadTopicTitles: (List<LocalDate>) -> Unit = {},
    onLoadSignature: () -> Unit = {},
) {
    var isShowingYear by rememberSaveable { mutableStateOf(false) }
    var displayedYear by rememberSaveable { mutableIntStateOf(uiState.month.year) }

    // 연간 달력에서는 뒤로 가기로 보던 달에 돌아간다.
    BackHandler(enabled = isShowingYear) { isShowingYear = false }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            ChalkakScreenBottomBar(
                selectedItem = ChalkakBottomBarItem.RECORD,
                onItemSelected = onNavigateToBottomBar,
                onAddClick = onOpenPhotoUpload,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = isShowingYear,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "record-year-month",
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding(),
        ) { showsYear ->
            if (showsYear) {
                RecordYearCalendar(
                    year = displayedYear,
                    latestMonth = uiState.latestMonth,
                    shownMonth = uiState.month,
                    postsByMonth = uiState.yearPosts,
                    photoColors = uiState.photoColors,
                    onPreviousYear = {
                        displayedYear -= 1
                        onLoadYear(displayedYear)
                    },
                    onNextYear = {
                        displayedYear += 1
                        onLoadYear(displayedYear)
                    },
                    onSelectMonth = { month ->
                        isShowingYear = false
                        onShowMonth(month)
                    },
                    canGoPreviousYear = displayedYear > uiState.earliestYear,
                    canGoNextYear = displayedYear < uiState.latestMonth.year,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                RecordMonthContent(
                    uiState = uiState,
                    images = images,
                    onOpenYear = {
                        displayedYear = uiState.month.year
                        isShowingYear = true
                        onLoadYear(displayedYear)
                    },
                    onNavigateToLogin = onNavigateToLogin,
                    onRetryClick = onRetryClick,
                    onOpenFeed = onOpenFeed,
                    onPendingPhotoClick = onPendingPhotoClick,
                    onToggleStackStyle = onToggleStackStyle,
                    onLoadTopicTitles = onLoadTopicTitles,
                    onLoadSignature = onLoadSignature,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun RecordMonthContent(
    uiState: RecordUiState,
    images: Map<String, ImageBitmap>,
    onOpenYear: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onRetryClick: () -> Unit,
    onOpenFeed: (String, String) -> Unit,
    onPendingPhotoClick: () -> Unit,
    onToggleStackStyle: () -> Unit,
    onLoadTopicTitles: (List<LocalDate>) -> Unit,
    onLoadSignature: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoTransitionCoordinator = LocalPhotoTransitionCoordinator.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val collapsePx = with(density) { StackExpansion.roundToPx() }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val stackPosition = remember { Animatable(0f) }
    val order = remember(uiState.month, uiState.posts) { RecordPhotoStackOrder(uiState.month, uiState.posts) }
    val stackStyle = uiState.photoStackStyle

    // 홈 주간 기록에서 날짜를 골라 들어오거나 달을 옮기면 묶음도 그날(없으면 가장 최근 날)을 보여준다.
    LaunchedEffect(uiState.month, uiState.selectedDate, order) {
        val index = uiState.selectedDate?.let(order::indexOf)
        stackPosition.snapTo(index?.let(order::position) ?: 0f)
    }

    // 묶음이 줄어드는 중간에 손을 떼면 움직이던 쪽으로 마저 간다. 내리던 중이면 다 줄어든 상태, 올리던 중이면 다 펼친 상태로 간다.
    LaunchedEffect(scrollState, collapsePx) {
        var dragStart = 0
        snapshotFlow { scrollState.isScrollInProgress }.collect { inProgress ->
            if (inProgress) {
                dragStart = scrollState.value
            } else {
                val offset = scrollState.value
                if (offset in 1 until collapsePx) {
                    scrollState.animateScrollTo(if (offset < dragStart) 0 else collapsePx)
                }
            }
        }
    }

    val focusedIndex by remember(order) {
        derivedStateOf { order.focusedIndex(stackPosition.value) }
    }
    // 묶음이 지금 보여 주는 주. 달력에서 그 줄이 떠오른다.
    val selectedWeekIndex = focusedIndex?.let { uiState.month.weekIndexOf(order.entries[it].date) }

    // 월간 책자에서 지금 펼친 날. 날이 바뀔 때만 앞뒤 며칠의 주제를 불러온다.
    val bookTopicIndex = focusedIndex.takeIf { stackStyle == RecordPhotoStackStyle.BOOK }
    LaunchedEffect(bookTopicIndex, order) {
        val index = bookTopicIndex ?: return@LaunchedEffect
        delay(BOOK_TOPIC_DEBOUNCE_MILLIS)
        onLoadTopicTitles(
            order.entries.indices
                .filter { abs(it - index) <= BOOK_TOPIC_RANGE }
                .map { order.entries[it].date },
        )
    }

    // 책자 속표지에 넣을 사인. 설정에서 바꿨을 수 있어 책자를 열 때마다 다시 받는다.
    // 처음 불러오는 중에는 로그인 갱신과 겹쳐 실패할 수 있어, 기록을 다 받은 뒤에도 한 번 더 받는다.
    LaunchedEffect(stackStyle, uiState.isLoading) {
        if (stackStyle == RecordPhotoStackStyle.BOOK) onLoadSignature()
    }

    // 묶음의 사진을 누르면 그 사진의 피드로 간다. 승인 전 사진은 피드에 보이지 않으므로(POST-007) 이동하지 않는다.
    val openFeed: (PostCalendarItem) -> Unit = { post ->
        if (post.status == PostStatus.APPROVED) {
            photoTransitionCoordinator.select(PhotoTransitionKey(post.postId, recordTransitionSourceId(post.postId)))
            onOpenFeed(post.postId, post.thumbnailImageUrl)
        } else {
            onPendingPhotoClick()
        }
    }

    Column(
        modifier = modifier
            .onSizeChanged { viewportHeight = it.height }
            .verticalScroll(scrollState),
    ) {
        Column(
            // 달력이 짧아도 묶음이 다 줄어드는 데까지는 스크롤되게 한다.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = with(density) { viewportHeight.toDp() } + StackExpansion)
                .padding(bottom = ContentBottomPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RecordStackMetrics.Height + StackExpansion)
                    .zIndex(1f),
            ) {
                // 줄어드는 동안에는 묶음을 화면 위에 붙여 두고 그만큼 높이를 줄인다. 다 줄어든 뒤에는 함께 스크롤된다.
                val shrink = minOf(collapsePx, scrollState.value)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, minOf(collapsePx, scrollState.value)) }
                        .fillMaxWidth()
                        .height(RecordStackMetrics.Height + StackExpansion - with(density) { shrink.toDp() }),
                ) {
                    RecordPhotoStack(
                        uiState = uiState,
                        style = stackStyle,
                        images = images,
                        position = stackPosition,
                        onSelectPost = openFeed,
                        modifier = Modifier.fillMaxSize(),
                    )
                    RecordStackControls(
                        month = uiState.month,
                        style = stackStyle,
                        onOpenYear = onOpenYear,
                        onToggleStyle = {
                            // 묶음 모양을 바꿔도 같은 날을 계속 보게 한다. 책의 표지·속표지를 보고 있었으면 1일로 맞춘다.
                            val index = order.focusedIndex(stackPosition.value)
                            scope.launch { stackPosition.snapTo(index?.let(order::position) ?: 0f) }
                            onToggleStackStyle()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = StackControlsPadding,
                                top = ChalkakTheme.spacing.sm,
                                end = StackControlsPadding,
                            ),
                    )
                }
            }

            RecordWeekdayHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = RecordHorizontalPadding, top = StackToWeekday, end = RecordHorizontalPadding),
            )

            RecordCalendarGrid(
                month = uiState.month,
                posts = uiState.posts,
                onDateClick = { date ->
                    // 달력에서 고른 사진을 묶음에서 펼친다. 피드로 넘어가지는 않는다.
                    val index = order.indexOf(date) ?: return@RecordCalendarGrid
                    scope.launch {
                        stackPosition.animateTo(order.position(index), BringToFrontSpring)
                    }
                },
                showsDayNumbers = true,
                selectedWeekIndex = selectedWeekIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = RecordHorizontalPadding, top = WeekdayToGrid, end = RecordHorizontalPadding),
            )

            uiState.errorMessage?.let { message ->
                RecordErrorSection(
                    message = message,
                    isLoginRequired = uiState.isLoginRequired,
                    onNavigateToLogin = onNavigateToLogin,
                    onRetryClick = onRetryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ErrorTopPadding),
                )
            }
        }
    }
}

@Composable
private fun RecordPhotoStack(
    uiState: RecordUiState,
    style: RecordPhotoStackStyle,
    images: Map<String, ImageBitmap>,
    position: Animatable<Float, AnimationVector1D>,
    onSelectPost: (PostCalendarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (style) {
        RecordPhotoStackStyle.PILLARS -> RecordPhotoPillars(
            month = uiState.month,
            posts = uiState.posts,
            images = images,
            position = position,
            onSelectPost = onSelectPost,
            modifier = modifier,
        )

        RecordPhotoStackStyle.BOOK -> {
            // 그 달 첫 사진의 대표색을 천 색으로 쓴다. 사진을 아직 못 받았으면 책자가 기본 색을 쓴다.
            val firstPhoto = uiState.posts
                .filter { YearMonth.from(it.topicDate) == uiState.month }
                .minByOrNull { it.topicDate }
                ?.let { images[it.postId] }
            val clothColor = remember(firstPhoto) {
                firstPhoto
                    ?.asAndroidBitmap()
                    ?.photoColor()
                    ?.let { Color(it.red, it.green, it.blue) }
            }
            RecordPhotoBook(
                month = uiState.month,
                posts = uiState.posts,
                images = images,
                topicTitles = uiState.topicTitles,
                position = position,
                onSelectPost = onSelectPost,
                clothColor = clothColor,
                signatureUrl = uiState.signatureUrl,
                modifier = modifier,
            )
        }
    }
}

/** 묶음 위의 버튼. 왼쪽은 연간 달력으로 나가는 연월, 오른쪽은 묶음 모양을 바꾸는 A/B 전환이다. */
@Composable
private fun RecordStackControls(
    month: YearMonth,
    style: RecordPhotoStackStyle,
    onOpenYear: () -> Unit,
    onToggleStyle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        RecordStackMonthButton(month = month, onClick = onOpenYear)
        Spacer(modifier = Modifier.weight(1f))
        RecordFloatingIconButton(
            title = "${style.toggled.title}로 보기",
            iconRes = style.toggled.iconRes,
            onClick = onToggleStyle,
        )
    }
}

@Composable
private fun RecordErrorSection(
    message: String,
    isLoginRequired: Boolean,
    onNavigateToLogin: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = message,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
            modifier = Modifier.padding(horizontal = RecordHorizontalPadding),
        )
        TextButton(onClick = if (isLoginRequired) onNavigateToLogin else onRetryClick) {
            Text(
                text = if (isLoginRequired) "로그인 하기" else "다시 시도",
                color = ChalkakTheme.colors.actionPrimary,
                style = ChalkakTheme.typography.body,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 402, heightDp = 1000)
@Composable
private fun RecordScreenPreview() {
    val month = YearMonth.of(2026, 8)
    val posts = listOf(2, 5, 9, 14).map { day ->
        PostCalendarItem(
            postId = "preview-$day",
            topicDate = month.atDay(day),
            thumbnailImageUrl = "",
            status = PostStatus.APPROVED,
        )
    }

    ChalkakTheme {
        RecordScreen(
            uiState = RecordUiState(
                month = month,
                latestMonth = month,
                isLoading = false,
                posts = posts,
                selectedDate = posts.last().topicDate,
            ),
            onOpenPhotoUpload = {},
            onNavigateToBottomBar = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 402, heightDp = 1000)
@Composable
private fun RecordScreenBookPreview() {
    ChalkakTheme {
        RecordScreen(
            uiState = RecordUiState(
                isLoading = false,
                photoStackStyle = RecordPhotoStackStyle.BOOK,
            ),
            onOpenPhotoUpload = {},
            onNavigateToBottomBar = {},
        )
    }
}
