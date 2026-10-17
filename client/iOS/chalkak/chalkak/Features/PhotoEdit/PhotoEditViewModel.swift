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
            // 비율을 고를 때마다 이전 자르기 영역이 아닌 전체 이미지에서 새로 시작한다.
            viewState.cropDraft.transformation = nil
            prepareCropperSource()
        case .rotateClicked:
            guard viewState.activeTool == .crop else { return }
            viewState.cropDraft.rotateCounterclockwise()
            prepareCropperSource()
        case .flipClicked:
            guard viewState.activeTool == .crop else { return }
            viewState.cropDraft.flipHorizontally()
            prepareCropperSource()
        case let .cropCompleted(crop):
            guard viewState.activeTool == .crop else { return }
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
            prepareCropperSource()
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
        viewState.cropperSource = nil
        viewState.activeTool = nil
    }

    /// 회전·반전을 원본에 적용하고, 화면에 보일 이미지에는 선택한 필터를 입힌다.
    private func prepareCropperSource() {
        cropperTask?.cancel()
        let draft = viewState.cropDraft

        if let source = viewState.cropperSource,
           source.state.quarterTurns == draft.quarterTurns,
           source.state.isFlipped == draft.isFlipped {
            viewState.cropperSource = PhotoEditCropperSource(
                baseImage: source.baseImage,
                displayImage: source.displayImage,
                state: draft
            )
            return
        }

        viewState.cropperSource = nil
        let original = viewState.session.original
        let filter = viewState.session.filter
        let renderer = renderer
        cropperTask = Task { [weak self] in
            let baseImage = await renderer.orient(original, draft.quarterTurns, draft.isFlipped) ?? original
            let displayImage = filter == .original
                ? baseImage
                : await renderer.render(baseImage, filter, nil) ?? baseImage
            guard !Task.isCancelled, let self, self.viewState.cropDraft == draft else { return }
            self.cropperTask = nil
            self.viewState.cropperSource = PhotoEditCropperSource(
                baseImage: baseImage,
                displayImage: displayImage,
                state: draft
            )
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

    private enum Constants {
        static let previewLongEdge: CGFloat = 1_600
        static let thumbnailLongEdge: CGFloat = 240
        static let renderFailureMessage = "사진을 편집하지 못했어요. 다시 시도해 주세요."
    }
}
