import SwiftUI

struct TodayPhotoCard: View {
    @Environment(\.chalkakTheme) private var theme
    let photo: TodayPhoto
    let isLiked: Bool
    let isLikeEnabled: Bool
    let imageReloadGeneration: Int
    let imageRatio: CGFloat?
    let onImageRatioChanged: (CGFloat?) -> Void
    let onLike: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Color.clear
                .frame(maxWidth: .infinity)
                .aspectRatio(1 / (imageRatio ?? TodayPhotoCardMetrics.defaultImageRatio), contentMode: .fit)
                .overlay {
                    ZStack(alignment: .bottomTrailing) {
                        photoImage
                            .id(imageReloadGeneration)

                        signatureImage
                        .frame(
                            width: TodayPhotoCardMetrics.signatureSize.width,
                            height: TodayPhotoCardMetrics.signatureSize.height
                        )
                        .padding(theme.spacing.sm)
                    }
                }
                .clipped()
                .accessibilityElement(children: .combine)

            actionRow
        }
        .background(theme.colors.surfaceElevated)
        .shadow(
            color: Color.black.opacity(TodayPhotoCardMetrics.shadowOpacity),
            radius: TodayPhotoCardMetrics.shadowRadius,
            y: TodayPhotoCardMetrics.shadowY
        )
    }

    @ViewBuilder
    private var photoImage: some View {
        switch photo.imageSource {
        case let .remote(url):
            TodayRemoteMeasuredImage(
                url: url,
                contentDescription: photo.contentDescription,
                onRatioLoaded: onImageRatioChanged
            )
        case .asset, .system:
            ChalkakImage(
                source: photo.imageSource,
                contentDescription: photo.contentDescription,
                contentMode: .fill
            )
            .task(id: photo.imageSource) {
                onImageRatioChanged(await ImageRatioLoader.ratio(for: photo.imageSource))
            }
        }
    }

    @ViewBuilder
    private var signatureImage: some View {
        switch photo.signatureSource {
        case let .remote(url):
            TodayRemoteMeasuredImage(
                url: url,
                contentDescription: nil,
                contentMode: .fit
            )
        case .asset, .system:
            ChalkakImage(
                source: photo.signatureSource,
                contentDescription: nil,
                contentMode: .fit
            )
        }
    }

    private var actionRow: some View {
        HStack(spacing: theme.spacing.md) {
            Button(action: onLike) {
                HStack(spacing: TodayPhotoCardMetrics.likeSpacing) {
                    Image(isLiked ? "ic_heart_filled" : "ic_heart")
                        .renderingMode(.template)
                        .resizable()
                        .frame(
                            width: TodayPhotoCardMetrics.heartSize,
                            height: TodayPhotoCardMetrics.heartSize
                        )
                        .foregroundStyle(
                            isLiked ? theme.colors.actionPrimary : theme.colors.textSecondary
                        )
                        .accessibilityHidden(true)

                    Text("\(photo.likeCount)")
                        .font(theme.typography.body)
                        .fontWeight(.regular)
                        .foregroundStyle(theme.colors.textSecondary)
                }
                .frame(minHeight: TodayPhotoCardMetrics.minimumTouchSize)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!isLikeEnabled)
            .accessibilityLabel("좋아요 \(photo.likeCount)")
            .accessibilityValue(isLiked ? "선택됨" : "")

            Text(photo.title?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfBlank ?? "무제")
                .font(theme.typography.body)
                .fontWeight(.regular)
                .foregroundStyle(theme.colors.textSecondary)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.horizontal, TodayPhotoCardMetrics.horizontalPadding)
        .frame(height: TodayPhotoCardMetrics.actionHeight)
    }
}

private enum TodayPhotoCardMetrics {
    static let defaultImageRatio: CGFloat = 1
    static let actionHeight: CGFloat = 57
    static let horizontalPadding: CGFloat = 14
    static let likeSpacing: CGFloat = 9
    static let heartSize: CGFloat = 24
    static let minimumTouchSize: CGFloat = 44
    static let signatureSize = CGSize(width: 56, height: 42)
    static let shadowOpacity: CGFloat = 0.14
    static let shadowRadius: CGFloat = 4
    static let shadowY: CGFloat = 2
}

private extension String {
    var nilIfBlank: String? {
        isEmpty ? nil : self
    }
}

#Preview("Today Photo Card", traits: .sizeThatFitsLayout) {
    TodayPhotoCard(
        photo: TodayPreviewData.contentState.photos[0],
        isLiked: true,
        isLikeEnabled: true,
        imageReloadGeneration: 0,
        imageRatio: nil,
        onImageRatioChanged: { _ in },
        onLike: {}
    )
    .padding()
    .chalkakTheme(.light)
}
