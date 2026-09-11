import Foundation

enum PhotoUploadEntryOutcome: Equatable, Sendable {
    case allowed(topicDate: Date)
    case alreadyPosted
    case noActiveTopic
    case reauthenticationRequired
    case suspended
    case failed
    case cancelled

    var message: String? {
        switch self {
        case .allowed, .reauthenticationRequired, .cancelled:
            nil
        case .alreadyPosted:
            "이미 이 주제에 게시물을 작성했어요."
        case .noActiveTopic:
            "지금 참여할 수 있는 주제가 없어요."
        case .suspended:
            "이용이 정지되어 게시물을 작성할 수 없어요."
        case .failed:
            "게시물 작성 여부를 확인하지 못했어요. 다시 시도해 주세요."
        }
    }
}

struct PhotoUploadEntryGate: Sendable {
    typealias StatusHandler = @Sendable () async throws -> PhotoUploadTodayPostStatus

    private let statusHandler: StatusHandler

    init(statusHandler: @escaping StatusHandler) {
        self.statusHandler = statusHandler
    }

    func check() async -> PhotoUploadEntryOutcome {
        do {
            let status = try await statusHandler()
            return status.isPosted
                ? .alreadyPosted
                : .allowed(topicDate: status.topicDate)
        } catch is CancellationError {
            return .cancelled
        } catch let error as PhotoUploadAPIError {
            switch error {
            case .network, .invalidResponse:
                return .failed
            case let .http(statusCode, _):
                switch statusCode {
                case 401:
                    return .reauthenticationRequired
                case 403:
                    return .suspended
                case 404:
                    return .noActiveTopic
                default:
                    return .failed
                }
            }
        } catch {
            return .failed
        }
    }

    static func api(client: PhotoUploadAPIClient) -> PhotoUploadEntryGate {
        PhotoUploadEntryGate {
            try await client.fetchTodayPostStatus()
        }
    }
}
