import SwiftUI
import UIKit

struct PhotoUploadSuccessScreen: View {
    @Environment(\.chalkakTheme) private var theme

    let submission: PhotoUploadSubmission
    let onConfirmClick: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer()
                    .frame(height: 60)

                PhotoUploadSuccessCard(
                    image: submission.image,
                    contentDescription: "전시한 사진",
                    date: submission.content.date,
                    title: submission.caption.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? submission.content.topic
                        : submission.caption
                )
                .frame(maxWidth: .infinity)
                .padding(.horizontal, theme.spacing.screenHorizontal)

                Spacer()
                    .frame(height: 34)

                Text("‘\(submission.content.topic)’를 기록했어요.")
                    .font(theme.typography.title1)
                    .foregroundStyle(theme.colors.textPrimary)
                    .padding(.horizontal, theme.spacing.screenHorizontal)

                Text(submission.content.moderationStatus.successMessage)
                    .font(theme.typography.footnote)
                    .foregroundStyle(theme.colors.textSecondary)
                    .padding(.leading, theme.spacing.screenHorizontal)
                    .padding(.top, 10)
                    .padding(.trailing, theme.spacing.screenHorizontal)
                    .padding(.bottom, 40)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(theme.colors.background)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakButton(
                title: "확인했어요.",
                action: onConfirmClick,
                fillsWidth: true
            )
            .padding(.horizontal, theme.spacing.xl)
            .padding(.bottom, 26)
        }
    }
}

#Preview("Photo Upload Success") {
    if let image = UIImage(named: "preview_photo") {
        PhotoUploadSuccessScreen(
            submission: PhotoUploadSubmission(
                image: image,
                caption: "한낮의 다리",
                content: PhotoUploadSuccessContent(
                    date: PhotoUploadDate.today(),
                    topic: "다리",
                    moderationStatus: .validating
                )
            ),
            onConfirmClick: {}
        )
        .chalkakTheme(.light)
    }
}
