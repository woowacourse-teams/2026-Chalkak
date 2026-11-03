import SwiftUI
import UIKit

/// 전시하기 2단계: 고른 사진을 작은 썸네일로 보며 제목과 이야기를 쓴다. 둘 다 선택 입력이다.
struct PhotoUploadWritingScreen: View {
    @Environment(\.chalkakTheme) private var theme

    let viewState: PhotoUploadViewState
    let onAction: (PhotoUploadAction) -> Void
    let onRetryTopicLoad: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: theme.spacing.lg) {
                    thumbnail

                    VStack(alignment: .leading, spacing: theme.spacing.xs) {
                        sectionLabel("제목")
                        ChalkakTextField(
                            text: Binding(
                                get: { viewState.caption },
                                set: { onAction(.captionChanged($0)) }
                            ),
                            label: "작품 제목",
                            placeholder: "제목은 선택이에요.",
                            isEnabled: !viewState.isSubmitting,
                            lineLimit: 1...1,
                            textFont: theme.typography.title3,
                            maximumCharacterCount: Constants.captionMaxLength,
                            style: .plain
                        )
                    }
                }

                Divider()
                    .overlay(theme.colors.border)
                    .padding(.vertical, theme.spacing.lg)

                sectionLabel("이야기")
                    .padding(.bottom, theme.spacing.sm)
                ChalkakTextField(
                    text: Binding(
                        get: { viewState.postDescription },
                        set: { onAction(.descriptionChanged($0)) }
                    ),
                    label: "작품 이야기",
                    placeholder: "사진에 담긴 이야기를 적어 주세요. (선택)",
                    isEnabled: !viewState.isSubmitting,
                    lineLimit: Constants.descriptionLineLimit,
                    textFont: theme.typography.body,
                    maximumCharacterCount: Constants.descriptionMaxLength,
                    lengthMetric: .unicodeScalars,
                    style: .plain
                )

                if let topicErrorMessage = viewState.topicErrorMessage {
                    PhotoUploadTopicError(message: topicErrorMessage, onRetry: onRetryTopicLoad)
                        .padding(.top, theme.spacing.lg)
                }
            }
            .padding(.horizontal, Metrics.contentHorizontalPadding)
            .padding(.top, theme.spacing.lg)
            .padding(.bottom, theme.spacing.xl)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(theme.colors.background)
        .navigationTitle("글 쓰기")
        .navigationBarTitleDisplayMode(.inline)
        // 전시 중에는 화면을 벗어나지 않도록 뒤로 가기를 막는다.
        .navigationBarBackButtonHidden(viewState.isSubmitting)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakButton(
                title: viewState.isSubmitting ? "전시 중..." : "전시하기",
                action: { onAction(.submitClicked) },
                isEnabled: viewState.canSubmit,
                fillsWidth: true
            )
            .accessibilityIdentifier("photoUpload.submit")
            .padding(.horizontal, Metrics.buttonHorizontalPadding)
            .padding(.bottom, Metrics.buttonBottomPadding)
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        if let image = viewState.selectedImage {
            let photo = Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: Metrics.thumbnailSize, height: Metrics.thumbnailSize)
                .clipShape(RoundedRectangle(cornerRadius: theme.shapes.photoCard))

            if viewState.canEditSelectedImage && !viewState.isSubmitting {
                Button { onAction(.selectedImageClicked) } label: { photo }
                    .buttonStyle(ChalkakUnhighlightedButtonStyle())
                    .accessibilityLabel("선택한 사진")
                    .accessibilityHint("사진을 다시 편집해요")
                    .accessibilityIdentifier("photoUpload.selectedImage")
            } else {
                photo
                    .accessibilityLabel("선택한 사진")
            }
        }
    }

    private func sectionLabel(_ title: String) -> some View {
        Text(title)
            .font(theme.typography.caption)
            .fontWeight(.semibold)
            .foregroundStyle(theme.colors.textPrimary)
            .accessibilityHidden(true)
    }

    private enum Constants {
        static let captionMaxLength = 10
        static let descriptionMaxLength = 300
        static let descriptionLineLimit = 10...20
    }
}

private enum Metrics {
    static let contentHorizontalPadding: CGFloat = 22
    static let buttonHorizontalPadding: CGFloat = 26
    static let buttonBottomPadding: CGFloat = 26
    static let thumbnailSize: CGFloat = 84
}

#Preview("Photo Upload Writing") {
    NavigationStack {
        PhotoUploadWritingScreen(
            viewState: PhotoUploadViewState(
                selectedImage: UIImage(named: "preview_photo"),
                selectedImageData: nil,
                imagePreparationStatus: .ready,
                topicTitle: "틈"
            ),
            onAction: { _ in },
            onRetryTopicLoad: {}
        )
    }
    .chalkakTheme(.light)
}
