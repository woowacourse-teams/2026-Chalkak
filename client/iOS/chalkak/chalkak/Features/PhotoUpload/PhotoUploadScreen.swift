import SwiftUI
import UIKit

/// 전시하기 1단계: 사진을 고른다. 사진을 고르거나 다시 편집하면 2단계(글 쓰기)로 넘어간다.
struct PhotoUploadScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast

    let viewState: PhotoUploadViewState
    let isCameraAvailable: Bool
    let onAction: (PhotoUploadAction) -> Void
    let onRetryTopicLoad: () -> Void

    @State private var scrollOffset: CGFloat = 0
    @State private var canDismissCurrentDrag: Bool?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                PhotoUploadImageArea(
                    selectedImage: viewState.selectedImage,
                    topicTitle: viewState.topicTitle,
                    isCameraAvailable: isCameraAvailable,
                    isImageEditable: viewState.canEditSelectedImage && !viewState.isSubmitting,
                    onImageClick: { onAction(.selectedImageClicked) },
                    onGalleryClick: { onAction(.galleryClicked) },
                    onCameraClick: { onAction(.cameraClicked) }
                )

                if let topicErrorMessage = viewState.topicErrorMessage {
                    PhotoUploadTopicError(message: topicErrorMessage, onRetry: onRetryTopicLoad)
                        .padding(.horizontal, Metrics.contentHorizontalPadding)
                        .padding(.top, theme.spacing.xl)
                }
            }
            .padding(.vertical, theme.spacing.xl)
            .frame(maxWidth: .infinity)
        }
        .onScrollGeometryChange(for: CGFloat.self) { geometry in
            max(0, geometry.contentOffset.y + geometry.contentInsets.top)
        } action: { _, offset in
            scrollOffset = offset
        }
        .background(theme.colors.background)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                ChalkakCloseButton { onAction(.backClicked) }
                    .disabled(viewState.isSubmitting)
                    .accessibilityLabel("닫기")
                    .accessibilityIdentifier("photoUpload.close")
            }
            .chalkakNavigationBackground()
        }
        .contentShape(Rectangle())
        .coordinateSpace(name: "photoUpload.dismiss")
        .simultaneousGesture(dismissGesture)
        .navigationDestination(isPresented: writingBinding) {
            PhotoUploadWritingScreen(
                viewState: viewState,
                onAction: onAction,
                onRetryTopicLoad: onRetryTopicLoad
            )
        }
        .onChange(of: viewState.pendingMessage?.id) { _, messageID in
            guard let messageID,
                  let pendingMessage = viewState.pendingMessage
            else { return }
            showToast(pendingMessage.text)
            onAction(.messageShown(messageID))
        }
    }

    private var writingBinding: Binding<Bool> {
        Binding(
            get: { viewState.isWriting },
            set: { isWriting in
                if !isWriting {
                    onAction(.writingBackClicked)
                }
            }
        )
    }

    /// 맨 위에서 아래로 끌면 닫는다. 작성 중인 내용이 있으면 확인을 거친다.
    private var dismissGesture: some Gesture {
        DragGesture(minimumDistance: 20, coordinateSpace: .named("photoUpload.dismiss"))
            .onChanged { _ in
                guard canDismissCurrentDrag == nil else { return }
                canDismissCurrentDrag = !viewState.isSubmitting && scrollOffset <= 1
            }
            .onEnded { value in
                let canDismiss = canDismissCurrentDrag == true
                canDismissCurrentDrag = nil
                guard canDismiss, !viewState.isSubmitting,
                      value.translation.height > abs(value.translation.width) else { return }
                if value.translation.height > 80 || value.predictedEndTranslation.height > 140 {
                    onAction(.backClicked)
                }
            }
    }
}

/// 주제를 불러오지 못하면 전시할 수 없으므로 두 단계 모두에서 다시 시도할 수 있게 보여 준다.
struct PhotoUploadTopicError: View {
    @Environment(\.chalkakTheme) private var theme

    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(message)
                .font(theme.typography.footnote)
                .foregroundStyle(theme.colors.error)

            Button("다시 시도", action: onRetry)
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.actionPrimary)
                .frame(minHeight: Metrics.minimumTouchHeight)
                .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private enum Metrics {
    static let contentHorizontalPadding: CGFloat = 22
    static let minimumTouchHeight: CGFloat = 44
}

#Preview("Photo Upload Empty") {
    NavigationStack {
        PhotoUploadScreen(
            viewState: PhotoUploadViewState(topicTitle: "틈"),
            isCameraAvailable: true,
            onAction: { _ in },
            onRetryTopicLoad: {}
        )
    }
    .chalkakTheme(.light)
}

#Preview("Photo Upload Selected") {
    NavigationStack {
        PhotoUploadScreen(
            viewState: PhotoUploadViewState(
                selectedImage: UIImage(named: "preview_photo"),
                selectedImageData: nil,
                imagePreparationStatus: .ready,
                topicTitle: "틈"
            ),
            isCameraAvailable: true,
            onAction: { _ in },
            onRetryTopicLoad: {}
        )
    }
    .chalkakTheme(.light)
}
