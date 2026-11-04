import SwiftUI
import UIKit

struct PhotoUploadScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast

    let viewState: PhotoUploadViewState
    let isCameraAvailable: Bool
    let onAction: (PhotoUploadAction) -> Void
    let onRetryTopicLoad: () -> Void

    @State private var isCaptionFocused = false
    @State private var isDescriptionFocused = false
    @State private var isKeyboardVisible = false
    @State private var isBackPending = false
    @State private var captionFrame: CGRect = .zero
    @State private var descriptionFrame: CGRect = .zero
    @State private var scrollOffset: CGFloat = 0
    @State private var canDismissCurrentDrag: Bool?

    var body: some View {
        ScrollViewReader { scrollProxy in
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
                    .simultaneousGesture(
                        TapGesture().onEnded {
                            guard isInputFocused || isKeyboardVisible else { return }
                            isCaptionFocused = false
                            isDescriptionFocused = false
                            dismissKeyboard()
                        }
                    )

                    VStack(alignment: .leading, spacing: 0) {
                        sectionLabel("제목")
                            .padding(.bottom, theme.spacing.sm)
                            .id(PhotoUploadInput.caption)
                        ChalkakTextField(
                            text: Binding(
                                get: { viewState.caption },
                                set: { onAction(.captionChanged($0)) }
                            ),
                            label: "작품 제목",
                            placeholder: "작품 제목은 선택이에요.",
                            isEnabled: !viewState.isSubmitting,
                            lineLimit: 1...1,
                            textFont: theme.typography.subheadline,
                            maximumCharacterCount: Constants.captionMaxLength,
                            height: Metrics.captionFieldHeight,
                            onFocusChange: { isCaptionFocused = $0 }
                        )
                        .onGeometryChange(for: CGRect.self) { proxy in
                            proxy.frame(in: .named("photoUpload.dismiss"))
                        } action: { frame in
                            captionFrame = frame
                        }

                        sectionLabel("이야기")
                            .padding(.top, theme.spacing.lg)
                            .padding(.bottom, theme.spacing.sm)
                        ChalkakTextField(
                            text: Binding(
                                get: { viewState.postDescription },
                                set: { onAction(.descriptionChanged($0)) }
                            ),
                            label: "작품 이야기",
                            placeholder: "작품 이야기는 선택이에요.",
                            isEnabled: !viewState.isSubmitting,
                            lineLimit: Constants.descriptionLineLimit,
                            textFont: theme.typography.subheadline,
                            maximumCharacterCount: Constants.descriptionMaxLength,
                            lengthMetric: .unicodeScalars,
                            onFocusChange: { isDescriptionFocused = $0 }
                        )
                        .onGeometryChange(for: CGRect.self) { proxy in
                            proxy.frame(in: .named("photoUpload.dismiss"))
                        } action: { frame in
                            descriptionFrame = frame
                        }
                        .id(PhotoUploadInput.description)

                        if let topicErrorMessage = viewState.topicErrorMessage {
                            PhotoUploadTopicError(message: topicErrorMessage, onRetry: onRetryTopicLoad)
                                .padding(.top, theme.spacing.sm)
                        }
                    }
                    .padding(.horizontal, Metrics.contentHorizontalPadding)
                    .padding(.top, theme.spacing.xl)
                }
                .padding(.vertical, theme.spacing.xl)
                .frame(maxWidth: .infinity)
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidShowNotification)) { _ in
                scrollToFocusedInput(scrollProxy)
            }
            .onChange(of: focusedInput) { _, _ in
                scrollToFocusedInput(scrollProxy)
            }
            // 이야기는 줄이 늘어나므로 쓰는 동안 칸 아래쪽을 따라간다.
            .onChange(of: viewState.postDescription) { _, _ in
                scrollToFocusedInput(scrollProxy)
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .onScrollGeometryChange(for: CGFloat.self) { geometry in
            max(0, geometry.contentOffset.y + geometry.contentInsets.top)
        } action: { _, offset in
            scrollOffset = offset
        }
        .background(theme.colors.background)
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
            .accessibilityIdentifier("photoUpload.submit")
            .padding(.horizontal, Metrics.buttonHorizontalPadding)
            .padding(.top, theme.spacing.sm)
            .padding(.bottom, Metrics.buttonBottomPadding)
            // 스크롤되는 입력칸이 버튼 주변으로 비치지 않게 가린다.
            .background(theme.colors.background)
        }
        .contentShape(Rectangle())
        .coordinateSpace(name: "photoUpload.dismiss")
        .simultaneousGesture(dismissGesture)
        .onChange(of: viewState.pendingMessage?.id) { _, messageID in
            guard let messageID,
                  let pendingMessage = viewState.pendingMessage
            else { return }
            showToast(pendingMessage.text)
            onAction(.messageShown(messageID))
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            isKeyboardVisible = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidHideNotification)) { _ in
            isKeyboardVisible = false
            completePendingBack()
        }
        .onChange(of: isInputFocused) { _, isFocused in
            guard !isFocused, isBackPending, !isKeyboardVisible else { return }
            completePendingBack()
        }
    }

    private var isInputFocused: Bool {
        focusedInput != nil
    }

    private var focusedInput: PhotoUploadInput? {
        if isCaptionFocused { return .caption }
        if isDescriptionFocused { return .description }
        return nil
    }

    /// 입력칸이 화면 아래쪽에 있어 키보드와 전시하기 버튼에 가리지 않도록 올린다.
    /// 제목은 맨 위까지 올려 아래 이야기 칸도 보이게 해서, 키보드를 내리지 않고 바로 옮겨 갈 수 있게 한다.
    private func scrollToFocusedInput(_ scrollProxy: ScrollViewProxy) {
        guard let focusedInput else { return }
        withAnimation {
            scrollProxy.scrollTo(focusedInput, anchor: focusedInput == .caption ? .top : .bottom)
        }
    }

    private func sectionLabel(_ title: String) -> some View {
        Text(title)
            .font(theme.typography.caption)
            .fontWeight(.semibold)
            .foregroundStyle(theme.colors.textPrimary)
            .accessibilityHidden(true)
    }

    private func requestBack() {
        guard !isBackPending else { return }

        if isInputFocused || isKeyboardVisible {
            isBackPending = true
            dismissKeyboard()
        } else {
            onAction(.backClicked)
        }
    }

    /// 맨 위에서 아래로 끌면 닫는다. 작성 중인 내용이 있으면 확인을 거친다.
    private var dismissGesture: some Gesture {
        DragGesture(minimumDistance: 20, coordinateSpace: .named("photoUpload.dismiss"))
            .onChanged { value in
                guard canDismissCurrentDrag == nil else { return }
                // 키보드가 드래그 도중 내려가더라도 같은 드래그로 화면을 닫지 않는다.
                canDismissCurrentDrag = !isInputFocused && !isKeyboardVisible
                    && !viewState.isSubmitting && scrollOffset <= 1
                    && !captionFrame.contains(value.startLocation)
                    && !descriptionFrame.contains(value.startLocation)
            }
            .onEnded { value in
                let canDismiss = canDismissCurrentDrag == true
                canDismissCurrentDrag = nil
                guard canDismiss, !viewState.isSubmitting,
                      !isInputFocused, !isKeyboardVisible,
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

    private enum Constants {
        static let captionMaxLength = 10
        static let descriptionMaxLength = 150
        static let descriptionLineLimit = 3...8
    }
}

private enum PhotoUploadInput: Hashable {
    case caption
    case description
}

/// 주제를 불러오지 못하면 전시할 수 없으므로 다시 시도할 수 있게 보여 준다.
private struct PhotoUploadTopicError: View {
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
    /// 빈 칸은 포커스를 받기 전까지 줄 높이가 달라, 높이를 고정해 칸이 움직이지 않게 한다. 근거: 이슈 #945
    static let captionFieldHeight: CGFloat = 54
    static let buttonHorizontalPadding: CGFloat = 26
    static let buttonBottomPadding: CGFloat = 26
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
