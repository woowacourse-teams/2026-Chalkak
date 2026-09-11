import SwiftUI

struct HomeTopic: View {
    @Environment(\.chalkakTheme) private var theme
    let topicDate: Date?
    let topic: String

    var body: some View {
        VStack(alignment: .leading, spacing: HomeTopicMetrics.titleSpacing) {
            Text(topicDateText)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textPrimary)
                .lineLimit(1)

            Text(topic)
                .font(theme.typography.title1)
                .fontWeight(.bold)
                .foregroundStyle(theme.colors.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, HomeTopicMetrics.topPadding)
        .padding(.bottom, HomeTopicMetrics.bottomPadding)
        .background(theme.colors.background)
    }

    private var topicDateText: String {
        guard let topicDate else { return "" }
        return "\(HomeDateFormatter.shared.string(from: topicDate)) · 오늘의 주제"
    }
}

extension View {
    func homeBottomDivider() -> some View {
        overlay(alignment: .bottom) {
            HomeBottomDivider()
        }
    }
}

private struct HomeBottomDivider: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        Rectangle()
            .fill(theme.colors.border)
            .frame(height: 0.5)
            .accessibilityHidden(true)
    }
}

private enum HomeDateFormatter {
    static let shared: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "M월 d일"
        return formatter
    }()
}

private enum HomeTopicMetrics {
    static let topPadding: CGFloat = 16
    static let bottomPadding: CGFloat = 20
    static let titleSpacing: CGFloat = 10
}

#Preview("Home Topic", traits: .sizeThatFitsLayout) {
    HomeTopic(
        topicDate: HomePreviewData.contentState.topicDate,
        topic: HomePreviewData.contentState.topic
    )
    .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
    .background(ChalkakTheme.light.colors.background)
    .homeBottomDivider()
    .chalkakTheme(.light)
}
