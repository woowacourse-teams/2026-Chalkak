import SwiftUI

struct FeedAIReviewSheet: View {
    @Environment(\.chalkakTheme) private var theme
    let model: FeedAIReviewViewModel
    let onDismiss: () -> Void
    let onStart: () -> Void

    var body: some View {
        presentation
            .presentationDragIndicator(.visible)

    }

    @ViewBuilder
    private var presentation: some View {
        if ChalkakPlatformAppearance.usesLiquidGlass {
            content
        } else {
            content
                .presentationCornerRadius(theme.shapes.sheet)
                .presentationBackground(theme.colors.surfaceElevated)
        }
    }

    private var content: some View {
        Group {
            if model.canRequest {
                AIReviewStartSheet(
                    onStart: onStart,
                    title: model.review?.status == .failed ? "AI 피드백을 재요청할까요?" : "AI 피드백을 시작할까요?",
                    message: model.message
                )
            } else if let reason = model.failureReason {
                failureContent(reason)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: theme.spacing.xl) {
                        Text(title)
                            .font(theme.typography.headline)
                            .foregroundStyle(theme.colors.textPrimary)

                        if let result = model.review?.result, model.review?.status == .completed {
                            resultContent(result)
                        } else {
                            statusContent
                        }
                    }
                    .padding(.horizontal, theme.spacing.xl)
                    .padding(.top, theme.spacing.xxl)
                    .padding(.bottom, theme.spacing.xl)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .background(ChalkakPlatformAppearance.usesLiquidGlass ? Color.clear : theme.colors.surfaceElevated)
            }
        }
    }

    private var title: String {
        switch model.review?.status {
        case .completed: "AI 피드백이 완료되었어요"
        case .processing: "AI가 사진을 분석하고 있어요"
        case .failed: "AI 피드백을 생성하지 못했어요"
        default: "AI 피드백"
        }
    }

    @ViewBuilder
    private var statusContent: some View {
        if model.isLoading || model.isSubmitting {
            ProgressView().frame(maxWidth: .infinity)
        }
        Text(model.message ?? (model.pollingTimedOut
            ? "처리가 오래 걸리고 있어요. 나중에 다시 확인해 주세요."
            : model.review?.status == .processing
                ? "앱을 나가도 분석은 계속돼요. 분석이 끝나면 알림으로 알려드릴게요."
                : "잠시 후 다시 확인해 주세요."))
            .font(theme.typography.body)
            .foregroundStyle(theme.colors.textSecondary)
        Button("상태 다시 확인") { Task { await model.refresh() } }
            .font(theme.typography.callout)
            .foregroundStyle(theme.colors.actionPrimary)
            .frame(minHeight: 44)
            .disabled(model.isLoading || model.isSubmitting)
    }

    // 기다려도 상태가 바뀌지 않는 실패라 다시 확인 대신 이유와 닫기만 보여 준다.
    private func failureContent(_ reason: String) -> some View {
        VStack(alignment: .leading, spacing: theme.spacing.lg) {
            Text("AI 피드백을 생성하지 못했어요")
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
            Text(reason)
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            ChalkakButton(title: "확인", action: onDismiss, fillsWidth: true)
        }
        .padding(.horizontal, theme.spacing.xl)
        .padding(.top, theme.spacing.xxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(ChalkakPlatformAppearance.usesLiquidGlass ? Color.clear : theme.colors.surfaceElevated)
    }

    private func resultContent(_ result: AIReviewResult) -> some View {
        VStack(alignment: .leading, spacing: theme.spacing.xl) {
            if result.distinctivePerspective {
                Label("작가님의 해석이 돋보이는 작품이에요", systemImage: "camera.aperture")
                    .font(theme.typography.caption)
                    .fontWeight(.semibold)
                    .padding(.horizontal, theme.spacing.sm)
                    .padding(.vertical, theme.spacing.xs)
                    .background(theme.colors.background, in: Capsule())
                    .accessibilityHint("종합 점수와 별개로 독특한 시각이 드러난 사진이라는 의미예요")
            }
            HStack(spacing: theme.spacing.lg) {
                Text(result.feedback.summary)
                    .font(theme.typography.headline)
                    .fontWeight(.bold)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                ZStack {
                    Circle().stroke(theme.colors.calendarCell, lineWidth: Metrics.ringWidth)
                    Circle()
                        .trim(from: 0, to: min(1, max(0, result.overallScore / 10)))
                        .stroke(theme.colors.actionPrimary, style: StrokeStyle(lineWidth: Metrics.ringWidth, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Text(result.overallScore.formatted(.number.precision(.fractionLength(1))))
                        .font(theme.typography.title1)
                }
                .frame(width: Metrics.scoreSize, height: Metrics.scoreSize)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("종합 점수 10점 만점에 \(result.overallScore.formatted(.number.precision(.fractionLength(1))))점")
            }
            .padding(.vertical, theme.spacing.md)
            Divider()
            paragraph("좋았던 점", result.feedback.strength)
            paragraph("아쉬운 점", result.feedback.weakness)
            Divider()
            paragraph("다음엔 이렇게", result.feedback.suggestion)
            AIReviewScoreExplanationButton()
        }
        .foregroundStyle(theme.colors.textPrimary)
    }

    private func paragraph(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            Text(title).font(theme.typography.caption).fontWeight(.semibold)
            Text(text)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)
                .lineSpacing(theme.spacing.xs)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private enum Metrics {
        static let ringWidth: CGFloat = 8
        static let scoreSize: CGFloat = 76
    }
}

struct FeedAIReviewButton: View {
    @Environment(\.chalkakTheme) private var theme
    let model: FeedAIReviewViewModel
    let action: () -> Void

    var body: some View {
        Group {
            if #available(iOS 26.0, *), ChalkakPlatformAppearance.usesLiquidGlass {
                button.glassEffect(.regular.interactive(), in: Capsule())
            } else {
                button
                    .background(theme.colors.surfaceElevated, in: Capsule())
                    .overlay { Capsule().strokeBorder(theme.colors.border, lineWidth: 1) }
            }
        }
        .disabled(model.isLoading || model.isSubmitting)
        .accessibilityIdentifier("feed-ai-review-button")
    }

    private var button: some View {
        Button(action: action) {
            HStack(spacing: theme.spacing.sm) {
                ChalkakAudioWaveIcon(isAnimating: model.review?.status == .processing)
                    .frame(width: 24, height: 24)
                    .accessibilityHidden(true)
                Text(model.buttonTitle).font(theme.typography.subheadline).fontWeight(.semibold)
            }
            .foregroundStyle(theme.colors.textPrimary)
            .padding(.horizontal, theme.spacing.lg)
            .frame(minHeight: 44)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}
