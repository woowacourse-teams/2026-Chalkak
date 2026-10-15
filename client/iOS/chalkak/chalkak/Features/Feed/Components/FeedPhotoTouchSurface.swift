import SwiftUI
import UIKit.UIGestureRecognizerSubclass

struct FeedPhotoTouch {
    let id: ObjectIdentifier
    let location: CGPoint
}

struct FeedPhotoTouchSurface: UIViewRepresentable {
    let isEnabled: Bool
    let onTouchesChanged: ([FeedPhotoTouch], CGPoint) -> Void

    func makeUIView(context: Context) -> FeedPhotoTouchView {
        FeedPhotoTouchView()
    }

    func updateUIView(_ view: FeedPhotoTouchView, context: Context) {
        view.tracker.onTouchesChanged = onTouchesChanged
        if view.tracker.isEnabled != isEnabled { view.tracker.isEnabled = isEnabled }
    }
}

final class FeedPhotoTouchView: UIView {
    let tracker = FeedPhotoTouchRecognizer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = true
        backgroundColor = .clear
        addGestureRecognizer(tracker)
    }

    required init?(coder: NSCoder) {
        nil
    }
}

final class FeedPhotoTouchRecognizer: UIGestureRecognizer, UIGestureRecognizerDelegate {
    var onTouchesChanged: (([FeedPhotoTouch], CGPoint) -> Void)?
    private var activeTouches: [UITouch] = []
    private var origin: CGPoint = .zero

    override init(target: Any?, action: Selector?) {
        super.init(target: target, action: action)
        delegate = self
        cancelsTouchesInView = false
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        for touch in touches where !activeTouches.contains(where: { $0 === touch }) {
            activeTouches.append(touch)
        }
        if activeTouches.count >= 2 {
            if state == .possible {
                origin = view?.convert(.zero, to: nil) ?? .zero
                state = .began
            }
            reportTouches()
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        if state == .began || state == .changed {
            state = .changed
            reportTouches()
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesEnded(touches, with: event)
        finishTouches(touches)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesCancelled(touches, with: event)
        finishTouches(touches, cancelled: true)
    }

    override func reset() {
        super.reset()
        if !activeTouches.isEmpty { onTouchesChanged?([], origin) }
        activeTouches.removeAll()
        origin = .zero
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    private func finishTouches(_ touches: Set<UITouch>, cancelled: Bool = false) {
        activeTouches.removeAll { active in touches.contains(where: { $0 === active }) }
        if state == .began || state == .changed {
            reportTouches()
            if activeTouches.isEmpty {
                state = cancelled ? .cancelled : .ended
            }
        } else if activeTouches.isEmpty {
            state = .failed
        }
    }

    private func reportTouches() {
        onTouchesChanged?(
            activeTouches.map { FeedPhotoTouch(id: ObjectIdentifier($0), location: $0.location(in: nil)) },
            origin
        )
    }

}
