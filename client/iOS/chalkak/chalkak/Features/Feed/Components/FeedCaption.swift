import SwiftUI

struct FeedCaption: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String?

    var body: some View {
        HStack(spacing: 0) {
            Text(displayTitle)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)
                .lineLimit(1)
                .truncationMode(.tail)

            Spacer(minLength: 0)
        }
        .padding(.vertical, Metrics.verticalPadding)
    }

    private var displayTitle: String {
        guard let trimmed = title?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty
        else {
            return "무제"
        }
        return trimmed
    }
}

private enum Metrics {
    static let verticalPadding: CGFloat = 6
}

#Preview("Feed Caption", traits: .sizeThatFitsLayout) {
    VStack(spacing: 0) {
        FeedCaption(title: "안녕하세요 감사합니다.")
        FeedCaption(title: nil)
    }
    .padding(.horizontal, 20)
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
