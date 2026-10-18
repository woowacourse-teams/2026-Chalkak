import Photos
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct PhotoUploadRoute: View {
    @Environment(\.openURL) private var openURL
    @Bindable var viewModel: PhotoUploadViewModel

    let onBack: () -> Void
    let onSubmitted: (PhotoUploadSubmission) -> Void
    let onReauthenticationRequired: () -> Void

    @State private var isGalleryPresented = false
    @State private var pickedPhoto: NSItemProvider?
    @State private var isCameraPresented = false
    @State private var capturedImage: UIImage?
    @State private var shouldSaveCapturedImage = false
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
        .onDisappear {
            photoSelectionLoader.cancel()
        }
        .onChange(of: viewModel.viewState.completedSubmission?.id) { _, _ in
            guard let submission = viewModel.viewState.completedSubmission else { return }
            onSubmitted(submission)
            viewModel.reset()
        }
        // 선택 창이 닫히는 중에 편집 화면을 띄우면 편집 화면이 Safe Area를 받지 못해 상단·하단 버튼이 시스템 영역에 겹친다.
        // 카메라처럼 선택 창이 완전히 닫힌 뒤 `onDismiss`에서 편집을 시작한다.
        .sheet(isPresented: $isGalleryPresented, onDismiss: completeGallerySelection) {
            PhotoUploadGalleryPicker(
                onPhotoPicked: { photo in
                    pickedPhoto = photo
                    isGalleryPresented = false
                },
                onCancel: {
                    pickedPhoto = nil
                    isGalleryPresented = false
                }
            )
            .ignoresSafeArea()
        }
        .sheet(isPresented: $isCameraPresented, onDismiss: completeCameraCapture) {
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
        .fullScreenCover(item: editRequest) { request in
            PhotoEditScreen(
                session: request.session,
                onComplete: viewModel.completeEditing,
                onCancel: viewModel.cancelEditing
            )
        }
        .alert("촬영 사진 저장 권한", isPresented: $isPhotoPermissionAlertPresented) {
            Button("설정 열기") {
                guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                openURL(url)
            }
            Button("저장 없이 촬영") {
                shouldSaveCapturedImage = false
                isCameraPresented = true
            }
            Button("취소", role: .cancel) {}
        } message: {
            Text("촬영한 사진을 자동으로 저장하려면 설정에서 사진 추가 권한을 허용해 주세요.")
        }
    }

    private var editRequest: Binding<PhotoUploadEditRequest?> {
        Binding(
            get: { viewModel.viewState.editRequest },
            set: { request in
                if request == nil {
                    viewModel.cancelEditing()
                }
            }
        )
    }

    private func openCamera() {
        guard !isRequestingPhotoPermission else { return }
        isRequestingPhotoPermission = true
        capturedImage = nil
        Task { @MainActor in
            let status = await PhotoLibraryImageSaver.requestAddOnlyAuthorization()
            isRequestingPhotoPermission = false
            if status == .authorized || status == .limited {
                shouldSaveCapturedImage = true
                isCameraPresented = true
            } else {
                isPhotoPermissionAlertPresented = true
            }
        }
    }

    private func completeCameraCapture() {
        let shouldSave = shouldSaveCapturedImage
        shouldSaveCapturedImage = false
        guard let image = capturedImage else { return }
        capturedImage = nil
        if shouldSave {
            Task { @MainActor in
                await viewModel.saveCapturedImage(image)
            }
        }
        guard let data = image.jpegData(compressionQuality: 1) ?? image.pngData() else {
            viewModel.showImageSelectionFailure()
            return
        }
        viewModel.beginEditing(sourceData: data)
    }

    private func handle(_ event: PhotoUploadEvent?) {
        guard let event else { return }

        switch event {
        case .navigateBack:
            viewModel.reset()
            onBack()
        case .openGallery:
            photoSelectionLoader.cancel()
            pickedPhoto = nil
            isGalleryPresented = true
        case .openCamera:
            openCamera()
        case .reauthenticationRequired:
            viewModel.reset()
            onReauthenticationRequired()
        }
        viewModel.consumeEvent()
    }

    private func completeGallerySelection() {
        guard let photo = pickedPhoto else { return }
        pickedPhoto = nil

        photoSelectionLoader.start(
            load: { try await PhotoUploadGalleryPicker.loadImageData(from: photo) },
            onLoaded: viewModel.beginEditing(sourceData:),
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

/// 앨범에서 사진 한 장을 고른다. 사진 접근 권한 없이 쓸 수 있는 시스템 선택 창이다.
struct PhotoUploadGalleryPicker: UIViewControllerRepresentable {
    let onPhotoPicked: (NSItemProvider) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onPhotoPicked: onPhotoPicked, onCancel: onCancel)
    }

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var configuration = PHPickerConfiguration()
        configuration.filter = .images
        configuration.selectionLimit = 1
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    static func loadImageData(from photo: NSItemProvider) async throws -> Data? {
        try await withCheckedThrowingContinuation { continuation in
            _ = photo.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { data, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: data)
                }
            }
        }
    }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        private let onPhotoPicked: (NSItemProvider) -> Void
        private let onCancel: () -> Void

        init(onPhotoPicked: @escaping (NSItemProvider) -> Void, onCancel: @escaping () -> Void) {
            self.onPhotoPicked = onPhotoPicked
            self.onCancel = onCancel
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            if let photo = results.first?.itemProvider {
                onPhotoPicked(photo)
            } else {
                onCancel()
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
