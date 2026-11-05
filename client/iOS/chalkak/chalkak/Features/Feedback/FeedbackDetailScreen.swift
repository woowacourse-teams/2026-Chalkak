import SwiftUI

/// 문의 한 건. 문의 내용·접수 시각 아래에 답변(또는 "아직 답변이 없어요")을 두고, 맨 아래에 삭제 버튼을 둔다.
struct FeedbackDetailScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @State private var viewModel: FeedbackDetailViewModel
    @State private var showsDeleteDialog = false

    /// 목록 ViewModel이 이 문의를 삭제하는 중이면 true. 삭제 버튼을 막는다.
    let isDeleting: Bool
    let onBackClick: () -> Void
    /// 확인 알림창에서 "삭제"를 눌렀을 때. 삭제 요청과 화면 닫기는 호출부가 한다.
    let onDelete: () -> Void
    /// 404로 문의를 찾을 수 없을 때. 호출부가 목록을 1페이지부터 다시 불러온다.
    let onNotFound: () -> Void

    init(
        viewModel: FeedbackDetailViewModel,
        isDeleting: Bool,
        onBackClick: @escaping () -> Void,
        onDelete: @escaping () -> Void,
        onNotFound: @escaping () -> Void
    ) {
        _viewModel = State(initialValue: viewModel)
        self.isDeleting = isDeleting
        self.onBackClick = onBackClick
        self.onDelete = onDelete
        self.onNotFound = onNotFound
    }

    var body: some View {
        VStack(spacing: 0) {
            FeedbackTopBar(title: "내 문의", onBackClick: onBackClick)

            switch viewModel.viewState.status {
            case .loading:
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .content:
                if let detail = viewModel.viewState.detail {
                    content(detail)
                }
            case .notFound:
                placeholder(message: viewModel.viewState.message ?? FeedbackMessages.notFound)
                    .accessibilityIdentifier("feedbackDetail.notFound")
            case .failed:
                placeholder(message: "문의를 불러오지 못했어요") {
                    ChalkakOutlinedButton(title: "다시 시도") {
                        Task { await viewModel.load() }
                    }
                    .accessibilityIdentifier("feedbackDetail.retry")
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .task {
            await viewModel.load()
        }
        .onChange(of: viewModel.event) { _, event in
            guard let event else { return }
            switch event {
            case .listRefreshRequested:
                onNotFound()
            }
            viewModel.consumeEvent()
        }
        .alert("문의를 삭제할까요?", isPresented: $showsDeleteDialog) {
            Button("취소", role: .cancel) {}
            Button("삭제", role: .destructive, action: onDelete)
        } message: {
            Text("삭제한 문의와 답변은 다시 볼 수 없어요.")
        }
        .accessibilityIdentifier("feedbackDetailScreen")
    }

    private func content(_ detail: FeedbackDetail) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.xl) {
                section(
                    label: "문의 내용",
                    text: detail.content,
                    date: detail.createdAt,
                    identifier: "feedbackDetail.content"
                )

                Rectangle()
                    .fill(theme.colors.border)
                    .frame(height: Metrics.dividerHeight)
                    .accessibilityHidden(true)

                if let answer = detail.answer {
                    section(
                        label: "답변",
                        text: answer.content,
                        date: answer.answeredAt,
                        identifier: "feedbackDetail.answer"
                    )
                } else {
                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        sectionLabel("답변")

                        Text("아직 답변이 없어요")
                            .font(theme.typography.subheadline)
                            .foregroundStyle(theme.colors.textMuted)
                            .accessibilityIdentifier("feedbackDetail.noAnswer")
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.vertical, theme.spacing.lg)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakOutlinedButton(
                title: isDeleting ? "삭제하는 중..." : "문의 삭제",
                action: { showsDeleteDialog = true },
                isEnabled: !isDeleting,
                fillsWidth: true
            )
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.top, theme.spacing.sm)
            .padding(.bottom, theme.spacing.lg)
            .background(theme.colors.background)
            .accessibilityIdentifier("feedbackDetail.delete")
        }
    }

    private func section(label: String, text: String, date: Date, identifier: String) -> some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            sectionLabel(label)

            Text(text)
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
                .accessibilityIdentifier(identifier)

            Text(ChalkakTimestampFormatter.string(from: date))
                .font(theme.typography.caption)
                .foregroundStyle(theme.colors.textInactive)
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(theme.typography.caption)
            .foregroundStyle(theme.colors.textSecondary)
            .accessibilityAddTraits(.isHeader)
    }

    private func placeholder(
        message: String,
        @ViewBuilder action: () -> some View = { EmptyView() }
    ) -> some View {
        VStack(spacing: theme.spacing.lg) {
            Text(message)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)
                .multilineTextAlignment(.center)

            action()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, theme.spacing.screenHorizontal)
    }
}

private enum Metrics {
    static let dividerHeight: CGFloat = 1
}

#Preview("Answered Feedback") {
    FeedbackDetailScreen(
        viewModel: FeedbackDetailViewModel(
            feedbackID: "preview",
            loadDetail: {
                FeedbackDetail(
                    id: "preview",
                    content: "사진 업로드 후 화면이 멈춰요.",
                    createdAt: .now.addingTimeInterval(-86_400),
                    answer: FeedbackAnswer(content: "확인했어요. 다음 업데이트에서 고칠게요.", answeredAt: .now)
                )
            }
        ),
        isDeleting: false,
        onBackClick: {},
        onDelete: {},
        onNotFound: {}
    )
    .chalkakTheme(.light)
}

#Preview("Unanswered Feedback") {
    FeedbackDetailScreen(
        viewModel: FeedbackDetailViewModel(
            feedbackID: "preview",
            loadDetail: {
                FeedbackDetail(id: "preview", content: "알림이 늦게 와요.", createdAt: .now, answer: nil)
            }
        ),
        isDeleting: false,
        onBackClick: {},
        onDelete: {},
        onNotFound: {}
    )
    .chalkakTheme(.light)
}
