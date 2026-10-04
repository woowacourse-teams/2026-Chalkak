import Photos
import PhotosUI
import SwiftUI
import UIKit

struct PhotoUploadRoute: View {
    @Environment(\.openURL) private var openURL
    @Bindable var viewModel: PhotoUploadViewModel

    let onBack: () -> Void
    let onSubmitted: (PhotoUploadSubmission) -> Void
    let onReauthenticationRequired: () -> Void

    @State private var isGalleryPresented = false
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var isCameraPresented = false
    @State private var capturedImage: UIImage?
    @State private var isPhotoPermissionAlertPresented = false
    @State private var isRequestingPhotoPermission = false
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
        .sheet(isPresented: $isCameraPresented, onDismiss: saveCapturedImage) {
            PhotoUploadCameraPicker(
                onImagePicked: { image in
                    capturedImage = image
                    isCameraPresented = false
                },
                onCancel: {
                    capturedImage = nil
                    isCameraPresented = false
                }
            )
            .ignoresSafeArea()
        }
        .alert("촬영 사진 저장 권한", isPresented: $isPhotoPermissionAlertPresented) {
            Button("설정 열기") {
                guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                openURL(url)
            }
            Button("저장 없이 촬영") {
                isCameraPresented = true
            }
            Button("취소", role: .cancel) {}
        } message: {
            Text("촬영한 사진을 자동으로 저장하려면 설정에서 사진 추가 권한을 허용해 주세요.")
        }
    }

    private func openCamera() {
        guard !isRequestingPhotoPermission else { return }
        isRequestingPhotoPermission = true
        capturedImage = nil
        Task { @MainActor in
            let status = await PhotoLibraryImageSaver.requestAddOnlyAuthorization()
            isRequestingPhotoPermission = false
            if status == .authorized || status == .limited {
                isCameraPresented = true
            } else {
                isPhotoPermissionAlertPresented = true
            }
        }
    }

    private func saveCapturedImage() {
        guard let image = capturedImage else { return }
        capturedImage = nil
        guard let data = image.jpegData(compressionQuality: 1) ?? image.pngData() else {
            viewModel.showImageSelectionFailure()
            return
        }
        Task { @MainActor in
            await viewModel.selectCapturedImage(data: data, preview: image)
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
            openCamera()
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
            onCancel()
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage {
                onImagePicked(image)
            } else {
                onCancel()
            }
        }
    }
}
