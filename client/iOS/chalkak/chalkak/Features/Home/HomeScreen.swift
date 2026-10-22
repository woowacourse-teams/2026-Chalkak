import SwiftUI

struct HomeScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.scenePhase) private var scenePhase
    @Bindable var viewModel: HomeViewModel
    var onOpenPhotoUpload: () -> Void = {}
    var onOpenRecord: () -> Void = {}
    var onOpenDisplay: (Date) -> Void = { _ in }
    var onOpenFeed: (DisplayPhoto, FeedZoomSource) -> Void = { _, _ in }
    var onOpenNotifications: () -> Void = {}
    var hasUnreadNotifications = false
    var onIssueClick: (URL) -> Void = { _ in }
    var bottomBarCompact: Binding<Bool> = .constant(false)
    var isActive = true

    @State private var scrollPosition = ScrollPosition()
    @State private var feedbackRetryID = 0
    @State private var isFeedbackSheetPresented = false
    @State private var isFeedbackExpanded = false
    @State private var visibleBannerIDs: Set<Banner.ID> = []

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
                            onStart: handleFeedbackAction,
                            onToggleExpanded: { withAnimation(.easeInOut) { isFeedbackExpanded.toggle() } },
                            result: viewModel.viewState.aiReview.review?.result,
                            isSubmitting: viewModel.viewState.aiReview.isSubmitting,
                            pollingTimedOut: viewModel.viewState.aiReview.pollingTimedOut
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

                    if !viewModel.viewState.banners.isEmpty || viewModel.viewState.bannerStatus == .loading {
                        Text("ISSUE")
                            .font(theme.typography.headline)
                            .fontWeight(.bold)
                            .foregroundStyle(theme.colors.textPrimary)
                            .padding(.horizontal, theme.spacing.screenHorizontal)
                            .padding(.top, theme.spacing.xxl)
                            .padding(.bottom, theme.spacing.lg)

                        ForEach(viewModel.viewState.banners) { banner in
                            HomeIssueSection(banner: banner, onIssueClick: onIssueClick, showHeader: false)
                                .frame(maxWidth: .infinity)
                                .padding(.horizontal, theme.spacing.screenHorizontal)
                                .padding(.bottom, theme.spacing.xxl)
                                .onScrollVisibilityChange(threshold: 0.01) { isVisible in
                                    if isVisible {
                                        visibleBannerIDs.insert(banner.id)
                                    } else {
                                        visibleBannerIDs.remove(banner.id)
                                    }
                                }
                        }
                    }

                    if viewModel.viewState.bannerStatus == .loading && viewModel.viewState.banners.isEmpty {
                        HomeIssueSection(banner: nil, onIssueClick: onIssueClick, showHeader: false)
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, theme.spacing.screenHorizontal)
                    }

                    if viewModel.viewState.isLoadingMoreBanners {
                        ProgressView()
                            .tint(theme.colors.actionPrimary)
                            .frame(maxWidth: .infinity)
                            .accessibilityLabel("ISSUE 더 불러오는 중")
                            .accessibilityIdentifier("home.issue.loadingMore")
                    } else if viewModel.viewState.bannerLoadMoreError {
                        Button {
                            Task { await viewModel.loadMoreBanners() }
                        } label: {
                            Text("ISSUE를 불러오지 못했어요. 다시 시도")
                                .font(theme.typography.subheadline)
                                .foregroundStyle(theme.colors.textPrimary)
                                .frame(maxWidth: .infinity, minHeight: Metrics.minimumTouchHeight)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .padding(.horizontal, theme.spacing.screenHorizontal)
                        .accessibilityIdentifier("home.issue.retry")
                    }
                }
                .padding(.bottom, theme.spacing.xxl)
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
            .onChange(of: feedbackPostID) { _, _ in
                resetFeedback()
            }
            .onChange(of: bannerPaginationTrigger) { _, trigger in
                guard trigger.canLoad else { return }
                Task { await viewModel.loadMoreBanners() }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .sheet(isPresented: $isFeedbackSheetPresented) {
            feedbackStartSheet
                .presentationDetents([.height(Metrics.feedbackSheetHeight)])
                .presentationDragIndicator(.visible)
        }
        .task(id: feedbackObservationID) {
            guard let postID = feedbackPostID, scenePhase == .active, isActive else { return }
            await viewModel.observeAIReview(postID: postID)
        }
        .alert("AI 피드백", isPresented: Binding(
            get: { viewModel.viewState.aiReview.message != nil },
            set: { if !$0 { viewModel.dismissAIReviewMessage() } }
        )) {
            Button("확인", role: .cancel) { viewModel.dismissAIReviewMessage() }
        } message: {
            Text(viewModel.viewState.aiReview.message ?? "")
        }
    }

    @ViewBuilder
    private var feedbackStartSheet: some View {
        let content = AIReviewStartSheet(
            onStart: {
                isFeedbackSheetPresented = false
                Task { await viewModel.requestAIReview() }
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

    private var feedbackPostID: String? {
        guard viewModel.hasAIReviewRepository,
              viewModel.viewState.isAuthenticated,
              case let .photo(photo) = viewModel.viewState.hero,
              !photo.isPending else { return nil }
        return photo.postID
    }

    private var showsFeedback: Bool { feedbackPostID != nil }

    private var feedbackObservationID: String? {
        guard scenePhase == .active, isActive, let postID = feedbackPostID else { return nil }
        return "\(postID):\(feedbackRetryID)"
    }

    private var feedbackPhase: HomeFeedbackPhase {
        let state = viewModel.viewState.aiReview
        if state.isUnavailable { return .unavailable }
        if state.isLoading { return .loading }
        guard let review = state.review else { return .loadError }
        switch review.status {
        case .none: return .ready
        case .processing: return .processing
        case .completed: return .completed
        case .failed: return .failed(canRequestAgain: review.canRequestAgain)
        }
    }

    private func handleFeedbackAction() {
        switch feedbackPhase {
        case .loadError:
            feedbackRetryID += 1
        case .ready:
            isFeedbackSheetPresented = true
        case .failed(canRequestAgain: true):
            Task { await viewModel.requestAIReview() }
        default:
            break
        }
    }

    private func resetFeedback() {
        isFeedbackExpanded = false
        isFeedbackSheetPresented = false
    }

    private var bannerPaginationTrigger: HomeBannerPaginationTrigger {
        let state = viewModel.viewState
        let isNearEnd = state.banners.suffix(Metrics.bannerEndThreshold)
            .contains { visibleBannerIDs.contains($0.id) }
        return HomeBannerPaginationTrigger(
            page: state.bannerCurrentPage,
            count: state.banners.count,
            canLoad: isNearEnd && state.bannerHasNext && state.bannerStatus == .ready
                && !state.isLoadingMoreBanners && !state.bannerLoadMoreError && !state.isRefreshing
        )
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

private struct HomeBannerPaginationTrigger: Equatable {
    let page: Int
    let count: Int
    let canLoad: Bool
}

private enum Metrics {
    static let topBarOpacity = 0.96
    static let feedbackSheetHeight: CGFloat = 320
    static let bannerEndThreshold = 3
    static let minimumTouchHeight: CGFloat = 44
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
