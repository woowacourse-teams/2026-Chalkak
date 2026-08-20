import SwiftUI

struct ChalkakTypography {
    let display: Font
    let title1: Font
    let title2: Font
    let title3: Font
    let headline: Font
    let body: Font
    let callout: Font
    let subheadline: Font
    let footnote: Font
    let caption: Font
    let brand: Font
    let handwriting: Font

    init(
        display: Font,
        title1: Font,
        title2: Font,
        title3: Font,
        headline: Font,
        body: Font,
        callout: Font,
        subheadline: Font,
        footnote: Font,
        caption: Font,
        brand: Font,
        handwriting: Font
    ) {
        self.display = display
        self.title1 = title1
        self.title2 = title2
        self.title3 = title3
        self.headline = headline
        self.body = body
        self.callout = callout
        self.subheadline = subheadline
        self.footnote = footnote
        self.caption = caption
        self.brand = brand
        self.handwriting = handwriting
    }

    init() {
        self = .standard
    }

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
    // Android는 자간 -0.42을 적용한다. SwiftUI `Font`는 자간을 담지 못하므로
    // 이 토큰을 쓰는 곳에서 `.tracking(-0.42)`로 보정해 디자인을 일치시킨다.
    static let brand = Font.custom("Continuous", size: 30, relativeTo: .title)
    // Android는 라인 높이를 17(폰트 크기 20보다 압축)로 적용한다. SwiftUI `Font`는
    // 라인 높이를 담지 못하므로 이 토큰을 쓰는 곳에서 `.lineSpacing`(또는 커스텀
    // 라인 높이 modifier)으로 보정해 디자인을 일치시킨다.
    static let handwriting = Font.custom(
        "GriunXHangeulBanguri-Regular",
        size: 20,
        relativeTo: .title3
    )

    static let standard = ChalkakTypography(
        display: display,
        title1: title1,
        title2: title2,
        title3: title3,
        headline: headline,
        body: body,
        callout: callout,
        subheadline: subheadline,
        footnote: footnote,
        caption: caption,
        brand: brand,
        handwriting: handwriting
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
