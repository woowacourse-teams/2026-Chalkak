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
        guard let item else { return }

        Task { @MainActor in
            do {
                guard let data = try await item.loadTransferable(type: Data.self),
                      let image = UIImage(data: data)
                else {
                    viewModel.showImageSelectionFailure()
                    return
                }
                viewModel.selectImage(data: data, preview: image)
            } catch is CancellationError {
                return
            } catch {
                viewModel.showImageSelectionFailure()
            }
        }
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
