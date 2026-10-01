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
            .tint(theme.colors.iconPrimary)
            .controlSize(controlSize)
            .buttonStyle(.glass)
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
