import Mantis
import SwiftUI
import UIKit

/// Mantis 자르기 화면에 비율 목록과 취소·완료 버튼을 붙인다.
struct PhotoEditCropper: View {
    @Environment(\.chalkakTheme) private var theme

    let image: UIImage
    let initialState: PhotoEditCropperState
    let onCropped: (PhotoEditCrop) -> Void
    let onCancel: () -> Void

    @State private var controller = PhotoEditCropperController()
    @State private var aspectRatio: PhotoEditAspectRatio
    @State private var isCropping = false

    init(
        image: UIImage,
        initialState: PhotoEditCropperState,
        onCropped: @escaping (PhotoEditCrop) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.image = image
        self.initialState = initialState
        self.onCropped = onCropped
        self.onCancel = onCancel
        _aspectRatio = State(initialValue: initialState.aspectRatio)
    }

    var body: some View {
        VStack(spacing: 0) {
            PhotoEditTopBar(
                title: "자르기",
                isDoneEnabled: !isCropping,
                onCancel: onCancel,
                onDone: crop
            )
            .padding(.horizontal, theme.spacing.sm)

            PhotoEditCropperView(
                image: image,
                initialState: initialState,
                controller: controller,
                onCropped: { croppedImage, transformation in
                    isCropping = false
                    onCropped(
                        PhotoEditCrop(
                            image: croppedImage,
                            cropperState: PhotoEditCropperState(
                                aspectRatio: aspectRatio,
                                transformation: transformation
                            )
                        )
                    )
                },
                onFailed: {
                    isCropping = false
                },
                onReset: {
                    // 초기화하면 비율도 자유로 되돌린다.
                    aspectRatio = .free
                    controller.setAspectRatio(.free)
                }
            )
            // Mantis 마스크가 영역 밖으로 커져 상단바를 덮지 않게 한다.
            .clipped()

            ratioSelector
                .padding(.vertical, theme.spacing.md)
        }
        .background(theme.colors.editorBackground)
        .preferredColorScheme(.dark)
    }

    private var ratioSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: theme.spacing.xs) {
                ForEach(PhotoEditAspectRatio.allCases) { ratio in
                    ratioButton(ratio)
                }
            }
        }
        .contentMargins(.horizontal, theme.spacing.lg, for: .scrollContent)
    }

    private func ratioButton(_ ratio: PhotoEditAspectRatio) -> some View {
        let isSelected = ratio == aspectRatio

        return Button {
            aspectRatio = ratio
            controller.setAspectRatio(ratio)
        } label: {
            Text(ratio.title)
                .font(theme.typography.footnote)
                .foregroundStyle(isSelected ? theme.colors.editorBackground : theme.colors.textOnEditor)
                .padding(.horizontal, theme.spacing.md)
                .frame(minWidth: Metrics.minimumTouchSize, minHeight: Metrics.chipHeight)
                .background(isSelected ? theme.colors.textOnEditor : .clear, in: Capsule())
                .frame(minHeight: Metrics.minimumTouchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(isCropping)
        .accessibilityLabel(ratio == .free ? "자유 비율" : "\(ratio.title) 비율")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func crop() {
        guard !isCropping else { return }
        isCropping = true
        controller.crop()
    }
}

private enum Metrics {
    static let minimumTouchSize: CGFloat = 44
    static let chipHeight: CGFloat = 32
}

/// SwiftUI 버튼에서 Mantis 화면을 조작한다.
@MainActor
final class PhotoEditCropperController {
    fileprivate weak var cropViewController: CropViewController?
    fileprivate var imageSize: CGSize = .zero
    fileprivate var rotation: CGFloat = 0

    func setAspectRatio(_ ratio: PhotoEditAspectRatio) {
        guard let cropViewController else { return }
        if let value = ratio.widthToHeight(originalSize: Self.rotatedSize(imageSize, rotation: rotation)) {
            cropViewController.didSelectRatio(ratio: value)
        } else {
            cropViewController.didSelectFreeRatio()
        }
    }

    func crop() {
        cropViewController?.crop()
    }

    /// 90° 단위로 돌린 횟수가 홀수면 원본 비율의 가로·세로를 바꾼다.
    fileprivate static func rotatedSize(_ size: CGSize, rotation: CGFloat) -> CGSize {
        let quarterTurns = Int((rotation / (.pi / 2)).rounded())
        return quarterTurns.isMultiple(of: 2) ? size : CGSize(width: size.height, height: size.width)
    }
}

private struct PhotoEditCropperView: UIViewControllerRepresentable {
    let image: UIImage
    let initialState: PhotoEditCropperState
    let controller: PhotoEditCropperController
    let onCropped: (UIImage, Transformation) -> Void
    let onFailed: () -> Void
    let onReset: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIViewController(context: Context) -> CropViewController {
        var config = Mantis.Config()
        config.appearanceMode = .forceDark
        config.cropToolbarConfig.mode = .embedded
        config.cropToolbarConfig.toolbarButtonOptions = [.counterclockwiseRotate, .reset]

        let rotation = initialState.transformation?.rotation ?? 0
        if let transformation = initialState.transformation {
            config.cropViewConfig.presetTransformationType = .presetInfo(info: transformation)
        }
        let originalSize = PhotoEditCropperController.rotatedSize(image.size, rotation: rotation)
        if let ratio = initialState.aspectRatio.widthToHeight(originalSize: originalSize) {
            config.presetFixedRatioType = .canUseMultiplePresetFixedRatio(defaultRatio: ratio)
        }

        let cropViewController = Mantis.cropViewController(image: image, config: config)
        cropViewController.delegate = context.coordinator
        controller.cropViewController = cropViewController
        controller.imageSize = image.size
        controller.rotation = rotation
        return cropViewController
    }

    func updateUIViewController(_ uiViewController: CropViewController, context: Context) {
        context.coordinator.parent = self
    }

    final class Coordinator: CropViewControllerDelegate {
        var parent: PhotoEditCropperView
        private var isResettable = false

        init(parent: PhotoEditCropperView) {
            self.parent = parent
        }

        func cropViewControllerDidCrop(
            _ cropViewController: CropViewController,
            cropped: UIImage,
            transformation: Transformation,
            cropInfo: CropInfo
        ) {
            parent.onCropped(cropped, transformation)
        }

        func cropViewControllerDidFailToCrop(_ cropViewController: CropViewController, original: UIImage) {
            parent.onFailed()
        }

        func cropViewControllerDidCancel(_ cropViewController: CropViewController, original: UIImage) {}

        func cropViewControllerDidImageTransformed(
            _ cropViewController: CropViewController,
            transformation: Transformation
        ) {
            parent.controller.rotation = transformation.rotation
        }

        func cropViewController(_ cropViewController: CropViewController, didBecomeResettable resettable: Bool) {
            defer { isResettable = resettable }
            if isResettable, !resettable {
                parent.controller.rotation = 0
                parent.onReset()
            }
        }
    }
}
