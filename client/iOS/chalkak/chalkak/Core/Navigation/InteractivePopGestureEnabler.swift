import SwiftUI
import UIKit

struct InteractivePopGestureEnabler: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        InteractivePopGestureViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        guard let viewController = uiViewController as? InteractivePopGestureViewController else {
            return
        }

        viewController.enableInteractivePopGesture()
    }
}

private final class InteractivePopGestureViewController: UIViewController {
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        enableInteractivePopGesture()
    }

    func enableInteractivePopGesture() {
        DispatchQueue.main.async { [weak self] in
            guard
                let navigationController = self?.navigationController,
                navigationController.viewControllers.count > 1,
                let gestureRecognizer = navigationController.interactivePopGestureRecognizer
            else {
                return
            }

            gestureRecognizer.delegate = nil
            gestureRecognizer.isEnabled = true
        }
    }
}
