import SwiftUI

struct FeedbackTopBar: View {
    @Environment(\.chalkakTheme) private var theme

    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 24, weight: .regular))
                    .foregroundStyle(theme.colors.iconPrimary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("뒤로 가기")

            Text("피드백 보내기")
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
                .frame(maxWidth: .infinity)

            Color.clear
                .frame(width: 44, height: 44)
                .accessibilityHidden(true)
        }
        .frame(maxWidth: .infinity)
    }
}

#Preview("Feedback Top Bar") {
    FeedbackTopBar(onBack: {})
        .padding(.horizontal, 8)
        .padding(.vertical, 10)
        .chalkakTheme(.light)
}
