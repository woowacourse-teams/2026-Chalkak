import SwiftUI

enum ChalkakImageSource {
    case asset(String)
    case system(String)
    case remote(URL?)
}

struct ChalkakImage: View {
    @Environment(\.chalkakColors) private var colors

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
                .foregroundStyle(colors.iconSecondary)
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
                    imagePlaceholder(systemName: "photo")
                @unknown default:
                    imagePlaceholder(systemName: "photo")
                }
            }
        }
    }

    private func imagePlaceholder(systemName: String) -> some View {
        ZStack {
            colors.inputBackground
            Image(systemName: systemName)
                .font(.system(size: Metrics.placeholderIconSize))
                .foregroundStyle(colors.iconSecondary)
        }
    }
}

private enum Metrics {
    static let placeholderIconSize: CGFloat = 24
}

#Preview("Image") {
    ChalkakImage(
        source: .asset("preview_photo"),
        contentDescription: "전시 사진",
        contentMode: .fit
    )
    .frame(width: 120, height: 120)
    .background(ChalkakColors().inputBackground)
}
