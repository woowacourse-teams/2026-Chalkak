import SwiftUI

/// 반려 알림의 본인 사진 원본과 반려 사유를 보여준다. 새로 올리기 버튼은 두지 않는다.
struct NotificationDetailScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @State private var viewModel: NotificationDetailViewModel

    let onBackClick: () -> Void

    init(viewModel: NotificationDetailViewModel, onBackClick: @escaping () -> Void) {
        _viewModel = State(initialValue: viewModel)
        self.onBackClick = onBackClick
    }

    var body: some View {
        VStack(spacing: 0) {
            NotificationTopBar(onBackClick: onBackClick)

            switch viewModel.viewState.status {
            case .loading:
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .content:
                if let detail = viewModel.viewState.detail {
                    content(detail)
                }
            case .notFound:
                placeholder(message: "알림을 찾을 수 없어요")
                    .accessibilityIdentifier("notificationDetail.notFound")
            case .failed:
                placeholder(message: "알림을 불러오지 못했어요") {
                    ChalkakOutlinedButton(title: "다시 시도") {
                        Task { await viewModel.load() }
                    }
                    .accessibilityIdentifier("notificationDetail.retry")
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .task {
            await viewModel.load()
        }
    }

    private func content(_ detail: NotificationDetail) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.xl) {
                VStack(alignment: .leading, spacing: theme.spacing.sm) {
                    Text(detail.title)
                        .font(theme.typography.headline)
                        .foregroundStyle(theme.colors.textPrimary)

                    Text(detail.body)
                        .font(theme.typography.subheadline)
                        .foregroundStyle(theme.colors.textSecondary)
                }

                if let imageURL = detail.originalImageURL {
                    ChalkakImage(
                        source: .remote(imageURL),
                        contentDescription: "반려된 내 사진",
                        contentMode: .fit
                    )
                    .frame(maxWidth: .infinity)
                    .aspectRatio(Metrics.photoAspectRatio, contentMode: .fit)
                    .background(theme.colors.surface)
                }

                if let rejectionReason = detail.rejectionReason, !rejectionReason.isEmpty {
                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        Text("반려 사유")
                            .font(theme.typography.caption)
                            .foregroundStyle(theme.colors.textInactive)

                        Text(rejectionReason)
                            .font(theme.typography.body)
                            .foregroundStyle(theme.colors.textPrimary)
                            .accessibilityIdentifier("notificationDetail.rejectionReason")
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.vertical, theme.spacing.lg)
        }
    }

    private func placeholder(
        message: String,
        @ViewBuilder action: () -> some View = { EmptyView() }
    ) -> some View {
        VStack(spacing: theme.spacing.lg) {
            Text(message)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)

            action()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, theme.spacing.screenHorizontal)
    }
}

private enum Metrics {
    // 원본 비율을 미리 알 수 없어 정사각형 영역 안에 사진 전체를 맞춰 보여준다.
    static let photoAspectRatio: CGFloat = 1
}

#Preview("Rejected Notification") {
    NotificationDetailScreen(
        viewModel: NotificationDetailViewModel(
            loadDetail: {
                NotificationDetail(
                    id: "preview",
                    title: "게시물이 반려되었습니다.",
                    body: "반려 사유를 확인해 주세요.",
                    originalImageURL: nil,
                    rejectionReason: "주제와 맞지 않는 사진이에요."
                )
            }
        ),
        onBackClick: {}
    )
    .chalkakTheme(.light)
}

#Preview("Notification Not Found") {
    NotificationDetailScreen(
        viewModel: NotificationDetailViewModel(
            loadDetail: { throw NotificationAPIError.notFound }
        ),
        onBackClick: {}
    )
    .chalkakTheme(.light)
}
