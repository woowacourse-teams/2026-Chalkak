import Mantis
import SwiftUI
import UIKit

/// SwiftUI 버튼에서 Mantis 자르기를 실행한다.
@MainActor
final class PhotoEditCropperController {
    fileprivate weak var cropViewController: CropViewController?

    /// 자르기 결과는 `PhotoEditCropper`의 `onCropped`로 바로 전달된다.
    func crop() {
        cropViewController?.crop()
    }
}

/// 자르기 영역만 Mantis에 맡긴다. 회전·반전·비율 목록은 편집 화면이 직접 그린다.
///
/// 비율이나 방향이 바뀌면 `source.id`가 바뀌므로 `.id(source.id)`로 새로 만들어 전체 이미지에서 다시 시작한다.
struct PhotoEditCropper: UIViewControllerRepresentable {
    let source: PhotoEditCropperSource
    let controller: PhotoEditCropperController
    let onCropped: (PhotoEditCrop) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIViewController(context: Context) -> CropViewController {
        var config = Mantis.Config()
        config.appearanceMode = .forceDark
        config.showAttachedCropToolbar = false
        config.cropViewConfig.showAttachedRotationControlView = false
        // 기본 흐림 효과는 빈 영역을 회색으로 칠하므로, 배경색을 반투명하게 덮어 자르기 밖 사진만 어둡게 한다.
        config.cropViewConfig.backgroundColor = UIColor(context.environment.chalkakTheme.colors.editorBackground)
            .withAlphaComponent(Metrics.outsideCropDimming)

        if let transformation = source.state.transformation {
            config.cropViewConfig.presetTransformationType = .presetInfo(info: transformation)
        }
        if let ratio = source.state.aspectRatio.widthToHeight(originalSize: source.baseImage.size) {
            config.presetFixedRatioType = .canUseMultiplePresetFixedRatio(defaultRatio: ratio)
        }

        let cropViewController = Mantis.cropViewController(image: source.displayImage, config: config)
        cropViewController.delegate = context.coordinator
        controller.cropViewController = cropViewController
        return cropViewController
    }

    func updateUIViewController(_ uiViewController: CropViewController, context: Context) {
        context.coordinator.parent = self
        controller.cropViewController = uiViewController
    }

    final class Coordinator: CropViewControllerDelegate {
        var parent: PhotoEditCropper

        init(parent: PhotoEditCropper) {
            self.parent = parent
        }

        func cropViewControllerDidCrop(
            _ cropViewController: CropViewController,
            cropped: UIImage,
            transformation: Transformation,
            cropInfo: CropInfo
        ) {
            let source = parent.source
            // 필터는 자른 뒤에 다시 입히므로 필터를 적용하기 전 이미지에서 같은 영역을 잘라 둔다.
            let image = source.displayImage === source.baseImage
                ? cropped
                : Mantis.crop(image: source.baseImage, by: cropInfo) ?? cropped
            var state = source.state
            state.transformation = transformation
            parent.onCropped(PhotoEditCrop(image: image, cropperState: state))
        }

        func cropViewControllerDidCancel(_ cropViewController: CropViewController, original: UIImage) {}
    }
}

private enum Metrics {
    static let outsideCropDimming: CGFloat = 0.6
}
