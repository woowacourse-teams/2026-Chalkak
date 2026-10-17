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

        VStack(spacing: 0) {
            PhotoEditTopBar(
                isDoneEnabled: !viewState.isCompleting,
                onCancel: { viewModel.handle(.cancelClicked) },
                onDone: done
            ) {
                if viewState.mode == .crop {
                    PhotoEditCropTools(
                        onFlip: { viewModel.handle(.flipClicked) },
                        onRotate: { viewModel.handle(.rotateClicked) }
                    )
                }
            }
            .padding(.horizontal, theme.spacing.lg)
            .padding(.top, theme.spacing.sm)

            content(viewState)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            tools(viewState)
                .frame(height: Metrics.toolsHeight)
                .disabled(viewState.isCompleting)

            PhotoEditModeBar(selectedMode: viewState.mode, onSelect: selectMode)
                .padding(.top, theme.spacing.md)
                .padding(.bottom, theme.spacing.sm)
                .disabled(viewState.isCompleting)
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

    @ViewBuilder
    private func content(_ viewState: PhotoEditViewState) -> some View {
        switch viewState.mode {
        case .filter:
            Image(uiImage: viewState.displayedImage)
                .resizable()
                .scaledToFit()
                .padding(theme.spacing.lg)
                .accessibilityLabel("편집 중인 사진")
        case .crop:
            if let source = viewState.cropperSource {
                PhotoEditCropper(
                    source: source,
                    controller: cropperController,
                    onCropped: { viewModel.handle(.cropCompleted($0)) }
                )
                .id(source.id)
                // Mantis 마스크가 영역 밖으로 커져 상단바를 덮지 않게 한다.
                .clipped()
            } else {
                ProgressView()
                    .tint(theme.colors.textOnEditor)
            }
        }
    }

    @ViewBuilder
    private func tools(_ viewState: PhotoEditViewState) -> some View {
        switch viewState.mode {
        case .filter:
            PhotoEditFilterStrip(
                selectedFilter: viewState.selectedFilter,
                thumbnails: viewState.thumbnails,
                onSelect: { viewModel.handle(.filterSelected($0)) }
            )
            .contentMargins(.horizontal, theme.spacing.lg, for: .scrollContent)
        case .crop:
            PhotoEditAspectRatioStrip(
                selectedAspectRatio: viewState.cropDraft.aspectRatio,
                originalSize: viewState.cropDraft.orientedSize(of: viewState.session.original.size),
                onSelect: { viewModel.handle(.aspectRatioSelected($0)) }
            )
            .contentMargins(.horizontal, theme.spacing.lg, for: .scrollContent)
        }
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

    /// 자르기 탭을 떠나거나 완료하면 Mantis의 현재 자르기 영역을 확정한다.
    /// Mantis는 자른 결과를 `crop()` 안에서 바로 전달하므로 다음 동작보다 먼저 반영된다.
    private func commitCropIfNeeded() {
        guard viewModel.viewState.mode == .crop, viewModel.viewState.cropperSource != nil else { return }
        cropperController.crop()
    }

    private func selectMode(_ mode: PhotoEditMode) {
        guard mode != viewModel.viewState.mode else { return }
        commitCropIfNeeded()
        viewModel.handle(.modeSelected(mode))
    }

    private func done() {
        commitCropIfNeeded()
        viewModel.handle(.doneClicked)
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
    static let toolsHeight: CGFloat = 96
}

#Preview("Photo Edit") {
    PhotoEditScreen(
        session: PhotoEditSession(original: UIImage(named: "preview_photo") ?? UIImage()),
        onComplete: { _ in },
        onCancel: {}
    )
    .chalkakTheme(.light)
}
