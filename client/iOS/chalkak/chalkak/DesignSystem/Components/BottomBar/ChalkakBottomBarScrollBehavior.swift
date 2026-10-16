import SwiftUI

extension View {
    func chalkakBottomBarScrollBehavior(isCompact: Binding<Bool>) -> some View {
        modifier(ChalkakBottomBarScrollBehavior(isCompact: isCompact))
    }
}

private struct ChalkakBottomBarScrollBehavior: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Binding var isCompact: Bool
    @State private var isUserScrolling = false
    @State private var directionTravel: CGFloat = 0

    private let directionThreshold: CGFloat = 16

    func body(content: Content) -> some View {
        content
            .onChange(of: isCompact) { wasCompact, compact in
                if wasCompact && !compact {
                    // 탭으로 펼친 뒤 남은 스크롤 관성이 바를 다시 접지 않게 한다.
                    isUserScrolling = false
                    directionTravel = 0
                }
            }
            .onScrollPhaseChange { _, phase in
                // 손을 뗀 뒤의 관성 이동과 바운스는 방향 전환으로 처리하지 않는다.
                isUserScrolling = phase == .interacting
                directionTravel = 0
            }
            .onScrollGeometryChange(for: ScrollOffset.self) { geometry in
                let offset = geometry.contentOffset.y + geometry.contentInsets.top
                let maximumOffset = max(
                    0,
                    geometry.contentSize.height + geometry.contentInsets.top
                        + geometry.contentInsets.bottom - geometry.containerSize.height
                )
                return ScrollOffset(offset: offset, maximumOffset: maximumOffset)
            } action: { oldOffset, newOffset in
                if newOffset.offset <= 0 {
                    directionTravel = 0
                    setCompact(false)
                    return
                }
                guard isUserScrolling else { return }

                // 끝을 넘겨 당기거나 되돌아오는 구간과 콘텐츠 높이 변화는 제외한다.
                guard oldOffset.maximumOffset == newOffset.maximumOffset,
                      oldOffset.offset >= 0,
                      oldOffset.offset < oldOffset.maximumOffset,
                      newOffset.offset < newOffset.maximumOffset else {
                    directionTravel = 0
                    return
                }

                let delta = newOffset.offset - oldOffset.offset
                if delta * directionTravel < 0 {
                    directionTravel = delta
                } else {
                    directionTravel += delta
                }
                guard abs(directionTravel) >= directionThreshold else { return }
                setCompact(directionTravel > 0)
                directionTravel = 0
            }
    }

    private struct ScrollOffset: Equatable {
        let offset: CGFloat
        let maximumOffset: CGFloat
    }

    private func setCompact(_ compact: Bool) {
        guard compact != isCompact else { return }
        withAnimation(reduceMotion ? nil : .smooth(duration: 0.25)) {
            isCompact = compact
        }
    }
}
