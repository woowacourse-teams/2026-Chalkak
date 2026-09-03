import PhotosUI
import SwiftUI
import UIKit

struct PhotoUploadRoute: View {
    @Bindable var viewModel: PhotoUploadViewModel

    let onBack: () -> Void
    let onSubmitted: (PhotoUploadSubmission) -> Void
    let onReauthenticationRequired: () -> Void

    @State private var isGalleryPresented = false
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var isCameraPresented = false
    @State private var photoSelectionLoader = PhotoUploadSelectionLoader()

    var body: some View {
        PhotoUploadScreen(
            viewState: viewModel.viewState,
            isCameraAvailable: UIImagePickerController.isSourceTypeAvailable(.camera),
            onAction: viewModel.handle,
            onRetryTopicLoad: viewModel.retryTopicLoad
        )
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
        .onChange(of: selectedPhotoItem) { _, item in
            loadSelectedPhoto(item)
        }
        .onDisappear {
            photoSelectionLoader.cancel()
        }
        .onChange(of: viewModel.viewState.completedSubmission?.id) { _, _ in
            guard let submission = viewModel.viewState.completedSubmission else { return }
            onSubmitted(submission)
            viewModel.reset()
        }
        .photosPicker(
            isPresented: $isGalleryPresented,
            selection: $selectedPhotoItem,
            matching: .images
        )
        .sheet(isPresented: $isCameraPresented) {
            PhotoUploadCameraPicker(
                onImagePicked: { image in
                    isCameraPresented = false
                    guard let data = image.jpegData(compressionQuality: 1) ?? image.pngData() else {
                        viewModel.showImageSelectionFailure()
                        return
                    }
                    viewModel.selectImage(data: data, preview: image)
                },
                onCancel: {
                    isCameraPresented = false
                }
            )
            .ignoresSafeArea()
        }
    }

    private func handle(_ event: PhotoUploadEvent?) {
        guard let event else { return }

        switch event {
        case .navigateBack:
            viewModel.reset()
            onBack()
        case .openGallery:
            photoSelectionLoader.cancel()
            selectedPhotoItem = nil
            isGalleryPresented = true
        case .openCamera:
            isCameraPresented = true
        case .reauthenticationRequired:
            viewModel.reset()
            onReauthenticationRequired()
        }
        viewModel.consumeEvent()
    }

    private func loadSelectedPhoto(_ item: PhotosPickerItem?) {
        guard let item else {
            photoSelectionLoader.cancel()
            return
        }

        photoSelectionLoader.start(
            load: { try await item.loadTransferable(type: Data.self) },
            onLoaded: { data in
                guard let image = UIImage(data: data) else {
                    viewModel.showImageSelectionFailure()
                    return
                }
                viewModel.selectImage(data: data, preview: image)
            },
            onFailure: viewModel.showImageSelectionFailure
        )
    }
}

@MainActor
final class PhotoUploadSelectionLoader {
    typealias DataLoader = @MainActor () async throws -> Data?

    private var generation = 0
    private var task: Task<Void, Never>?

    func start(
        load: @escaping DataLoader,
        onLoaded: @escaping (Data) -> Void,
        onFailure: @escaping () -> Void
    ) {
        cancel()
        generation += 1
        let currentGeneration = generation

        task = Task { @MainActor [weak self] in
            defer {
                if let self, self.generation == currentGeneration {
                    self.task = nil
                }
            }

            do {
                guard let data = try await load() else {
                    guard let self, self.generation == currentGeneration else { return }
                    onFailure()
                    return
                }
                guard let self,
                      self.generation == currentGeneration,
                      !Task.isCancelled
                else { return }
                onLoaded(data)
            } catch is CancellationError {
                return
            } catch {
                guard let self,
                      self.generation == currentGeneration,
                      !Task.isCancelled
                else { return }
                onFailure()
            }
        }
    }

    func cancel() {
        generation += 1
        task?.cancel()
        task = nil
    }
}

struct PhotoUploadCameraPicker: UIViewControllerRepresentable {
    let onImagePicked: (UIImage) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onImagePicked: onImagePicked, onCancel: onCancel)
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.mediaTypes = ["public.image"]
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        picker.modalPresentationStyle = .fullScreen
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        private let onImagePicked: (UIImage) -> Void
        private let onCancel: () -> Void

        init(onImagePicked: @escaping (UIImage) -> Void, onCancel: @escaping () -> Void) {
            self.onImagePicked = onImagePicked
            self.onCancel = onCancel
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true, completion: onCancel)
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            let image = info[.originalImage] as? UIImage
            picker.dismiss(animated: true) { [onImagePicked] in
                if let image {
                    onImagePicked(image)
                }
            }
        }
    }
}
