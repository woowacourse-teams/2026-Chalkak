import SwiftUI

struct ChalkakColors {
    let background = Color(hex: 0xF7F6F3)
    let surface = Color(hex: 0xF7F6F3)
    let surfaceElevated = Color.white
    let inputBackground = Color(hex: 0xFCFAF6)
    let bottomBar = Color(hex: 0x8C8479)
    let textPrimary = Color(hex: 0x2B2724)
    let textSecondary = Color(hex: 0x2B2724).opacity(0.68)
    let textMuted = Color(hex: 0x2B2724).opacity(0.48)
    let textInactive = Color(hex: 0x9A968D)
    let textOnImage = Color.white
    let border = Color(hex: 0x888888).opacity(0.15)
    let actionPrimary = Color(hex: 0x2B2724)
    let onActionPrimary = Color.white
    let iconPrimary = Color(hex: 0x2B2724)
    let iconSecondary = Color(hex: 0x2B2724).opacity(0.62)
    let scrim = Color.black.opacity(0.4)
    let error = Color(hex: 0xBA1A1A)
}

private struct ChalkakColorsKey: EnvironmentKey {
    static let defaultValue = ChalkakColors()
}

extension EnvironmentValues {
    var chalkakColors: ChalkakColors {
        get { self[ChalkakColorsKey.self] }
        set { self[ChalkakColorsKey.self] = newValue }
    }
}

extension View {
    func chalkakColors(_ colors: ChalkakColors) -> some View {
        environment(\.chalkakColors, colors)
    }
}

private extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
