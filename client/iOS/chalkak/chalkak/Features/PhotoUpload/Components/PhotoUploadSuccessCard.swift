import SwiftUI
import UIKit

struct PhotoUploadSuccessCard: View {
    @Environment(\.chalkakTheme) private var theme

    let image: UIImage
    let contentDescription: String
    let date: Date
    let title: String

    var body: some View {
        VStack(spacing: 0) {
            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, theme.spacing.lg)
                .padding(.top, theme.spacing.lg)
                .accessibilityLabel(contentDescription)

            VStack(alignment: .leading, spacing: 0) {
                Text(PhotoUploadDate.displayString(from: date))
                    .font(theme.typography.caption)
                    .foregroundStyle(theme.colors.textMuted)

                Text(title)
                    .font(theme.typography.photoCardTitle)
                    .foregroundStyle(theme.colors.textPrimary)
                    .padding(.top, 8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, theme.spacing.lg)
            .padding(.top, 27)
            .padding(.trailing, theme.spacing.lg)
            .padding(.bottom, 36)
        }
        .background(theme.colors.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.small))
        .shadow(color: .black.opacity(0.12), radius: 12, x: 0, y: 4)
    }
}

#Preview("Photo Upload Success Card") {
    if let image = UIImage(named: "preview_photo") {
        PhotoUploadSuccessCard(
            image: image,
            contentDescription: "전시한 사진",
            date: PhotoUploadDate.today(),
            title: "한낮의 다리"
        )
        .padding(ChalkakSpacing.screenHorizontal)
        .chalkakTheme(.light)
    }
}
