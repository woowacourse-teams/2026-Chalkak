import SwiftUI
import UIKit

struct PhotoUploadImageArea: View {
    @Environment(\.chalkakTheme) private var theme

    let selectedImage: UIImage?
    let topicTitle: String?
    let isCameraAvailable: Bool
    let onGalleryClick: () -> Void
    let onCameraClick: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Rectangle()
                .fill(theme.colors.inputBackground)
                .aspectRatio(Metrics.aspectRatio, contentMode: .fit)
                .overlay {
                    if let selectedImage {
                        Image(uiImage: selectedImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .clipped()
                            .accessibilityLabel("선택한 사진")
                    } else {
                        emptyContent
                    }
                }

            HStack(spacing: Metrics.actionSpacing) {
                PhotoUploadActionButton(
                    imageName: "ic_photo_library",
                    description: "앨범에서 사진 선택",
                    onClick: onGalleryClick
                )
                if isCameraAvailable {
                    PhotoUploadActionButton(
                        imageName: "ic_photo_camera",
                        description: "카메라로 촬영",
                        onClick: onCameraClick
                    )
                }
            }
            .padding(.top, Metrics.actionTopPadding)
            .padding(.trailing, Metrics.actionTrailingPadding)
        }
        .frame(maxWidth: .infinity)
        .clipped()
    }

    private var emptyContent: some View {
        VStack(spacing: 0) {
            if let topicTitle {
                Text("주제 ‘\(topicTitle)’에 맞는 한 장")
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.textSecondary)
            }
            Text("앨범에서 고르거나 지금 찍어요")
                .font(theme.typography.caption)
                .foregroundStyle(theme.colors.textMuted)
                .padding(.top, topicTitle == nil ? 0 : 5)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay {
            Rectangle()
                .inset(by: Metrics.borderWidth / 2)
                .stroke(
                    theme.colors.border,
                    style: StrokeStyle(
                        lineWidth: Metrics.borderWidth,
                        dash: [Metrics.dashLength, Metrics.gapLength]
                    )
                )
        }
        .accessibilityElement(children: .combine)
    }
}

private enum Metrics {
    static let aspectRatio: CGFloat = 1.216
    static let actionTopPadding: CGFloat = 14
    static let actionTrailingPadding: CGFloat = 11
    static let actionSpacing: CGFloat = 10
    static let borderWidth: CGFloat = 1
    static let dashLength: CGFloat = 5
    static let gapLength: CGFloat = 4
}

#Preview("Photo Upload Empty") {
    PhotoUploadImageArea(
        selectedImage: nil,
        topicTitle: "틈",
        isCameraAvailable: true,
        onGalleryClick: {},
        onCameraClick: {}
    )
    .chalkakTheme(.light)
}

#Preview("Photo Upload Selected") {
    PhotoUploadImageArea(
        selectedImage: UIImage(named: "preview_photo"),
        topicTitle: "틈",
        isCameraAvailable: true,
        onGalleryClick: {},
        onCameraClick: {}
    )
    .chalkakTheme(.light)
}
