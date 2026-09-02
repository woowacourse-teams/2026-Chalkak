import Foundation
import SwiftUI
import Testing
import UIKit
@testable import chalkak

@MainActor
@Suite(.serialized)
struct PhotoUploadViewStateTests {
    @Test("사진이 없으면 전시하기를 비활성화한다")
    func disablesSubmitWithoutPhoto() {
        #expect(!PhotoUploadViewState().canSubmit)
    }

    @Test("사진이 준비 중이거나 실패해도 재시도 가능한 상태를 유지한다")
    func keepsSubmitRetryableAfterPreparationFailure() {
        let state = PhotoUploadViewState(
            selectedImage: Self.image(),
            selectedImageData: Data([0x01]),
            imagePreparationStatus: .failed
        )

        #expect(state.canSubmit)
    }

    @Test("사진을 선택하면 이미지 준비 상태와 무관하게 Android처럼 전시하기를 활성화한다")
    func enablesSubmitForSelectedPhotoBeforePreparation() {
        let state = PhotoUploadViewState(selectedImage: Self.image())

        #expect(state.canSubmit)
    }

    @Test("KST 날짜를 API 및 성공 카드 표시 형식으로 변환한다")
    func formatsDatesInKoreaTimeZone() {
        let date = Self.date(2026, 9, 2)

        #expect(PhotoUploadDate.apiString(from: date) == "2026-09-02")
        #expect(PhotoUploadDate.displayString(from: date) == "2026. 09. 02")
    }

    private static func image() -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 40, height: 40)).image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 40, height: 40))
        }
    }

    private static func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = PhotoUploadDate.timeZone
        return calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }
}

@MainActor
@Suite(.serialized)
struct PhotoUploadViewModelTests {
    @Test("사진 선택부터 이미지 준비, 게시물 생성까지 전체 상태 흐름을 처리한다")
    func completesSubmissionFlow() async {
        let topic = Self.topic()
        let preparation = Self.preparation()
        let viewModel = PhotoUploadViewModel(
            topicDate: topic.date,
            repository: PhotoUploadRepository(
                getCreationTopic: { _ in .success(topic) },
                prepareImage: { _ in .success(preparation) },
                createPost: { _, title, topic in
                    #expect(title == "작품 제목")
                    return .success(
                        PhotoUploadCreation(
                            postID: "post-id",
                            topic: topic,
                            moderationStatus: .validating
                        )
                    )
                }
            )
        )

        viewModel.selectImage(data: Data([0x01]), preview: Self.image())
        viewModel.handle(.captionChanged("작품 제목"))
        await waitUntil { viewModel.viewState.imagePreparationStatus == .ready }

        viewModel.handle(.submitClicked)
        await waitUntil { viewModel.viewState.completedSubmission != nil }

        #expect(viewModel.viewState.isSubmitting == false)
        #expect(viewModel.viewState.completedSubmission?.content.topic == topic.title)
        #expect(viewModel.viewState.completedSubmission?.content.moderationStatus == .validating)
    }

    @Test("주제 API 인증 만료는 재인증 이벤트로 변환한다")
    func publishesReauthenticationEventForTopicFailure() async {
        let date = PhotoUploadDate.today()
        let viewModel = PhotoUploadViewModel(
            topicDate: date,
            repository: PhotoUploadRepository(
                getCreationTopic: { _ in .failure(.reauthenticationRequired) }
            )
        )

        await waitUntil { viewModel.event == .reauthenticationRequired }

        #expect(viewModel.viewState.isTopicLoading == false)
        #expect(viewModel.viewState.topicErrorMessage == nil)
    }

    @Test("제목 입력은 Android와 같은 10자 제한을 사용한다")
    func limitsCaptionToTenCharacters() {
        let viewModel = PhotoUploadViewModel(topicDate: PhotoUploadDate.today())

        viewModel.handle(.captionChanged("12345678901"))

        #expect(viewModel.viewState.caption == "1234567890")
    }

    private func waitUntil(
        _ condition: @escaping @MainActor () -> Bool
    ) async {
        for _ in 0..<100 {
            if condition() { return }
            await Task.yield()
        }
        Issue.record("조건이 제한 시간 안에 충족되지 않았습니다")
    }

    private static func topic() -> PhotoUploadTopic {
        PhotoUploadTopic(
            id: "topic-id",
            title: "틈",
            date: PhotoUploadDate.today()
        )
    }

    private static func preparation() -> PhotoUploadPreparation {
        PhotoUploadPreparation(
            id: UUID(),
            sourceData: Data([0x01]),
            encodedData: Data([0x02]),
            upload: PhotoUploadUploadPolicy(
                uploadID: "upload-id",
                uploadURL: URL(string: "https://example.com/upload")!,
                expiresInSeconds: 60,
                contentType: "image/webp",
                maxBytes: 1_024
            ),
            uploadURLExpiresAt: Date().addingTimeInterval(60)
        )
    }

    private static func image() -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 40, height: 40)).image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 40, height: 40))
        }
    }
}

@MainActor
@Suite(.serialized)
struct PhotoUploadSelectionLoaderTests {
    @Test("새 사진을 선택하면 이전 사진의 늦은 로딩 결과를 반영하지 않는다")
    func ignoresStaleSelectionResult() async {
        let firstGate = PhotoUploadSelectionGate()
        let secondGate = PhotoUploadSelectionGate()
        let loader = PhotoUploadSelectionLoader()
        var loadedData: [Data] = []
        var failureCount = 0

        loader.start(
            load: { try await firstGate.wait() },
            onLoaded: { loadedData.append($0) },
            onFailure: { failureCount += 1 }
        )
        await firstGate.waitUntilRequested()

        loader.start(
            load: { try await secondGate.wait() },
            onLoaded: { loadedData.append($0) },
            onFailure: { failureCount += 1 }
        )
        await secondGate.waitUntilRequested()

        await firstGate.resume(with: Data([0x01]))
        await secondGate.resume(with: Data([0x02]))
        await waitUntil { loadedData == [Data([0x02])] }

        #expect(loadedData == [Data([0x02])])
        #expect(failureCount == 0)
        loader.cancel()
    }

    private func waitUntil(
        _ condition: @escaping @MainActor () -> Bool
    ) async {
        for _ in 0..<100 {
            if condition() { return }
            await Task.yield()
        }
        Issue.record("조건이 제한 시간 안에 충족되지 않았습니다")
    }
}

@MainActor
@Suite(.serialized)
struct PhotoUploadAPIClientTests {
    @Test("업로드 정책, presigned PUT, 게시물 생성 요청을 Android 계약대로 보낸다")
    func sendsCompleteUploadRequestSequence() async throws {
        let recorder = PhotoUploadRequestRecorder()
        let client = makeClient(accessToken: "access-token") { request in
            await recorder.append(request)
            switch (request.httpMethod, request.url?.path) {
            case ("GET", "/api/v1/topics"):
                return Self.response(
                    for: request,
                    body: #"{"id":"topic-id","title":"틈","topicDate":"2026-09-02"}"#
                )
            case ("POST", "/api/v1/posts/uploads"):
                return Self.response(
                    for: request,
                    body: #"{"uploadId":"upload-id","uploadUrl":"https://upload.example.com/file","expiresInSeconds":60,"contentType":"image/webp","maxBytes":1024}"#
                )
            case ("PUT", "/file"):
                return Self.response(for: request, body: "")
            case ("POST", "/api/v1/posts"):
                return Self.response(
                    for: request,
                    body: #"{"postId":"post-id","moderationStatus":"VALIDATING"}"#
                )
            default:
                return Self.response(for: request, statusCode: 404, body: "{}")
            }
        }

        let date = Self.date(2026, 9, 2)
        let topic = try await client.fetchTopic(date: date)
        let policy = try await client.createPostImageUpload()
        try await client.upload(
            data: Data([0x01, 0x02]),
            to: policy.uploadURL,
            contentType: policy.contentType
        )
        let creation = try await client.createPost(
            topicID: topic.id,
            photoUploadID: policy.uploadID,
            title: "작품 제목"
        )

        #expect(topic.title == "틈")
        #expect(creation.postID == "post-id")
        #expect(creation.moderationStatus == "VALIDATING")

        let requests = await recorder.requests
        #expect(requests.count == 4)
        #expect(requests[0].url?.query?.contains("date=2026-09-02") == true)
        #expect(requests[0].value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
        #expect(requests[1].value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
        #expect(requests[2].value(forHTTPHeaderField: "Authorization") == nil)
        #expect(requests[2].value(forHTTPHeaderField: "Content-Type") == "image/webp")

        #expect(requests[3].value(forHTTPHeaderField: "Content-Type") == "application/json")
    }

    @Test("topics 응답 날짜가 요청 날짜와 다르면 invalidResponse가 된다")
    func rejectsTopicWithWrongDate() async {
        let client = makeClient { request in
            Self.response(
                for: request,
                body: #"{"id":"topic-id","title":"틈","topicDate":"2026-09-01"}"#
            )
        }

        do {
            _ = try await client.fetchTopic(date: Self.date(2026, 9, 2))
            Issue.record("다른 날짜의 주제가 성공하면 안 됩니다")
        } catch let error as PhotoUploadAPIError {
            #expect(error == .invalidResponse)
        } catch {
            Issue.record("예상하지 못한 오류: \(error)")
        }
    }

    private func makeClient(
        accessToken: String? = nil,
        handler: @escaping PhotoUploadMockURLProtocol.Handler
    ) -> PhotoUploadAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [PhotoUploadMockURLProtocol.self]
        PhotoUploadMockURLProtocol.handler = handler
        return PhotoUploadAPIClient(
            configuration: PhotoUploadAPIConfiguration(
                baseURL: URL(string: "https://example.com/api/v1/")!
            ),
            session: URLSession(configuration: configuration),
            accessTokenProvider: { accessToken }
        )
    }

    nonisolated private static func response(
        for request: URLRequest,
        statusCode: Int = 200,
        body: String
    ) -> (HTTPURLResponse, Data) {
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: statusCode,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        return (response, Data(body.utf8))
    }

    private static func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = PhotoUploadDate.timeZone
        return calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }
}

@MainActor
struct PhotoUploadScreenTests {
    @Test("업로드 화면은 iPhone 폭에서 Android와 같은 전체 레이아웃으로 렌더링된다")
    func rendersAtIPhoneWidth() {
        let view = PhotoUploadScreen(
            viewState: PhotoUploadViewState(topicTitle: "틈"),
            isCameraAvailable: true,
            onAction: { _ in },
            onRetryTopicLoad: {}
        )
        .chalkakTheme(.light)
        let hostingController = UIHostingController(
            rootView: view.frame(width: 402, height: 874)
        )

        let size = hostingController.sizeThatFits(
            in: CGSize(width: 402, height: 874)
        )

        #expect(size.width == 402)
        #expect(size.height == 874)
    }

    @Test("선택한 사진도 Android와 같은 이미지 영역 비율을 유지한다")
    func keepsSelectedImageAspectRatio() {
        let view = PhotoUploadImageArea(
            selectedImage: Self.image(),
            topicTitle: "틈",
            isCameraAvailable: true,
            onGalleryClick: {},
            onCameraClick: {}
        )
        .chalkakTheme(.light)
        let hostingController = UIHostingController(
            rootView: view.frame(width: 402)
        )

        let size = hostingController.sizeThatFits(
            in: CGSize(width: 402, height: 1_000)
        )

        #expect(abs(size.width - 402) < 1)
        #expect(abs(size.height - (402 / 1.216)) < 1)
    }

    private static func image() -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 80, height: 120)).image { context in
            UIColor.systemGreen.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 80, height: 120))
        }
    }
}

@MainActor
@Suite(.serialized)
struct PhotoUploadImageEncoderTests {
    @Test("사진을 Android 업로드 계약과 같은 WebP 데이터로 인코딩한다")
    func encodesWebP() async throws {
        let sourceImage = UIGraphicsImageRenderer(size: CGSize(width: 120, height: 80)).image { context in
            UIColor.systemPink.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 120, height: 80))
        }
        let sourceData = try #require(sourceImage.jpegData(compressionQuality: 1))

        let encoded = try await PhotoUploadImageEncoder.encode(
            sourceData: sourceData,
            maxBytes: 1_024 * 1_024
        )

        #expect(encoded.count >= 12)
        #expect(encoded.prefix(4) == Data("RIFF".utf8))
        #expect(encoded.subdata(in: 8..<12) == Data("WEBP".utf8))
    }
}

private actor PhotoUploadRequestRecorder {
    private(set) var requests: [URLRequest] = []

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private actor PhotoUploadSelectionGate {
    private var continuations: [CheckedContinuation<Data?, Error>] = []

    func wait() async throws -> Data? {
        try await withCheckedThrowingContinuation { continuation in
            continuations.append(continuation)
        }
    }

    func waitUntilRequested() async {
        for _ in 0..<100 {
            if !continuations.isEmpty { return }
            try? await Task.sleep(nanoseconds: 1_000_000)
        }
        Issue.record("사진 로더가 대기 상태가 되지 않았습니다")
    }

    func resume(with data: Data?) {
        guard continuations.isEmpty == false else {
            Issue.record("사진 로더의 대기 작업이 없습니다")
            return
        }
        continuations.removeFirst().resume(returning: data)
    }
}

private final class PhotoUploadMockURLProtocol: URLProtocol, @unchecked Sendable {
    typealias Handler = @Sendable (URLRequest) async throws -> (HTTPURLResponse, Data)

    nonisolated(unsafe) static var handler: Handler?

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        Task {
            do {
                let (response, data) = try await handler(request)
                client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: data)
                client?.urlProtocolDidFinishLoading(self)
            } catch {
                client?.urlProtocol(self, didFailWithError: error)
            }
        }
    }

    override func stopLoading() {}
}
