import SwiftUI

enum HomeFeedbackPhase: Hashable {
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
                        AudioWaveIcon(isAnimating: phase == .processing)
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
            .disabled(phase == .processing)
            .accessibilityLabel(title)
            .accessibilityValue(phase == .completed ? (isExpanded ? "펼쳐짐" : "접힘") : "")

            if phase == .completed && isExpanded {
                feedbackDetail
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

    private var title: String {
        switch phase {
        case .ready: "이 사진, AI에게 피드백 받아보기"
        case .processing: "AI 피드백이 진행중이에요"
        case .completed: "AI 피드백이 완료되었어요"
        }
    }

    private var feedbackDetail: some View {
        VStack(alignment: .leading, spacing: theme.spacing.lg) {
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
            .accessibilityHint("주제 해석 배지의 의미를 확인합니다")
            .overlay(alignment: .topLeading) {
                if isInterpretationTipPresented {
                    GeometryReader { geometry in
                        interpretationTip
                            .offset(y: geometry.size.height + theme.spacing.xs)
                    }
                }
            }
            .zIndex(1)

            HStack(alignment: .center, spacing: theme.spacing.lg) {
                Text("건물의 크기와 높이 차이가 잘 비교되는 구도예요")
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
                        .trim(from: 0, to: 0.87)
                        .stroke(
                            theme.colors.actionPrimary,
                            style: StrokeStyle(lineWidth: Metrics.scoreRingWidth, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                    Text("8.7")
                        .font(theme.typography.title1)
                        .foregroundStyle(theme.colors.textPrimary)
                }
                .frame(width: Metrics.scoreSize, height: Metrics.scoreSize)
                .accessibilityLabel("구도 점수 10점 만점에 8.7점")
            }
            .padding(.bottom, theme.spacing.sm)

            Divider()

            feedbackParagraph(title: "좋았던 점", body: "아래쪽 계단과 겹겹이 쌓인 집들, 위쪽 아파트가 한 화면에 같이 담겼어요.")
            feedbackParagraph(title: "아쉬운 점", body: "맨 아래 담장과 검은 옥상이 골목의 흐름을 끊어요.")

            Divider()

            feedbackParagraph(
                title: "다음엔 이렇게",
                body: "왼쪽 계단 입구와 붉은 도로는 남기면서 맨 아래 담장이 덜 나오게 담아보세요."
            )
        }
    }

    private var interpretationTip: some View {
        VStack(alignment: .leading, spacing: 0) {
            InterpretationTipArrow()
                .fill(theme.colors.actionPrimary)
                .frame(width: Metrics.tipArrowWidth, height: Metrics.tipArrowHeight)
                .padding(.leading, theme.spacing.lg)

            Text("구도 점수와 별개로, 주제 해석이 특별한 사진이라는 의미예요.")
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

struct HomeFeedbackStartSheet: View {
    @Environment(\.chalkakTheme) private var theme

    let onStart: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.lg) {
            Text("AI 피드백을 시작할까요?")
                .font(theme.typography.title3)
                .foregroundStyle(theme.colors.textPrimary)

            VStack(alignment: .leading, spacing: theme.spacing.lg) {
                note("clock", "결과가 나오기까지 최대 1~2분 소요될 수 있어요")
                note("iphone", "앱을 나가도 분석은 계속되니 걱정마세요")
                note("bell", "분석이 끝나면 알림으로 알려드릴게요")
            }
            .padding(theme.spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.colors.background, in: RoundedRectangle(cornerRadius: theme.shapes.large))

            ChalkakButton(title: "분석 시작하기", action: onStart, fillsWidth: true)
        }
        .padding(.horizontal, theme.spacing.xl)
        .padding(.top, theme.spacing.xxl)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(ChalkakPlatformAppearance.usesLiquidGlass ? Color.clear : theme.colors.surfaceElevated)
    }

    private func note(_ symbol: String, _ text: String) -> some View {
        HStack(spacing: theme.spacing.md) {
            Image(systemName: symbol)
                .font(.system(size: Metrics.noteIconSize))
                .foregroundStyle(theme.colors.iconSecondary)
                .frame(width: Metrics.noteCircleSize, height: Metrics.noteCircleSize)
                .background(theme.colors.surfaceElevated, in: Circle())
                .accessibilityHidden(true)
            Text(text)
                .font(theme.typography.subheadline)
                .fontWeight(.semibold)
                .foregroundStyle(theme.colors.textPrimary)
        }
    }
}

private struct AudioWaveIcon: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let isAnimating: Bool

    private let barHeights: [CGFloat] = [9, 15, 11, 18, 8]

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !isAnimating || reduceMotion)) { context in
            HStack(alignment: .center, spacing: 2) {
                ForEach(barHeights.indices, id: \.self) { index in
                    Capsule()
                        .fill(theme.colors.onActionPrimary)
                        .frame(width: 2, height: barHeights[index])
                        .scaleEffect(y: barScale(at: context.date, index: index))
                }
            }
            .frame(height: 18)
        }
    }

    private func barScale(at date: Date, index: Int) -> CGFloat {
        guard isAnimating, !reduceMotion else { return 1 }
        let phase = date.timeIntervalSinceReferenceDate * 7 + Double(index) * 1.2
        return CGFloat(0.7 + 0.3 * sin(phase))
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
        HomeFeedbackCard(phase: .ready, isExpanded: false, onStart: {}, onToggleExpanded: {})
        HomeFeedbackCard(phase: .processing, isExpanded: false, onStart: {}, onToggleExpanded: {})
        HomeFeedbackCard(phase: .completed, isExpanded: true, onStart: {}, onToggleExpanded: {})
    }
    .padding()
    .chalkakTheme(.light)
}
