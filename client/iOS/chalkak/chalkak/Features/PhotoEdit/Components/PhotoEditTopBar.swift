import SwiftUI

struct PhotoEditTopBar: View {
    @Environment(\.chalkakTheme) private var theme

    let title: String
    var isDoneEnabled = true
    let onCancel: () -> Void
    let onDone: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            textButton("취소", isEnabled: true, action: onCancel)
                .accessibilityIdentifier("photoEdit.cancel")

            Text(title)
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textOnEditor)
                .frame(maxWidth: .infinity)
                .accessibilityAddTraits(.isHeader)

            textButton("완료", isEnabled: isDoneEnabled, action: onDone)
                .accessibilityIdentifier("photoEdit.done")
        }
    }

    private func textButton(_ title: String, isEnabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(theme.typography.body)
                .foregroundStyle(isEnabled ? theme.colors.textOnEditor : theme.colors.textOnEditorMuted)
                .frame(minWidth: Metrics.minimumTouchSize, minHeight: Metrics.minimumTouchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }
}

private enum Metrics {
    static let minimumTouchSize: CGFloat = 44
}

#Preview("Photo Edit Top Bar") {
    VStack {
        PhotoEditTopBar(title: "편집", onCancel: {}, onDone: {})
        PhotoEditTopBar(title: "자르기", isDoneEnabled: false, onCancel: {}, onDone: {})
    }
    .padding(.horizontal, ChalkakSpacing.sm)
    .background(ChalkakColors.light.editorBackground)
    .chalkakTheme(.light)
}
