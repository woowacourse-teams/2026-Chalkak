import SwiftUI
import UIKit

struct PhotoEditScreen: View {
    @Environment(\.chalkakTheme) private var theme

    @State private var viewModel: PhotoEditViewModel
    @State private var cropperController = PhotoEditCropperController()

    let onComplete: (PhotoEditResult) -> Void
    let onCancel: () -> Void

    init(
        session: PhotoEditSession,
        renderer: PhotoEditRenderer = .live,
        onComplete: @escaping (PhotoEditResult) -> Void,
        onCancel: @escaping () -> Void
    ) {
        _viewModel = State(initialValue: PhotoEditViewModel(session: session, renderer: renderer))
        self.onComplete = onComplete
        self.onCancel = onCancel
    }

    var body: some View {
        let viewState = viewModel.viewState

        Group {
            switch viewState.activeTool {
            case nil:
                overview(viewState)
            case .adjust:
                adjustTool(viewState)
            case .filter:
                filterTool(viewState)
            case .crop:
                cropTool(viewState)
            }
        }
        .background(theme.colors.editorBackground)
        .overlay {
            if viewState.isCompleting {
                ProgressView()
                    .tint(theme.colors.textOnEditor)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            viewModel.start()
        }
        .alert(
            viewState.failureMessage ?? "",
            isPresented: failureAlertPresented
        ) {
            Button("확인", role: .cancel) {}
        }
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
    }

    /// 편집 첫 화면. 사진을 보여 주고 도구를 고른다.
    private func overview(_ viewState: PhotoEditViewState) -> some View {
        VStack(spacing: 0) {
            PhotoEditHeader(
                isDoneEnabled: !viewState.isCompleting,
                onBack: { viewModel.handle(.cancelClicked) },
                onDone: { viewModel.handle(.doneClicked) }
            )
            .padding(.horizontal, theme.spacing.lg)
            .padding(.top, theme.spacing.sm)

            preview(viewState)

            PhotoEditToolPicker(onSelect: { viewModel.handle(.toolSelected($0)) })
                .disabled(viewState.isCompleting)
                .padding(.vertical, theme.spacing.md)
        }
    }

    private func adjustTool(_ viewState: PhotoEditViewState) -> some View {
        VStack(spacing: 0) {
            preview(viewState)

            PhotoEditAdjustControls(
                selectedItem: viewState.selectedAdjustItem,
                value: viewState.value(of:),
                automaticValue: viewState.automaticValue(of:),
                onSelect: { viewModel.handle(.adjustItemSelected($0)) },
                onValueChange: { viewModel.handle(.adjustValueChanged($0)) }
            )
            .contentMargins(.horizontal, theme.spacing.lg, for: .scrollContent)
            .padding(.vertical, theme.spacing.sm)

            PhotoEditActionBar(
                onCancel: { viewModel.handle(.toolCancelled) },
                onApply: { viewModel.handle(.toolApplied) }
            )
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)
        }
    }

    private func filterTool(_ viewState: PhotoEditViewState) -> some View {
        VStack(spacing: 0) {
            preview(viewState)

            PhotoEditFilterStrip(
                selectedFilter: viewState.selectedFilter,
                thumbnails: viewState.thumbnails,
                onSelect: { viewModel.handle(.filterSelected($0)) }
            )
            .contentMargins(.horizontal, theme.spacing.lg, for: .scrollContent)
            .frame(height: Metrics.toolOptionsHeight)

            PhotoEditActionBar(
                onCancel: { viewModel.handle(.toolCancelled) },
                onApply: { viewModel.handle(.toolApplied) }
            )
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)
        }
    }

    private func cropTool(_ viewState: PhotoEditViewState) -> some View {
        VStack(spacing: 0) {
            GeometryReader { proxy in
                if let source = viewState.cropperSource {
                    cropper(source, in: proxy.size)
                        .frame(width: proxy.size.width, height: proxy.size.height)
                } else {
                    ProgressView()
                        .tint(theme.colors.textOnEditor)
                        .frame(width: proxy.size.width, height: proxy.size.height)
                }
            }
            .animation(.easeInOut(duration: Metrics.rotationDuration), value: viewState.cropperSource?.id)

            PhotoEditAspectRatioStrip(
                selectedAspectRatio: viewState.cropDraft.aspectRatio,
                originalSize: viewState.cropDraft.orientation.orientedSize(of: viewState.session.original.size),
                onSelect: { viewModel.handle(.aspectRatioSelected($0)) }
            )
            .contentMargins(.horizontal, theme.spacing.lg, for: .scrollContent)
            .frame(height: Metrics.toolOptionsHeight)

            PhotoEditActionBar(
                isApplyEnabled: viewState.cropperSource != nil,
                onCancel: { viewModel.handle(.toolCancelled) },
                // Mantis는 자른 결과를 `crop()` 안에서 바로 `onCropped`로 전달한다.
                onApply: cropperController.crop
            ) {
                PhotoEditCropTools(
                    onFlip: {
                        if cropperController.flipHorizontally() {
                            viewModel.handle(.flipClicked)
                        }
                    },
                    onRotate: { viewModel.handle(.rotateClicked) }
                )
            }
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)
        }
    }

    /// 사진이 보이는 크기에 맞춘 Mantis. 회전해서 새로 시작하면 돌리기 전 모습에서 반시계 방향으로 돌아 들어온다.
    private func cropper(_ source: PhotoEditCropperSource, in availableSize: CGSize) -> some View {
        let size = PhotoEditCropper.size(for: source.baseImage.size, in: availableSize)
        let previousSize = PhotoEditCropper.size(
            for: CGSize(width: source.baseImage.size.height, height: source.baseImage.size.width),
            in: availableSize
        )
        // 90° 돌린 새 화면이 돌리기 전 화면과 같은 크기로 보이게 하는 배율.
        let rotationStartScale = min(previousSize.width / size.height, previousSize.height / size.width)

        return PhotoEditCropper(
            source: source,
            controller: cropperController,
            onCropped: { viewModel.handle(.cropCompleted($0)) }
        )
        .frame(width: size.width, height: size.height)
        // Mantis 마스크가 영역 밖으로 커져 다른 버튼을 덮지 않게 한다.
        .clipped()
        .id(source.id)
        .transition(
            source.animatesRotation
                ? .asymmetric(
                    insertion: .modifier(
                        active: PhotoEditRotationEffect(degrees: Metrics.rotationStartDegrees, scale: rotationStartScale),
                        identity: PhotoEditRotationEffect(degrees: 0, scale: 1)
                    ),
                    removal: .identity
                )
                : .identity
        )
    }

    private func preview(_ viewState: PhotoEditViewState) -> some View {
        Image(uiImage: viewState.displayedImage)
            .resizable()
            .scaledToFit()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.vertical, theme.spacing.lg)
            .accessibilityLabel("편집 중인 사진")
    }

    private var failureAlertPresented: Binding<Bool> {
        Binding(
            get: { viewModel.viewState.failureMessage != nil },
            set: { isPresented in
                if !isPresented {
                    viewModel.handle(.failureMessageDismissed)
                }
            }
        )
    }

    private func handle(_ event: PhotoEditEvent?) {
        guard let event else { return }
        viewModel.consumeEvent()

        switch event {
        case let .completed(result):
            onComplete(result)
        case .cancelled:
            onCancel()
        }
    }
}

private struct PhotoEditRotationEffect: ViewModifier {
    let degrees: Double
    let scale: CGFloat

    func body(content: Content) -> some View {
        content
            .rotationEffect(.degrees(degrees))
            .scaleEffect(scale)
    }
}

private enum Metrics {
    static let toolOptionsHeight: CGFloat = 96
    /// 반시계 방향으로 돌린 사진을 시계 방향으로 90° 돌려 두면 돌리기 전 모습과 같다.
    static let rotationStartDegrees: Double = 90
    static let rotationDuration: Double = 0.3
}

#Preview("Photo Edit") {
    PhotoEditScreen(
        session: PhotoEditSession(original: UIImage(named: "preview_photo") ?? UIImage()),
        onComplete: { _ in },
        onCancel: {}
    )
    .chalkakTheme(.light)
}
