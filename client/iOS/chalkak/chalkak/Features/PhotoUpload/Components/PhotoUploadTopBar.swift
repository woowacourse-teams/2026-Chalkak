import SwiftUI

struct PhotoUploadTopBar: View {
    @Environment(\.chalkakTheme) private var theme

    let onBackClick: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBackClick) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 24, weight: .regular))
                    .foregroundStyle(theme.colors.iconPrimary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("뒤로 가기")

            Text("전시하기")
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

#Preview("Photo Upload Top Bar") {
    PhotoUploadTopBar(onBackClick: {})
        .padding(.leading, 8)
        .padding(.trailing, 12)
        .padding(.top, 10)
        .padding(.bottom, 8)
        .chalkakTheme(.light)
}
