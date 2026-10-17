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
    private var renderGeneration = 0
    private var previewTask: Task<Void, Never>?
    private var thumbnailTask: Task<Void, Never>?
    private var completionTask: Task<Void, Never>?

    init(session: PhotoEditSession, renderer: PhotoEditRenderer = .live) {
        self.viewState = PhotoEditViewState(session: session)
        self.renderer = renderer
        prepareRenderSources()
    }

    func handle(_ action: PhotoEditAction) {
        guard !viewState.isCompleting else { return }

        switch action {
        case let .filterSelected(filter):
            guard filter != viewState.session.filter else { return }
            viewState.session.filter = filter
            renderPreview()
        case .cropClicked:
            viewState.isCropperPresented = true
        case let .cropCompleted(crop):
            viewState.isCropperPresented = false
            viewState.session.crop = crop
            prepareRenderSources()
        case .cropCancelled:
            viewState.isCropperPresented = false
        case .doneClicked:
            complete()
        case .cancelClicked:
            cancelRendering()
            event = .cancelled
        case .failureMessageDismissed:
            viewState.failureMessage = nil
        }
    }

    func consumeEvent() {
        event = nil
    }

    /// 자르기 결과가 바뀌면 미리보기와 필터 썸네일의 원본을 새로 만든다.
    private func prepareRenderSources() {
        cancelRendering()
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
    }

    private enum Constants {
        static let previewLongEdge: CGFloat = 1_600
        static let thumbnailLongEdge: CGFloat = 240
        static let renderFailureMessage = "사진을 편집하지 못했어요. 다시 시도해 주세요."
    }
}
