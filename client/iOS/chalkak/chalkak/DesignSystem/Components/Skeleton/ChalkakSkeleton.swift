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

    /// 로딩 상태를 "지연 표시 + 최소 표시 시간" 규칙으로 가공해 스켈레톤을 덧입힌다.
    ///
    /// - 응답이 `showDelay` 이내로 오면 스켈레톤을 아예 표시하지 않는다.
    /// - 한 번 표시된 스켈레톤은 최소 `minVisibleDuration` 만큼 유지한 뒤 닫는다.
    ///
    /// 짧거나 캐시된 로딩에서 스켈레톤이 번쩍이는 깜빡임을 막는다.
    func loadingSkeleton(isLoading: Bool) -> some View {
        modifier(LoadingSkeletonModifier(isLoading: isLoading))
    }
}

private struct LoadingSkeletonModifier: ViewModifier {
    let isLoading: Bool
    @State private var isVisible = false
    @State private var shownAt: Date?

    func body(content: Content) -> some View {
        content
            .overlay {
                if isVisible {
                    ChalkakSkeleton()
                        .transition(.opacity)
                }
            }
            .task(id: isLoading) { await updateVisibility() }
    }

    private func updateVisibility() async {
        if isLoading {
            // 응답이 지연 시간 이내로 오면(도중 취소되면) 스켈레톤을 표시하지 않는다.
            guard await wait(for: Metrics.showDelay) else { return }
            withAnimation(.easeOut(duration: Metrics.fadeDuration)) { isVisible = true }
            shownAt = .now
        } else {
            // 이미 노출된 스켈레톤은 최소 표시 시간을 채운 뒤 닫는다.
            if isVisible, let shownAt {
                let remaining = Metrics.minVisibleDuration - Date.now.timeIntervalSince(shownAt)
                if remaining > 0, await wait(for: remaining) == false { return }
            }
            withAnimation(.easeOut(duration: Metrics.fadeDuration)) { isVisible = false }
            shownAt = nil
        }
    }

    /// 지정 시간만큼 대기한다. 도중에 취소되면 `false`를 반환한다.
    private func wait(for seconds: TimeInterval) async -> Bool {
        do {
            try await Task.sleep(for: .seconds(seconds))
            return true
        } catch {
            return false
        }
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
                        .linear(duration: Metrics.shimmerDuration).repeatForever(autoreverses: false),
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
    static let shimmerDuration: TimeInterval = 1.2

    /// 응답이 이 시간 이내로 오면 스켈레톤을 표시하지 않는다.
    static let showDelay: TimeInterval = 0.5
    /// 스켈레톤이 한 번 표시되면 최소 이 시간만큼 유지한다. (표시 시작 0.5s + 0.25s = 0.75s)
    static let minVisibleDuration: TimeInterval = 0.25
    static let fadeDuration: TimeInterval = 0.2
}

#Preview("Skeleton", traits: .sizeThatFitsLayout) {
    ChalkakSkeleton()
        .frame(width: 270, height: 360)
        .clipShape(RoundedRectangle(cornerRadius: ChalkakShapes.photoCard))
        .padding()
}
