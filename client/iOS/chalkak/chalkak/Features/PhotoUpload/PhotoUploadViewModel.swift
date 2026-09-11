import Foundation
import Observation
import UIKit

struct PhotoUploadRepository: Sendable {
    typealias TopicHandler = @Sendable (Date) async -> Result<PhotoUploadTopic, PhotoUploadFailure>
    typealias ImagePreparationHandler = @Sendable (Data) async -> Result<PhotoUploadPreparation, PhotoUploadFailure>
    typealias PostCreationHandler = @Sendable (
        PhotoUploadPreparation,
        String?,
        PhotoUploadTopic
    ) async -> Result<PhotoUploadCreation, PhotoUploadFailure>

    let getCreationTopic: TopicHandler
    let prepareImage: ImagePreparationHandler
    let createPost: PostCreationHandler

    init(
        getCreationTopic: @escaping TopicHandler = { _ in .failure(.networkUnavailable) },
        prepareImage: @escaping ImagePreparationHandler = { _ in .failure(.imagePreparationFailed) },
        createPost: @escaping PostCreationHandler = { _, _, _ in .failure(.postCreationRejected) }
    ) {
        self.getCreationTopic = getCreationTopic
        self.prepareImage = prepareImage
        self.createPost = createPost
    }
}

@MainActor
@Observable
final class PhotoUploadViewModel {
    private(set) var viewState: PhotoUploadViewState
    private(set) var event: PhotoUploadEvent?

    private let topicDate: Date
    private let repository: PhotoUploadRepository

    private var creationTopic: PhotoUploadTopic?
    private var preparedImage: PhotoUploadPreparation?
    private var imageGeneration = 0
    private var preparationTask: Task<Void, Never>?
    private var submissionTask: Task<Void, Never>?
    private var topicTask: Task<Void, Never>?
    private var shouldSubmitAfterTopicLoad = false
    private var nextMessageID = 0

    init(
        topicDate: Date,
        initialState: PhotoUploadViewState? = nil,
        repository: PhotoUploadRepository? = nil
    ) {
        self.topicDate = PhotoUploadDate.startOfDay(topicDate)
        self.viewState = initialState ?? PhotoUploadViewState()
        self.repository = repository ?? PhotoUploadRepository()
        loadCreationTopic()
    }

    func handle(_ action: PhotoUploadAction) {
        if viewState.isSubmitting {
            switch action {
            case .backClicked, .messageShown:
                break
            default:
                return
            }
        }

        switch action {
        case .backClicked:
            publish(.navigateBack)
        case .galleryClicked:
            publish(.openGallery)
        case .cameraClicked:
            publish(.openCamera)
        case let .captionChanged(caption):
            viewState.caption = caption.limited(toCharacterCount: Constants.captionMaxLength)
        case .submitClicked:
            submit()
        case let .messageShown(messageID):
            if viewState.pendingMessage?.id == messageID {
                viewState.pendingMessage = nil
            }
        }
    }

    func selectImage(data: Data, preview: UIImage) {
        guard !viewState.isSubmitting, !data.isEmpty else {
            showImageSelectionFailure()
            return
        }

        imageGeneration += 1
        let generation = imageGeneration
        preparationTask?.cancel()
        preparedImage = nil
        viewState.selectedImage = preview
        viewState.selectedImageData = data
        viewState.imagePreparationStatus = .preparing
        startImagePreparation(data: data, generation: generation)
    }

    func showImageSelectionFailure() {
        publishMessage(PhotoUploadFailure.imagePreparationFailed.message)
    }

    func retryTopicLoad() {
        loadCreationTopic()
    }

    func consumeEvent() {
        event = nil
    }

    func reset() {
        let topicTitle = viewState.topicTitle
        clearWork()
        creationTopic = nil
        viewState = PhotoUploadViewState(topicTitle: topicTitle)
        event = nil
    }

    private func submit() {
        guard viewState.canSubmit else { return }
        guard let selectedImageData = viewState.selectedImageData else {
            showImageSelectionFailure()
            return
        }

        guard let topic = creationTopic else {
            shouldSubmitAfterTopicLoad = true
            loadCreationTopic(submitAfterLoad: true)
            return
        }

        viewState.isSubmitting = true
        if let preparedImage {
            submitPreparedImage(preparedImage, topic: topic)
        } else if preparationTask == nil {
            startImagePreparation(data: selectedImageData, generation: imageGeneration)
        }
    }

    private func startImagePreparation(data: Data, generation: Int) {
        preparationTask?.cancel()
        viewState.imagePreparationStatus = .preparing
        let repository = repository
        preparationTask = Task { [weak self] in
            let result = await repository.prepareImage(data)
            guard !Task.isCancelled, let self else { return }
            guard generation == self.imageGeneration else {
                return
            }
            self.preparationTask = nil

            switch result {
            case let .success(preparation):
                self.preparedImage = preparation
                self.viewState.imagePreparationStatus = .ready
                if self.viewState.isSubmitting {
                    if let topic = self.creationTopic {
                        self.submitPreparedImage(preparation, topic: topic)
                    } else {
                        self.shouldSubmitAfterTopicLoad = true
                        self.loadCreationTopic(submitAfterLoad: true)
                    }
                }
            case let .failure(failure):
                self.viewState.imagePreparationStatus = .failed
                self.handleTransientFailure(failure)
            }
        }
    }

    private func submitPreparedImage(
        _ preparation: PhotoUploadPreparation,
        topic: PhotoUploadTopic
    ) {
        guard preparedImage?.id == preparation.id,
              submissionTask == nil,
              let image = viewState.selectedImage
        else { return }

        preparedImage = nil
        let caption = viewState.caption
        let repository = repository
        submissionTask = Task { [weak self] in
            let result = await repository.createPost(preparation, caption, topic)
            guard !Task.isCancelled, let self else { return }
            self.submissionTask = nil

            switch result {
            case let .success(creation):
                self.viewState.imagePreparationStatus = .idle
                self.viewState.isSubmitting = false
                self.viewState.completedSubmission = PhotoUploadSubmission(
                    image: image,
                    caption: caption,
                    content: PhotoUploadSuccessContent(
                        date: creation.topic.date,
                        topic: creation.topic.title,
                        moderationStatus: creation.moderationStatus
                    )
                )
            case let .failure(failure):
                self.viewState.imagePreparationStatus = .failed
                self.handleTransientFailure(failure)
            }
        }
    }

    private func loadCreationTopic(submitAfterLoad: Bool = false) {
        if viewState.isTopicLoading {
            shouldSubmitAfterTopicLoad = shouldSubmitAfterTopicLoad || submitAfterLoad
            return
        }

        shouldSubmitAfterTopicLoad = submitAfterLoad
        viewState.isTopicLoading = true
        viewState.topicErrorMessage = nil
        let repository = repository
        let topicDate = topicDate

        topicTask?.cancel()
        topicTask = Task { [weak self] in
            let result = await repository.getCreationTopic(topicDate)
            guard !Task.isCancelled, let self else { return }

            let submitAfterLoad = self.shouldSubmitAfterTopicLoad
            self.shouldSubmitAfterTopicLoad = false

            switch result {
            case let .success(topic):
                self.creationTopic = topic
                self.viewState.isTopicLoading = false
                self.viewState.topicErrorMessage = nil
                self.viewState.topicTitle = topic.title
                if submitAfterLoad {
                    self.submit()
                }
            case let .failure(failure):
                self.viewState.isTopicLoading = false
                self.handleTopicFailure(failure)
            }
        }
    }

    private func handleTransientFailure(_ failure: PhotoUploadFailure) {
        viewState.isSubmitting = false
        if failure == .reauthenticationRequired {
            publish(.reauthenticationRequired)
        } else {
            publishMessage(failure.message)
        }
    }

    private func handleTopicFailure(_ failure: PhotoUploadFailure) {
        viewState.isSubmitting = false
        if failure == .reauthenticationRequired {
            publish(.reauthenticationRequired)
        } else {
            viewState.topicErrorMessage = failure.message
        }
    }

    private func publish(_ event: PhotoUploadEvent) {
        self.event = event
    }

    private func publishMessage(_ text: String) {
        let message = PhotoUploadMessage(id: nextMessageID, text: text)
        nextMessageID += 1
        viewState.pendingMessage = message
    }

    private func clearWork() {
        imageGeneration += 1
        preparationTask?.cancel()
        preparationTask = nil
        submissionTask?.cancel()
        submissionTask = nil
        topicTask?.cancel()
        topicTask = nil
        preparedImage = nil
        shouldSubmitAfterTopicLoad = false
    }

    private enum Constants {
        static let captionMaxLength = 10
    }
}

enum PhotoUploadAction: Equatable, Sendable {
    case backClicked
    case galleryClicked
    case cameraClicked
    case captionChanged(String)
    case submitClicked
    case messageShown(Int)
}
