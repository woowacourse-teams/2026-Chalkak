import Photos
import UIKit

enum PhotoLibrarySaveResult {
    case saved
    case failed
    case permissionDenied
}

/// 이미지를 사진 앱에 추가 전용 권한으로 저장한다.
enum PhotoLibraryImageSaver {
    static func save(_ image: UIImage) async -> PhotoLibrarySaveResult {
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

    @MainActor
    static func requestAddOnlyAuthorization() async -> PHAuthorizationStatus {
        let status = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        guard status == .notDetermined else { return status }

        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                continuation.resume(returning: status)
            }
        }
    }
}
