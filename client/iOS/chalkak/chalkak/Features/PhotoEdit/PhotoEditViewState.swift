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
    case original
    case free
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
///
/// 회전·반전은 원본 이미지에 직접 적용하고, Mantis에는 자르기 영역만 맡긴다.
/// 화면에 보이는 이미지는 `좌우반전^isFlipped ∘ 반시계 90°^quarterTurns`를 원본에 적용한 결과다.
struct PhotoEditCropperState: Equatable, Sendable {
    var aspectRatio: PhotoEditAspectRatio = .free
    var quarterTurns = 0
    var isFlipped = false
    /// `quarterTurns`·`isFlipped`를 적용한 이미지 기준의 자르기 영역.
    var transformation: Transformation?

    /// 보이는 이미지를 반시계 방향으로 90° 돌린다. 반전된 상태에서는 원본 기준 회전 방향이 반대가 된다.
    mutating func rotateCounterclockwise() {
        quarterTurns = (quarterTurns + (isFlipped ? 3 : 1)) % 4
        transformation = nil
    }

    mutating func flipHorizontally() {
        isFlipped.toggle()
        transformation = nil
    }

    func orientedSize(of size: CGSize) -> CGSize {
        quarterTurns.isMultiple(of: 2) ? size : CGSize(width: size.height, height: size.width)
    }
}

/// Mantis에 넘길 이미지. 화면에는 필터를 적용한 이미지를 보여 주고, 결과는 필터 전 이미지에서 자른다.
struct PhotoEditCropperSource: Identifiable {
    let id = UUID()
    /// 회전·반전만 적용한 원본.
    let baseImage: UIImage
    /// `baseImage`에 선택한 필터를 적용한 이미지.
    let displayImage: UIImage
    let state: PhotoEditCropperState
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

enum PhotoEditMode: CaseIterable, Identifiable, Sendable {
    case filter
    case crop

    var id: Self { self }

    var title: String {
        switch self {
        case .filter: "필터"
        case .crop: "자르기"
        }
    }

    var systemImage: String {
        switch self {
        case .filter: "camera.filters"
        case .crop: "crop.rotate"
        }
    }
}

struct PhotoEditViewState {
    var session: PhotoEditSession
    var mode: PhotoEditMode = .filter
    var previewImage: UIImage?
    var thumbnails: [PhotoEditFilter: UIImage] = [:]
    /// 자르기 탭에서 아직 확정하지 않은 비율·회전·반전.
    var cropDraft = PhotoEditCropperState()
    var cropperSource: PhotoEditCropperSource?
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
    case modeSelected(PhotoEditMode)
    case filterSelected(PhotoEditFilter)
    case aspectRatioSelected(PhotoEditAspectRatio)
    case rotateClicked
    case flipClicked
    case cropCompleted(PhotoEditCrop)
    case doneClicked
    case cancelClicked
    case failureMessageDismissed
}
