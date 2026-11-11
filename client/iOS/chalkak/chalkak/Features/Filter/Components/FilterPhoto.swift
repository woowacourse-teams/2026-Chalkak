import SwiftUI

/// 필터 화면의 사진 한 장. 정한 비율(폭 ÷ 높이)의 칸을 사진으로 채우고 모서리를 둥글게 자른다.
/// 장식용이라 접근성 트리에서 숨긴다(이름·설명 글이 내용을 전한다).
struct FilterPhoto: View {
    @Environment(\.chalkakTheme) private var theme

    let source: ChalkakImageSource
    /// 폭 ÷ 높이. 예: 1이면 정사각형, 2.2면 가로로 긴 사진.
    let aspectRatio: CGFloat

    var body: some View {
        Color.clear
            .aspectRatio(aspectRatio, contentMode: .fit)
            .overlay { ChalkakImage(source: source) }
            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.photoCard))
            .accessibilityHidden(true)
    }
}
