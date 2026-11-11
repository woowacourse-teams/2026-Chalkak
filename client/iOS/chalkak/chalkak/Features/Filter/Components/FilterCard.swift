import SwiftUI

/// 필터 탭 2열 목록의 카드 한 장. 위에서부터 정사각형 대표 사진, 이름, 설명(한 줄)이다.
/// 카드 폭은 호출부(목록 칸)가 정하고, 누르면 `onClick`으로 알린다(시트 열기는 호출부가 한다).
struct FilterCard: View {
    @Environment(\.chalkakTheme) private var theme

    let filter: FilterItem
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 0) {
                FilterPhoto(source: filter.coverImage, aspectRatio: Metrics.coverAspectRatio)

                Text(filter.name)
                    .font(theme.typography.callout)
                    .foregroundStyle(theme.colors.textPrimary)
                    .lineLimit(1)
                    .padding(.top, theme.spacing.sm)

                Text(filter.description)
                    .font(theme.typography.caption)
                    .foregroundStyle(theme.colors.textSecondary)
                    .lineLimit(1)
                    .padding(.top, theme.spacing.xs)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }
}

private enum Metrics {
    // 시안의 목록 카드 사진은 정사각형이다.
    static let coverAspectRatio: CGFloat = 1
}

#Preview("Filter Card", traits: .sizeThatFitsLayout) {
    FilterCard(filter: FilterItem.samples[0], onClick: {})
        .frame(width: 170)
        .padding()
        .chalkakTheme(.light)
}
