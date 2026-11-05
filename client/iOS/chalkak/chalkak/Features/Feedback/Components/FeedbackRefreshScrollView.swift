import SwiftUI
import UIKit

/// Native refresh control keeps its inset until the asynchronous refresh finishes.
struct FeedbackRefreshScrollView<Content: View>: UIViewControllerRepresentable {
    let tintColor: UIColor
    let onRefresh: () async -> Void
    let onApproachBottom: () -> Void
    @ViewBuilder let content: () -> Content

    func makeUIViewController(context: Context) -> FeedbackRefreshScrollController<Content> {
        FeedbackRefreshScrollController(content: content(), tintColor: tintColor, onRefresh: onRefresh, onApproachBottom: onApproachBottom)
    }

    func updateUIViewController(_ controller: FeedbackRefreshScrollController<Content>, context: Context) {
        controller.update(content: content(), tintColor: tintColor, onRefresh: onRefresh, onApproachBottom: onApproachBottom)
    }

    static func dismantleUIViewController(_ controller: FeedbackRefreshScrollController<Content>, coordinator: ()) {
        controller.cancelRefresh()
    }
}

final class FeedbackRefreshScrollController<Content: View>: UIViewController, UIScrollViewDelegate {
    let scrollView = UIScrollView()
    let refreshControl = UIRefreshControl()
    private let hostingController: UIHostingController<Content>
    private var onRefresh: () async -> Void
    private var onApproachBottom: () -> Void
    private var refreshTask: Task<Void, Never>?

    init(content: Content, tintColor: UIColor, onRefresh: @escaping () async -> Void, onApproachBottom: @escaping () -> Void = {}) {
        hostingController = UIHostingController(rootView: content)
        self.onRefresh = onRefresh
        self.onApproachBottom = onApproachBottom
        super.init(nibName: nil, bundle: nil)
        refreshControl.tintColor = tintColor
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.backgroundColor = .clear
        scrollView.alwaysBounceVertical = true
        scrollView.contentInsetAdjustmentBehavior = .never
        scrollView.delegate = self
        scrollView.refreshControl = refreshControl
        refreshControl.addTarget(self, action: #selector(refreshRequested), for: .valueChanged)
        view.addSubview(scrollView)

        addChild(hostingController)
        let hostedView = hostingController.view!
        hostedView.translatesAutoresizingMaskIntoConstraints = false
        hostedView.backgroundColor = .clear
        hostingController.sizingOptions = .intrinsicContentSize
        scrollView.addSubview(hostedView)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            hostedView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            hostedView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            hostedView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            hostedView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            hostedView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),
        ])
        hostingController.didMove(toParent: self)
    }

    func update(content: Content, tintColor: UIColor, onRefresh: @escaping () async -> Void, onApproachBottom: @escaping () -> Void = {}) {
        hostingController.rootView = content
        refreshControl.tintColor = tintColor
        self.onRefresh = onRefresh
        self.onApproachBottom = onApproachBottom
        view.setNeedsLayout()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        requestNextPageIfVisible()
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        // A short list still needs top pull-to-refresh, but must not reveal empty
        // space below its rows when pushed upward. Keep the native refresh inset.
        let insets = scrollView.adjustedContentInset
        let visibleHeight = scrollView.bounds.height - insets.top - insets.bottom
        let topOffset = -insets.top
        if scrollView.contentSize.height <= visibleHeight, scrollView.contentOffset.y > topOffset {
            scrollView.contentOffset.y = topOffset
        }
        requestNextPageIfVisible()
    }

    private func requestNextPageIfVisible() {
        guard refreshTask == nil, scrollView.contentSize.height > 0 else { return }
        let visibleBottom = scrollView.contentOffset.y + scrollView.bounds.height - scrollView.adjustedContentInset.bottom
        if visibleBottom >= scrollView.contentSize.height {
            onApproachBottom()
        }
    }

    @objc func refreshRequested() {
        guard refreshTask == nil else { return }
        // UIRefreshControl owns its indicator's inset until endRefreshing.
        // Ending on release (rather than after the request) lets rows cover it. #964
        refreshTask = Task { [weak self, onRefresh] in
            await onRefresh()
            guard !Task.isCancelled else { return }
            self?.refreshControl.endRefreshing()
            self?.refreshTask = nil
        }
    }

    func cancelRefresh() {
        refreshTask?.cancel()
        refreshTask = nil
        refreshControl.endRefreshing()
    }
}
