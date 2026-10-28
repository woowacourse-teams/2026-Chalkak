import SwiftUI

struct TodayScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast
    @Bindable var viewModel: TodayViewModel
    var onOpenPhotoUpload: () -> Void = {}
    var onOpenNotifications: () -> Void = {}
    var hasUnreadNotifications = false
    var onNavigateToBottomBar: (ChalkakBottomBarItem) -> Void = { _ in }

    var bottomBarCompact: Binding<Bool> = .constant(false)

    var body: some View {
        Group {
            switch viewModel.viewState.contentStatus {
            case .loading:
                TodayInitialStatus(
                    status: viewModel.viewState.contentStatus,
                    onRetry: { Task { await viewModel.retry() } },
                    onOpenNotifications: onOpenNotifications,
                    hasUnreadNotifications: hasUnreadNotifications
                )
            case .error:
                TodayInitialStatus(
                    status: viewModel.viewState.contentStatus,
                    onRetry: { Task { await viewModel.retry() } },
                    onOpenNotifications: onOpenNotifications,
                    hasUnreadNotifications: hasUnreadNotifications
                )
            case .content:
                TodayContent(
                    viewModel: viewModel,
                    onOpenNotifications: onOpenNotifications,
                    hasUnreadNotifications: hasUnreadNotifications,
                    isBottomBarCompact: bottomBarCompact
                )
            }
        }
        .background(theme.colors.background)
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
    }

    private func handle(_ event: TodayEvent?) {
        guard let event else { return }

        switch event {
        case .openPhotoUpload:
            onOpenPhotoUpload()
        case .showGuestLikeMessage:
            showMessage("로그인 후 좋아요를 누를 수 있어요")
        case let .showRefreshFailure(reason):
            showMessage(reason.message)
        case let .navigateToBottomBar(item):
            onNavigateToBottomBar(item)
        }
        viewModel.consumeEvent()
    }

    private func showMessage(_ text: String) {
        showToast(text)
    }
}

private struct TodayInitialStatus: View {
    @Environment(\.chalkakTheme) private var theme
    let status: TodayContentStatus
    let onRetry: () -> Void
    let onOpenNotifications: () -> Void
    let hasUnreadNotifications: Bool

    var body: some View {
        VStack(spacing: 0) {
            TodayTopBar(
                onOpenNotifications: onOpenNotifications,
                hasUnreadNotifications: hasUnreadNotifications
            )
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .todayBottomDivider()

            Spacer(minLength: 0)

            switch status {
            case .loading:
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .accessibilityIdentifier("today-loading")
            case let .error(reason):
                VStack(spacing: theme.spacing.xl) {
                    Text(reason.message)
                        .font(theme.typography.body)
                        .foregroundStyle(theme.colors.textSecondary)
                        .multilineTextAlignment(.center)

                    Button(action: onRetry) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: Metrics.retryIconSize, weight: .semibold))
                            .foregroundStyle(theme.colors.onActionPrimary)
                            .frame(width: Metrics.iconButtonSize, height: Metrics.iconButtonSize)
                            .background(theme.colors.actionPrimary, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("오늘 새로고침")
                }
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .accessibilityIdentifier("today-initial-error")
            case .content:
                EmptyView()
            }

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
    }
}

private struct TodayContent: View {
    @Environment(\.chalkakTheme) private var theme
    @Bindable var viewModel: TodayViewModel
    let onOpenNotifications: () -> Void
    let hasUnreadNotifications: Bool
    @Binding var isBottomBarCompact: Bool
    @State private var scrollPosition = ScrollPosition()

    private func scrollToTop() {
        Task { @MainActor in
            await Task.yield()
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) {
                scrollPosition.scrollTo(id: TodayScrollTarget.top, anchor: .top)
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // 고정된 상단 바를 스크롤 뷰 밖에 배치해, 배경이 당겨서 새로고침 인디케이터를 가리지 않게 한다.
            TodayTopBar(
                onOpenNotifications: onOpenNotifications,
                hasUnreadNotifications: hasUnreadNotifications
            )
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .background(theme.colors.background.opacity(TodayMetrics.topBarOpacity))
                .todayBottomDivider()

            ScrollView {
                VStack(spacing: 0) {
                    TodayTopic(
                        topicDate: viewModel.viewState.topicDate,
                        topic: viewModel.viewState.topic
                    )
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .todayBottomDivider()
                    .id(TodayScrollTarget.top)

                    if viewModel.viewState.photos.isEmpty {
                        TodayEmptyContent()
                            .frame(maxWidth: .infinity)
                            .padding(.top, TodayMetrics.emptyTopPadding)
                    } else {
                        TodayPhotoList(
                            photos: viewModel.viewState.photos,
                            likedPhotoIDs: viewModel.viewState.likedPhotoIDs,
                            areLikesEnabled: viewModel.viewState.areLikesEnabled,
                            imageReloadGeneration: viewModel.imageReloadGeneration,
                            onLike: { photoID in
                                Task { await viewModel.toggleLike(photoID: photoID) }
                            },
                            onEndThreshold: { isReached in
                                Task { await viewModel.didReachEndThreshold(isReached) }
                            }
                        )
                    }
                }
                .scrollTargetLayout()
            }
            // 이미지 비율이나 페이지 추가로 셀 높이가 바뀌어도 현재 스크롤 기준점을 유지하고,
            // 최상단 이동도 같은 ScrollPosition을 사용해 API 간 충돌을 피한다.
            .scrollPosition($scrollPosition, anchor: .top)
            .chalkakBottomBarScrollBehavior(isCompact: $isBottomBarCompact)
            .onChange(of: viewModel.scrollToTopRequestID) { _, _ in
                scrollToTop()
            }
            .refreshable {
                // SwiftUI가 새로고침 컨트롤 작업을 취소하더라도, 사용자가 시작한
                // 네트워크 갱신은 별도 작업으로 끝까지 완료되도록 한다.
                let refreshTask = Task { await viewModel.refresh() }
                await refreshTask.value
            }
            .background(theme.colors.background)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
    }
}

private enum TodayScrollTarget {
    static let top = "today-scroll-top"
}

private struct TodayEmptyContent: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        VStack(spacing: theme.spacing.sm) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: TodayMetrics.emptyIconSize))
                .foregroundStyle(theme.colors.iconSecondary)
                .accessibilityHidden(true)

            Text("아직 올라온 사진이 없어요")
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textSecondary)

            Text("첫 번째 사진을 올려보세요")
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textMuted)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("today-empty")
    }
}

private enum Metrics {
    static let retryIconSize: CGFloat = 20
    static let iconButtonSize: CGFloat = 48
}

private enum TodayMetrics {
    static let topBarOpacity = 0.96
    static let emptyTopPadding: CGFloat = 144
    static let emptyIconSize: CGFloat = 34
}

#Preview("Today") {
    TodayScreen(
        viewModel: TodayViewModel(initialState: TodayPreviewData.contentState)
    )
    .chalkakTheme(.light)
}

#Preview("Today Loading") {
    TodayScreen(
        viewModel: TodayViewModel(initialState: TodayPreviewData.loadingState)
    )
    .chalkakTheme(.light)
}

#Preview("Today Error") {
    TodayScreen(
        viewModel: TodayViewModel(initialState: TodayPreviewData.errorState)
    )
    .chalkakTheme(.light)
}
