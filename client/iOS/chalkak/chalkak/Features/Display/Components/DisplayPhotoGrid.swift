import ImageIO
import SwiftUI

struct DisplayPhotoGrid: View {
    @Environment(\.chalkakTheme) private var theme
    let photos: [DisplayPhoto]
    let isLoadingNext: Bool
    let onEndThreshold: (Bool) -> Void

    // 로드되며 측정된 사진별 세로/가로 비율(height / width). 미측정 사진은 기본 비율로 배치한다.
    @State private var ratioByID: [DisplayPhoto.ID: CGFloat] = [:]

    var body: some View {
        VStack(spacing: theme.spacing.md) {
            let columns = distributedColumns()
            HStack(alignment: .top, spacing: theme.spacing.md) {
                column(columns.left)
                column(columns.right)
            }

            if isLoadingNext {
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.top, theme.spacing.md)
                    .accessibilityIdentifier("display-next-loading")
            }
        }
        .onAppear { onEndThreshold(false) }
    }

    private func column(_ items: [MasonryItem]) -> some View {
        LazyVStack(spacing: theme.spacing.md) {
            ForEach(items) { item in
                DisplayMasonryCell(
                    photo: item.photo,
                    ratio: ratioByID[item.photo.id],
                    onMeasured: { ratio in
                        guard ratioByID[item.photo.id] == nil else { return }
                        ratioByID[item.photo.id] = ratio
                    }
                )
                .onAppear {
                    onEndThreshold(item.index >= photos.count - Metrics.endThreshold)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .top)
    }

    // 항상 더 짧은 열에 다음 사진을 넣는 그리디 패킹.
    // i번째 사진의 열은 0..<i 사진들의 비율에만 의존하므로, 위에서 아래로 로드되는 동안
    // 화면에 보이는 시점에는 이미 확정되어 있어 재배치(reflow)가 눈에 띄지 않는다.
    private func distributedColumns() -> (left: [MasonryItem], right: [MasonryItem]) {
        var left: [MasonryItem] = []
        var right: [MasonryItem] = []
        var leftHeight: CGFloat = 0
        var rightHeight: CGFloat = 0

        for (index, photo) in photos.enumerated() {
            let unitHeight = (ratioByID[photo.id] ?? Metrics.defaultRatio) + Metrics.spacingRatio
            if leftHeight <= rightHeight {
                left.append(MasonryItem(index: index, photo: photo))
                leftHeight += unitHeight
            } else {
                right.append(MasonryItem(index: index, photo: photo))
                rightHeight += unitHeight
            }
        }
        return (left, right)
    }
}

private struct MasonryItem: Identifiable {
    let index: Int
    let photo: DisplayPhoto

    var id: DisplayPhoto.ID { photo.id }
}

private struct DisplayMasonryCell: View {
    @Environment(\.chalkakTheme) private var theme
    let photo: DisplayPhoto
    let ratio: CGFloat?
    let onMeasured: (CGFloat) -> Void

    var body: some View {
        // aspectRatio는 가로/세로(width / height)를 받으므로 저장한 세로/가로 비율을 뒤집는다.
        let widthOverHeight = 1 / (ratio ?? Metrics.defaultRatio)

        Color.black
            .aspectRatio(widthOverHeight, contentMode: .fit)
            .overlay {
                ChalkakSignedImage(
                    imageSource: photo.thumbnailImageSource,
                    signatureSource: photo.signatureThumbnailImageSource,
                    contentDescription: photo.contentDescription,
                    contentMode: .fill,
                    signatureSize: Metrics.signatureSize
                )
            }
            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.photoCard))
            .overlay(alignment: .bottomLeading) {
                DisplayLikeBadge(likeCount: photo.likeCount)
                    .padding(Metrics.badgeInset)
            }
            .task(id: photo.id) {
                guard ratio == nil,
                      let measured = await ImageRatioLoader.ratio(for: photo.thumbnailImageSource)
                else { return }
                onMeasured(measured)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("\(photo.contentDescription), 좋아요 \(photo.likeCount)")
    }
}

struct DisplayLikeBadge: View {
    @Environment(\.chalkakTheme) private var theme
    let likeCount: Int

    var body: some View {
        HStack(spacing: Metrics.spacing) {
            Image("ic_heart")
                .renderingMode(.template)
                .resizable()
                .frame(width: Metrics.heartSize, height: Metrics.heartSize)
                .accessibilityHidden(true)

            Text("\(likeCount)")
                .font(theme.typography.subheadline)
        }
        .foregroundStyle(theme.colors.textOnImage)
        .shadow(color: .black.opacity(Metrics.shadowOpacity), radius: Metrics.shadowRadius, y: 1)
    }

    private enum Metrics {
        static let spacing: CGFloat = 5
        static let heartSize: CGFloat = 18
        static let shadowOpacity: CGFloat = 0.25
        static let shadowRadius: CGFloat = 2
    }
}

/// 원격 이미지의 헤더만 읽어 EXIF 방향을 반영한 세로/가로 비율(height / width)을 구한다.
/// `URLSession.shared`를 사용하므로 `AsyncImage`와 `URLCache`를 공유해 실제 다운로드는 한 번만 일어난다.
enum ImageRatioLoader {
    static func ratio(for source: ChalkakImageSource) async -> CGFloat? {
        guard case let .remote(url?) = source else { return nil }
        guard let (data, _) = try? await URLSession.shared.data(from: url) else { return nil }
        guard let imageSource = CGImageSourceCreateWithData(data as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil)
                  as? [CFString: Any],
              let pixelWidth = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.doubleValue,
              let pixelHeight = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.doubleValue,
              pixelWidth > 0, pixelHeight > 0
        else { return nil }

        // EXIF 방향 5~8은 90/270도 회전이라 가로·세로가 바뀐다.
        let orientation = (properties[kCGImagePropertyOrientation] as? NSNumber)?.intValue ?? 1
        let isRotated = (5...8).contains(orientation)
        let width = isRotated ? pixelHeight : pixelWidth
        let height = isRotated ? pixelWidth : pixelHeight
        return CGFloat(height / width)
    }
}

private enum Metrics {
    static let endThreshold = 4
    static let defaultRatio: CGFloat = 4.0 / 3.0
    // 카드 사이 세로 간격을 열 높이 추정에 반영하기 위한 정규화 상수.
    static let spacingRatio: CGFloat = 0.06
    static let signatureSize = CGSize(width: 40, height: 30)
    static let badgeInset: CGFloat = 10
}

#Preview("Display Photo Grid") {
    ScrollView {
        DisplayPhotoGrid(
            photos: DisplayPreviewData.latestState.photos,
            isLoadingNext: true,
            onEndThreshold: { _ in }
        )
        .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
    }
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
