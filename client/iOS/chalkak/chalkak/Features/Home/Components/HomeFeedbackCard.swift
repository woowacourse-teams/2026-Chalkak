import SwiftUI

enum HomeFeedbackPhase: Hashable {
    case loading
    case unavailable
    case loadError
    case failed(canRequestAgain: Bool)
    case ready
    case processing
    case completed
}

struct HomeFeedbackCard: View {
    @Environment(\.chalkakTheme) private var theme
    @State private var isInterpretationTipPresented = false

    let phase: HomeFeedbackPhase
    let isExpanded: Bool
    let onStart: () -> Void
    let onToggleExpanded: () -> Void
    var result: AIReviewResult? = nil
    var isSubmitting = false
    var pollingTimedOut = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: phase == .completed ? onToggleExpanded : onStart) {
                HStack(spacing: theme.spacing.sm) {
                    Text(title)
                        .font(phase == .completed && isExpanded ? theme.typography.headline : theme.typography.callout)
                        .fontWeight(.semibold)
                        .foregroundStyle(theme.colors.textPrimary)

                    Spacer(minLength: 0)

                    if phase == .completed {
                        if !isExpanded {
                            Text("자세히 보기")
                                .font(theme.typography.footnote)
                                .foregroundStyle(theme.colors.textSecondary)
                        }
                        Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                            .font(.system(size: Metrics.chevronSize, weight: .semibold))
                            .foregroundStyle(theme.colors.iconSecondary)
                            .frame(width: Metrics.iconSize, height: Metrics.iconSize)
                            .accessibilityHidden(true)
                    } else {
                        if phase == .failed(canRequestAgain: true) && !isSubmitting {
                            Text("재요청")
                                .font(theme.typography.footnote)
                                .foregroundStyle(theme.colors.textSecondary)
                        }
                        ChalkakAudioWaveIcon(isAnimating: phase == .processing)
                            .foregroundStyle(theme.colors.onActionPrimary)
                            .frame(width: Metrics.audioIconSize, height: Metrics.audioIconSize)
                            .background(theme.colors.actionPrimary, in: Circle())
                            .accessibilityHidden(true)
                    }
                }
                .padding(.leading, phase == .completed && isExpanded ? theme.spacing.xl : theme.spacing.lg)
                .padding(.trailing, theme.spacing.sm)
                .frame(minHeight: Metrics.rowHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(HomeFeedbackHeaderButtonStyle())
            .disabled(isDisabled)
            .accessibilityLabel(
                phase == .failed(canRequestAgain: true) && !isSubmitting
                    ? "\(title), 재요청"
                    : title
            )
            .accessibilityValue(phase == .completed ? (isExpanded ? "펼쳐짐" : "접힘") : "")

            if pollingTimedOut {
                Text("처리가 오래 걸리고 있어요. 나중에 다시 확인해 주세요.")
                    .font(theme.typography.footnote)
                    .foregroundStyle(theme.colors.textSecondary)
                    .padding(.horizontal, theme.spacing.lg)
                    .padding(.bottom, theme.spacing.lg)
            }

            if phase == .completed && isExpanded, let result {
                feedbackDetail(result)
                    .padding(.horizontal, theme.spacing.xl)
                    .padding(.bottom, theme.spacing.xl)
            }
        }
        .background(theme.colors.surfaceElevated, in: RoundedRectangle(cornerRadius: theme.shapes.xlarge))
        .contentShape(Rectangle())
        .gesture(
            TapGesture().onEnded { isInterpretationTipPresented = false },
            including: isInterpretationTipPresented ? .all : .subviews
        )
        .overlay {
            RoundedRectangle(cornerRadius: theme.shapes.xlarge)
                .strokeBorder(theme.colors.border, lineWidth: Metrics.borderWidth)
        }
        .shadow(color: theme.colors.actionPrimary.opacity(0.08), radius: 12, y: 5)
        .onChange(of: isExpanded) { _, expanded in
            if !expanded { isInterpretationTipPresented = false }
        }
    }

    private var isDisabled: Bool {
        if isSubmitting { return true }
        return switch phase {
        case .loading, .processing, .unavailable, .failed(canRequestAgain: false): true
        default: false
        }
    }

    private var title: String {
        if isSubmitting { return "AI 피드백을 요청하고 있어요" }
        return switch phase {
        case .loading: "AI 피드백 상태를 확인하고 있어요"
        case .unavailable: "AI 피드백을 확인할 수 없는 게시물이에요"
        case .loadError: "AI 피드백 상태 다시 확인"
        case .failed: "AI 피드백을 생성하지 못했어요"
        case .ready: "이 사진, AI에게 피드백 받아보기"
        case .processing: "AI 피드백이 진행중이에요"
        case .completed: "AI 피드백이 완료되었어요"
        }
    }

    private func feedbackDetail(_ result: AIReviewResult) -> some View {
        VStack(alignment: .leading, spacing: theme.spacing.lg) {
            if result.distinctivePerspective {
                Button {
                    isInterpretationTipPresented.toggle()
                } label: {
                    Label("작가님의 해석이 돋보이는 작품이에요", systemImage: "camera.aperture")
                        .font(theme.typography.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(theme.colors.onActionPrimary)
                        .padding(.horizontal, theme.spacing.sm)
                        .padding(.vertical, theme.spacing.xs)
                        .background(theme.colors.actionPrimary, in: RoundedRectangle(cornerRadius: theme.shapes.small))
                        .frame(minHeight: Metrics.iconSize)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityHint("독특한 시각 배지의 의미를 확인합니다")
                .overlay(alignment: .topLeading) {
                    if isInterpretationTipPresented {
                        GeometryReader { geometry in
                            interpretationTip
                                .offset(y: geometry.size.height + theme.spacing.xs)
                        }
                    }
                }
                .zIndex(1)
            }

            HStack(alignment: .center, spacing: theme.spacing.lg) {
                Text(result.feedback.summary)
                    .font(theme.typography.title3)
                    .fontWeight(.bold)
                    .foregroundStyle(theme.colors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(1)

                ZStack {
                    Circle()
                        .stroke(theme.colors.calendarCell, lineWidth: Metrics.scoreRingWidth)
                    Circle()
                        .trim(from: 0, to: min(1, max(0, result.overallScore / 10)))
                        .stroke(
                            theme.colors.actionPrimary,
                            style: StrokeStyle(lineWidth: Metrics.scoreRingWidth, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                    Text(result.overallScore.formatted(.number.precision(.fractionLength(1))))
                        .font(theme.typography.title1)
                        .foregroundStyle(theme.colors.textPrimary)
                }
                .frame(width: Metrics.scoreSize, height: Metrics.scoreSize)
                .accessibilityLabel("종합 점수 10점 만점에 \(result.overallScore.formatted(.number.precision(.fractionLength(1))))점")
            }
            .padding(.bottom, theme.spacing.sm)

            Divider()

            feedbackParagraph(title: "좋았던 점", body: result.feedback.strength)
            feedbackParagraph(title: "아쉬운 점", body: result.feedback.weakness)

            Divider()

            feedbackParagraph(
                title: "다음엔 이렇게",
                body: result.feedback.suggestion
            )

            AIReviewScoreExplanationButton()
        }
    }

    private var interpretationTip: some View {
        VStack(alignment: .leading, spacing: 0) {
            InterpretationTipArrow()
                .fill(theme.colors.actionPrimary)
                .frame(width: Metrics.tipArrowWidth, height: Metrics.tipArrowHeight)
                .padding(.leading, theme.spacing.lg)

            Text("종합 점수와 별개로, 독특한 시각이 드러난 사진이라는 의미예요.")
                .font(theme.typography.footnote)
                .foregroundStyle(theme.colors.onActionPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(theme.spacing.md)
                .frame(width: Metrics.interpretationTipWidth, alignment: .leading)
                .background(theme.colors.actionPrimary, in: RoundedRectangle(cornerRadius: theme.shapes.button))
        }
        .accessibilityAction(.escape) {
            isInterpretationTipPresented = false
        }
    }

    private func feedbackParagraph(title: String, body: String) -> some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            Text(title)
                .font(theme.typography.footnote)
                .fontWeight(.semibold)
                .foregroundStyle(theme.colors.textPrimary)
            Text(body)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)
                .lineSpacing(theme.spacing.xs)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct InterpretationTipArrow: Shape {
    func path(in rect: CGRect) -> Path {
        Path { path in
            path.move(to: CGPoint(x: rect.minX, y: rect.maxY))
            path.addLine(to: CGPoint(x: rect.midX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
            path.closeSubpath()
        }
    }
}

private struct HomeFeedbackHeaderButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}


private enum Metrics {
    static let borderWidth: CGFloat = 1
    static let rowHeight: CGFloat = 64
    static let iconSize: CGFloat = 44
    static let audioIconSize: CGFloat = 32
    static let chevronSize: CGFloat = 15
    static let scoreSize: CGFloat = 84
    static let scoreRingWidth: CGFloat = 8
    static let interpretationTipWidth: CGFloat = 240
    static let tipArrowWidth: CGFloat = 12
    static let tipArrowHeight: CGFloat = 6
    static let noteIconSize: CGFloat = 17
    static let noteCircleSize: CGFloat = 36
}

#Preview("Feedback states") {
    VStack(spacing: ChalkakSpacing.lg) {
        HomeFeedbackCard(phase: .failed(canRequestAgain: true), isExpanded: false, onStart: {}, onToggleExpanded: {})
        HomeFeedbackCard(phase: .failed(canRequestAgain: false), isExpanded: false, onStart: {}, onToggleExpanded: {})
        HomeFeedbackCard(phase: .ready, isExpanded: false, onStart: {}, onToggleExpanded: {})
        HomeFeedbackCard(phase: .processing, isExpanded: false, onStart: {}, onToggleExpanded: {})
        HomeFeedbackCard(phase: .completed, isExpanded: true, onStart: {}, onToggleExpanded: {}, result: HomePreviewData.aiReviewResult)
    }
    .padding()
    .chalkakTheme(.light)
}
