import SwiftUI

struct DisplayTopic: View {
    @Environment(\.chalkakTheme) private var theme
    let topic: String
    let caption: String

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.spacing) {
            Text(caption)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textMuted)
                .lineLimit(1)

            Text(topic)
                .font(theme.typography.title1)
                .fontWeight(.bold)
                .foregroundStyle(theme.colors.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

private enum Metrics {
    static let spacing: CGFloat = 8
}

#Preview("Display Topic", traits: .sizeThatFitsLayout) {
    DisplayTopic(topic: "반짝이는 순간", caption: "오늘의 전시")
        .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
