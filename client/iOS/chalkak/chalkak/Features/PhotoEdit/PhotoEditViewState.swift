import Foundation
import Mantis
import UIKit

nonisolated enum PhotoEditFilter: CaseIterable, Identifiable, Sendable {
    case original
    case vivid
    case chrome
    case fade
    case mono
    case noir

    var id: Self { self }

    var title: String {
        switch self {
        case .original: "원본"
        case .vivid: "선명"
        case .chrome: "크롬"
        case .fade: "페이드"
        case .mono: "흑백"
        case .noir: "누아르"
        }
    }
}

enum PhotoEditAspectRatio: CaseIterable, Identifiable, Sendable {
    case free
    case original
    case square
    case portrait4x5
    case portrait3x4
    case landscape16x9

    var id: Self { self }

    var title: String {
        switch self {
        case .free: "자유"
        case .original: "원본"
        case .square: "1:1"
        case .portrait4x5: "4:5"
        case .portrait3x4: "3:4"
        case .landscape16x9: "16:9"
        }
    }

    /// 자르기 영역의 가로÷세로 비율. 자유 비율이면 `nil`이다.
    func widthToHeight(originalSize: CGSize) -> Double? {
        switch self {
        case .free:
            nil
        case .original:
            originalSize.height > 0 ? Double(originalSize.width / originalSize.height) : nil
        case .square:
            1
        case .portrait4x5:
            4.0 / 5.0
        case .portrait3x4:
            3.0 / 4.0
        case .landscape16x9:
            16.0 / 9.0
        }
    }
}

/// 다시 자를 때 이전 자르기 화면을 복원하기 위한 상태.
struct PhotoEditCropperState: Equatable, Sendable {
    var aspectRatio: PhotoEditAspectRatio = .free
    var transformation: Transformation?
}

struct PhotoEditCrop {
    let image: UIImage
    let cropperState: PhotoEditCropperState
}

/// 다시 편집할 수 있도록 원본과 적용한 편집을 함께 보관한다.
struct PhotoEditSession {
    /// 긴 변 4096 이하로 줄이고 방향을 `.up`으로 맞춘 원본.
    let original: UIImage
    var crop: PhotoEditCrop?
    var filter: PhotoEditFilter = .original

    var croppedImage: UIImage {
        crop?.image ?? original
    }
}

struct PhotoEditResult: Equatable {
    let id: UUID
    let image: UIImage
    let session: PhotoEditSession

    init(id: UUID = UUID(), image: UIImage, session: PhotoEditSession) {
        self.id = id
        self.image = image
        self.session = session
    }

    static func == (lhs: PhotoEditResult, rhs: PhotoEditResult) -> Bool {
        lhs.id == rhs.id
    }
}

struct PhotoEditViewState {
    var session: PhotoEditSession
    var previewImage: UIImage?
    var thumbnails: [PhotoEditFilter: UIImage] = [:]
    var isCropperPresented = false
    var isCompleting = false
    var failureMessage: String?

    var selectedFilter: PhotoEditFilter {
        session.filter
    }

    var displayedImage: UIImage {
        previewImage ?? session.croppedImage
    }
}

enum PhotoEditEvent: Equatable {
    case completed(PhotoEditResult)
    case cancelled
}

enum PhotoEditAction {
    case filterSelected(PhotoEditFilter)
    case cropClicked
    case cropCompleted(PhotoEditCrop)
    case cropCancelled
    case doneClicked
    case cancelClicked
    case failureMessageDismissed
}
