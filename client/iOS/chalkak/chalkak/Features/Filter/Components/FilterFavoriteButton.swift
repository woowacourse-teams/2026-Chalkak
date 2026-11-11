import SwiftUI

/// 시트 대표 사진 오른쪽 위에 얹는 즐겨찾기 별. 반투명 흰 원 위에 별을 그린다(안드 `FilterFavoriteButton`과 같은 모양).
/// 켜짐 여부는 `isFavorite`으로 받기만 하고, 누르면 `onToggle`로 알린다.
/// 켜져 있으면 접근성 값 "선택됨"과 선택 상태(`.isSelected`)를 노출한다.
struct FilterFavoriteButton: View {
    @Environment(\.chalkakTheme) private var theme

    let isFavorite: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            Image(systemName: isFavorite ? "star.fill" : "star")
                .font(.system(size: Metrics.iconSize, weight: .regular))
                .foregroundStyle(theme.colors.iconPrimary)
                .frame(width: Metrics.circleSize, height: Metrics.circleSize)
                .background(
                    theme.colors.surface.opacity(Metrics.circleOpacity),
                    in: Circle()
                )
                .frame(width: Metrics.touchSize, height: Metrics.touchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("즐겨찾기")
        .accessibilityValue(isFavorite ? "선택됨" : "")
        .accessibilityAddTraits(isFavorite ? .isSelected : [])
        .accessibilityIdentifier("filter.favorite")
    }
}

private enum Metrics {
    static let touchSize: CGFloat = 44
    // 시안의 별 바탕 원 지름과 별 크기.
    static let circleSize: CGFloat = 40
    static let iconSize: CGFloat = 20
    // 사진이 비치도록 원 바탕을 조금 투명하게 둔다.
    static let circleOpacity = 0.8
}

#Preview("Filter Favorite Button", traits: .sizeThatFitsLayout) {
    HStack {
        FilterFavoriteButton(isFavorite: false, onToggle: {})
        FilterFavoriteButton(isFavorite: true, onToggle: {})
    }
    .padding()
    .background(Color.gray)
    .chalkakTheme(.light)
}
