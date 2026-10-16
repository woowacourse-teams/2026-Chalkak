import SwiftUI

struct ChalkakNavigationButton: View {
    @Environment(\.chalkakTheme) private var theme

    enum Kind {
        case back
        case close

        var title: String {
            switch self {
            case .back: "뒤로 가기"
            case .close: "닫기"
            }
        }

        var systemImage: String {
            switch self {
            case .back: "chevron.backward"
            case .close: "xmark"
            }
        }
    }

    static let diameter: CGFloat = 48

    let kind: Kind
    var controlSize: ControlSize = .large
    let action: () -> Void

    var body: some View {
        Button(kind.title, systemImage: kind.systemImage, action: action)
            .labelStyle(.iconOnly)
            .foregroundStyle(theme.colors.iconPrimary)
            .tint(theme.colors.iconPrimary)
            .controlSize(controlSize)
            .modifier(ChalkakNavigationButtonStyle())
            .buttonBorderShape(.circle)
            .frame(width: Self.diameter, height: Self.diameter)
            .accessibilityLabel(kind.title)
    }
}

#Preview("Navigation Buttons") {
    HStack {
        ChalkakNavigationButton(kind: .back, action: {})
        ChalkakNavigationButton(kind: .close, action: {})
    }
    .padding()
    .chalkakTheme(.light)
}

struct ChalkakNavigationButtonStyle: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *), ChalkakPlatformAppearance.usesLiquidGlass {
            content.buttonStyle(.glass)
        } else {
            content.buttonStyle(.plain)
        }
    }
}

struct ChalkakCloseButton: View {
    @Environment(\.chalkakTheme) private var theme
    let action: () -> Void

    var body: some View {
        if #available(iOS 26.0, *), ChalkakPlatformAppearance.usesLiquidGlass {
            Button(role: .close, action: action)
                .tint(theme.colors.iconPrimary)
                .accessibilityLabel("닫기")
        } else {
            ChalkakNavigationButton(kind: .close, controlSize: .regular, action: action)
        }
    }
}

extension ToolbarContent {
    @ToolbarContentBuilder
    func chalkakNavigationBackground() -> some ToolbarContent {
        if #available(iOS 26.0, *) {
            sharedBackgroundVisibility(ChalkakPlatformAppearance.usesLiquidGlass ? .automatic : .hidden)
        } else {
            self
        }
    }
}
