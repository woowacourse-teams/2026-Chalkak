import SwiftUI

struct HomePhotoCard: View {
    @Environment(\.chalkakTheme) private var theme
    let photo: HomePhoto
    let isLiked: Bool
    let isLikeEnabled: Bool
    let onLike: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ChalkakSignedImage(
                imageSource: photo.imageSource,
                signatureSource: photo.signatureSource,
                contentDescription: photo.contentDescription,
                contentMode: .fill
            )
            .frame(maxWidth: .infinity)
            .frame(height: HomePhotoCardMetrics.photoHeight)
            .clipped()
            .accessibilityElement(children: .combine)

            actionRow
        }
        .background(theme.colors.surfaceElevated)
        .shadow(
            color: Color.black.opacity(HomePhotoCardMetrics.shadowOpacity),
            radius: HomePhotoCardMetrics.shadowRadius,
            y: HomePhotoCardMetrics.shadowY
        )
    }

    private var actionRow: some View {
        HStack(spacing: theme.spacing.md) {
            Button(action: onLike) {
                HStack(spacing: HomePhotoCardMetrics.likeSpacing) {
                    Image(systemName: isLiked ? "heart.fill" : "heart")
                        .font(.system(size: HomePhotoCardMetrics.heartSize))
                        .foregroundStyle(
                            isLiked ? theme.colors.actionPrimary : theme.colors.textSecondary
                        )
                        .accessibilityHidden(true)

                    Text("\(photo.likeCount)")
                        .font(theme.typography.body)
                        .fontWeight(.regular)
                        .foregroundStyle(theme.colors.textSecondary)
                }
                .frame(minHeight: HomePhotoCardMetrics.minimumTouchSize)
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
        .padding(.horizontal, HomePhotoCardMetrics.horizontalPadding)
        .frame(height: HomePhotoCardMetrics.actionHeight)
    }
}

private enum HomePhotoCardMetrics {
    static let photoHeight: CGFloat = 415
    static let actionHeight: CGFloat = 57
    static let horizontalPadding: CGFloat = 14
    static let likeSpacing: CGFloat = 9
    static let heartSize: CGFloat = 24
    static let minimumTouchSize: CGFloat = 44
    static let shadowOpacity: CGFloat = 0.14
    static let shadowRadius: CGFloat = 4
    static let shadowY: CGFloat = 2
}

private extension String {
    var nilIfBlank: String? {
        isEmpty ? nil : self
    }
}

#Preview("Home Photo Card", traits: .sizeThatFitsLayout) {
    HomePhotoCard(
        photo: HomePreviewData.contentState.photos[0],
        isLiked: true,
        isLikeEnabled: true,
        onLike: {}
    )
    .padding()
    .chalkakTheme(.light)
}
