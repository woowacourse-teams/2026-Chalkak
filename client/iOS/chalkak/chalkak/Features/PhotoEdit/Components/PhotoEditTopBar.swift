import SwiftUI

/// 취소·완료 버튼 사이에 탭별 도구를 둔다.
struct PhotoEditTopBar<Tools: View>: View {
    @Environment(\.chalkakTheme) private var theme

    var isDoneEnabled = true
    let onCancel: () -> Void
    let onDone: () -> Void
    @ViewBuilder let tools: () -> Tools

    var body: some View {
        HStack(spacing: 0) {
            circleButton("취소", systemImage: "xmark", isEnabled: true, action: onCancel)
                .accessibilityIdentifier("photoEdit.cancel")

            // 도구가 없어도 취소·완료 버튼이 양 끝에 머물도록 Spacer로 밀어 둔다.
            Spacer(minLength: 0)
            tools()
            Spacer(minLength: 0)

            circleButton("완료", systemImage: "checkmark", isEnabled: isDoneEnabled, action: onDone)
                .accessibilityIdentifier("photoEdit.done")
        }
    }

    private func circleButton(
        _ title: String,
        systemImage: String,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: Metrics.iconSize, weight: .semibold))
                .foregroundStyle(isEnabled ? theme.colors.textOnEditor : theme.colors.textOnEditorMuted)
                .frame(width: Metrics.buttonSize, height: Metrics.buttonSize)
                .background(theme.colors.editorControl, in: Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(title)
    }
}

extension PhotoEditTopBar where Tools == EmptyView {
    init(isDoneEnabled: Bool = true, onCancel: @escaping () -> Void, onDone: @escaping () -> Void) {
        self.init(isDoneEnabled: isDoneEnabled, onCancel: onCancel, onDone: onDone, tools: { EmptyView() })
    }
}

/// 자르기 탭의 좌우반전·회전 버튼.
struct PhotoEditCropTools: View {
    @Environment(\.chalkakTheme) private var theme

    let onFlip: () -> Void
    let onRotate: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            toolButton("좌우 반전", systemImage: "arrow.left.and.right.righttriangle.left.righttriangle.right", action: onFlip)
            toolButton("왼쪽으로 회전", systemImage: "rotate.left", action: onRotate)
        }
        .padding(.horizontal, theme.spacing.xs)
        .background(theme.colors.editorControl, in: Capsule())
    }

    private func toolButton(_ title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: Metrics.iconSize, weight: .regular))
                .foregroundStyle(theme.colors.textOnEditor)
                .frame(width: Metrics.buttonSize, height: Metrics.buttonSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}

private enum Metrics {
    static let buttonSize: CGFloat = 48
    static let iconSize: CGFloat = 18
}

#Preview("Photo Edit Top Bar") {
    VStack(spacing: ChalkakSpacing.lg) {
        PhotoEditTopBar(onCancel: {}, onDone: {})
        PhotoEditTopBar(isDoneEnabled: false, onCancel: {}, onDone: {}) {
            PhotoEditCropTools(onFlip: {}, onRotate: {})
        }
    }
    .padding(ChalkakSpacing.lg)
    .background(ChalkakColors.light.editorBackground)
    .chalkakTheme(.light)
}
