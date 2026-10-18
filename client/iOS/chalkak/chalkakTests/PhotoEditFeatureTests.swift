import Foundation
import ImageIO
import Mantis
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

        viewModel.handle(.toolSelected(.filter))
        viewModel.handle(.filterSelected(.mono))
        viewModel.handle(.toolApplied)
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

        viewModel.handle(.toolSelected(.filter))
        viewModel.handle(.filterSelected(.noir))
        viewModel.handle(.toolApplied)
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
        viewModel.handle(.toolSelected(.filter))
        viewModel.handle(.filterSelected(.chrome))
        viewModel.handle(.toolApplied)
        try await waitUntil { viewModel.viewState.previewImage != nil }

        viewModel.handle(.toolSelected(.crop))
        try await waitUntil { viewModel.viewState.cropperSource != nil }
        let cropperState = PhotoEditCropperState(aspectRatio: .square)
        viewModel.handle(.cropCompleted(PhotoEditCrop(image: cropped, cropperState: cropperState)))

        #expect(viewModel.viewState.activeTool == nil)
        #expect(viewModel.viewState.cropperSource == nil)
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

    @Test("자르기 탭은 필터를 적용한 원본을 보여 주고 이전 자르기 상태를 복원한다")
    func restoresCropperFromOriginal() async throws {
        let recorder = PhotoEditRenderRecorder()
        let original = Self.image(width: 40, height: 30)
        let cropped = Self.image(width: 20, height: 20)
        let cropperState = PhotoEditCropperState(
            aspectRatio: .portrait4x5,
            orientation: PhotoEditOrientation(quarterTurns: 2),
            imageOrientation: PhotoEditOrientation(quarterTurns: 1),
            transformation: Self.transformation
        )
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(
                original: original,
                crop: PhotoEditCrop(image: cropped, cropperState: cropperState),
                filter: .fade
            ),
            renderer: recorder.renderer
        )

        viewModel.handle(.toolSelected(.crop))
        try await waitUntil { viewModel.viewState.cropperSource != nil }

        let source = try #require(viewModel.viewState.cropperSource)
        #expect(source.state == cropperState)
        // Mantis에 넘길 방향을 먼저 만들고, 이어서 화면의 최종 방향을 미리 만든다.
        try await waitUntil { recorder.orientCalls.count == 2 }
        #expect(recorder.orientCalls.allSatisfy { $0.image === original })
        #expect(recorder.orientCalls.map(\.quarterTurns) == [1, 2])
        #expect(source.baseImage !== original)
        #expect(recorder.calls.contains { $0.image === source.baseImage && $0.filter == .fade && $0.maxLongEdge == nil })
    }

    @Test("비율을 고를 때마다 이전 자르기 영역을 버리고 전체 이미지에서 다시 시작한다")
    func restartsCropForEachAspectRatio() async throws {
        let recorder = PhotoEditRenderRecorder()
        let original = Self.image(width: 40, height: 30)
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(
                original: original,
                crop: PhotoEditCrop(
                    image: Self.image(width: 20, height: 20),
                    cropperState: PhotoEditCropperState(aspectRatio: .free, transformation: Self.transformation)
                )
            ),
            renderer: recorder.renderer
        )
        viewModel.handle(.toolSelected(.crop))
        let firstSource = try #require(viewModel.viewState.cropperSource)

        viewModel.handle(.aspectRatioSelected(.square))
        let squareSource = try #require(viewModel.viewState.cropperSource)
        viewModel.handle(.aspectRatioSelected(.landscape16x9))
        let landscapeSource = try #require(viewModel.viewState.cropperSource)

        #expect(squareSource.id != firstSource.id)
        #expect(landscapeSource.id != squareSource.id)
        #expect(squareSource.state.transformation == nil)
        #expect(landscapeSource.state.aspectRatio == .landscape16x9)
        #expect(landscapeSource.state.transformation == nil)
        #expect(landscapeSource.baseImage === original)
        #expect(landscapeSource.baseImage === firstSource.baseImage)
        #expect(recorder.orientCalls.isEmpty)
    }

    @Test("회전·반전은 Mantis 화면을 다시 만들지 않고 방향만 기록하며 돌린 원본을 미리 만든다")
    func recordsOrientationWithoutRestartingCropper() async throws {
        let recorder = PhotoEditRenderRecorder()
        let original = Self.image(width: 40, height: 30)
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: original),
            renderer: recorder.renderer
        )
        viewModel.handle(.toolSelected(.crop))
        let firstSource = try #require(viewModel.viewState.cropperSource)

        viewModel.handle(.rotateClicked)
        viewModel.handle(.flipClicked)

        #expect(viewModel.viewState.cropperSource?.id == firstSource.id)
        #expect(viewModel.viewState.cropDraft.orientation == PhotoEditOrientation(quarterTurns: 1, isFlipped: true))
        #expect(viewModel.viewState.cropDraft.imageOrientation.isIdentity)
        try await waitUntil {
            recorder.orientCalls.last.map { $0.quarterTurns == 1 && $0.isFlipped } == true
        }
        #expect(recorder.orientCalls.allSatisfy { $0.image === original })
    }

    @Test("돌린 뒤 비율을 고르면 돌린 원본에서 Mantis를 새로 시작하고 준비되기 전에는 지금 화면을 유지한다")
    func restartsFromOrientedOriginalAfterRotation() async throws {
        let original = Self.image(width: 40, height: 30)
        let gate = PhotoEditLockedValue<CheckedContinuation<Void, Never>?>(nil)
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: original),
            renderer: PhotoEditRenderer(
                render: { image, _, _ in image },
                orient: { image, _, _ in
                    await withCheckedContinuation { continuation in
                        gate.update { $0 = continuation }
                    }
                    return PhotoEditViewModelTests.image(width: 30, height: 40)
                }
            )
        )
        viewModel.handle(.toolSelected(.crop))
        let firstSource = try #require(viewModel.viewState.cropperSource)

        viewModel.handle(.rotateClicked)
        viewModel.handle(.aspectRatioSelected(.portrait4x5))
        #expect(viewModel.viewState.cropperSource?.id == firstSource.id)

        try await waitUntil { gate.value != nil }
        gate.value?.resume()
        try await waitUntil { viewModel.viewState.cropperSource?.id != firstSource.id }

        let source = try #require(viewModel.viewState.cropperSource)
        #expect(source.baseImage.size == CGSize(width: 30, height: 40))
        #expect(source.state.aspectRatio == .portrait4x5)
        #expect(source.state.imageOrientation == PhotoEditOrientation(quarterTurns: 1))
        #expect(source.state.orientation == PhotoEditOrientation(quarterTurns: 1))
        #expect(source.state.transformation == nil)
    }

    @Test("자르기 결과에는 Mantis에 넘긴 이미지 방향과 화면의 최종 방향을 함께 남긴다")
    func storesBothOrientationsOnCrop() async throws {
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: Self.image(width: 40, height: 30)),
            renderer: PhotoEditRenderRecorder().renderer
        )
        viewModel.handle(.toolSelected(.crop))
        let source = try #require(viewModel.viewState.cropperSource)
        viewModel.handle(.rotateClicked)

        var mantisState = source.state
        mantisState.transformation = Self.transformation
        viewModel.handle(.cropCompleted(PhotoEditCrop(image: Self.image(width: 10, height: 10), cropperState: mantisState)))

        let state = try #require(viewModel.viewState.session.crop?.cropperState)
        #expect(state.orientation == PhotoEditOrientation(quarterTurns: 1))
        #expect(state.imageOrientation.isIdentity)
        #expect(state.transformation == Self.transformation)
    }

    @Test("필터 화면에서 취소하면 들어가기 전 필터로 되돌린다")
    func revertsFilterOnToolCancel() async throws {
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: Self.image(width: 40, height: 30), filter: .fade),
            renderer: PhotoEditRenderRecorder().renderer
        )

        viewModel.handle(.toolSelected(.filter))
        viewModel.handle(.filterSelected(.mono))
        #expect(viewModel.viewState.selectedFilter == .mono)
        viewModel.handle(.toolCancelled)

        #expect(viewModel.viewState.activeTool == nil)
        #expect(viewModel.viewState.selectedFilter == .fade)
    }

    @Test("자르기 화면에서 취소하면 회전·비율 변경을 버리고 이전 자르기를 유지한다")
    func discardsCropDraftOnToolCancel() async throws {
        let cropped = Self.image(width: 20, height: 20)
        let cropperState = PhotoEditCropperState(aspectRatio: .square, transformation: Self.transformation)
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(
                original: Self.image(width: 40, height: 30),
                crop: PhotoEditCrop(image: cropped, cropperState: cropperState)
            ),
            renderer: PhotoEditRenderRecorder().renderer
        )

        viewModel.handle(.toolSelected(.crop))
        viewModel.handle(.rotateClicked)
        viewModel.handle(.aspectRatioSelected(.landscape16x9))
        viewModel.handle(.toolCancelled)

        #expect(viewModel.viewState.activeTool == nil)
        #expect(viewModel.viewState.cropperSource == nil)
        #expect(viewModel.viewState.session.croppedImage === cropped)
        #expect(viewModel.viewState.session.crop?.cropperState == cropperState)

        viewModel.handle(.toolSelected(.crop))
        #expect(viewModel.viewState.cropDraft == cropperState)
    }

    @Test("자동 보정은 자른 사진을 한 번 분석하고 강도에 맞춰 미리보기와 완료 렌더링에 적용한다")
    func appliesAutoAdjustment() async throws {
        let recorder = PhotoEditRenderRecorder()
        let original = Self.image(width: 40, height: 30)
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: original),
            renderer: recorder.renderer
        )
        try await waitUntil { viewModel.viewState.previewImage != nil && !viewModel.viewState.thumbnails.isEmpty }
        #expect(recorder.analyzeCount == 1)

        viewModel.handle(.toolSelected(.adjust))
        viewModel.handle(.autoAdjustmentClicked)
        #expect(viewModel.viewState.autoAdjustmentIntensity == 1)
        viewModel.handle(.autoAdjustmentIntensityChanged(0.4))
        viewModel.handle(.autoAdjustmentIntensityChanged(1.5))
        #expect(viewModel.viewState.autoAdjustmentIntensity == 1)
        viewModel.handle(.autoAdjustmentIntensityChanged(0.6))
        try await waitUntil {
            recorder.calls.last { $0.maxLongEdge == nil && $0.image !== original }?.look.autoAdjustmentIntensity == 0.6
        }

        viewModel.handle(.toolApplied)
        #expect(viewModel.viewState.activeTool == nil)
        // 필터 썸네일도 자동 보정 위에 다시 만든다.
        try await waitUntil {
            PhotoEditFilter.allCases.allSatisfy { filter in
                recorder.calls.contains { $0.filter == filter && $0.look.autoAdjustmentIntensity == 0.6 }
            }
        }

        viewModel.handle(.doneClicked)
        try await waitUntil { viewModel.event != nil }
        guard case let .completed(result) = viewModel.event else {
            Issue.record("완료 이벤트가 필요합니다")
            return
        }
        let fullSizeCall = try #require(recorder.calls.last { $0.image === original && $0.maxLongEdge == nil })
        #expect(fullSizeCall.look.autoAdjustmentIntensity == 0.6)
        #expect(fullSizeCall.look.autoAdjustment != nil)
        #expect(result.session.autoAdjustmentIntensity == 0.6)
        #expect(recorder.analyzeCount == 1)
    }

    @Test("조정 화면에서 취소하면 들어가기 전 자동 보정 강도로 되돌리고, 다시 누르면 자동 보정을 끈다")
    func revertsAutoAdjustmentOnToolCancel() async throws {
        var session = PhotoEditSession(original: Self.image(width: 40, height: 30))
        session.autoAdjustmentIntensity = 0.3
        let viewModel = PhotoEditViewModel(session: session, renderer: PhotoEditRenderRecorder().renderer)

        viewModel.handle(.toolSelected(.adjust))
        viewModel.handle(.autoAdjustmentClicked)
        #expect(viewModel.viewState.autoAdjustmentIntensity == 0)
        viewModel.handle(.autoAdjustmentClicked)
        #expect(viewModel.viewState.autoAdjustmentIntensity == 1)
        viewModel.handle(.toolCancelled)

        #expect(viewModel.viewState.activeTool == nil)
        #expect(viewModel.viewState.autoAdjustmentIntensity == 0.3)
        // 조정 화면 밖에서는 강도를 바꾸지 않는다.
        viewModel.handle(.autoAdjustmentIntensityChanged(0.8))
        #expect(viewModel.viewState.autoAdjustmentIntensity == 0.3)
    }

    @Test("자르기를 마치면 자른 사진으로 자동 보정값을 다시 구한다")
    func reanalyzesAfterCrop() async throws {
        let recorder = PhotoEditRenderRecorder()
        let cropped = Self.image(width: 20, height: 20)
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: Self.image(width: 40, height: 30)),
            renderer: recorder.renderer
        )
        try await waitUntil { recorder.analyzeCount == 1 }

        viewModel.handle(.toolSelected(.crop))
        viewModel.handle(.cropCompleted(PhotoEditCrop(image: cropped, cropperState: PhotoEditCropperState())))
        try await waitUntil { recorder.analyzeCount == 2 && viewModel.viewState.previewImage != nil }

        #expect(recorder.analyzedImages.last?.size == cropped.size)
    }

    @Test("도구 화면이 열려 있으면 편집 완료와 취소를 받지 않는다")
    func ignoresDoneWhileToolIsOpen() {
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: Self.image(width: 40, height: 30)),
            renderer: PhotoEditRenderRecorder().renderer
        )

        viewModel.handle(.toolSelected(.filter))
        viewModel.handle(.doneClicked)
        viewModel.handle(.cancelClicked)

        #expect(viewModel.viewState.isCompleting == false)
        #expect(viewModel.event == nil)
    }

    @Test("고정 비율은 세로·가로 짝을 모두 제공한다")
    func providesBothOrientationsForFixedRatios() {
        let size = CGSize(width: 3, height: 2)
        let ratios = PhotoEditAspectRatio.allCases.map(\.title)
        #expect(ratios == ["원본", "자유", "1:1", "4:5", "5:4", "3:4", "4:3", "9:16", "16:9"])

        let pairs: [(PhotoEditAspectRatio, PhotoEditAspectRatio)] = [
            (.portrait4x5, .landscape5x4), (.portrait3x4, .landscape4x3), (.portrait9x16, .landscape16x9),
        ]
        for (portrait, landscape) in pairs {
            let portraitValue = portrait.widthToHeight(originalSize: size) ?? 0
            let landscapeValue = landscape.widthToHeight(originalSize: size) ?? 0
            #expect(portraitValue < 1)
            #expect(abs(portraitValue * landscapeValue - 1) < 0.0001)
        }
        #expect(PhotoEditAspectRatio.free.widthToHeight(originalSize: size) == nil)
        #expect(PhotoEditAspectRatio.original.widthToHeight(originalSize: size) == 1.5)
    }

    @Test("반전한 상태에서 회전하면 원본 기준 회전 방향이 반대가 된다")
    func rotatesFlippedImageInDisplayedDirection() {
        var state = PhotoEditOrientation()
        state.rotateCounterclockwise()
        #expect(state.quarterTurns == 1)

        state.flipHorizontally()
        state.rotateCounterclockwise()
        #expect(state.quarterTurns == 0)
        #expect(state.isFlipped)
        #expect(state.orientedSize(of: CGSize(width: 4, height: 3)) == CGSize(width: 4, height: 3))

        state.rotateCounterclockwise()
        #expect(state.quarterTurns == 3)
        #expect(state.orientedSize(of: CGSize(width: 4, height: 3)) == CGSize(width: 3, height: 4))
    }

    @Test("렌더링에 실패하면 화면을 닫지 않고 실패를 안내한다")
    func reportsRenderFailure() async throws {
        let viewModel = PhotoEditViewModel(
            session: PhotoEditSession(original: Self.image(width: 40, height: 30)),
            renderer: PhotoEditRenderer { image, _, maxLongEdge in
                maxLongEdge == nil ? nil : image
            }
        )

        viewModel.handle(.toolSelected(.filter))
        viewModel.handle(.filterSelected(.mono))
        viewModel.handle(.toolApplied)
        viewModel.handle(.doneClicked)
        try await waitUntil { viewModel.viewState.failureMessage != nil }

        #expect(viewModel.event == nil)
        #expect(viewModel.viewState.isCompleting == false)
        viewModel.handle(.failureMessageDismissed)
        #expect(viewModel.viewState.failureMessage == nil)
    }

    static let transformation = Transformation(
        offset: CGPoint(x: 1, y: 2),
        rotation: 0,
        scale: 1.5,
        isManuallyZoomed: true,
        initialMaskFrame: CGRect(x: 0, y: 0, width: 100, height: 100),
        maskFrame: CGRect(x: 10, y: 10, width: 50, height: 50),
        cropWorkbenchViewBounds: CGRect(x: 0, y: 0, width: 100, height: 100),
        horizontallyFlipped: false,
        verticallyFlipped: false
    )

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

        let rendered = try #require(PhotoEditFilterRenderer.render(image, look: PhotoEditLook(filter: filter)))

        #expect(rendered.cgImage?.width == 120)
        #expect(rendered.cgImage?.height == 80)
    }

    @Test("미리보기 크기를 지정하면 긴 변을 그 크기로 줄인다")
    func rendersPreviewSize() throws {
        let image = Self.colorfulImage(width: 120, height: 80)

        let rendered = try #require(PhotoEditFilterRenderer.render(image, look: PhotoEditLook(filter: .vivid), maxLongEdge: 60))

        #expect(rendered.cgImage?.width == 60)
        #expect(rendered.cgImage?.height == 40)
    }

    @Test("흑백 필터는 모든 픽셀의 R, G, B 값을 같게 만든다", arguments: [PhotoEditFilter.mono, .noir])
    func rendersGrayscale(filter: PhotoEditFilter) throws {
        let image = Self.colorfulImage(width: 40, height: 40)

        let rendered = try #require(PhotoEditFilterRenderer.render(image, look: PhotoEditLook(filter: filter)))
        let pixels = try #require(Self.rgbaPixels(of: rendered))

        for index in stride(from: 0, to: pixels.count, by: 4) {
            let red = Int(pixels[index])
            let green = Int(pixels[index + 1])
            let blue = Int(pixels[index + 2])
            #expect(abs(red - green) <= 1 && abs(green - blue) <= 1)
        }
    }

    @Test("자동 보정은 대비가 낮은 사진의 밝기 범위를 넓히고 강도만큼만 적용한다")
    func appliesAutoAdjustmentByIntensity() throws {
        let image = Self.lowContrastImage(width: 64, height: 64)
        let autoAdjustment = try #require(PhotoEditFilterRenderer.autoAdjustment(for: image))

        let none = try #require(PhotoEditFilterRenderer.render(
            image,
            look: PhotoEditLook(autoAdjustment: autoAdjustment, autoAdjustmentIntensity: 0)
        ))
        let half = try #require(PhotoEditFilterRenderer.render(
            image,
            look: PhotoEditLook(autoAdjustment: autoAdjustment, autoAdjustmentIntensity: 0.5)
        ))
        let full = try #require(PhotoEditFilterRenderer.render(
            image,
            look: PhotoEditLook(autoAdjustment: autoAdjustment, autoAdjustmentIntensity: 1)
        ))

        #expect(none === image)
        let originalRange = try #require(Self.brightnessRange(of: image))
        let halfRange = try #require(Self.brightnessRange(of: half))
        let fullRange = try #require(Self.brightnessRange(of: full))
        #expect(fullRange > originalRange + 10)
        #expect(halfRange > originalRange && halfRange < fullRange)
        #expect(full.cgImage?.width == 64 && full.cgImage?.height == 64)
    }

    @Test("반시계 방향 회전과 좌우반전을 화면에 보이는 방향대로 적용한다")
    func orientsImage() throws {
        // 왼쪽 절반은 빨강, 오른쪽 절반은 파랑인 가로 이미지.
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: CGSize(width: 20, height: 10), format: format).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 10, height: 10))
            UIColor.blue.setFill()
            context.fill(CGRect(x: 10, y: 0, width: 10, height: 10))
        }

        let rotated = try #require(PhotoEditFilterRenderer.orient(image, quarterTurns: 1, isFlipped: false))
        let rotatedPixels = try #require(Self.rgbaPixels(of: rotated))
        #expect(rotated.cgImage?.width == 10)
        #expect(rotated.cgImage?.height == 20)
        // 반시계로 돌리면 오른쪽(파랑)이 위로 간다.
        #expect(Self.isBlue(rotatedPixels, x: 5, y: 2, width: 10))
        #expect(Self.isRed(rotatedPixels, x: 5, y: 17, width: 10))

        let flipped = try #require(PhotoEditFilterRenderer.orient(image, quarterTurns: 0, isFlipped: true))
        let flippedPixels = try #require(Self.rgbaPixels(of: flipped))
        #expect(Self.isBlue(flippedPixels, x: 2, y: 5, width: 20))
        #expect(Self.isRed(flippedPixels, x: 17, y: 5, width: 20))

        #expect(PhotoEditFilterRenderer.orient(image, quarterTurns: 4, isFlipped: false) === image)
    }

    private static func isRed(_ pixels: [UInt8], x: Int, y: Int, width: Int) -> Bool {
        let index = (y * width + x) * 4
        return pixels[index] > 200 && pixels[index + 2] < 60
    }

    private static func isBlue(_ pixels: [UInt8], x: Int, y: Int, width: Int) -> Bool {
        let index = (y * width + x) * 4
        return pixels[index] < 60 && pixels[index + 2] > 200
    }

    /// 왼쪽은 어두운 회색, 오른쪽은 밝은 회색으로 밝기 범위가 좁은 이미지.
    private static func lowContrastImage(width: Int, height: Int) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            for x in 0..<width {
                let brightness = 0.35 + 0.25 * CGFloat(x) / CGFloat(width - 1)
                UIColor(hue: 0.08, saturation: 0.3, brightness: brightness, alpha: 1).setFill()
                context.fill(CGRect(x: x, y: 0, width: 1, height: height))
            }
        }
    }

    /// 가장 밝은 픽셀과 가장 어두운 픽셀의 녹색 채널 차이.
    private static func brightnessRange(of image: UIImage) -> Int? {
        guard let pixels = rgbaPixels(of: image) else { return nil }
        let greens = stride(from: 1, to: pixels.count, by: 4).map { Int(pixels[$0]) }
        guard let minimum = greens.min(), let maximum = greens.max() else { return nil }
        return maximum - minimum
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
        let look: PhotoEditLook
        let maxLongEdge: CGFloat?

        var filter: PhotoEditFilter {
            look.filter
        }
    }

    struct OrientCall {
        let image: UIImage
        let quarterTurns: Int
        let isFlipped: Bool
    }

    private let lock = NSLock()
    private var storedCalls: [Call] = []
    private var storedOrientCalls: [OrientCall] = []
    private var storedAnalyzedImages: [UIImage] = []

    var calls: [Call] {
        lock.withLock { storedCalls }
    }

    var orientCalls: [OrientCall] {
        lock.withLock { storedOrientCalls }
    }

    var analyzedImages: [UIImage] {
        lock.withLock { storedAnalyzedImages }
    }

    var analyzeCount: Int {
        analyzedImages.count
    }

    /// 호출을 기록한다. 축소·회전·반전 요청이면 새 이미지를, 아니면 입력 이미지를 그대로 돌려준다.
    /// 자동 보정 분석은 실제 Core Image 분석 결과를 돌려준다.
    var renderer: PhotoEditRenderer {
        PhotoEditRenderer(
            render: { [self] image, look, maxLongEdge in
                lock.withLock {
                    storedCalls.append(Call(image: image, look: look, maxLongEdge: maxLongEdge))
                }
                return maxLongEdge == nil ? image : Self.copy(image)
            },
            orient: { [self] image, quarterTurns, isFlipped in
                lock.withLock {
                    storedOrientCalls.append(OrientCall(image: image, quarterTurns: quarterTurns, isFlipped: isFlipped))
                }
                return quarterTurns == 0 && !isFlipped ? image : Self.copy(image)
            },
            analyze: { [self] image in
                lock.withLock { storedAnalyzedImages.append(image) }
                return PhotoEditFilterRenderer.autoAdjustment(for: image)
            }
        )
    }

    private static func copy(_ image: UIImage) -> UIImage {
        guard let cgImage = image.cgImage else { return image }
        return UIImage(cgImage: cgImage, scale: 1, orientation: .up)
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
