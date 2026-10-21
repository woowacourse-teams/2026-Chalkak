import SwiftUI

/// 도구 화면 하단의 취소·적용 버튼. 가운데에는 도구별 버튼을 둔다.
struct PhotoEditActionBar<Tools: View>: View {
    @Environment(\.chalkakTheme) private var theme

    var isApplyEnabled = true
    let onCancel: () -> Void
    let onApply: () -> Void
    @ViewBuilder let tools: () -> Tools

    var body: some View {
        PhotoEditControlGroup {
            HStack(spacing: 0) {
                circleButton("취소", systemImage: "xmark", isEnabled: true, action: onCancel)
                    .accessibilityIdentifier("photoEdit.tool.cancel")

                // 도구가 없어도 취소·적용 버튼이 양 끝에 머물도록 Spacer로 밀어 둔다.
                Spacer(minLength: 0)
                tools()
                Spacer(minLength: 0)

                circleButton("적용", systemImage: "checkmark", isEnabled: isApplyEnabled, action: onApply)
                    .accessibilityIdentifier("photoEdit.tool.apply")
            }
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
                .photoEditControlBackground(in: Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(title)
    }
}

extension PhotoEditActionBar where Tools == EmptyView {
    init(isApplyEnabled: Bool = true, onCancel: @escaping () -> Void, onApply: @escaping () -> Void) {
        self.init(isApplyEnabled: isApplyEnabled, onCancel: onCancel, onApply: onApply, tools: { EmptyView() })
    }
}

/// 자르기 화면의 좌우반전·회전 버튼.
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
        .photoEditControlBackground(in: Capsule())
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
    static let buttonSize: CGFloat = 52
    static let iconSize: CGFloat = 20
}

#Preview("Photo Edit Action Bar") {
    VStack(spacing: ChalkakSpacing.lg) {
        PhotoEditActionBar(onCancel: {}, onApply: {})
        PhotoEditActionBar(isApplyEnabled: false, onCancel: {}, onApply: {}) {
            PhotoEditCropTools(onFlip: {}, onRotate: {})
        }
    }
    .padding(ChalkakSpacing.lg)
    .background(ChalkakColors.light.editorBackground)
    .chalkakTheme(.light)
}
