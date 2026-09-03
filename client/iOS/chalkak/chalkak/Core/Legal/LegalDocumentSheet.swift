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
                Button(action: dismiss.callAsFunction) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(theme.colors.textSecondary)
                        .frame(width: 48, height: 48)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("닫기")
            }
            .padding(.trailing, theme.spacing.sm)

            Rectangle()
                .fill(theme.colors.border)
                .frame(height: 1)

            ZStack {
                LegalDocumentWebView(
                    document: document,
                    reloadToken: reloadToken,
                    loadState: $loadState,
                    onOpenExternalURL: { openURL($0) }
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
                Button("다시 시도") {
                    loadState = .loading
                    reloadToken = UUID()
                }
                .font(theme.typography.callout)
                .foregroundStyle(theme.colors.textPrimary)
            }
        }
    }
}

