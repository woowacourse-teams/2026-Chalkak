import SwiftUI

struct FeedTopic: View {
    @Environment(\.chalkakTheme) private var theme
    let dateLabel: String
    let topic: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(dateLabel)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textPrimary)

            Text(topic)
                .font(theme.typography.display)
                .foregroundStyle(theme.colors.textPrimary)
                .padding(.top, Metrics.topicTopSpacing)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private enum Metrics {
    static let topicTopSpacing: CGFloat = 8
}

#Preview("Feed Topic", traits: .sizeThatFitsLayout) {
    FeedTopic(dateLabel: "8월 3일의 주제", topic: "하늘하늘하늘")
        .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
