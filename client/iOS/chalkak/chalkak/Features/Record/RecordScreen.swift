import SwiftUI
import UIKit

struct RecordScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.displayScale) private var displayScale
    @Bindable var viewModel: RecordViewModel
    var onOpenPhotoUpload: () -> Void = {}
    var onSelectBottomBarItem: (ChalkakBottomBarItem) -> Void = { _ in }
    var onOpenDisplay: (Date) -> Void = { _ in }
    // 선택한 사진(postId)의 피드로 이동한다.
    var onOpenFeed: (String) -> Void = { _ in }
    var onNavigateToLogin: () -> Void = {}

    @State private var calendarWidth: CGFloat = 0
    @State private var message: String?
    @State private var messageDismissTask: Task<Void, Never>?
    @State private var isSavingCalendarImage = false

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                calendarSection

                if viewModel.viewState.errorMessage == nil {
                    loadedSection
                } else {
                    errorSection
                }
            }
            .frame(maxWidth: .infinity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakBottomBar(
                selectedItem: .record,
                onSelect: onSelectBottomBarItem,
                onAdd: onOpenPhotoUpload
            )
        }
        .overlay(alignment: .bottom) { toast }
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
        .task {
            guard viewModel.viewState.contentStatus == .loading else { return }
            await viewModel.load()
        }
        .onDisappear {
            messageDismissTask?.cancel()
        }
    }

    // MARK: - 달력(상단바 + 요일 헤더 + 그리드) + 이미지 저장 링크

    private var calendarSection: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 0) {
                RecordTopBar(
                    month: viewModel.viewState.month,
                    canGoPrevious: viewModel.viewState.canGoPrevious,
                    canGoNext: viewModel.viewState.canGoNext,
                    onPrevious: { Task { await viewModel.moveToPreviousMonth() } },
                    onNext: { Task { await viewModel.moveToNextMonth() } }
                )

                Spacer().frame(height: Metrics.topBarToWeekday)

                RecordWeekdayHeader()
                    .padding(.horizontal, Metrics.horizontalPadding)

                Spacer().frame(height: Metrics.weekdayToGrid)

                RecordCalendarGrid(
                    month: viewModel.viewState.month,
                    posts: viewModel.viewState.posts,
                    onDateClick: { viewModel.selectDate($0) }
                )
                .padding(.horizontal, Metrics.horizontalPadding)

                if viewModel.viewState.selectedPost != nil {
                    Spacer().frame(height: Metrics.gridToPhoto)
                }
            }
            .background {
                GeometryReader { proxy in
                    Color.clear.onAppear { calendarWidth = proxy.size.width }
                        .onChange(of: proxy.size.width) { _, width in calendarWidth = width }
                }
            }

            saveImageLink
        }
    }

    private var saveImageLink: some View {
        Button(action: saveCalendarImage) {
            Text("이미지로 저장")
                .font(theme.typography.footnote)
                .foregroundStyle(theme.colors.textMuted)
                .underline()
                .frame(height: Metrics.saveLinkHeight)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(isSavingCalendarImage)
        .padding(.top, Metrics.saveLinkTopPadding)
        .padding(.trailing, Metrics.saveLinkTrailingPadding)
    }

    // MARK: - 선택 사진 + 액션 버튼

    @ViewBuilder
    private var loadedSection: some View {
        if let selectedPost = viewModel.viewState.selectedPost {
            RecordSelectedPhoto(post: selectedPost)
                .frame(maxWidth: .infinity)

            if selectedPost.status == .pending || selectedPost.status == .approved {
                RecordPhotoActions(
                    onFeedClick: { onOpenFeed(selectedPost.postId) },
                    onDisplayClick: { onOpenDisplay(selectedPost.topicDate) },
                    isDisplayVisible: selectedPost.status == .approved
                )
                .padding(.leading, Metrics.horizontalPadding)
                .padding(.top, Metrics.actionsTopPadding)
                .padding(.trailing, Metrics.horizontalPadding)
                .padding(.bottom, Metrics.actionsBottomPadding)
            }
        }
    }

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

    // MARK: - Toast

    @ViewBuilder
    private var toast: some View {
        if let message {
            Text(message)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.onActionPrimary)
                .padding(.horizontal, theme.spacing.lg)
                .padding(.vertical, theme.spacing.md)
                .background(theme.colors.actionPrimary, in: Capsule())
                .padding(.bottom, Metrics.toastBottomPadding)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .accessibilityLabel(message)
        }
    }

    // MARK: - 이미지 저장

    @MainActor
    private func saveCalendarImage() {
        guard !isSavingCalendarImage else { return }
        isSavingCalendarImage = true

        let width = calendarWidth > 0 ? calendarWidth : UIScreen.main.bounds.width
        let state = viewModel.viewState

        Task { @MainActor in
            defer { isSavingCalendarImage = false }

            let thumbnailData = await RecordCalendarThumbnailLoader.loadData(for: state.posts)
            let thumbnailImages = thumbnailData.compactMapValues(UIImage.init(data:))
            let snapshot = RecordCalendarSnapshot(
                month: state.month,
                posts: state.posts,
                canGoPrevious: state.canGoPrevious,
                canGoNext: state.canGoNext,
                width: width,
                thumbnailImages: thumbnailImages
            )
            .chalkakTheme(theme)

            let renderer = ImageRenderer(content: snapshot)
            renderer.scale = displayScale
            guard let image = renderer.uiImage else {
                viewModel.onCalendarImageSaved(false)
                return
            }

            let result = await RecordCalendarImageSaver.save(image)
            switch result {
            case .saved:
                viewModel.onCalendarImageSaved(true)
            case .failed:
                viewModel.onCalendarImageSaved(false)
            case .permissionDenied:
                viewModel.onPhotoLibraryPermissionDenied()
            }
        }
    }

    private func handle(_ event: RecordEvent?) {
        guard let event else { return }
        switch event {
        case let .showToast(text):
            showMessage(text)
        }
        viewModel.consumeEvent()
    }

    private func showMessage(_ text: String) {
        messageDismissTask?.cancel()
        withAnimation(.snappy) {
            message = text
        }
        messageDismissTask = Task {
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) {
                message = nil
            }
        }
    }
}

private enum Metrics {
    // Android RecordScreen / RecordHorizontalPadding 값과 맞춘다.
    static let horizontalPadding: CGFloat = 20
    static let topBarToWeekday: CGFloat = 8
    static let weekdayToGrid: CGFloat = 14
    static let gridToPhoto: CGFloat = 36
    static let actionsTopPadding: CGFloat = 24
    static let actionsBottomPadding: CGFloat = 32
    static let saveLinkHeight: CGFloat = 48
    static let saveLinkTopPadding: CGFloat = 20
    static let saveLinkTrailingPadding: CGFloat = 20
    static let errorTopPadding: CGFloat = 24
    static let retryTouchHeight: CGFloat = 44
    static let toastBottomPadding: CGFloat = 88
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
