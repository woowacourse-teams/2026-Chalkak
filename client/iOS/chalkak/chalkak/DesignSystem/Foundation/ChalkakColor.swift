import SwiftUI

struct ChalkakColors {
    let background: Color
    let surface: Color
    let surfaceElevated: Color
    let inputBackground: Color
    let bottomBar: Color
    let textPrimary: Color
    let textSecondary: Color
    let textMuted: Color
    let textInactive: Color
    let textOnImage: Color
    let border: Color
    let inputCursor: Color
    let actionPrimary: Color
    let onActionPrimary: Color
    let iconPrimary: Color
    let iconSecondary: Color
    let scrim: Color
    let error: Color
    let calendarCell: Color
    let calendarCellBorder: Color
    let skeletonBase: Color
    let skeletonHighlight: Color
    let editorBackground: Color
    let editorControl: Color
    let editorAccent: Color
    let textOnEditor: Color
    let textOnEditorMuted: Color

    init(
        background: Color,
        surface: Color,
        surfaceElevated: Color,
        inputBackground: Color,
        bottomBar: Color,
        textPrimary: Color,
        textSecondary: Color,
        textMuted: Color,
        textInactive: Color,
        textOnImage: Color,
        border: Color,
        inputCursor: Color,
        actionPrimary: Color,
        onActionPrimary: Color,
        iconPrimary: Color,
        iconSecondary: Color,
        scrim: Color,
        error: Color,
        calendarCell: Color,
        calendarCellBorder: Color,
        skeletonBase: Color,
        skeletonHighlight: Color,
        editorBackground: Color,
        editorControl: Color,
        editorAccent: Color,
        textOnEditor: Color,
        textOnEditorMuted: Color
    ) {
        self.background = background
        self.surface = surface
        self.surfaceElevated = surfaceElevated
        self.inputBackground = inputBackground
        self.bottomBar = bottomBar
        self.textPrimary = textPrimary
        self.textSecondary = textSecondary
        self.textMuted = textMuted
        self.textInactive = textInactive
        self.textOnImage = textOnImage
        self.border = border
        self.inputCursor = inputCursor
        self.actionPrimary = actionPrimary
        self.onActionPrimary = onActionPrimary
        self.iconPrimary = iconPrimary
        self.iconSecondary = iconSecondary
        self.scrim = scrim
        self.error = error
        self.calendarCell = calendarCell
        self.calendarCellBorder = calendarCellBorder
        self.skeletonBase = skeletonBase
        self.skeletonHighlight = skeletonHighlight
        self.editorBackground = editorBackground
        self.editorControl = editorControl
        self.editorAccent = editorAccent
        self.textOnEditor = textOnEditor
        self.textOnEditorMuted = textOnEditorMuted
    }

    init() {
        self = .light
    }

    static let light = ChalkakColors(
        background: Color(hex: 0xF7F6F3),
        surface: Color(hex: 0xF7F6F3),
        surfaceElevated: .white,
        inputBackground: Color(hex: 0xFCFAF6),
        bottomBar: Color(hex: 0x8C8479),
        textPrimary: Color(hex: 0x2B2724),
        textSecondary: Color(hex: 0x2B2724).opacity(0.68),
        textMuted: Color(hex: 0x2B2724).opacity(0.48),
        textInactive: Color(hex: 0x9A968D),
        textOnImage: .white,
        border: Color(hex: 0x888888).opacity(0.15),
        inputCursor: Color(hex: 0xB0563B),
        actionPrimary: Color(hex: 0x2B2724),
        onActionPrimary: .white,
        iconPrimary: Color(hex: 0x2B2724),
        iconSecondary: Color(hex: 0x2B2724).opacity(0.62),
        scrim: Color.black.opacity(0.4),
        error: Color(hex: 0xBA1A1A),
        calendarCell: Color(hex: 0xEAE7DE),
        calendarCellBorder: Color(hex: 0xDCD8CE),
        skeletonBase: Color(hex: 0xEAE7DE),
        skeletonHighlight: Color(hex: 0xF7F6F3),
        // 사진 편집 화면의 임시 색. 디자인 시안이 나오면 확정한다.
        editorBackground: .black,
        editorControl: Color(hex: 0x1C1C1E),
        editorAccent: Color(hex: 0xFFD60A),
        textOnEditor: .white,
        textOnEditorMuted: Color.white.opacity(0.6)
    )
}

private struct ChalkakColorsKey: EnvironmentKey {
    static let defaultValue = ChalkakColors.light
}

extension EnvironmentValues {
    var chalkakColors: ChalkakColors {
        get { self[ChalkakColorsKey.self] }
        set { self[ChalkakColorsKey.self] = newValue }
    }
}

extension View {
    func chalkakColors(_ colors: ChalkakColors = .light) -> some View {
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
