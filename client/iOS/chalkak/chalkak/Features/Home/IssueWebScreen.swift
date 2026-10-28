import SwiftUI
import WebKit

struct IssueWebScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast
    @State private var loadState: LoadState = .loading
    @State private var reloadToken = UUID()

    let url: URL
    let onBack: () -> Void

    var body: some View {
        ZStack {
            IssueWebView(url: url, loadState: $loadState) {
                showToast("링크를 열 수 없어요")
            }
                .id(reloadToken)

            switch loadState {
            case .loading:
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .accessibilityLabel("페이지 불러오는 중")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .loaded:
                EmptyView()
            case .failed:
                VStack(spacing: theme.spacing.sm) {
                    Text("페이지를 불러오지 못했어요")
                        .font(theme.typography.callout)
                        .foregroundStyle(theme.colors.textMuted)
                    Button("다시 시도") {
                        loadState = .loading
                        reloadToken = UUID()
                    }
                    .font(theme.typography.callout)
                    .foregroundStyle(theme.colors.textPrimary)
                    .frame(minWidth: 44, minHeight: 44)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .overlay(alignment: .top) {
            HStack {
                ChalkakNavigationButton(kind: .back, action: onBack)
                    .accessibilityIdentifier("issue.back")
                Spacer()
            }
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.sm)
        }
        .background(theme.colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
    }
}

private enum LoadState {
    case loading
    case loaded
    case failed
}

private struct IssueWebView: UIViewRepresentable {
    let url: URL
    @Binding var loadState: LoadState
    let onExternalLinkFailure: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView(frame: .zero)
        webView.navigationDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = false
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.parent = self
    }

    static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        webView.stopLoading()
        webView.navigationDelegate = nil
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var parent: IssueWebView
        private var navigationPolicy: IssueWebNavigationPolicy

        init(parent: IssueWebView) {
            self.parent = parent
            navigationPolicy = IssueWebNavigationPolicy(initialURL: parent.url)
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            let action = navigationPolicy.action(
                for: navigationAction.request.url,
                currentURL: webView.url,
                navigationType: navigationAction.navigationType,
                targetsMainFrame: navigationAction.targetFrame?.isMainFrame
            )
            switch action {
            case .allow:
                decisionHandler(.allow)
            case .cancel:
                decisionHandler(.cancel)
            case .openExternally(let url):
                // Cancel before leaving the app so returning preserves the issue and its scroll position.
                decisionHandler(.cancel)
                UIApplication.shared.open(url) { [weak self] didOpen in
                    guard !didOpen else { return }
                    self?.parent.onExternalLinkFailure()
                }
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation?) {
            parent.loadState = .loaded
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation?, withError error: Error) {
            handle(error)
        }

        func webView(
            _ webView: WKWebView,
            didFailProvisionalNavigation navigation: WKNavigation?,
            withError error: Error
        ) {
            handle(error)
        }

        private func handle(_ error: Error) {
            guard (error as NSError).code != NSURLErrorCancelled else { return }
            parent.loadState = .failed
        }
    }
}

struct IssueWebNavigationPolicy {
    enum Action: Equatable {
        case allow
        case cancel
        case openExternally(URL)
    }

    let initialURL: URL
    private var didAllowInitialNavigation = false

    init(initialURL: URL) {
        self.initialURL = initialURL
    }

    mutating func action(
        for url: URL?,
        currentURL: URL?,
        navigationType: WKNavigationType,
        targetsMainFrame: Bool?
    ) -> Action {
        guard let url,
              let scheme = url.scheme?.lowercased(),
              ["http", "https"].contains(scheme),
              let host = url.host, !host.isEmpty else { return .cancel }

        // Keep embedded frames inside the issue; they must not launch other apps.
        if targetsMainFrame == false { return .allow }

        if !didAllowInitialNavigation,
           targetsMainFrame == true,
           navigationType == .other,
           url == initialURL {
            didAllowInitialNavigation = true
            return .allow
        }

        guard navigationType == .linkActivated else { return .cancel }

        // A nil target is a new-window link (for example, target="_blank").
        if targetsMainFrame == true,
           let currentURL,
           (url.fragment != nil || currentURL.fragment != nil),
           Self.withoutFragment(url) == Self.withoutFragment(currentURL) {
            return .allow
        }
        return .openExternally(url)
    }

    private static func withoutFragment(_ url: URL) -> URL? {
        var components = URLComponents(url: url, resolvingAgainstBaseURL: true)
        components?.fragment = nil
        return components?.url
    }
}
