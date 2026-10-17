import Mantis
import SwiftUI
import UIKit

/// SwiftUI 버튼에서 Mantis의 자르기·회전·반전을 실행한다.
@MainActor
final class PhotoEditCropperController {
    fileprivate weak var cropViewController: CropViewController?
    /// Mantis는 회전 애니메이션 중에 들어온 회전을 무시하므로, 같은 동안 우리도 받지 않아 방향 기록이 어긋나지 않게 한다.
    private var animationEnd = ContinuousClock.now

    /// 자르기 결과는 `PhotoEditCropper`의 `onCropped`로 바로 전달된다.
    func crop() {
        cropViewController?.crop()
    }

    /// 실제로 돌렸으면 `true`를 돌려준다.
    func rotateCounterclockwise() -> Bool {
        guard let cropViewController, ContinuousClock.now >= animationEnd else { return false }
        cropViewController.didSelectCounterClockwiseRotate()
        animationEnd = .now + Animation.rotation
        return true
    }

    /// 실제로 뒤집었으면 `true`를 돌려준다.
    func flipHorizontally() -> Bool {
        guard let cropViewController, ContinuousClock.now >= animationEnd else { return false }
        cropViewController.didSelectHorizontallyFlip()
        animationEnd = .now + Animation.flip
        return true
    }

    /// Mantis 애니메이션 시간(회전 0.25초, 반전 0.5초)에 여유를 둔 값.
    private enum Animation {
        static let rotation: Duration = .milliseconds(300)
        static let flip: Duration = .milliseconds(550)
    }
}

/// 자르기 영역·회전·반전을 Mantis에 맡긴다. 비율 목록과 버튼은 편집 화면이 직접 그린다.
///
/// 비율을 고르면 `source.id`가 바뀌므로 `.id(source.id)`로 새로 만들어 전체 이미지에서 다시 시작한다.
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
