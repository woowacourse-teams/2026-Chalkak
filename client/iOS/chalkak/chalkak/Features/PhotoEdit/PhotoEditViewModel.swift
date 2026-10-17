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
    /// 필터 화면에서 취소하면 되돌릴 필터.
    private var filterBeforeTool: PhotoEditFilter = .original
    private var renderGeneration = 0
    private var previewTask: Task<Void, Never>?
    private var thumbnailTask: Task<Void, Never>?
    private var cropperTask: Task<Void, Never>?
    /// 지금 방향으로 돌린 원본과 필터 이미지. 비율을 고르면 바로 Mantis를 새로 시작할 수 있도록 미리 만든다.
    private var orientedImages: OrientedImages?
    private var preparingOrientation: PhotoEditOrientation?
    /// 이미지가 준비되면 `cropDraft`로 Mantis를 새로 시작해야 한다.
    private var needsCropperSource = false
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
        case .filterApplied:
            guard viewState.activeTool == .filter else { return }
            viewState.activeTool = nil
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
            // 화면의 회전은 Mantis가 애니메이션으로 처리하고, 여기서는 방향만 기록해 둔다.
            guard viewState.activeTool == .crop else { return }
            viewState.cropDraft.orientation.rotateCounterclockwise()
            prepareOrientedImages(for: viewState.cropDraft.orientation)
        case .flipClicked:
            guard viewState.activeTool == .crop else { return }
            viewState.cropDraft.orientation.flipHorizontally()
            prepareOrientedImages(for: viewState.cropDraft.orientation)
        case var .cropCompleted(crop):
            guard viewState.activeTool == .crop else { return }
            // Mantis가 돌린 만큼은 `transformation`에 들어 있고, 화면의 최종 방향은 여기서 기록해 둔 값이다.
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
        case .filter:
            filterBeforeTool = viewState.session.filter
        case .crop:
            viewState.cropDraft = viewState.session.crop?.cropperState ?? PhotoEditCropperState()
            requestCropperSource()
        }
    }

    /// 도구 화면에서 바꾼 것만 버리고 편집 첫 화면으로 돌아간다.
    private func cancelTool() {
        switch viewState.activeTool {
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
        viewState.cropperSource = nil
        viewState.activeTool = nil
    }

    /// `cropDraft`로 Mantis를 새로 시작한다. 이미지가 아직 없으면 준비되는 대로 바꾸고, 그동안 지금 화면을 유지한다.
    private func requestCropperSource() {
        needsCropperSource = true
        prepareOrientedImages(for: viewState.cropDraft.imageOrientation)
        applyCropperSourceIfReady()
    }

    private func applyCropperSourceIfReady() {
        let draft = viewState.cropDraft
        guard needsCropperSource,
              let orientedImages,
              orientedImages.orientation == draft.imageOrientation
        else { return }
        needsCropperSource = false
        viewState.cropperSource = PhotoEditCropperSource(
            baseImage: orientedImages.base,
            displayImage: orientedImages.display,
            state: draft
        )
    }

    /// 방향을 적용한 원본과, 화면에 보일 필터 이미지를 만든다.
    private func prepareOrientedImages(for orientation: PhotoEditOrientation) {
        guard orientedImages?.orientation != orientation, preparingOrientation != orientation else { return }
        // 띄워야 할 Mantis 이미지를 만드는 중이면 미리 만들기보다 그쪽을 먼저 끝낸다.
        if needsCropperSource, preparingOrientation == viewState.cropDraft.imageOrientation { return }

        cropperTask?.cancel()
        let original = viewState.session.original
        let filter = viewState.session.filter
        if orientation.isIdentity, filter == .original {
            preparingOrientation = nil
            orientedImages = OrientedImages(orientation: orientation, base: original, display: original)
            return
        }

        preparingOrientation = orientation
        let renderer = renderer
        cropperTask = Task { [weak self] in
            let base = await renderer.orient(original, orientation.quarterTurns, orientation.isFlipped) ?? original
            let display = filter == .original ? base : await renderer.render(base, filter, nil) ?? base
            guard !Task.isCancelled, let self else { return }
            self.cropperTask = nil
            self.preparingOrientation = nil
            self.orientedImages = OrientedImages(orientation: orientation, base: base, display: display)
            self.applyCropperSourceIfReady()
            // 기다리는 동안 방향이 또 바뀌었으면 마지막 방향을 이어서 만든다.
            self.prepareOrientedImages(for: self.viewState.cropDraft.orientation)
        }
    }

    /// 자르기 결과가 바뀌면 미리보기와 필터 썸네일의 원본을 새로 만든다.
    private func prepareRenderSources() {
        previewTask?.cancel()
        thumbnailTask?.cancel()
        renderGeneration += 1
        let generation = renderGeneration
        let source = viewState.session.croppedImage
        let renderer = renderer
        previewSource = nil
        viewState.previewImage = nil
        viewState.thumbnails = [:]

        previewTask = Task { [weak self] in
            let previewSource = await renderer.render(source, .original, Constants.previewLongEdge)
            guard !Task.isCancelled, let self, generation == self.renderGeneration else { return }
            self.previewSource = previewSource ?? source
            self.renderPreview()
        }

        thumbnailTask = Task { [weak self] in
            guard let thumbnailSource = await renderer.render(source, .original, Constants.thumbnailLongEdge)
            else { return }
            var thumbnails: [PhotoEditFilter: UIImage] = [:]
            for filter in PhotoEditFilter.allCases {
                guard !Task.isCancelled else { return }
                thumbnails[filter] = await renderer.render(thumbnailSource, filter, nil)
            }
            guard !Task.isCancelled, let self, generation == self.renderGeneration else { return }
            self.viewState.thumbnails = thumbnails
        }
    }

    private func renderPreview() {
        // 미리보기 원본이 준비되면 그때 선택된 필터로 다시 호출된다.
        guard let previewSource else { return }
        previewTask?.cancel()
        let generation = renderGeneration
        let filter = viewState.session.filter
        let renderer = renderer

        previewTask = Task { [weak self] in
            let preview = await renderer.render(previewSource, filter, nil)
            guard !Task.isCancelled,
                  let self,
                  generation == self.renderGeneration,
                  filter == self.viewState.session.filter
            else { return }
            self.viewState.previewImage = preview
        }
    }

    /// 원본 크기 렌더링은 완료할 때 한 번만 한다.
    private func complete() {
        viewState.isCompleting = true
        let session = viewState.session
        let renderer = renderer

        completionTask = Task { [weak self] in
            let image = await renderer.render(session.croppedImage, session.filter, nil)
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
        previewTask?.cancel()
        previewTask = nil
        thumbnailTask?.cancel()
        thumbnailTask = nil
        cropperTask?.cancel()
        cropperTask = nil
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
