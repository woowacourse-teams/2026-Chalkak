import Foundation
import Observation
import UIKit

@MainActor
@Observable
final class PhotoEditViewModel {
    private(set) var viewState: PhotoEditViewState
    private(set) var event: PhotoEditEvent?

    private let renderer: PhotoEditRenderer

    /// 필터를 바꿀 때마다 큰 이미지를 다시 줄이지 않도록 미리보기 크기 이미지를 보관한다.
    private var previewSource: UIImage?
    /// 도구 화면에서 취소하면 되돌릴 필터와 조정.
    private var filterBeforeTool: PhotoEditFilter = .original
    private var adjustmentsBeforeTool = AdjustmentSnapshot(autoIntensity: 0, manual: PhotoEditAdjustments())
    private var renderGeneration = 0
    private var sourceTask: Task<Void, Never>?
    private var previewTask: Task<Void, Never>?
    /// 미리보기를 그리는 동안 편집이 또 바뀌어서, 끝나면 다시 그려야 한다.
    private var needsPreviewRender = false
    private var thumbnailTask: Task<Void, Never>?
    private var cropperTask: Task<Void, Never>?
    /// 방향을 적용한 원본과 필터 이미지. 반전 뒤 비율을 고르거나 다음 회전을 누르면 바로 Mantis를 새로 시작할 수 있도록 미리 만든다.
    private var orientedImages: OrientedImages?
    private var preparingOrientation: PhotoEditOrientation?
    /// 이미지가 준비되면 `cropDraft`로 Mantis를 새로 시작해야 한다.
    private var needsCropperSource = false
    /// 새로 시작할 Mantis를 회전 애니메이션과 함께 보여 준다.
    private var pendingSourceAnimatesRotation = false
    private var completionTask: Task<Void, Never>?

    init(session: PhotoEditSession, renderer: PhotoEditRenderer = .live) {
        self.viewState = PhotoEditViewState(session: session)
        self.renderer = renderer
        prepareRenderSources()
    }

    func handle(_ action: PhotoEditAction) {
        guard !viewState.isCompleting else { return }

        switch action {
        case let .toolSelected(tool):
            openTool(tool)
        case .toolCancelled:
            cancelTool()
        case .toolApplied:
            applyTool()
        case let .adjustItemSelected(item):
            guard viewState.activeTool == .adjust else { return }
            selectAdjustItem(item)
        case let .adjustValueChanged(value):
            guard viewState.activeTool == .adjust else { return }
            setValue(value, of: viewState.selectedAdjustItem)
        case let .filterSelected(filter):
            guard viewState.activeTool == .filter, filter != viewState.session.filter else { return }
            viewState.session.filter = filter
            renderPreview()
        case let .aspectRatioSelected(aspectRatio):
            guard viewState.activeTool == .crop else { return }
            viewState.cropDraft.aspectRatio = aspectRatio
            // Mantis는 확대된 영역 기준으로 비율을 다시 잡으므로, 지금 방향으로 돌린 원본에서 새로 시작한다.
            viewState.cropDraft.imageOrientation = viewState.cropDraft.orientation
            viewState.cropDraft.transformation = nil
            requestCropperSource()
        case .rotateClicked:
            // 돌리면 사진 모양이 바뀌어 자르기 화면 크기도 바뀌므로, 돌린 원본으로 Mantis를 새로 시작한다.
            // 사진 앱처럼 자르기 비율도 함께 돌린다.
            guard viewState.activeTool == .crop else { return }
            viewState.cropDraft.orientation.rotateCounterclockwise()
            viewState.cropDraft.aspectRatio = viewState.cropDraft.aspectRatio.rotated
            viewState.cropDraft.imageOrientation = viewState.cropDraft.orientation
            viewState.cropDraft.transformation = nil
            requestCropperSource(animatesRotation: true)
        case .flipClicked:
            // 반전은 사진 모양이 그대로라 Mantis가 애니메이션으로 처리하고, 여기서는 방향만 기록해 둔다.
            guard viewState.activeTool == .crop else { return }
            viewState.cropDraft.orientation.flipHorizontally()
            prepareOrientedImages(for: viewState.cropDraft.orientation)
        case var .cropCompleted(crop):
            guard viewState.activeTool == .crop else { return }
            // Mantis가 반전한 만큼은 `transformation`에 들어 있고, 화면의 최종 방향은 여기서 기록해 둔 값이다.
            crop.cropperState.orientation = viewState.cropDraft.orientation
            closeCropper()
            viewState.session.crop = crop
            prepareRenderSources()
        case .doneClicked:
            guard viewState.activeTool == nil else { return }
            complete()
        case .cancelClicked:
            guard viewState.activeTool == nil else { return }
            cancelRendering()
            event = .cancelled
        case .failureMessageDismissed:
            viewState.failureMessage = nil
        }
    }

    func consumeEvent() {
        event = nil
    }

    private func openTool(_ tool: PhotoEditTool) {
        guard viewState.activeTool == nil else { return }
        viewState.activeTool = tool

        switch tool {
        case .adjust:
            adjustmentsBeforeTool = adjustmentSnapshot
        case .filter:
            filterBeforeTool = viewState.session.filter
        case .crop:
            viewState.cropDraft = viewState.session.crop?.cropperState ?? PhotoEditCropperState()
            requestCropperSource()
        }
    }

    /// 크롭은 Mantis가 자른 결과를 `cropCompleted`로 따로 보낸다.
    private func applyTool() {
        switch viewState.activeTool {
        case .adjust:
            viewState.activeTool = nil
            // 필터 썸네일은 조정 위에 필터를 입힌 모습이므로 조정이 바뀌었으면 다시 만든다.
            if adjustmentSnapshot != adjustmentsBeforeTool {
                renderThumbnails()
            }
        case .filter:
            viewState.activeTool = nil
        case .crop, nil:
            return
        }
    }

    private var adjustmentSnapshot: AdjustmentSnapshot {
        AdjustmentSnapshot(
            autoIntensity: viewState.session.autoAdjustmentIntensity,
            manual: viewState.session.manualAdjustments
        )
    }

    /// 자동 보정은 처음 고를 때 켜고, 고른 상태에서 다시 누르면 껐다 켠다.
    private func selectAdjustItem(_ item: PhotoEditAdjustItem) {
        let wasSelected = viewState.selectedAdjustItem == item
        viewState.selectedAdjustItem = item
        guard item == .auto else { return }
        if wasSelected {
            setValue(viewState.autoAdjustmentIntensity > 0 ? 0 : 1, of: .auto)
        } else if viewState.autoAdjustmentIntensity == 0 {
            setValue(1, of: .auto)
        }
    }

    /// 항목 값을 바꾸면 자동 보정이 더한 몫은 그대로 두고 그 위에 더한 값만 바꾼다.
    private func setValue(_ value: Double, of item: PhotoEditAdjustItem) {
        let value = min(max(value, item.range.lowerBound), item.range.upperBound)
        guard value != viewState.value(of: item) else { return }
        switch item {
        case .auto:
            viewState.session.autoAdjustmentIntensity = value
        case let .adjustment(adjustment):
            viewState.session.manualAdjustments[adjustment] = value - (viewState.automaticValue(of: item) ?? 0)
        }
        renderPreview()
    }

    /// 도구 화면에서 바꾼 것만 버리고 편집 첫 화면으로 돌아간다.
    private func cancelTool() {
        switch viewState.activeTool {
        case .adjust:
            viewState.activeTool = nil
            guard adjustmentSnapshot != adjustmentsBeforeTool else { return }
            viewState.session.autoAdjustmentIntensity = adjustmentsBeforeTool.autoIntensity
            viewState.session.manualAdjustments = adjustmentsBeforeTool.manual
            renderPreview()
        case .filter:
            viewState.activeTool = nil
            guard viewState.session.filter != filterBeforeTool else { return }
            viewState.session.filter = filterBeforeTool
            renderPreview()
        case .crop:
            closeCropper()
        case nil:
            return
        }
    }

    private func closeCropper() {
        cropperTask?.cancel()
        cropperTask = nil
        orientedImages = nil
        preparingOrientation = nil
        needsCropperSource = false
        pendingSourceAnimatesRotation = false
        viewState.cropperSource = nil
        viewState.activeTool = nil
    }

    /// `cropDraft`로 Mantis를 새로 시작한다. 이미지가 아직 없으면 준비되는 대로 바꾸고, 그동안 지금 화면을 유지한다.
    private func requestCropperSource(animatesRotation: Bool = false) {
        needsCropperSource = true
        pendingSourceAnimatesRotation = animatesRotation
        prepareOrientedImages(for: viewState.cropDraft.imageOrientation)
        applyCropperSourceIfReady()
    }

    private func applyCropperSourceIfReady() {
        let draft = viewState.cropDraft
        guard needsCropperSource, let images = images(for: draft.imageOrientation) else { return }
        needsCropperSource = false
        viewState.cropperSource = PhotoEditCropperSource(
            baseImage: images.base,
            displayImage: images.display,
            state: draft,
            animatesRotation: pendingSourceAnimatesRotation
        )
        pendingSourceAnimatesRotation = false
        // 다음 회전을 누르면 바로 돌아가도록 한 번 더 돌린 원본을 미리 만든다.
        var nextOrientation = draft.orientation
        nextOrientation.rotateCounterclockwise()
        prepareOrientedImages(for: nextOrientation)
    }

    /// 지금 Mantis가 쓰는 이미지도 같은 방향이면 다시 만들지 않고 쓴다.
    private func images(for orientation: PhotoEditOrientation) -> OrientedImages? {
        if let orientedImages, orientedImages.orientation == orientation {
            return orientedImages
        }
        if let source = viewState.cropperSource, source.state.imageOrientation == orientation {
            return OrientedImages(orientation: orientation, base: source.baseImage, display: source.displayImage)
        }
        return nil
    }

    /// 방향을 적용한 원본과, 화면에 보일 필터 이미지를 만든다.
    private func prepareOrientedImages(for orientation: PhotoEditOrientation) {
        guard images(for: orientation) == nil, preparingOrientation != orientation else { return }
        // 띄워야 할 Mantis 이미지를 만드는 중이면 미리 만들기보다 그쪽을 먼저 끝낸다.
        if needsCropperSource, preparingOrientation == viewState.cropDraft.imageOrientation { return }

        cropperTask?.cancel()
        let original = viewState.session.original
        let look = currentLook
        if orientation.isIdentity, look.isOriginal {
            preparingOrientation = nil
            orientedImages = OrientedImages(orientation: orientation, base: original, display: original)
            return
        }

        preparingOrientation = orientation
        let renderer = renderer
        cropperTask = Task { [weak self] in
            let base = await renderer.orient(original, orientation.quarterTurns, orientation.isFlipped) ?? original
            let display = look.isOriginal ? base : await renderer.render(base, look, nil) ?? base
            guard !Task.isCancelled, let self else { return }
            self.cropperTask = nil
            self.preparingOrientation = nil
            self.orientedImages = OrientedImages(orientation: orientation, base: base, display: display)
            self.applyCropperSourceIfReady()
            // 기다리는 동안 Mantis에 띄울 방향이 또 바뀌었으면 그 방향을 이어서 만든다.
            if self.needsCropperSource {
                self.prepareOrientedImages(for: self.viewState.cropDraft.imageOrientation)
            }
        }
    }

    private var currentLook: PhotoEditLook {
        PhotoEditLook(filter: viewState.session.filter, adjustments: viewState.adjustments)
    }

    /// 자르기 결과가 바뀌면 미리보기 원본과 자동 보정값을 새로 구하고 미리보기와 필터 썸네일을 다시 만든다.
    private func prepareRenderSources() {
        sourceTask?.cancel()
        previewTask?.cancel()
        previewTask = nil
        needsPreviewRender = false
        thumbnailTask?.cancel()
        renderGeneration += 1
        let generation = renderGeneration
        let source = viewState.session.croppedImage
        let renderer = renderer
        previewSource = nil
        viewState.autoAdjustments = nil
        viewState.previewImage = nil
        viewState.thumbnails = [:]

        sourceTask = Task { [weak self] in
            let previewSource = await renderer.render(source, .original, Constants.previewLongEdge) ?? source
            let autoAdjustments = await renderer.analyze(previewSource)
            guard !Task.isCancelled, let self, generation == self.renderGeneration else { return }
            self.sourceTask = nil
            self.previewSource = previewSource
            self.viewState.autoAdjustments = autoAdjustments
            self.renderPreview()
            self.renderThumbnails()
        }
    }

    private func renderThumbnails() {
        guard let previewSource else { return }
        thumbnailTask?.cancel()
        let generation = renderGeneration
        let look = currentLook
        let renderer = renderer

        thumbnailTask = Task { [weak self] in
            guard let thumbnailSource = await renderer.render(previewSource, .original, Constants.thumbnailLongEdge)
            else { return }
            var thumbnails: [PhotoEditFilter: UIImage] = [:]
            for filter in PhotoEditFilter.allCases {
                guard !Task.isCancelled else { return }
                var filterLook = look
                filterLook.filter = filter
                thumbnails[filter] = await renderer.render(thumbnailSource, filterLook, nil)
            }
            guard !Task.isCancelled, let self, generation == self.renderGeneration else { return }
            self.thumbnailTask = nil
            self.viewState.thumbnails = thumbnails
        }
    }

    /// 슬라이더를 움직이는 동안 렌더링이 쌓이지 않도록, 그리는 중이면 끝난 뒤 마지막 편집으로 한 번 더 그린다.
    private func renderPreview() {
        // 미리보기 원본이 준비되면 그때의 편집으로 다시 호출된다.
        guard let previewSource else { return }
        guard previewTask == nil else {
            needsPreviewRender = true
            return
        }
        needsPreviewRender = false
        let generation = renderGeneration
        let look = currentLook
        let renderer = renderer

        previewTask = Task { [weak self] in
            let preview = await renderer.render(previewSource, look, nil)
            guard !Task.isCancelled, let self, generation == self.renderGeneration else { return }
            self.previewTask = nil
            self.viewState.previewImage = preview
            if self.needsPreviewRender {
                self.renderPreview()
            }
        }
    }

    /// 원본 크기 렌더링은 완료할 때 한 번만 한다.
    private func complete() {
        viewState.isCompleting = true
        let session = viewState.session
        let needsAnalysis = viewState.autoAdjustments == nil && session.autoAdjustmentIntensity > 0
        var look = currentLook
        let renderer = renderer

        completionTask = Task { [weak self] in
            // 미리보기를 준비하기 전에 완료하면 자동 보정값을 여기서 구한다.
            if needsAnalysis, let autoAdjustments = await renderer.analyze(session.croppedImage) {
                look.adjustments = autoAdjustments.scaled(
                    by: session.autoAdjustmentIntensity,
                    adding: session.manualAdjustments
                )
            }
            let image = await renderer.render(session.croppedImage, look, nil)
            guard !Task.isCancelled, let self else { return }
            self.completionTask = nil
            self.viewState.isCompleting = false
            guard let image else {
                self.viewState.failureMessage = Constants.renderFailureMessage
                return
            }
            self.cancelRendering()
            self.event = .completed(PhotoEditResult(image: image, session: session))
        }
    }

    private func cancelRendering() {
        sourceTask?.cancel()
        sourceTask = nil
        previewTask?.cancel()
        previewTask = nil
        needsPreviewRender = false
        thumbnailTask?.cancel()
        thumbnailTask = nil
        cropperTask?.cancel()
        cropperTask = nil
    }

    private struct AdjustmentSnapshot: Equatable {
        let autoIntensity: Double
        let manual: PhotoEditAdjustments
    }

    private struct OrientedImages {
        let orientation: PhotoEditOrientation
        let base: UIImage
        let display: UIImage
    }

    private enum Constants {
        static let previewLongEdge: CGFloat = 1_600
        static let thumbnailLongEdge: CGFloat = 240
        static let renderFailureMessage = "사진을 편집하지 못했어요. 다시 시도해 주세요."
    }
}
