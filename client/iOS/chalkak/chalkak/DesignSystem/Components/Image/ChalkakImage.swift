import ImageIO
import SwiftUI
import UIKit

enum ChalkakImageSource: Equatable, Sendable {
    case asset(String)
    case system(String)
    case remote(URL?)
}

/// 이미지의 EXIF 방향을 반영한 세로/가로 비율(height / width)을 구한다.
enum ImageRatioLoader {
    static func ratio(for source: ChalkakImageSource) async -> CGFloat? {
        switch source {
        case let .asset(name):
            guard let image = UIImage(named: name), image.size.width > 0 else { return nil }
            return image.size.height / image.size.width
        case .system:
            return nil
        case let .remote(url?):
            guard let (data, _) = try? await URLSession.shared.data(from: url) else { return nil }
            guard let imageSource = CGImageSourceCreateWithData(data as CFData, nil),
                  let properties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil)
                      as? [CFString: Any],
                  let pixelWidth = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.doubleValue,
                  let pixelHeight = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.doubleValue,
                  pixelWidth > 0, pixelHeight > 0
            else { return nil }

            let orientation = (properties[kCGImagePropertyOrientation] as? NSNumber)?.intValue ?? 1
            let isRotated = (5...8).contains(orientation)
            let width = isRotated ? pixelHeight : pixelWidth
            let height = isRotated ? pixelWidth : pixelHeight
            return CGFloat(height / width)
        case .remote(nil):
            return nil
        }
    }
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
        if let url {
            AsyncImage(url: url) { phase in
                phaseContent(phase)
            }
            .loadingSkeleton(isLoading: isLoading)
        } else {
            // URL이 없으면 로드가 끝나지 않으므로 스켈레톤 대신 고정 플레이스홀더를 표시한다.
            imagePlaceholder(systemName: "photo")
        }
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
