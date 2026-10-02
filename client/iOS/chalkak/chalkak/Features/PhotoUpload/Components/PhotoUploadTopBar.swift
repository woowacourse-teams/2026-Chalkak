import SwiftUI

struct PhotoUploadTopBar: View {
    @Environment(\.chalkakTheme) private var theme

    let onBackClick: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            ChalkakNavigationButton(kind: .back, action: onBackClick)

            Text("전시하기")
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
                .frame(maxWidth: .infinity)

            Color.clear
                .frame(
                    width: ChalkakNavigationButton.diameter,
                    height: ChalkakNavigationButton.diameter
                )
                .accessibilityHidden(true)
        }
        .frame(maxWidth: .infinity)
    }
}

#Preview("Photo Upload Top Bar") {
    PhotoUploadTopBar(onBackClick: {})
        .padding(.horizontal, ChalkakSpacing.lg)
        .padding(.vertical, ChalkakSpacing.md)
        .chalkakTheme(.light)
}
