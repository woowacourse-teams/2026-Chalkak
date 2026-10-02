import SwiftUI

struct FeedbackScreen: View {
    @Environment(\.chalkakTheme) private var theme

    @Bindable var viewModel: FeedbackViewModel
    let onDismiss: () -> Void
    let onSubmitted: () -> Void
    let onReauthenticationRequired: () -> Void

    @State private var message: String?
    @State private var messageDismissTask: Task<Void, Never>?
    @State private var inputFrame: CGRect = .zero
    @State private var submitButtonFrame: CGRect = .zero
    @State private var isKeyboardVisible = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: theme.spacing.md) {
                    Text("찰캌을 사용하며\n느낀 점을 알려주세요.")
                        .font(theme.typography.title2)
                        .foregroundStyle(theme.colors.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("feedback.title")

                    Text("서비스 이용 중 불편한 점이나 개선되었으면 하는 점을\n자유롭게 남겨주세요.")
                        .font(theme.typography.subheadline)
                        .foregroundStyle(theme.colors.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())

                ChalkakTextField(
                    text: Binding(
                        get: { viewModel.viewState.content },
                        set: viewModel.updateContent
                    ),
                    label: "피드백 내용",
                    placeholder: "내용을 입력해 주세요.",
                    isEnabled: !viewModel.viewState.isSubmitting,
                    lineLimit: 8...14,
                    textFont: theme.typography.subheadline,
                    maximumCharacterCount: FeedbackLimits.maximumContentLength,
                    lengthMetric: .unicodeScalars,
                    height: Metrics.inputHeight
                )
                .onGeometryChange(for: CGRect.self) { proxy in
                    proxy.frame(in: .named("feedback.dismiss"))
                } action: { frame in
                    inputFrame = frame
                }
                .padding(.top, Metrics.inputTopPadding)

                Text("보내주신 의견은 더 나은 서비스를 만드는 데 활용돼요.")
                    .font(theme.typography.footnote)
                    .foregroundStyle(theme.colors.textMuted)
                    .padding(.top, theme.spacing.sm)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.top, Metrics.contentTopPadding)
            .padding(.bottom, Metrics.contentBottomPadding)
        }
        .scrollDisabled(!isKeyboardVisible)
        .scrollDismissesKeyboard(.never)
        .scrollBounceBehavior(.basedOnSize)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(theme.colors.background)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakButton(
                title: viewModel.viewState.isSubmitting ? "보내는 중..." : "보내기",
                action: { Task { await viewModel.submit() } },
                isEnabled: viewModel.viewState.canSubmit,
                fillsWidth: true
            )
            .onGeometryChange(for: CGRect.self) { proxy in
                proxy.frame(in: .named("feedback.dismiss"))
            } action: { frame in
                submitButtonFrame = frame
            }
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.top, theme.spacing.sm)
            .padding(.bottom, theme.spacing.lg)
            .background(theme.colors.background)
        }
        .overlay(alignment: .bottom) {
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
        .contentShape(Rectangle())
        .coordinateSpace(name: "feedback.dismiss")
        .simultaneousGesture(dismissGesture)
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            isKeyboardVisible = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidHideNotification)) { _ in
            isKeyboardVisible = false
        }
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
        .onDisappear {
            messageDismissTask?.cancel()
        }
        .accessibilityIdentifier("feedbackScreen")
    }

    private func handle(_ event: FeedbackEvent?) {
        guard let event else { return }

        switch event {
        case .submitted:
            onSubmitted()
        case .reauthenticationRequired:
            onReauthenticationRequired()
        case let .showMessage(text):
            showMessage(text)
        }
        viewModel.consumeEvent()
    }

    private func showMessage(_ text: String) {
        messageDismissTask?.cancel()
        withAnimation(.snappy) {
            message = text
        }
        messageDismissTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) {
                message = nil
            }
        }
    }

    private var dismissGesture: some Gesture {
        DragGesture(
            minimumDistance: Metrics.dismissMinimumDragDistance,
            coordinateSpace: .named("feedback.dismiss")
        )
            .onEnded { value in
                guard !viewModel.viewState.isSubmitting,
                      !isKeyboardVisible,
                      !inputFrame.contains(value.startLocation),
                      !submitButtonFrame.contains(value.startLocation),
                      value.translation.height > abs(value.translation.width) else { return }
                if value.translation.height > Metrics.dismissDragDistance
                    || value.predictedEndTranslation.height > Metrics.dismissPredictedDistance {
                    onDismiss()
                }
            }
    }
}

private enum Metrics {
    static let contentTopPadding: CGFloat = ChalkakSpacing.xl
    static let dismissMinimumDragDistance: CGFloat = 20
    static let dismissDragDistance: CGFloat = 80
    static let dismissPredictedDistance: CGFloat = 140
    static let contentBottomPadding: CGFloat = ChalkakSpacing.lg
    static let inputTopPadding: CGFloat = 28
    static let inputHeight: CGFloat = 240
    static let toastBottomPadding: CGFloat = 96
}

#Preview("Feedback") {
    FeedbackScreen(
        viewModel: FeedbackViewModel(
            initialState: FeedbackViewState(content: "사진 업로드 후 화면이 멈춰요.")
        ),
        onDismiss: {},
        onSubmitted: {},
        onReauthenticationRequired: {}
    )
    .chalkakTheme(.light)
}
