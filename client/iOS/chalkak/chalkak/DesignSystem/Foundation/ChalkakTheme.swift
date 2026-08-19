import SwiftUI

struct ChalkakTheme {
    let colors: ChalkakColors
    let typography: ChalkakTypography
    let spacing: ChalkakSpacing
    let shapes: ChalkakShape

    static let light = ChalkakTheme(
        colors: .light,
        typography: .standard,
        spacing: .standard,
        shapes: .standard
    )
}

private struct ChalkakThemeKey: EnvironmentKey {
    static let defaultValue = ChalkakTheme.light
}

private struct ChalkakTypographyKey: EnvironmentKey {
    static let defaultValue = ChalkakTypography.standard
}

private struct ChalkakSpacingKey: EnvironmentKey {
    static let defaultValue = ChalkakSpacing.standard
}

private struct ChalkakShapesKey: EnvironmentKey {
    static let defaultValue = ChalkakShape.standard
}

extension EnvironmentValues {
    var chalkakTheme: ChalkakTheme {
        get { self[ChalkakThemeKey.self] }
        set { self[ChalkakThemeKey.self] = newValue }
    }

    var chalkakTypography: ChalkakTypography {
        get { self[ChalkakTypographyKey.self] }
        set { self[ChalkakTypographyKey.self] = newValue }
    }

    var chalkakSpacing: ChalkakSpacing {
        get { self[ChalkakSpacingKey.self] }
        set { self[ChalkakSpacingKey.self] = newValue }
    }

    var chalkakShapes: ChalkakShape {
        get { self[ChalkakShapesKey.self] }
        set { self[ChalkakShapesKey.self] = newValue }
    }
}

extension View {
    func chalkakTheme(_ theme: ChalkakTheme = .light) -> some View {
        environment(\.chalkakTheme, theme)
            .environment(\.chalkakColors, theme.colors)
            .environment(\.chalkakTypography, theme.typography)
            .environment(\.chalkakSpacing, theme.spacing)
            .environment(\.chalkakShapes, theme.shapes)
    }
}
