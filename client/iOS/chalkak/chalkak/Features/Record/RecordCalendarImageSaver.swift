import Photos
import SwiftUI

/// "이미지로 저장"에서 사진 앱에 저장하는 대상 스냅샷.
/// Android가 캡처하는 영역(상단바 + 요일 헤더 + 그리드)과 구성을 맞춘다.
/// 좌우 패딩(20)은 화면과 동일하게 이 뷰가 직접 적용한다.
struct RecordCalendarSnapshot: View {
    @Environment(\.chalkakTheme) private var theme
    let month: RecordMonth
    let posts: [RecordPost]
    let canGoPrevious: Bool
    let canGoNext: Bool
    let width: CGFloat

    var body: some View {
        VStack(spacing: 0) {
            RecordTopBar(
                month: month,
                canGoPrevious: canGoPrevious,
                canGoNext: canGoNext,
                onPrevious: {},
                onNext: {}
            )

            Spacer().frame(height: Metrics.topBarToWeekday)

            RecordWeekdayHeader()
                .padding(.horizontal, Metrics.horizontalPadding)

            Spacer().frame(height: Metrics.weekdayToGrid)

            RecordCalendarGrid(month: month, posts: posts, onDateClick: { _ in })
                .padding(.horizontal, Metrics.horizontalPadding)

            Spacer().frame(height: Metrics.bottomPadding)
        }
        .frame(width: width)
        .background(theme.colors.background)
    }

    private enum Metrics {
        static let horizontalPadding: CGFloat = 20
        static let topBarToWeekday: CGFloat = 8
        static let weekdayToGrid: CGFloat = 14
        static let bottomPadding: CGFloat = 20
    }
}

enum RecordImageSaveResult {
    case saved
    case failed
    case permissionDenied
}

/// 렌더링된 달력 이미지를 사진 앱에 저장한다.
/// Android의 갤러리 저장 + 권한 거부 흐름에 대응한다(추가 전용 권한).
enum RecordCalendarImageSaver {
    static func save(_ image: UIImage) async -> RecordImageSaveResult {
        let status = await requestAddOnlyAuthorization()
        guard status == .authorized || status == .limited else {
            return .permissionDenied
        }

        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.creationRequestForAsset(from: image)
            } completionHandler: { success, _ in
                continuation.resume(returning: success ? .saved : .failed)
            }
        }
    }

    private static func requestAddOnlyAuthorization() async -> PHAuthorizationStatus {
        await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                continuation.resume(returning: status)
            }
        }
    }
}
