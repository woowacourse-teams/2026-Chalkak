import Foundation

struct PhotoUploadAPIRepository: Sendable {
    let apiClient: PhotoUploadAPIClient

    func getCreationTopic(_ date: Date) async -> Result<PhotoUploadTopic, PhotoUploadFailure> {
        do {
            return .success(try await apiClient.fetchTopic(date: date))
        } catch {
            return .failure(mapTopicError(error))
        }
    }

    func prepareImage(_ sourceData: Data) async -> Result<PhotoUploadPreparation, PhotoUploadFailure> {
        do {
            let policyRequestStartedAt = Date()
            let upload = try await apiClient.createPostImageUpload()
            let encodedData = try await PhotoUploadImageEncoder.encode(
                sourceData: sourceData,
                maxBytes: upload.maxBytes
            )
            guard encodedData.isEmpty == false,
                  Int64(encodedData.count) <= upload.maxBytes
            else {
                return .failure(.imagePreparationFailed)
            }

            return .success(
                PhotoUploadPreparation(
                    id: UUID(),
                    sourceData: sourceData,
                    encodedData: encodedData,
                    upload: upload,
                    uploadURLExpiresAt: expiryDate(
                        for: upload,
                        requestStartedAt: policyRequestStartedAt
                    )
                )
            )
        } catch is PhotoUploadImageEncodingError {
            return .failure(.imagePreparationFailed)
        } catch {
            return .failure(mapUploadError(error))
        }
    }

    func createPost(
        _ preparation: PhotoUploadPreparation,
        title: String?,
        topic: PhotoUploadTopic
    ) async -> Result<PhotoUploadCreation, PhotoUploadFailure> {
        do {
            guard topic.id.isEmpty == false, topic.title.isEmpty == false else {
                return .failure(.invalidResponse)
            }

            var upload = preparation.upload
            var encodedData = preparation.encodedData
            if Date() >= preparation.uploadURLExpiresAt {
                do {
                    upload = try await apiClient.createPostImageUpload()
                } catch {
                    return .failure(mapUploadError(error))
                }
                if Int64(encodedData.count) > upload.maxBytes {
                    do {
                        encodedData = try await PhotoUploadImageEncoder.encode(
                            sourceData: preparation.sourceData,
                            maxBytes: upload.maxBytes
                        )
                    } catch is PhotoUploadImageEncodingError {
                        return .failure(.imagePreparationFailed)
                    }
                }
            }

            guard encodedData.isEmpty == false,
                  Int64(encodedData.count) <= upload.maxBytes
            else {
                return .failure(.imagePreparationFailed)
            }

            do {
                try await apiClient.upload(
                    data: encodedData,
                    to: upload.uploadURL,
                    contentType: upload.contentType
                )
            } catch {
                return .failure(mapImageUploadError(error))
            }

            let response = try await apiClient.createPost(
                topicID: topic.id,
                photoUploadID: upload.uploadID,
                title: normalizedTitle(title)
            )
            guard let moderationStatus = PhotoUploadModerationStatus(
                rawValue: response.moderationStatus
            ), response.postID.isEmpty == false else {
                return .failure(.invalidResponse)
            }

            return .success(
                PhotoUploadCreation(
                    postID: response.postID,
                    topic: topic,
                    moderationStatus: moderationStatus
                )
            )
        } catch {
            return .failure(mapPostCreationError(error))
        }
    }

    private func expiryDate(
        for upload: PhotoUploadUploadPolicy,
        requestStartedAt: Date
    ) -> Date {
        requestStartedAt.addingTimeInterval(
            TimeInterval(max(0, upload.expiresInSeconds - Constants.expirySafetySeconds))
        )
    }

    private func normalizedTitle(_ title: String?) -> String? {
        guard let title, title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false else {
            return nil
        }
        return title
    }

    private func mapTopicError(_ error: Error) -> PhotoUploadFailure {
        guard let error = error as? PhotoUploadAPIError else {
            return .networkUnavailable
        }
        if case let .http(statusCode, _) = error, statusCode == 401 {
            return .reauthenticationRequired
        }
        switch error {
        case .network:
            return .networkUnavailable
        case .invalidResponse:
            return .invalidResponse
        case .http:
            return .postCreationRejected
        }
    }

    private func mapUploadError(_ error: Error) -> PhotoUploadFailure {
        guard let error = error as? PhotoUploadAPIError else {
            return .imagePreparationFailed
        }
        if case let .http(statusCode, _) = error, statusCode == 401 {
            return .reauthenticationRequired
        }
        switch error {
        case .network:
            return .networkUnavailable
        case .invalidResponse:
            return .invalidResponse
        case .http:
            return .uploadRejected
        }
    }

    private func mapPostCreationError(_ error: Error) -> PhotoUploadFailure {
        guard let error = error as? PhotoUploadAPIError else {
            return .postCreationRejected
        }
        switch error {
        case .network:
            return .networkUnavailable
        case .invalidResponse:
            return .invalidResponse
        case let .http(statusCode, message):
            if statusCode == 401 {
                return .reauthenticationRequired
            }
            if message == Constants.alreadySubmittedMessage {
                return .alreadySubmitted
            }
            if message == Constants.topicNotOpenMessage {
                return .topicNotOpen
            }
            return .postCreationRejected
        }
    }

    private func mapImageUploadError(_ error: Error) -> PhotoUploadFailure {
        guard let error = error as? PhotoUploadAPIError else {
            return .uploadRejected
        }
        switch error {
        case .network:
            return .networkUnavailable
        case .invalidResponse, .http:
            return .uploadRejected
        }
    }

    private enum Constants {
        static let alreadySubmittedMessage = "이미 해당 주제에 게시물을 작성했습니다."
        static let expirySafetySeconds: Int64 = 5
        static let topicNotOpenMessage = "현재 게시물을 작성할 수 없는 주제입니다."
    }
}

extension PhotoUploadRepository {
    static func api(client: PhotoUploadAPIClient) -> PhotoUploadRepository {
        let repository = PhotoUploadAPIRepository(apiClient: client)
        return PhotoUploadRepository(
            getCreationTopic: { date in
                await repository.getCreationTopic(date)
            },
            prepareImage: { data in
                await repository.prepareImage(data)
            },
            createPost: { preparation, title, topic in
                await repository.createPost(preparation, title: title, topic: topic)
            }
        )
    }
}
