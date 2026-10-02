import SwiftUI

/// Feed로 줌 전환할 때 출발 뷰를 구분하는 식별자.
/// 같은 게시물이 전시의 추천 캐러셀과 그리드에 함께 보일 수 있어 위치별로 나눈다.
enum FeedZoomSource: Hashable, Sendable {
    case displayFeatured(FeedPost.ID)
    case displayGrid(FeedPost.ID)
    case record(FeedPost.ID)
}

extension EnvironmentValues {
    /// Feed 줌 전환의 출발 뷰와 도착 화면을 잇는 네임스페이스. 없으면 줌 전환을 쓰지 않는다.
    @Entry var feedZoomNamespace: Namespace.ID?
}

extension View {
    /// 이 뷰를 Feed 줌 전환의 출발 뷰로 등록한다.
    func feedZoomSource(_ source: FeedZoomSource, cornerRadius: CGFloat = 0) -> some View {
        modifier(FeedZoomSourceModifier(source: source, cornerRadius: cornerRadius))
    }

    /// 출발 뷰가 있으면 그 뷰에서 확대되는 줌 전환으로 화면을 연다.
    @ViewBuilder
    func feedZoomTransition(from source: FeedZoomSource?, in namespace: Namespace.ID) -> some View {
        if let source {
            navigationTransition(.zoom(sourceID: source, in: namespace))
        } else {
            self
        }
    }
}

private struct FeedZoomSourceModifier: ViewModifier {
    @Environment(\.feedZoomNamespace) private var namespace
    let source: FeedZoomSource
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        if let namespace {
            content.matchedTransitionSource(id: source, in: namespace) { configuration in
                configuration.clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            }
        } else {
            content
        }
    }
}
