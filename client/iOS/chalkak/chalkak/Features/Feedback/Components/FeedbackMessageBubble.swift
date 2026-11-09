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
        VStack(alignment: isAnswer ? .leading : .trailing, spacing: theme.spacing.xs) {
            Text(text)
                .font(theme.typography.body)
                .foregroundStyle(isAnswer ? theme.colors.textPrimary : theme.colors.onActionPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(color, in: RoundedRectangle(cornerRadius: theme.shapes.input))
                .frame(maxWidth: maximumWidth, alignment: isAnswer ? .leading : .trailing)
                .accessibilityIdentifier(identifier)

            Text(FeedbackTimestampFormatter.string(from: date))
                .font(theme.typography.caption)
                .foregroundStyle(theme.colors.textInactive)
        }
        .frame(maxWidth: .infinity, alignment: isAnswer ? .leading : .trailing)
    }
}
