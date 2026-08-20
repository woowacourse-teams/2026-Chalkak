import SwiftUI

enum ChalkakColor {
    static let background = Color(hex: 0xF7F6F3)
    static let surface = Color(hex: 0xF7F6F3)
    static let surfaceElevated = Color.white
    static let inputBackground = Color(hex: 0xFCFAF6)
    static let bottomBar = Color(hex: 0x8C8479)
    static let textPrimary = Color(hex: 0x2B2724)
    static let textSecondary = Color(hex: 0x2B2724).opacity(0.68)
    static let textMuted = Color(hex: 0x2B2724).opacity(0.48)
    static let textInactive = Color(hex: 0x9A968D)
    static let textOnImage = Color.white
    static let border = Color(hex: 0x888888).opacity(0.15)
    static let inputCursor = Color(hex: 0xB0563B)
    static let actionPrimary = Color(hex: 0x2B2724)
    static let onActionPrimary = Color.white
    static let iconPrimary = Color(hex: 0x2B2724)
    static let iconSecondary = Color(hex: 0x2B2724).opacity(0.62)
    static let scrim = Color.black.opacity(0.4)
    static let error = Color(hex: 0xBA1A1A)
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
