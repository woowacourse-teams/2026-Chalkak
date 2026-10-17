import SwiftUI

/// 편집 첫 화면 상단의 뒤로 가기와 완료 버튼.
struct PhotoEditHeader: View {
    @Environment(\.chalkakTheme) private var theme

    var isDoneEnabled = true
    let onBack: () -> Void
    let onDone: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                Image(systemName: "chevron.backward")
                    .font(.system(size: Metrics.iconSize, weight: .semibold))
                    .foregroundStyle(theme.colors.textOnEditor)
                    .frame(width: Metrics.controlHeight, height: Metrics.controlHeight)
                    .background(theme.colors.editorControl, in: Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("뒤로 가기")
            .accessibilityIdentifier("photoEdit.back")

            Spacer(minLength: 0)

            Button(action: onDone) {
                Text("완료")
                    .font(theme.typography.body)
                    .foregroundStyle(isDoneEnabled ? theme.colors.textOnEditor : theme.colors.textOnEditorMuted)
                    .padding(.horizontal, theme.spacing.lg)
                    .frame(height: Metrics.controlHeight)
                    .background(theme.colors.editorControl, in: Capsule())
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .disabled(!isDoneEnabled)
            .accessibilityIdentifier("photoEdit.done")
        }
    }
}

private enum Metrics {
    static let controlHeight: CGFloat = 48
    static let iconSize: CGFloat = 18
}

#Preview("Photo Edit Header") {
    PhotoEditHeader(onBack: {}, onDone: {})
        .padding(ChalkakSpacing.lg)
        .background(ChalkakColors.light.editorBackground)
        .chalkakTheme(.light)
}
