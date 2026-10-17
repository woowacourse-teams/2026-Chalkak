import SwiftUI
import UIKit

struct PhotoEditScreen: View {
    @Environment(\.chalkakTheme) private var theme

    @State private var viewModel: PhotoEditViewModel

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
                title: "편집",
                isDoneEnabled: !viewState.isCompleting,
                onCancel: { viewModel.handle(.cancelClicked) },
                onDone: { viewModel.handle(.doneClicked) }
            )
            .padding(.horizontal, theme.spacing.sm)

            Image(uiImage: viewState.displayedImage)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(theme.spacing.lg)
                .accessibilityLabel("편집 중인 사진")

            Button {
                viewModel.handle(.cropClicked)
            } label: {
                Label("자르기·회전", systemImage: "crop.rotate")
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.textOnEditor)
                    .padding(.horizontal, theme.spacing.lg)
                    .frame(minHeight: Metrics.minimumTouchSize)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(viewState.isCompleting)
            .accessibilityIdentifier("photoEdit.crop")

            PhotoEditFilterStrip(
                selectedFilter: viewState.selectedFilter,
                thumbnails: viewState.thumbnails,
                onSelect: { viewModel.handle(.filterSelected($0)) }
            )
            .contentMargins(.horizontal, theme.spacing.lg, for: .scrollContent)
            .padding(.top, theme.spacing.md)
            .padding(.bottom, theme.spacing.lg)
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
        .fullScreenCover(isPresented: cropperPresented) {
            PhotoEditCropper(
                image: viewState.session.original,
                initialState: viewState.session.crop?.cropperState ?? PhotoEditCropperState(),
                onCropped: { viewModel.handle(.cropCompleted($0)) },
                onCancel: { viewModel.handle(.cropCancelled) }
            )
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

    private var cropperPresented: Binding<Bool> {
        Binding(
            get: { viewModel.viewState.isCropperPresented },
            set: { isPresented in
                if !isPresented {
                    viewModel.handle(.cropCancelled)
                }
            }
        )
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
    static let minimumTouchSize: CGFloat = 44
}

#Preview("Photo Edit") {
    PhotoEditScreen(
        session: PhotoEditSession(original: UIImage(named: "preview_photo") ?? UIImage()),
        onComplete: { _ in },
        onCancel: {}
    )
    .chalkakTheme(.light)
}
