import Foundation

enum AIReviewStatus: String, Decodable, Equatable, Sendable {
    case none = "NONE"
    case processing = "PROCESSING"
    case completed = "COMPLETED"
    case failed = "FAILED"
}

struct AIReview: Decodable, Equatable, Sendable {
    let status: AIReviewStatus
    let requestedAt: String?
    let completedAt: String?
    let canRequestAgain: Bool
    var result: AIReviewResult? = nil
}

struct AIReviewResult: Decodable, Equatable, Sendable {
    let overallScore: Double
    let criteria: [AIReviewCriterion]
    let distinctivePerspective: Bool
    let feedback: AIReviewFeedback
}

struct AIReviewCriterion: Decodable, Equatable, Sendable {
    let key: String
    let label: String
    let score: Double
}

struct AIReviewFeedback: Decodable, Equatable, Sendable {
    let summary: String
    let strength: String
    let weakness: String
    let suggestion: String
}

enum AIReviewError: Error, Equatable, Sendable {
    case network
    case invalidResponse
    case unauthorized
    case http(statusCode: Int, message: String?)
}

protocol AIReviewRepository: Sendable {
    func fetchReview(postID: String) async throws -> AIReview
    func requestReview(postID: String) async throws -> AIReview
}

struct APIAIReviewRepository: AIReviewRepository {
    let client: AIReviewAPIClient

    func fetchReview(postID: String) async throws -> AIReview {
        try await client.fetchReview(postID: postID)
    }

    func requestReview(postID: String) async throws -> AIReview {
        try await client.requestReview(postID: postID)
    }
}
