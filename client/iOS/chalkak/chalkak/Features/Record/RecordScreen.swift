import SwiftUI
import UIKit

/// 기록 탭.
/// - 월간: 위에 사진 묶음, 아래에 달력. 달력의 사진을 누르면 묶음이 그날로 옮겨 가고, 묶음의 사진을 누르면 그 사진의 피드로 간다.
/// - 묶음은 기둥과 눈금자와 월간 책자 두 모양이 있고, 오른쪽 위 버튼으로 바꿔 A/B로 비교한다.
///   월간 책자는 다시 펼친 책자와 한 면 책자 두 모양이 있고, 책자를 보는 동안 옆의 책 선택 버튼으로 바꾼다.
/// - 연간: 월간의 뒤로 가기로 나가 다른 달을 고른다.
struct RecordScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast
    @Bindable var viewModel: RecordViewModel
    var onOpenPhotoUpload: () -> Void = {}
    var onSelectBottomBarItem: (ChalkakBottomBarItem) -> Void = { _ in }
    var onOpenDisplay: (Date) -> Void = { _ in }
    // 선택한 사진의 피드로 이동한다. 두 번째 값은 묶음에 그려 둔 사진의 세로/가로 비율(모르면 nil)이다.
    // 피드가 캐시에서 사진을 못 찾아도 처음부터 이 비율로 사진 칸을 잡는다.
    var onOpenFeed: (RecordPost, CGFloat?) -> Void = { _, _ in }
    var onNavigateToLogin: () -> Void = {}
    var bottomBarCompact: Binding<Bool> = .constant(false)

    @Namespace private var monthNamespace
    @State private var images: [RecordPost.ID: UIImage] = [:]

    // 연간
    @State private var isShowingYear = false
    @State private var displayedYear = RecordMonth.current().year

    // 사진 묶음
    @AppStorage("record.photoStackStyle") private var stackStyle: RecordPhotoStackStyle = .pillars
    @AppStorage("record.photoBookLayout") private var bookLayout: RecordBookLayout = .spread
    @State private var stackPosition: CGFloat = 0

    var body: some View {
        ZStack {
            if isShowingYear {
                yearContent
            } else {
                monthContent
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(theme.colors.background)
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
        .onChange(of: viewModel.viewState.month) { _, _ in
            stackPosition = 0
        }
        .onChange(of: viewModel.viewState.selectedDate) { _, date in
            // 홈 주간 기록에서 날짜를 골라 들어오면 묶음도 그날을 보여준다.
            focus(on: date)
        }
        .task {
            await viewModel.loadIfNeeded()
        }
        .task(id: viewModel.viewState.posts.map(\.postId)) {
            await loadImages(for: viewModel.viewState.posts)
        }
        .task(id: bookTopicKey) {
            await loadBookTopics()
        }
        .task(id: signatureTaskKey) {
            // 책자 속표지에 넣을 사인. 설정에서 바꿨을 수 있어 책자를 열 때마다 다시 받는다.
            // 처음 불러오는 중에는 로그인 갱신과 겹쳐 실패할 수 있어, 기록을 다 받은 뒤에도 한 번 더 받는다.
            guard stackStyle == .book else { return }
            await viewModel.loadSignature()
        }
    }

    // MARK: - 월간

    private var monthContent: some View {
        let state = viewModel.viewState

        return ScrollView {
            VStack(spacing: 0) {
                photoStack
                    .overlay(alignment: .top) { stackControls }

                RecordWeekdayHeader()
                    .padding(.horizontal, Metrics.horizontalPadding)
                    .padding(.top, Metrics.stackToWeekday)

                RecordCalendarGrid(
                    month: state.month,
                    posts: state.posts,
                    onDateClick: { date in
                        guard let post = state.posts.first(where: { $0.topicDate == date }) else { return }
                        bringToFront(post)
                    },
                    showsDayNumbers: true,
                    selectedWeekIndex: selectedWeekIndex
                )
                .matchedGeometryEffect(id: RecordMonthTransition.id(state.month), in: monthNamespace)
                .padding(.horizontal, Metrics.horizontalPadding)
                .padding(.top, Metrics.weekdayToGrid)

                if state.errorMessage != nil {
                    errorSection
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.bottom, Metrics.contentBottomPadding)
        }
        .chalkakBottomBarScrollBehavior(isCompact: bottomBarCompact)
    }

    @ViewBuilder
    private var photoStack: some View {
        let state = viewModel.viewState
        switch stackStyle {
        case .pillars:
            RecordPhotoPillars(
                month: state.month,
                posts: state.posts,
                images: images,
                position: $stackPosition,
                onSelectPost: openFeed
            )
        case .book:
            RecordPhotoBook(
                month: state.month,
                posts: state.posts,
                images: images,
                topicTitles: state.topicTitles,
                layout: bookLayout,
                signatureURL: state.signatureURL,
                position: $stackPosition,
                onSelectPost: openFeed
            )
        }
    }

    /// 묶음 위의 버튼. 왼쪽은 연간 달력으로 나가는 뒤로 가기, 오른쪽은 묶음 모양을 바꾸는 A/B 전환이다.
    /// 책자를 보는 동안에는 전환 버튼 앞에 펼친 책자 ↔ 한 면 책자를 바꾸는 책 선택 버튼이 붙는다.
    private var stackControls: some View {
        HStack(spacing: theme.spacing.xs) {
            ChalkakNavigationButton(kind: .back, action: openYear)

            Spacer(minLength: 0)

            if stackStyle == .book {
                RecordGlassIconButton(
                    title: "\(bookLayout.toggled.title)로 보기",
                    systemImage: bookLayout.toggled.systemImage
                ) {
                    bookLayout = bookLayout.toggled
                }
            }

            RecordGlassIconButton(
                title: "\(stackStyle.toggled.title)로 보기",
                systemImage: stackStyle.toggled.systemImage
            ) {
                keepFocus()
                stackStyle = stackStyle.toggled
            }
        }
        .padding(.horizontal, Metrics.stackControlsPadding)
        .padding(.top, theme.spacing.sm)
    }

    /// 사인을 다시 받을 때. 책자로 바꿀 때와 기록을 다 받았을 때 바뀐다.
    private var signatureTaskKey: String {
        "\(stackStyle.rawValue)-\(viewModel.viewState.contentStatus == .loaded)"
    }

    private var stackOrder: RecordPhotoStackOrder {
        RecordPhotoStackOrder(month: viewModel.viewState.month, posts: viewModel.viewState.posts)
    }

    /// 묶음의 사진을 누르면 그 사진의 피드로 간다.
    /// 승인 전 사진은 피드에 보이지 않으므로(POST-007) 이동하지 않고 알려 준다.
    private func openFeed(_ post: RecordPost) {
        guard post.status == .approved else {
            showToast("승인 전 사진은 아직 피드에서 볼 수 없어요")
            return
        }
        let ratio = images[post.id].flatMap { image in
            image.size.width > 0 ? image.size.height / image.size.width : nil
        }
        onOpenFeed(post, ratio)
    }

    /// 달력에서 고른 사진을 묶음에서 펼친다. 피드로 넘어가지는 않는다.
    private func bringToFront(_ post: RecordPost) {
        guard let index = stackOrder.index(of: post) else { return }
        let target = stackOrder.position(focusing: index)
        withAnimation(.spring(response: 0.5, dampingFraction: 0.86)) {
            stackPosition = target
        }
    }

    private func focus(on date: Date?) {
        guard let date, let index = stackOrder.index(of: date) else { return }
        stackPosition = stackOrder.position(focusing: index)
    }

    /// 묶음 모양을 바꿔도 같은 날을 계속 보게 한다. 책의 표지·속표지를 보고 있었으면 1일로 맞춘다.
    private func keepFocus() {
        guard let index = stackOrder.focusedIndex(at: stackPosition) else {
            stackPosition = 0
            return
        }
        stackPosition = stackOrder.position(focusing: index)
    }

    /// 묶음이 지금 보여 주는 날.
    private var focusedEntry: RecordPhotoStackEntry? {
        let order = stackOrder
        guard let index = order.focusedIndex(at: stackPosition) else { return nil }
        return order.entries[index]
    }

    /// 묶음이 지금 보여 주는 주. 달력에서 그 줄이 떠오른다.
    private var selectedWeekIndex: Int? {
        guard let focusedEntry else { return nil }
        return viewModel.viewState.month.weekIndex(of: focusedEntry.date)
    }

    // MARK: - 책의 주제

    /// 월간 책자에서 지금 펼친 날. 날이 바뀔 때만 앞뒤 며칠의 주제를 불러온다.
    private var bookTopicKey: String? {
        guard stackStyle == .book, !isShowingYear, let entry = focusedEntry else { return nil }
        let month = viewModel.viewState.month
        return "\(month.year)-\(month.month)-\(entry.day)"
    }

    private func loadBookTopics() async {
        guard bookTopicKey != nil, let index = stackOrder.focusedIndex(at: stackPosition) else { return }
        // 여러 장을 빠르게 넘기는 동안에는 부르지 않는다.
        try? await Task.sleep(for: .milliseconds(250))
        guard !Task.isCancelled else { return }

        let entries = stackOrder.entries
        let nearby = entries.indices
            .filter { abs($0 - index) <= Metrics.bookTopicRange }
            .map { entries[$0].date }
        await viewModel.loadTopicTitles(for: nearby)
    }

    // MARK: - 연간

    private var yearContent: some View {
        let state = viewModel.viewState
        return RecordYearCalendar(
            year: displayedYear,
            latestMonth: state.latestMonth,
            canGoPreviousYear: displayedYear > state.earliestYear,
            canGoNextYear: displayedYear < state.latestMonth.year,
            postsByMonth: state.yearPosts,
            photoColors: state.photoColors,
            namespace: monthNamespace,
            bottomBarCompact: bottomBarCompact,
            onPreviousYear: { moveYear(by: -1) },
            onNextYear: { moveYear(by: 1) },
            onSelectMonth: selectMonth
        )
    }

    private func openYear() {
        let year = viewModel.viewState.month.year
        displayedYear = year
        withAnimation(Self.yearTransition) {
            isShowingYear = true
        }
        Task { await viewModel.loadYear(year) }
    }

    private func moveYear(by offset: Int) {
        let year = displayedYear + offset
        withAnimation(.snappy) {
            displayedYear = year
        }
        Task { await viewModel.loadYear(year) }
    }

    private func selectMonth(_ month: RecordMonth) {
        viewModel.showMonth(month)
        withAnimation(Self.yearTransition) {
            isShowingYear = false
        }
        Task { await viewModel.refreshShownMonth() }
    }

    // MARK: - 오류

    private var errorSection: some View {
        VStack(spacing: 0) {
            Text(viewModel.viewState.errorMessage ?? "")
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, Metrics.horizontalPadding)

            Button {
                if viewModel.viewState.isLoginRequired {
                    onNavigateToLogin()
                } else {
                    Task { await viewModel.retry() }
                }
            } label: {
                Text(viewModel.viewState.isLoginRequired ? "로그인 하기" : "다시 시도")
                    .font(theme.typography.body)
                    .foregroundStyle(theme.colors.actionPrimary)
                    .frame(minHeight: Metrics.retryTouchHeight)
                    .padding(.horizontal, theme.spacing.sm)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Metrics.errorTopPadding)
    }

    private func handle(_ event: RecordEvent?) {
        guard let event else { return }
        switch event {
        case let .showToast(text):
            showToast(text)
        }
        viewModel.consumeEvent()
    }

    // MARK: - 사진 불러오기

    /// 묶음은 사진의 원래 비율로 자리를 잡아야 하므로, 썸네일을 미리 받아 크기를 알아 둔다.
    private func loadImages(for posts: [RecordPost]) async {
        let postIDs = Set(posts.map(\.id))
        var known = images.filter { postIDs.contains($0.key) }
        for post in posts where known[post.id] == nil {
            known[post.id] = post.thumbnailImageSource.cachedImage()
        }
        images = known

        await withTaskGroup(of: (RecordPost.ID, UIImage?).self) { group in
            for post in posts where known[post.id] == nil {
                guard case let .remote(url?) = post.thumbnailImageSource else { continue }
                let postID = post.id
                group.addTask {
                    (postID, try? await RemoteImageCache.shared.load(url))
                }
            }
            for await (postID, image) in group {
                guard let image, !Task.isCancelled else { continue }
                withAnimation(.easeOut(duration: Metrics.imageFadeDuration)) {
                    images[postID] = image
                }
            }
        }
    }

    private static let yearTransition = Animation.spring(response: 0.5, dampingFraction: 0.86)
}

private enum Metrics {
    // Android RecordScreen / RecordHorizontalPadding 값과 맞춘다.
    static let horizontalPadding: CGFloat = 20
    static let stackControlsPadding: CGFloat = 12
    static let stackToWeekday: CGFloat = 8
    static let weekdayToGrid: CGFloat = 14
    static let contentBottomPadding: CGFloat = 32
    static let imageFadeDuration: Double = 0.2
    static let errorTopPadding: CGFloat = 24
    static let retryTouchHeight: CGFloat = 44
    /// 책에서 펼친 날 앞뒤로 주제를 미리 불러올 날 수.
    static let bookTopicRange = 3
}

#Preview("Record Loaded") {
    RecordScreen(viewModel: RecordViewModel(initialState: RecordPreviewData.loadedState))
        .chalkakTheme(.light)
}

#Preview("Record Error") {
    RecordScreen(viewModel: RecordViewModel(initialState: RecordPreviewData.errorState))
        .chalkakTheme(.light)
}

#Preview("Record Login Required") {
    RecordScreen(viewModel: RecordViewModel(initialState: RecordPreviewData.loginRequiredState))
        .chalkakTheme(.light)
}
