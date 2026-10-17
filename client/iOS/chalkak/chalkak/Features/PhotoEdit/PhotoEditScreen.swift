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
                onApply: { viewModel.handle(.filterApplied) }
            )
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)
        }
    }

    private func cropTool(_ viewState: PhotoEditViewState) -> some View {
        VStack(spacing: 0) {
            Group {
                if let source = viewState.cropperSource {
                    PhotoEditCropper(
                        source: source,
                        controller: cropperController,
                        onCropped: { viewModel.handle(.cropCompleted($0)) }
                    )
                    .id(source.id)
                    // Mantis 마스크가 영역 밖으로 커져 다른 버튼을 덮지 않게 한다.
                    .clipped()
                } else {
                    ProgressView()
                        .tint(theme.colors.textOnEditor)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

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
                    onRotate: {
                        if cropperController.rotateCounterclockwise() {
                            viewModel.handle(.rotateClicked)
                        }
                    }
                )
            }
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)
        }
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

private enum Metrics {
    static let toolOptionsHeight: CGFloat = 96
}

#Preview("Photo Edit") {
    PhotoEditScreen(
        session: PhotoEditSession(original: UIImage(named: "preview_photo") ?? UIImage()),
        onComplete: { _ in },
        onCancel: {}
    )
    .chalkakTheme(.light)
}
