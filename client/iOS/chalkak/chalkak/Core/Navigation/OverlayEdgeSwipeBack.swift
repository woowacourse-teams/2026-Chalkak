import SwiftUI

extension View {
    /// overlay로 띄운 화면을 push 화면처럼 왼쪽 가장자리에서 오른쪽으로 밀어 닫게 한다.
    /// NavigationStack 밖에 떠 있어 시스템 스와이프 백(`InteractivePopGestureEnabler`)을 쓸 수 없는 화면에 쓴다.
    /// 화면이 오른쪽 끝까지 밀려난 뒤 `onBack`을 애니메이션 없이 부르므로, 호출부는 상태만 지우면 된다.
    /// 예) `.overlayEdgeSwipeBack { selectedAuthorPosts = nil }`
    func overlayEdgeSwipeBack(onBack: @escaping () -> Void) -> some View {
        modifier(OverlayEdgeSwipeBack(onBack: onBack))
    }
}

private struct OverlayEdgeSwipeBack: ViewModifier {
    let onBack: () -> Void
    @State private var dragOffset: CGFloat = 0
    @State private var isDragging = false
    @State private var isClosing = false
    @State private var width: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .offset(x: dragOffset)
            .shadow(color: .black.opacity(isDragging || isClosing ? Metrics.shadowOpacity : 0), radius: Metrics.shadowRadius)
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
            .simultaneousGesture(swipeGesture)
    }

    private var swipeGesture: some Gesture {
        DragGesture(minimumDistance: Metrics.minimumDistance, coordinateSpace: .global)
            .onChanged { value in
                guard !isClosing else { return }
                if !isDragging {
                    // 그리드 세로 스크롤과 겹치지 않게, 가장자리에서 시작한 가로 드래그만 받는다.
                    guard value.startLocation.x <= Metrics.edgeWidth,
                          value.translation.width > abs(value.translation.height) else { return }
                    isDragging = true
                }
                dragOffset = max(0, value.translation.width)
            }
            .onEnded { value in
                guard isDragging else { return }
                isDragging = false
                let distance = max(value.translation.width, value.predictedEndTranslation.width)
                if width > 0, distance >= width * Metrics.closeRatio {
                    isClosing = true
                    withAnimation(Metrics.animation) {
                        dragOffset = width
                    } completion: {
                        var transaction = Transaction()
                        transaction.disablesAnimations = true
                        withTransaction(transaction) { onBack() }
                    }
                } else {
                    withAnimation(Metrics.animation) { dragOffset = 0 }
                }
            }
    }
}

private enum Metrics {
    // 시스템 스와이프 백과 비슷한 가장자리 폭.
    static let edgeWidth: CGFloat = 24
    static let minimumDistance: CGFloat = 10
    // 화면 폭의 이만큼 이상 밀거나 그 이상 튕기면 닫는다.
    static let closeRatio: CGFloat = 0.35
    static let animation = Animation.smooth(duration: 0.25)
    static let shadowOpacity: CGFloat = 0.12
    static let shadowRadius: CGFloat = 8
}
