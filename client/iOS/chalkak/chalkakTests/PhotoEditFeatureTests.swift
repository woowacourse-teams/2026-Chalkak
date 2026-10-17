import Foundation
import ImageIO
import Testing
import UIKit
import UniformTypeIdentifiers

@testable import chalkak

@MainActor
@Suite(.serialized)
struct PhotoEditViewModelTests {
    @Test("완료하면 선택한 필터를 원본 크기로 한 번만 렌더링하고 편집 상태를 함께 전달한다")
    func completesWithFullSizeRender() async throws {
        let recorder = PhotoEditRenderRecorder()
        let original = Self.image(width: 40, height: 30)
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: original),
            renderer: recorder.renderer
        )
        try await waitUntil { viewModel.viewState.previewImage != nil && !viewModel.viewState.thumbnails.isEmpty }

        viewModel.handle(.filterSelected(.mono))
        viewModel.handle(.doneClicked)
        #expect(viewModel.viewState.isCompleting)
        try await waitUntil { viewModel.event != nil }

        guard case let .completed(result) = viewModel.event else {
            Issue.record("완료 이벤트가 필요합니다")
            return
        }
        let fullSizeCalls = recorder.calls.filter { $0.maxLongEdge == nil && $0.image === original }
        #expect(fullSizeCalls.map(\.filter) == [.mono])
        #expect(result.image === original)
        #expect(result.session.filter == .mono)
        #expect(result.session.original === original)
        #expect(viewModel.viewState.isCompleting == false)
    }

    @Test("취소하면 원본 크기 렌더링 없이 취소 이벤트만 보낸다")
    func cancelsWithoutRendering() async throws {
        let recorder = PhotoEditRenderRecorder()
        let original = Self.image(width: 40, height: 30)
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: original),
            renderer: recorder.renderer
        )

        viewModel.handle(.filterSelected(.noir))
        viewModel.handle(.cancelClicked)

        #expect(viewModel.event == .cancelled)
        #expect(recorder.calls.contains { $0.maxLongEdge == nil && $0.image === original } == false)
    }

    @Test("자르기를 마치면 잘린 이미지로 미리보기와 썸네일을 다시 만들고 선택한 필터를 다시 적용한다")
    func reappliesFilterAfterCrop() async throws {
        let recorder = PhotoEditRenderRecorder()
        let original = Self.image(width: 40, height: 30)
        let cropped = Self.image(width: 20, height: 20)
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: original),
            renderer: recorder.renderer
        )
        viewModel.handle(.filterSelected(.chrome))
        try await waitUntil { viewModel.viewState.previewImage != nil }

        viewModel.handle(.cropClicked)
        #expect(viewModel.viewState.isCropperPresented)
        let cropperState = PhotoEditCropperState(aspectRatio: .square)
        viewModel.handle(.cropCompleted(PhotoEditCrop(image: cropped, cropperState: cropperState)))

        #expect(viewModel.viewState.isCropperPresented == false)
        #expect(viewModel.viewState.previewImage == nil)
        try await waitUntil {
            viewModel.viewState.previewImage != nil && viewModel.viewState.thumbnails.count == PhotoEditFilter.allCases.count
        }
        let callsAfterCrop = recorder.calls.drop { $0.image !== cropped }
        #expect(callsAfterCrop.contains { $0.image === cropped && $0.maxLongEdge == 1_600 })
        #expect(callsAfterCrop.contains { $0.image !== cropped && $0.filter == .chrome && $0.maxLongEdge == nil })
        #expect(callsAfterCrop.contains { $0.image === original } == false)

        viewModel.handle(.doneClicked)
        try await waitUntil { viewModel.event != nil }
        guard case let .completed(result) = viewModel.event else {
            Issue.record("완료 이벤트가 필요합니다")
            return
        }
        #expect(result.image === cropped)
        #expect(result.session.filter == .chrome)
        #expect(result.session.crop?.cropperState == cropperState)
    }

    @Test("다시 자를 때는 필터를 적용하기 전 원본과 이전 자르기 상태를 사용한다")
    func recropsFromOriginal() async throws {
        let original = Self.image(width: 40, height: 30)
        let cropped = Self.image(width: 20, height: 20)
        let cropperState = PhotoEditCropperState(aspectRatio: .portrait4x5)
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(
                original: original,
                crop: PhotoEditCrop(image: cropped, cropperState: cropperState),
                filter: .fade
            ),
            renderer: PhotoEditRenderRecorder().renderer
        )

        viewModel.handle(.cropClicked)
        viewModel.handle(.cropCancelled)

        #expect(viewModel.viewState.session.original === original)
        #expect(viewModel.viewState.session.croppedImage === cropped)
        #expect(viewModel.viewState.session.crop?.cropperState == cropperState)
        #expect(viewModel.viewState.selectedFilter == .fade)
    }

    @Test("렌더링에 실패하면 화면을 닫지 않고 실패를 안내한다")
    func reportsRenderFailure() async throws {
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: Self.image(width: 40, height: 30)),
            renderer: PhotoEditRenderer { image, _, maxLongEdge in
                maxLongEdge == nil ? nil : image
            }
        )

        viewModel.handle(.filterSelected(.mono))
        viewModel.handle(.doneClicked)
        try await waitUntil { viewModel.viewState.failureMessage != nil }

        #expect(viewModel.event == nil)
        #expect(viewModel.viewState.isCompleting == false)
        viewModel.handle(.failureMessageDismissed)
        #expect(viewModel.viewState.failureMessage == nil)
    }

    static func image(width: Int, height: Int) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            UIColor.systemOrange.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
    }
}

@MainActor
@Suite(.serialized)
struct PhotoUploadEditingTests {
    @Test("고른 사진을 줄여 새 편집 상태로 편집 화면에 넘긴다")
    func beginsEditingNewSelection() async throws {
        let prepared = PhotoEditViewModelTests.image(width: 40, height: 30)
        var preparedData: [Data] = []
        let viewModel = PhotoUploadViewModel(
            topicDate: PhotoUploadDate.today(),
            prepareEditImage: { data in
                preparedData.append(data)
                return prepared
            }
        )

        viewModel.beginEditing(sourceData: Data([0x01]))
        try await waitUntil { viewModel.viewState.editRequest != nil }

        #expect(preparedData == [Data([0x01])])
        #expect(viewModel.viewState.editRequest?.session.original === prepared)
        #expect(viewModel.viewState.editRequest?.session.crop == nil)
        #expect(viewModel.viewState.editRequest?.session.filter == .original)
        #expect(viewModel.viewState.selectedImage == nil)
    }

    @Test("처음 고른 사진의 편집을 취소하면 사진 선택도 취소한다")
    func cancelsNewSelection() async throws {
        let preparedImageCount = PhotoEditLockedValue(0)
        let viewModel = PhotoUploadViewModel(
            topicDate: PhotoUploadDate.today(),
            repository: PhotoUploadRepository(
                prepareImage: { _ in
                    preparedImageCount.update { $0 += 1 }
                    return .failure(.imagePreparationFailed)
                }
            ),
            prepareEditImage: { _ in PhotoEditViewModelTests.image(width: 40, height: 30) }
        )

        viewModel.beginEditing(sourceData: Data([0x01]))
        try await waitUntil { viewModel.viewState.editRequest != nil }
        viewModel.cancelEditing()
        await Task.yield()

        #expect(viewModel.viewState.editRequest == nil)
        #expect(viewModel.viewState.selectedImage == nil)
        #expect(viewModel.viewState.selectedImageData == nil)
        #expect(viewModel.viewState.canSubmit == false)
        #expect(preparedImageCount.value == 0)
    }

    @Test("편집을 마치면 편집한 사진을 JPEG로 넘겨 업로드 준비를 시작한다")
    func selectsEditedImage() async throws {
        let preparedSources = PhotoEditLockedValue<[Data]>([])
        let original = PhotoEditViewModelTests.image(width: 40, height: 30)
        let edited = PhotoEditViewModelTests.image(width: 20, height: 20)
        let viewModel = PhotoUploadViewModel(
            topicDate: PhotoUploadDate.today(),
            repository: PhotoUploadRepository(
                prepareImage: { data in
                    preparedSources.update { $0.append(data) }
                    return .success(Self.preparation(sourceData: data))
                }
            ),
            prepareEditImage: { _ in original }
        )

        viewModel.beginEditing(sourceData: Data([0x01]))
        try await waitUntil { viewModel.viewState.editRequest != nil }
        viewModel.completeEditing(
            PhotoEditResult(image: edited, session: PhotoEditSession(original: original, filter: .mono))
        )
        try await waitUntil { viewModel.viewState.imagePreparationStatus == .ready }

        let data = try #require(viewModel.viewState.selectedImageData)
        let decoded = try #require(UIImage(data: data))
        #expect(viewModel.viewState.editRequest == nil)
        #expect(viewModel.viewState.selectedImage === edited)
        #expect(decoded.size == CGSize(width: 20, height: 20))
        #expect(preparedSources.value == [data])
        #expect(viewModel.viewState.canEditSelectedImage)
    }

    @Test("사진을 탭하면 이전 편집 상태로 다시 편집하고 취소하면 이전 결과를 유지한다")
    func keepsPreviousResultWhenReeditCancelled() async throws {
        let original = PhotoEditViewModelTests.image(width: 40, height: 30)
        let edited = PhotoEditViewModelTests.image(width: 20, height: 20)
        let topic = PhotoUploadTopic(id: "topic-id", title: "틈", date: PhotoUploadDate.today())
        let viewModel = PhotoUploadViewModel(
            topicDate: topic.date,
            repository: PhotoUploadRepository(
                getCreationTopic: { _ in .success(topic) },
                prepareImage: { data in .success(Self.preparation(sourceData: data)) }
            ),
            prepareEditImage: { _ in original }
        )
        try await waitUntil { !viewModel.viewState.isTopicLoading }
        viewModel.beginEditing(sourceData: Data([0x01]))
        try await waitUntil { viewModel.viewState.editRequest != nil }
        viewModel.completeEditing(
            PhotoEditResult(image: edited, session: PhotoEditSession(original: original, filter: .noir))
        )
        let selectedData = viewModel.viewState.selectedImageData

        viewModel.handle(.selectedImageClicked)
        let request = try #require(viewModel.viewState.editRequest)
        #expect(request.session.original === original)
        #expect(request.session.filter == .noir)

        viewModel.cancelEditing()

        #expect(viewModel.viewState.editRequest == nil)
        #expect(viewModel.viewState.selectedImage === edited)
        #expect(viewModel.viewState.selectedImageData == selectedData)
        #expect(viewModel.viewState.canSubmit)
    }

    @Test("다시 편집을 마치면 이전 사진의 업로드 준비를 취소하고 새 사진을 준비한다")
    func replacesPreparationAfterReedit() async throws {
        let firstPreparation = PhotoEditLockedValue<CheckedContinuation<Void, Never>?>(nil)
        let preparedSources = PhotoEditLockedValue<[Data]>([])
        let original = PhotoEditViewModelTests.image(width: 40, height: 30)
        let firstEdit = PhotoEditViewModelTests.image(width: 20, height: 20)
        let secondEdit = PhotoEditViewModelTests.image(width: 10, height: 10)
        let topic = PhotoUploadTopic(id: "topic-id", title: "틈", date: PhotoUploadDate.today())
        let createdSources = PhotoEditLockedValue<[Data]>([])
        let viewModel = PhotoUploadViewModel(
            topicDate: topic.date,
            repository: PhotoUploadRepository(
                getCreationTopic: { _ in .success(topic) },
                prepareImage: { data in
                    let isFirst = preparedSources.update { sources -> Bool in
                        sources.append(data)
                        return sources.count == 1
                    }
                    if isFirst {
                        await withCheckedContinuation { continuation in
                            firstPreparation.update { $0 = continuation }
                        }
                    }
                    return .success(Self.preparation(sourceData: data))
                },
                createPost: { preparation, _, topic in
                    createdSources.update { $0.append(preparation.sourceData) }
                    return .success(PhotoUploadCreation(postID: "post", topic: topic, moderationStatus: .validating))
                }
            ),
            prepareEditImage: { _ in original }
        )

        viewModel.beginEditing(sourceData: Data([0x01]))
        try await waitUntil { viewModel.viewState.editRequest != nil }
        viewModel.completeEditing(PhotoEditResult(image: firstEdit, session: PhotoEditSession(original: original)))
        try await waitUntil { firstPreparation.value != nil }

        viewModel.handle(.selectedImageClicked)
        viewModel.completeEditing(
            PhotoEditResult(image: secondEdit, session: PhotoEditSession(original: original, filter: .mono))
        )
        let secondData = try #require(viewModel.viewState.selectedImageData)
        try await waitUntil { viewModel.viewState.imagePreparationStatus == .ready }
        firstPreparation.value?.resume()
        await Task.yield()

        #expect(preparedSources.value.count == 2)
        #expect(preparedSources.value.last == secondData)
        viewModel.handle(.submitClicked)
        try await waitUntil { viewModel.viewState.completedSubmission != nil }
        #expect(createdSources.value == [secondData])
        #expect(viewModel.viewState.completedSubmission?.image === secondEdit)
    }

    @Test("편집 화면 없이 바로 고른 사진은 다시 편집할 수 없다")
    func ignoresReeditWithoutSession() {
        let viewModel = PhotoUploadViewModel(topicDate: PhotoUploadDate.today())

        viewModel.selectImage(data: Data([0x01]), preview: PhotoEditViewModelTests.image(width: 4, height: 4))
        viewModel.handle(.selectedImageClicked)

        #expect(viewModel.viewState.canEditSelectedImage == false)
        #expect(viewModel.viewState.editRequest == nil)
    }

    private static func preparation(sourceData: Data) -> PhotoUploadPreparation {
        PhotoUploadPreparation(
            id: UUID(),
            sourceData: sourceData,
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
}

@MainActor
struct PhotoImageDownsamplerTests {
    @Test("긴 변을 4096 이하로 줄인다", arguments: [
        [8_193, 4_096], [5_000, 2_500], [1_200, 1_200],
    ])
    func limitsLongEdge(dimensions: [Int]) throws {
        let data = try #require(Self.solidImage(width: dimensions[0], height: 8).pngData())

        let image = try #require(PhotoImageDownsampler.downsample(data: data))

        #expect(Int(image.size.width) == dimensions[1])
        #expect(image.imageOrientation == .up)
    }

    @Test("EXIF 회전 정보가 있는 사진을 방향이 반영된 .up 이미지로 만든다")
    func appliesExifOrientation() throws {
        let data = try Self.jpegData(Self.solidImage(width: 40, height: 20), orientation: .right)

        let image = try #require(PhotoImageDownsampler.downsample(data: data))

        #expect(image.imageOrientation == .up)
        #expect(image.cgImage?.width == 20)
        #expect(image.cgImage?.height == 40)
    }

    private static func solidImage(width: Int, height: Int) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            UIColor.systemTeal.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
    }

    private static func jpegData(_ image: UIImage, orientation: CGImagePropertyOrientation) throws -> Data {
        let cgImage = try #require(image.cgImage)
        let data = NSMutableData()
        let destination = try #require(
            CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil)
        )
        CGImageDestinationAddImage(
            destination,
            cgImage,
            [kCGImagePropertyOrientation: orientation.rawValue] as CFDictionary
        )
        try #require(CGImageDestinationFinalize(destination))
        return data as Data
    }
}

@MainActor
struct PhotoEditFilterRendererTests {
    @Test("필터를 적용해도 원래 크기를 유지한다", arguments: PhotoEditFilter.allCases)
    func keepsSize(filter: PhotoEditFilter) throws {
        let image = Self.colorfulImage(width: 120, height: 80)

        let rendered = try #require(PhotoEditFilterRenderer.render(image, filter: filter))

        #expect(rendered.cgImage?.width == 120)
        #expect(rendered.cgImage?.height == 80)
    }

    @Test("미리보기 크기를 지정하면 긴 변을 그 크기로 줄인다")
    func rendersPreviewSize() throws {
        let image = Self.colorfulImage(width: 120, height: 80)

        let rendered = try #require(PhotoEditFilterRenderer.render(image, filter: .vivid, maxLongEdge: 60))

        #expect(rendered.cgImage?.width == 60)
        #expect(rendered.cgImage?.height == 40)
    }

    @Test("흑백 필터는 모든 픽셀의 R, G, B 값을 같게 만든다", arguments: [PhotoEditFilter.mono, .noir])
    func rendersGrayscale(filter: PhotoEditFilter) throws {
        let image = Self.colorfulImage(width: 40, height: 40)

        let rendered = try #require(PhotoEditFilterRenderer.render(image, filter: filter))
        let pixels = try #require(Self.rgbaPixels(of: rendered))

        for index in stride(from: 0, to: pixels.count, by: 4) {
            let red = Int(pixels[index])
            let green = Int(pixels[index + 1])
            let blue = Int(pixels[index + 2])
            #expect(abs(red - green) <= 1 && abs(green - blue) <= 1)
        }
    }

    private static func colorfulImage(width: Int, height: Int) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            for x in stride(from: 0, to: width, by: 4) {
                UIColor(hue: CGFloat(x) / CGFloat(width), saturation: 1, brightness: 1, alpha: 1).setFill()
                context.fill(CGRect(x: x, y: 0, width: 4, height: height))
            }
        }
    }

    private static func rgbaPixels(of image: UIImage) -> [UInt8]? {
        guard let cgImage = image.cgImage,
              let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)
        else { return nil }
        let width = cgImage.width
        let height = cgImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        return drawn ? pixels : nil
    }
}

private final class PhotoEditRenderRecorder: @unchecked Sendable {
    struct Call {
        let image: UIImage
        let filter: PhotoEditFilter
        let maxLongEdge: CGFloat?
    }

    private let lock = NSLock()
    private var storedCalls: [Call] = []

    var calls: [Call] {
        lock.withLock { storedCalls }
    }

    /// 호출을 기록한다. 축소 요청이면 새 이미지를, 아니면 입력 이미지를 그대로 돌려준다.
    var renderer: PhotoEditRenderer {
        PhotoEditRenderer { [self] image, filter, maxLongEdge in
            lock.withLock {
                storedCalls.append(Call(image: image, filter: filter, maxLongEdge: maxLongEdge))
            }
            guard maxLongEdge != nil, let cgImage = image.cgImage else { return image }
            return UIImage(cgImage: cgImage, scale: 1, orientation: .up)
        }
    }
}

private final class PhotoEditLockedValue<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var storedValue: Value

    init(_ value: Value) {
        storedValue = value
    }

    var value: Value {
        lock.withLock { storedValue }
    }

    @discardableResult
    func update<Result>(_ body: (inout Value) -> Result) -> Result {
        lock.withLock { body(&storedValue) }
    }
}
