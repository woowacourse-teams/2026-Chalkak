import SwiftUI

/// 콘텐츠 로딩 중 자리를 채우는 스켈레톤 뷰.
///
/// 베이스 색 위로 밝은 하이라이트 밴드가 좌 -> 우로 지나가는 shimmer 애니메이션을 가진다.
/// 부모가 지정한 프레임을 가득 채우며, 모서리 클리핑은 호출부가 담당한다.
struct ChalkakSkeleton: View {
    @Environment(\.chalkakColors) private var colors

    var body: some View {
        colors.skeletonBase
            .shimmer(highlight: colors.skeletonHighlight)
            .accessibilityHidden(true)
    }
}

extension View {
    /// 이 뷰 위에 좌 -> 우로 흐르는 shimmer 하이라이트를 덧입힌다.
    func shimmer(highlight: Color) -> some View {
        modifier(ShimmerModifier(highlight: highlight))
    }
}

private struct ShimmerModifier: ViewModifier {
    let highlight: Color
    @State private var isAnimating = false

    func body(content: Content) -> some View {
        content
            .overlay {
                GeometryReader { proxy in
                    let width = proxy.size.width
                    let bandWidth = width * Metrics.bandWidthRatio
                    LinearGradient(
                        colors: [.clear, highlight, .clear],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: bandWidth, height: proxy.size.height)
                    .offset(x: isAnimating ? width : -bandWidth)
                    .animation(
                        .linear(duration: Metrics.duration).repeatForever(autoreverses: false),
                        value: isAnimating
                    )
                }
            }
            .clipped()
            .onAppear { isAnimating = true }
    }
}

private enum Metrics {
    static let bandWidthRatio: CGFloat = 0.6
    static let duration: TimeInterval = 1.2
}

#Preview("Skeleton", traits: .sizeThatFitsLayout) {
    ChalkakSkeleton()
        .frame(width: 270, height: 360)
        .clipShape(RoundedRectangle(cornerRadius: ChalkakShapes.photoCard))
        .padding()
}
