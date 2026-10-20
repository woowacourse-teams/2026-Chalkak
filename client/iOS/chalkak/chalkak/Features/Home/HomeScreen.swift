import SwiftUI

struct HomeScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Bindable var viewModel: HomeViewModel
    var onOpenPhotoUpload: () -> Void = {}
    var onOpenRecord: () -> Void = {}
    var onOpenDisplay: (Date) -> Void = { _ in }
    var onOpenFeed: (DisplayPhoto, FeedZoomSource) -> Void = { _, _ in }
    var onOpenNotifications: () -> Void = {}
    var hasUnreadNotifications = false
    var onIssueClick: () -> Void = {}
    var bottomBarCompact: Binding<Bool> = .constant(false)

    @State private var scrollPosition = ScrollPosition()
    @State private var feedbackPhase: HomeFeedbackPhase = .ready
    @State private var isFeedbackSheetPresented = false
    @State private var isFeedbackExpanded = false

    var body: some View {
        VStack(spacing: 0) {
            HomeTopBar(
                onOpenNotifications: onOpenNotifications,
                hasUnreadNotifications: hasUnreadNotifications
            )
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .background(theme.colors.background.opacity(Metrics.topBarOpacity))
                .overlay(alignment: .bottom) {
                    Divider()
                        .overlay(theme.colors.border)
                }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    HomeTopicHeader(
                        date: viewModel.viewState.date,
                        topic: viewModel.viewState.topic,
                        topicStatus: viewModel.viewState.topicStatus
                    )
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .padding(.top, theme.spacing.xl + theme.spacing.sm)
                    .id(HomeScrollTarget.top)

                    HomeHeroCard(
                        hero: viewModel.viewState.hero,
                        topic: viewModel.viewState.topic,
                        onOpenPhotoUpload: onOpenPhotoUpload,
                        onRetry: { Task { await viewModel.refresh() } }
                    )
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .padding(.top, theme.spacing.xl)

                    if showsFeedback {
                        HomeFeedbackCard(
                            phase: feedbackPhase,
                            isExpanded: isFeedbackExpanded,
                            onStart: { isFeedbackSheetPresented = true },
                            onToggleExpanded: { withAnimation(.easeInOut) { isFeedbackExpanded.toggle() } }
                        )
                        .padding(.horizontal, theme.spacing.screenHorizontal)
                        .padding(.top, theme.spacing.lg)
                    }

                    HomeWeeklyRecord(
                        days: viewModel.viewState.week,
                        status: viewModel.viewState.recordsStatus,
                        onOpenRecord: onOpenRecord,
                        onRetry: { Task { await viewModel.refresh() } }
                    )
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .padding(.top, showsFeedback ? theme.spacing.xl : theme.spacing.xxl)

                    HomeRankingRow(
                        title: "어제 랭킹",
                        ranking: viewModel.viewState.yesterday,
                        displayDate: yesterday,
                        onOpenDisplay: onOpenDisplay,
                        zoomSource: FeedZoomSource.homeYesterday,
                        onOpenFeed: onOpenFeed
                    )
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .padding(.top, theme.spacing.xxl)

                    HomeRankingRow(
                        title: "오늘 인기있는 사진",
                        ranking: viewModel.viewState.trending,
                        displayDate: viewModel.viewState.date,
                        onOpenDisplay: onOpenDisplay,
                        zoomSource: FeedZoomSource.homeTrending,
                        onOpenFeed: onOpenFeed
                    )
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .padding(.top, theme.spacing.xxl)

                    HomeIssueSection(onIssueClick: onIssueClick)
                        .padding(.horizontal, theme.spacing.screenHorizontal)
                        .padding(.top, theme.spacing.xxl)
                        .padding(.bottom, theme.spacing.xxl)
                }
                .scrollTargetLayout()
            }
            .feedZoomViewport(.home)
            .scrollPosition($scrollPosition, anchor: .top)
            .chalkakBottomBarScrollBehavior(isCompact: bottomBarCompact)
            .refreshable {
                let refreshTask = Task { await viewModel.refresh() }
                await refreshTask.value
            }
            .onChange(of: viewModel.scrollToTopRequestID) { _, _ in
                scrollToTop()
            }
            .onChange(of: viewModel.viewState.date) { _, _ in
                feedbackPhase = .ready
                isFeedbackExpanded = false
                isFeedbackSheetPresented = false
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .sheet(isPresented: $isFeedbackSheetPresented) {
            feedbackStartSheet
                .presentationDetents([.height(Metrics.feedbackSheetHeight)])
                .presentationDragIndicator(.visible)
        }
        .task(id: feedbackPhase) {
            guard feedbackPhase == .processing else { return }
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled else { return }
            feedbackPhase = .completed
        }
    }

    @ViewBuilder
    private var feedbackStartSheet: some View {
        let content = HomeFeedbackStartSheet(
            onStart: {
                isFeedbackSheetPresented = false
                feedbackPhase = .processing
            }
        )

        if ChalkakPlatformAppearance.usesLiquidGlass {
            content
        } else {
            content
                .presentationCornerRadius(theme.shapes.sheet)
                .presentationBackground(theme.colors.surfaceElevated)
        }
    }

    private var yesterday: Date {
        RecordMonth.calendar.date(byAdding: .day, value: -1, to: viewModel.viewState.date)
            ?? viewModel.viewState.date
    }

    private var showsFeedback: Bool {
        if case let .photo(photo) = viewModel.viewState.hero {
            return !photo.isPending
        }
        return false
    }

    private func scrollToTop() {
        Task { @MainActor in
            await Task.yield()
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) {
                scrollPosition.scrollTo(id: HomeScrollTarget.top, anchor: .top)
            }
        }
    }
}

private struct HomeTopicHeader: View {
    @Environment(\.chalkakTheme) private var theme
    let date: Date
    let topic: String
    let topicStatus: HomeSectionStatus

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            Text("\(date.homeMonthDayText) · 오늘의 주제")
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textPrimary)

            Text(title)
                .font(theme.typography.title2)
                .foregroundStyle(theme.colors.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var title: String {
        switch topicStatus {
        case .loading:
            "오늘의 주제를 불러오는 중"
        case .error:
            "오늘의 주제를 불러오지 못했어요"
        case .ready:
            topic.isEmpty ? "오늘의 주제가 아직 준비되지 않았어요" : topic
        }
    }
}

private enum HomeScrollTarget {
    static let top = "home-scroll-top"
}

private enum Metrics {
    static let topBarOpacity = 0.96
    static let feedbackSheetHeight: CGFloat = 320
}

extension Date {
    var homeMonthDayText: String {
        let components = RecordMonth.calendar.dateComponents([.month, .day], from: self)
        return "\(components.month ?? 0)월 \(components.day ?? 0)일"
    }

    var homeFullDateText: String {
        let components = RecordMonth.calendar.dateComponents([.year, .month, .day], from: self)
        return "\(components.year ?? 0)년 \(components.month ?? 0)월 \(components.day ?? 0)일"
    }
}

#Preview("Home") {
    HomeScreen(viewModel: HomePreviewData.viewModel(state: HomePreviewData.contentState))
        .chalkakTheme(.light)
}

#Preview("Home Loading") {
    HomeScreen(viewModel: HomePreviewData.viewModel(state: HomePreviewData.loadingState))
        .chalkakTheme(.light)
}

#Preview("Home Empty") {
    var state = HomePreviewData.contentState
    state.hero = .empty
    state.week = HomeViewState.weekDates(HomePreviewData.previewDate).map { HomeWeekDay(date: $0) }
    state.yesterday = HomeRankingState(status: .ready)
    state.trending = HomeRankingState(status: .ready)
    return HomeScreen(viewModel: HomePreviewData.viewModel(state: state))
        .chalkakTheme(.light)
}

#Preview("Home Error") {
    HomeScreen(viewModel: HomePreviewData.viewModel(state: HomePreviewData.errorState))
        .chalkakTheme(.light)
}
