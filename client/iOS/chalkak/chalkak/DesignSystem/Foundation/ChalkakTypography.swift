import SwiftUI

enum ChalkakTypography {
    static let display = pretendard(.bold, size: 32, relativeTo: .largeTitle)
    static let title1 = pretendard(.bold, size: 28, relativeTo: .title)
    static let title2 = pretendard(.bold, size: 24, relativeTo: .title2)
    static let title3 = pretendard(.semiBold, size: 20, relativeTo: .title3)
    static let headline = pretendard(.semiBold, size: 18, relativeTo: .headline)
    static let body = pretendard(.regular, size: 17, relativeTo: .body)
    static let callout = pretendard(.regular, size: 16, relativeTo: .callout)
    static let subheadline = pretendard(.regular, size: 14, relativeTo: .subheadline)
    static let footnote = pretendard(.regular, size: 13, relativeTo: .footnote)
    static let caption = pretendard(.regular, size: 12, relativeTo: .caption)
    static let brand = Font.custom("Continuous", size: 30, relativeTo: .title)
    static let handwriting = Font.custom(
        "GriunXHangeulBanguri-Regular",
        size: 20,
        relativeTo: .title3
    )

    private static func pretendard(
        _ weight: PretendardWeight,
        size: CGFloat,
        relativeTo textStyle: Font.TextStyle
    ) -> Font {
        Font.custom(weight.postScriptName, size: size, relativeTo: textStyle)
    }
}

private enum PretendardWeight {
    case regular
    case semiBold
    case bold

    var postScriptName: String {
        switch self {
        case .regular:
            "Pretendard-Regular"
        case .semiBold:
            "Pretendard-SemiBold"
        case .bold:
            "Pretendard-Bold"
        }
    }
}
