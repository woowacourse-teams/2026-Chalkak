import SwiftUI
import WebKit

struct IssueWebScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @State private var loadState: LoadState = .loading
    @State private var reloadToken = UUID()

    let onBack: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            theme.colors.background
                .ignoresSafeArea()

            IssueWebView(loadState: $loadState)
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

            ChalkakNavigationButton(kind: .back, action: onBack)
                .background(theme.colors.background, in: Circle())
                .padding(.leading, theme.spacing.lg)
                .padding(.top, theme.spacing.sm)
                .accessibilityIdentifier("issue.back")
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

private enum LoadState {
    case loading
    case loaded
    case failed
}

private struct IssueWebView: UIViewRepresentable {
    @Binding var loadState: LoadState

    private static let pageURL = URL(string: "https://2026-chalkak-web.vercel.app/beta/beta.html")!

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView(frame: .zero)
        webView.navigationDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = false
        webView.load(URLRequest(url: Self.pageURL))
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
        private var didAllowInitialNavigation = false

        init(parent: IssueWebView) {
            self.parent = parent
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            // CSS, scripts and images are subresources; only the initial top-level page may navigate.
            guard navigationAction.targetFrame?.isMainFrame == true else {
                decisionHandler(navigationAction.targetFrame == nil ? .cancel : .allow)
                return
            }
            guard !didAllowInitialNavigation,
                  navigationAction.request.url == IssueWebView.pageURL else {
                decisionHandler(.cancel)
                return
            }
            didAllowInitialNavigation = true
            decisionHandler(.allow)
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
