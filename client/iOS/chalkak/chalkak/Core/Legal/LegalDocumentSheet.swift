import SwiftUI

enum LegalDocumentLoadState {
    case loading
    case loaded
    case failed
}

struct LegalDocumentSheet: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var loadState = LegalDocumentLoadState.loading
    @State private var reloadToken = UUID()

    let document: LegalDocument

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                ChalkakNavigationButton(
                    kind: .close,
                    controlSize: .regular,
                    action: dismiss.callAsFunction
                )
            }
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)

            Rectangle()
                .fill(theme.colors.border)
                .frame(height: Metrics.dividerHeight)

            ZStack {
                LegalDocumentWebView(
                    document: document,
                    reloadToken: reloadToken,
                    loadState: $loadState,
                    onOpenExternalURL: { url in openURL(url) }
                )

                switch loadState {
                case .loading:
                    loadingView
                case .loaded:
                    EmptyView()
                case .failed:
                    errorView
                }
            }
        }
        .background(theme.colors.surfaceElevated)
        .accessibilityIdentifier("legalDocumentSheet.\(document.rawValue)")
    }

    private var loadingView: some View {
        ZStack {
            theme.colors.surfaceElevated
            ProgressView()
                .tint(theme.colors.actionPrimary)
                .accessibilityLabel("문서 불러오는 중")
        }
    }

    private var errorView: some View {
        ZStack {
            theme.colors.surfaceElevated
            VStack(spacing: theme.spacing.sm) {
                Text("문서를 불러오지 못했어요")
                    .font(theme.typography.callout)
                    .foregroundStyle(theme.colors.textMuted)
                    .multilineTextAlignment(.center)
                Button("다시 시도") {
                    loadState = .loading
                    reloadToken = UUID()
                }
                .font(theme.typography.callout)
                .foregroundStyle(theme.colors.textPrimary)
            }
            .padding(theme.spacing.xl)
        }
    }
}

private enum Metrics {
    static let dividerHeight: CGFloat = 1
}
