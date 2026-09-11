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

    static func dismantleUIViewController(
        _ uiViewController: UIViewController,
        coordinator: ()
    ) {
        (uiViewController as? InteractivePopGestureViewController)?
            .restoreInteractivePopGesture()
    }
}

private final class InteractivePopGestureViewController: UIViewController {
    private let interactivePopDelegate = InteractivePopGestureDelegate()
    private weak var installedGestureRecognizer: UIGestureRecognizer?
    private var originalDelegate: UIGestureRecognizerDelegate?
    private var originalIsEnabled = false

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        enableInteractivePopGesture()
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        restoreInteractivePopGesture()
    }

    func enableInteractivePopGesture() {
        DispatchQueue.main.async { [weak self] in
            guard
                let self,
                let navigationController = navigationController,
                navigationController.viewControllers.count > 1,
                let gestureRecognizer = navigationController.interactivePopGestureRecognizer
            else {
                return
            }

            guard self.installedGestureRecognizer !== gestureRecognizer else {
                gestureRecognizer.isEnabled = true
                return
            }

            self.originalDelegate = gestureRecognizer.delegate
            self.originalIsEnabled = gestureRecognizer.isEnabled
            self.installedGestureRecognizer = gestureRecognizer
            self.interactivePopDelegate.navigationController = navigationController
            gestureRecognizer.delegate = self.interactivePopDelegate
            gestureRecognizer.isEnabled = true
        }
    }

    func restoreInteractivePopGesture() {
        guard let gestureRecognizer = installedGestureRecognizer else { return }

        if gestureRecognizer.delegate === interactivePopDelegate {
            gestureRecognizer.delegate = originalDelegate
            gestureRecognizer.isEnabled = originalIsEnabled
        }

        installedGestureRecognizer = nil
        originalDelegate = nil
        interactivePopDelegate.navigationController = nil
    }
}

private final class InteractivePopGestureDelegate: NSObject, UIGestureRecognizerDelegate {
    weak var navigationController: UINavigationController?

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let navigationController else { return false }

        return navigationController.viewControllers.count > 1
            && navigationController.transitionCoordinator == nil
    }
}
