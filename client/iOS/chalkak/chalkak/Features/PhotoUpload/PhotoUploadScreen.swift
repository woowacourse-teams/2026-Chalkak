import SwiftUI
import UIKit

struct PhotoUploadScreen: View {
    @Environment(\.chalkakTheme) private var theme

    let viewState: PhotoUploadViewState
    let isCameraAvailable: Bool
    let onAction: (PhotoUploadAction) -> Void
    let onRetryTopicLoad: () -> Void

    @State private var isCaptionFocused = false
    @State private var isKeyboardVisible = false
    @State private var isBackPending = false
    @State private var message: String?
    @State private var messageDismissTask: Task<Void, Never>?
    @State private var captionFrame: CGRect = .zero
    @State private var scrollOffset: CGFloat = 0
    @State private var canDismissCurrentDrag: Bool?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                PhotoUploadImageArea(
                    selectedImage: viewState.selectedImage,
                    topicTitle: viewState.topicTitle,
                    isCameraAvailable: isCameraAvailable,
                    onGalleryClick: { onAction(.galleryClicked) },
                    onCameraClick: { onAction(.cameraClicked) }
                )
                .simultaneousGesture(
                    TapGesture().onEnded {
                        guard isCaptionFocused || isKeyboardVisible else { return }
                        isCaptionFocused = false
                        dismissKeyboard()
                    }
                )

                Spacer()
                    .frame(height: 34)

                VStack(spacing: 0) {
                    ChalkakTextField(
                        text: Binding(
                            get: { viewState.caption },
                            set: { onAction(.captionChanged($0)) }
                        ),
                        label: "사진 설명",
                        placeholder: "작품 제목은 선택이에요.",
                        isEnabled: !viewState.isSubmitting,
                        lineLimit: 3...5,
                        textFont: theme.typography.subheadline,
                        maximumCharacterCount: Constants.captionMaxLength,
                        onFocusChange: { isCaptionFocused = $0 }
                    )
                    .onGeometryChange(for: CGRect.self) { proxy in
                        proxy.frame(in: .named("photoUpload.dismiss"))
                    } action: { frame in
                        captionFrame = frame
                    }

                    if let topicErrorMessage = viewState.topicErrorMessage {
                        Text(topicErrorMessage)
                            .font(theme.typography.footnote)
                            .foregroundStyle(theme.colors.error)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, 8)

                        Button("다시 시도", action: onRetryTopicLoad)
                            .font(theme.typography.body)
                            .foregroundStyle(theme.colors.actionPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .buttonStyle(.plain)
                    }

                    Spacer()
                        .frame(height: 16)
                }
                .padding(.horizontal, 22)
            }
            .padding(.top, theme.spacing.xl)
            .padding(.bottom, 104)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .onScrollGeometryChange(for: CGFloat.self) { geometry in
            max(0, geometry.contentOffset.y + geometry.contentInsets.top)
        } action: { _, offset in
            scrollOffset = offset
        }
        .background(theme.colors.background)
        .navigationTitle("전시하기")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                ChalkakCloseButton(action: requestBack)
                    .disabled(viewState.isSubmitting)
                    .accessibilityLabel("닫기")
                    .accessibilityIdentifier("photoUpload.close")
            }
            .chalkakNavigationBackground()
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakButton(
                title: viewState.isSubmitting ? "전시 중..." : "전시하기",
                action: { onAction(.submitClicked) },
                isEnabled: viewState.canSubmit,
                fillsWidth: true
            )
            .padding(.horizontal, 26)
            .padding(.bottom, 26)
        }
        .overlay(alignment: .bottom) {
            if let message {
                Text(message)
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.onActionPrimary)
                    .padding(.horizontal, theme.spacing.lg)
                    .padding(.vertical, theme.spacing.md)
                    .background(theme.colors.actionPrimary, in: Capsule())
                    .padding(.bottom, 88)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .accessibilityLabel(message)
            }
        }
        .contentShape(Rectangle())
        .coordinateSpace(name: "photoUpload.dismiss")
        .simultaneousGesture(dismissGesture)
        .onChange(of: viewState.pendingMessage?.id) { _, messageID in
            guard let messageID,
                  let pendingMessage = viewState.pendingMessage
            else { return }
            showMessage(pendingMessage.text)
            onAction(.messageShown(messageID))
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            isKeyboardVisible = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidHideNotification)) { _ in
            isKeyboardVisible = false
            completePendingBack()
        }
        .onChange(of: isCaptionFocused) { _, isFocused in
            guard !isFocused, isBackPending, !isKeyboardVisible else { return }
            completePendingBack()
        }
        .onDisappear {
            messageDismissTask?.cancel()
        }
    }

    private func requestBack() {
        guard !isBackPending else { return }

        if isCaptionFocused || isKeyboardVisible {
            isBackPending = true
            dismissKeyboard()
        } else {
            onAction(.backClicked)
        }
    }

    private var dismissGesture: some Gesture {
        DragGesture(minimumDistance: 20, coordinateSpace: .named("photoUpload.dismiss"))
            .onChanged { value in
                guard canDismissCurrentDrag == nil else { return }
                // 키보드가 드래그 도중 내려가더라도 같은 드래그로 화면을 닫지 않는다.
                canDismissCurrentDrag = !isCaptionFocused && !isKeyboardVisible
                    && !viewState.isSubmitting && scrollOffset <= 1
                    && !captionFrame.contains(value.startLocation)
            }
            .onEnded { value in
                let canDismiss = canDismissCurrentDrag == true
                canDismissCurrentDrag = nil
                guard canDismiss, !viewState.isSubmitting,
                      !isCaptionFocused, !isKeyboardVisible,
                      value.translation.height > abs(value.translation.width) else { return }
                if value.translation.height > 80 || value.predictedEndTranslation.height > 140 {
                    requestBack()
                }
            }
    }

    private func completePendingBack() {
        guard isBackPending else { return }
        isBackPending = false
        onAction(.backClicked)
    }

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
    }

    private func showMessage(_ text: String) {
        messageDismissTask?.cancel()
        withAnimation(.snappy) {
            message = text
        }
        messageDismissTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) {
                message = nil
            }
        }
    }

    private enum Constants {
        static let captionMaxLength = 10
    }
}

#Preview("Photo Upload Empty") {
    PhotoUploadScreen(
        viewState: PhotoUploadViewState(topicTitle: "틈"),
        isCameraAvailable: true,
        onAction: { _ in },
        onRetryTopicLoad: {}
    )
    .chalkakTheme(.light)
}

#Preview("Photo Upload Selected") {
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
    .chalkakTheme(.light)
}
