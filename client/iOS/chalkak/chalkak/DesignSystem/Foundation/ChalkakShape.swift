import SwiftUI

struct ChalkakShape {
    let small: CGFloat
    let button: CGFloat
    let input: CGFloat
    let photoCard: CGFloat
    let large: CGFloat
    let xlarge: CGFloat
    let sheet: CGFloat
    let pill: Capsule

    init(
        small: CGFloat,
        button: CGFloat,
        input: CGFloat,
        photoCard: CGFloat,
        large: CGFloat,
        xlarge: CGFloat,
        sheet: CGFloat,
        pill: Capsule
    ) {
        self.small = small
        self.button = button
        self.input = input
        self.photoCard = photoCard
        self.large = large
        self.xlarge = xlarge
        self.sheet = sheet
        self.pill = pill
    }

    init() {
        self = .standard
    }

    static let small: CGFloat = 7
    static let button: CGFloat = 12
    static let input: CGFloat = 14
    static let photoCard: CGFloat = 12
    static let large: CGFloat = 16
    static let xlarge: CGFloat = 18
    static let sheet: CGFloat = 28
    static let pill = Capsule()

    static let standard = ChalkakShape(
        small: small,
        button: button,
        input: input,
        photoCard: photoCard,
        large: large,
        xlarge: xlarge,
        sheet: sheet,
        pill: pill
    )
}
