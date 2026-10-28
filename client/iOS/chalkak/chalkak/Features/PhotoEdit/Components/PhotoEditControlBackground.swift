import SwiftUI

/// 편집 화면 버튼의 바탕. 리퀴드 글라스를 쓸 수 있으면 글라스로, 아니면 `editorControl` 색으로 채운다.
///
/// 예: `.photoEditControlBackground(in: Circle())`
private struct PhotoEditControlBackground<S: Shape>: ViewModifier {
    @Environment(\.chalkakTheme) private var theme

    let shape: S

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *), ChalkakPlatformAppearance.usesLiquidGlass {
            content.glassEffect(.regular.interactive(), in: shape)
        } else {
            content.background(theme.colors.editorControl, in: shape)
        }
    }
}

extension View {
    func photoEditControlBackground(in shape: some Shape) -> some View {
        modifier(PhotoEditControlBackground(shape: shape))
    }
}

/// 가까이 놓인 편집 버튼들의 글라스를 함께 그려, 서로 겹치거나 가까워질 때 하나로 이어 보이게 한다.
/// 리퀴드 글라스를 쓰지 않으면 `content`를 그대로 보여 준다.
struct PhotoEditControlGroup<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        if #available(iOS 26.0, *), ChalkakPlatformAppearance.usesLiquidGlass {
            GlassEffectContainer(content: content)
        } else {
            content()
        }
    }
}
