import SwiftUI
import UIKit

/// 화면 왼쪽 가장자리 스와이프. DragGesture와 달리 시작하면 아래 버튼의 터치를 취소하고,
/// 끄는 동안 다른 손가락으로 무엇을 눌러도 끊기지 않는다.
struct FeedBackSwipeGesture: UIGestureRecognizerRepresentable {
    let canBegin: () -> Bool
    let onChanged: (CGSize) -> Void
    let onEnded: (_ translation: CGSize, _ velocity: CGSize) -> Void
    let onCancelled: () -> Void

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator {
        Coordinator()
    }

    func makeUIGestureRecognizer(context: Context) -> UIScreenEdgePanGestureRecognizer {
        let recognizer = UIScreenEdgePanGestureRecognizer()
        recognizer.edges = .left
        recognizer.maximumNumberOfTouches = 1
        recognizer.delegate = context.coordinator
        return recognizer
    }

    func updateUIGestureRecognizer(_ recognizer: UIScreenEdgePanGestureRecognizer, context: Context) {
        context.coordinator.canBegin = canBegin
    }

    func handleUIGestureRecognizerAction(_ recognizer: UIScreenEdgePanGestureRecognizer, context: Context) {
        // 화면이 손을 따라 움직이므로 창 기준으로 잰다.
        let translation = recognizer.translation(in: nil)
        let velocity = recognizer.velocity(in: nil)
        switch recognizer.state {
        case .began, .changed:
            onChanged(CGSize(width: translation.x, height: translation.y))
        case .ended:
            onEnded(
                CGSize(width: translation.x, height: translation.y),
                CGSize(width: velocity.x, height: velocity.y)
            )
        case .cancelled, .failed:
            onCancelled()
        default:
            break
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var canBegin: () -> Bool = { false }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            canBegin()
        }
    }
}
