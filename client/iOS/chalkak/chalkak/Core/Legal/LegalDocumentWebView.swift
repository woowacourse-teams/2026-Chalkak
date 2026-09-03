import Foundation
import SwiftUI
import WebKit

struct LegalDocumentWebView: UIViewRepresentable {
    let document: LegalDocument
    let reloadToken: UUID
    @Binding var loadState: LegalDocumentLoadState
    let onOpenExternalURL: (URL) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.allowsBackForwardNavigationGestures = true
        context.coordinator.lastReloadToken = reloadToken
        webView.load(URLRequest(url: document.url))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.parent = self
        guard context.coordinator.lastReloadToken != reloadToken else { return }

        context.coordinator.lastReloadToken = reloadToken
        webView.load(URLRequest(url: document.url))
    }

    static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        webView.stopLoading()
        webView.navigationDelegate = nil
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var parent: LegalDocumentWebView
        var lastReloadToken: UUID?

        init(parent: LegalDocumentWebView) {
            self.parent = parent
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }

            guard LegalDocumentNavigationPolicy.allows(url) else {
                parent.onOpenExternalURL(url)
                decisionHandler(.cancel)
                return
            }

            decisionHandler(.allow)
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation?) {
            parent.loadState = .loading
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation?) {
            parent.loadState = .loaded
            webView.evaluateJavaScript(Self.notionLayoutFixScript)
        }

        func webView(
            _ webView: WKWebView,
            didFail navigation: WKNavigation?,
            withError error: Error
        ) {
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

        private static let notionLayoutFixScript = """
            (() => {
                let attempts = 0;

                const fixLayout = () => {
                    attempts += 1;

                    const main = document.getElementById('main');
                    const scroller = main?.querySelector('.notion-scroller');
                    if (!main || !scroller) {
                        if (attempts < 50) setTimeout(fixLayout, 100);
                        return;
                    }

                    const viewportHeight = window.innerHeight;
                    main.style.height = viewportHeight + 'px';
                    main.style.minHeight = viewportHeight + 'px';
                    main.style.maxHeight = 'none';
                    main.style.flexShrink = '0';

                    const mainRect = main.getBoundingClientRect();
                    const scrollerRect = scroller.getBoundingClientRect();
                    const topInset = Math.max(scrollerRect.top - mainRect.top, 0);
                    scroller.style.height = Math.max(viewportHeight - topInset, 0) + 'px';
                    scroller.style.minHeight = '0px';
                    scroller.style.maxHeight = 'none';
                    scroller.style.flex = '1 1 auto';

                    if (attempts < 50) setTimeout(fixLayout, 100);
                };

                fixLayout();
            })();
            """
    }
}
