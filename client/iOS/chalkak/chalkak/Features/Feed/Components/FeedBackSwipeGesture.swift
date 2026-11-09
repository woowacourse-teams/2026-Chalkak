import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

/// 화면 왼쪽 가장자리 스와이프. DragGesture와 달리 시작하면 아래 버튼의 터치를 취소하고,
/// 끄는 동안 다른 손가락으로 무엇을 눌러도 끊기지 않는다.
struct FeedBackSwipeGesture: UIGestureRecognizerRepresentable {
    // 시스템 가장자리 스와이프는 폭이 좁아 놓치기 쉬워서 최소 터치 크기만큼 넓힌다.
    static let edgeWidth: CGFloat = 44

    let canBegin: () -> Bool
    let onChanged: (CGSize) -> Void
    let onEnded: (_ translation: CGSize, _ velocity: CGSize) -> Void
    let onCancelled: () -> Void

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator {
        Coordinator()
    }

    func makeUIGestureRecognizer(context: Context) -> LeftEdgePanGestureRecognizer {
        let recognizer = LeftEdgePanGestureRecognizer()
        recognizer.edgeWidth = Self.edgeWidth
        recognizer.maximumNumberOfTouches = 1
        recognizer.delegate = context.coordinator
        return recognizer
    }

    func updateUIGestureRecognizer(_ recognizer: LeftEdgePanGestureRecognizer, context: Context) {
        context.coordinator.canBegin = canBegin
    }

    func handleUIGestureRecognizerAction(_ recognizer: LeftEdgePanGestureRecognizer, context: Context) {
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
            guard let pan = gestureRecognizer as? UIPanGestureRecognizer else { return false }
            // 오른쪽으로 미는 스와이프만 받는다. 위아래로 밀면 스크롤에 넘긴다.
            let velocity = pan.velocity(in: nil)
            return velocity.x > abs(velocity.y) && canBegin()
        }

        // 가장자리에서 시작한 터치는 스크롤보다 백 스와이프가 먼저 판단한다.
        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            otherGestureRecognizer.view is UIScrollView
        }
    }
}

/// 창 왼쪽에서 `edgeWidth` 안에 닿은 터치만 받는 팬.
final class LeftEdgePanGestureRecognizer: UIPanGestureRecognizer {
    var edgeWidth: CGFloat = 0

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        guard state == .possible, let touch = touches.first else { return }
        // 가장자리 밖 터치는 바로 놓아, 스크롤이 이 제스처를 기다리지 않게 한다.
        if touch.location(in: nil).x > edgeWidth {
            state = .failed
        }
    }
}
