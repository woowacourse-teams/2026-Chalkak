import SwiftUI

struct DisplayTopic: View {
    @Environment(\.chalkakTheme) private var theme
    let topic: String
    let description: String

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.spacing) {
            Text(topic)
                .font(theme.typography.title2)
                .foregroundStyle(theme.colors.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            Text(description)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textInactive)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

private enum Metrics {
    // Android DisplayDateHeader: 주제 → 설명 사이 12dp.
    static let spacing: CGFloat = 12
}

#Preview("Display Topic", traits: .sizeThatFitsLayout) {
    DisplayTopic(topic: "반짝이는 순간", description: "같은 주제에서 다른 시선을 느껴보세요")
        .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
