import SwiftUI

enum ChalkakImageSource: Equatable, Sendable {
    case asset(String)
    case system(String)
    case remote(URL?)
}

struct ChalkakImage: View {
    @Environment(\.chalkakTheme) private var theme
    let source: ChalkakImageSource
    var contentDescription: String?
    var contentMode: ContentMode = .fill

    var body: some View {
        image
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(contentDescription ?? "")
            .accessibilityHidden(contentDescription == nil)
    }

    @ViewBuilder
    private var image: some View {
        switch source {
        case let .asset(name):
            Image(name)
                .resizable()
                .aspectRatio(contentMode: contentMode)
        case let .system(name):
            Image(systemName: name)
                .resizable()
                .aspectRatio(contentMode: contentMode)
                .foregroundStyle(theme.colors.iconSecondary)
        case let .remote(url):
            RemoteImage(url: url, contentMode: contentMode)
        }
    }
}

/// 원격 이미지를 로드하며, 로딩 지연·최소 표시 규칙에 따라 스켈레톤을 노출한다.
private struct RemoteImage: View {
    @Environment(\.chalkakTheme) private var theme
    let url: URL?
    let contentMode: ContentMode
    @State private var isLoading = true

    var body: some View {
        AsyncImage(url: url) { phase in
            phaseContent(phase)
        }
        .loadingSkeleton(isLoading: isLoading)
    }

    @ViewBuilder
    private func phaseContent(_ phase: AsyncImagePhase) -> some View {
        switch phase {
        case let .success(image):
            image
                .resizable()
                .aspectRatio(contentMode: contentMode)
                .onAppear { isLoading = false }
        case .failure:
            imagePlaceholder(systemName: "photo.badge.exclamationmark")
                .onAppear { isLoading = false }
        case .empty:
            Color.clear
                .onAppear { isLoading = true }
        @unknown default:
            Color.clear
                .onAppear { isLoading = true }
        }
    }

    private func imagePlaceholder(systemName: String) -> some View {
        ZStack {
            theme.colors.inputBackground
            Image(systemName: systemName)
                .font(.system(size: Metrics.placeholderIconSize))
                .foregroundStyle(theme.colors.iconSecondary)
        }
    }
}

private enum Metrics {
    static let placeholderIconSize: CGFloat = 24
}

#Preview("Image", traits: .sizeThatFitsLayout) {
    ChalkakImage(
        source: .asset("preview_photo"),
        contentDescription: "전시 사진",
        contentMode: .fit
    )
    .frame(width: 270, height: 360)
    .background(ChalkakTheme.light.colors.inputBackground)
}
