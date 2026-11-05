import SwiftUI

/// 텍스트 말풍선과 말풍선 바깥의 시각. 좌우 정렬만 다르고 문의·답변 의미는 호출부가 정한다.
struct FeedbackMessageBubble: View {
    @Environment(\.chalkakTheme) private var theme
    let text: String
    let date: Date
    let isAnswer: Bool
    let maximumWidth: CGFloat
    let identifier: String

    private var color: Color { isAnswer ? theme.colors.calendarCell : theme.colors.actionPrimary }

    var body: some View {
        VStack(alignment: isAnswer ? .trailing : .leading, spacing: theme.spacing.xs) {
            Text(text)
                .font(theme.typography.body)
                .foregroundStyle(isAnswer ? theme.colors.textPrimary : theme.colors.onActionPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(color, in: UnevenRoundedRectangle(
                    topLeadingRadius: isAnswer ? theme.shapes.input : theme.spacing.xs,
                    bottomLeadingRadius: theme.shapes.input,
                    bottomTrailingRadius: theme.shapes.input,
                    topTrailingRadius: isAnswer ? theme.spacing.xs : theme.shapes.input
                ))
                .overlay(alignment: isAnswer ? .topTrailing : .topLeading) {
                    FeedbackBubbleTail(isAnswer: isAnswer)
                        .fill(color)
                        .frame(width: Metrics.tailWidth, height: Metrics.tailHeight)
                        .offset(x: isAnswer ? Metrics.tailWidth - 1 : 1 - Metrics.tailWidth)
                        .accessibilityHidden(true)
                }
                .frame(maxWidth: maximumWidth, alignment: isAnswer ? .trailing : .leading)
                .accessibilityIdentifier(identifier)

            Text(FeedbackTimestampFormatter.string(from: date))
                .font(theme.typography.caption)
                .foregroundStyle(theme.colors.textInactive)
        }
        .frame(maxWidth: .infinity, alignment: isAnswer ? .trailing : .leading)
    }
}

private struct FeedbackBubbleTail: Shape {
    let isAnswer: Bool
    func path(in rect: CGRect) -> Path {
        Path { path in
            path.move(to: CGPoint(x: rect.minX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
            path.addLine(to: CGPoint(x: isAnswer ? rect.minX : rect.maxX, y: rect.maxY))
            path.closeSubpath()
        }
    }
}

private enum Metrics {
    static let tailWidth: CGFloat = 6
    static let tailHeight: CGFloat = 10
}
