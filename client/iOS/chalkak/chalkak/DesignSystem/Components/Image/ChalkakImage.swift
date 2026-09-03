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
            AsyncImage(url: url) { phase in
                switch phase {
                case let .success(image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: contentMode)
                case .failure:
                    imagePlaceholder(systemName: "photo.badge.exclamationmark")
                case .empty:
                    ChalkakSkeleton()
                @unknown default:
                    ChalkakSkeleton()
                }
            }
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
