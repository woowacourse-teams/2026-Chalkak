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

/// 원본에 적용한 방향. 보이는 이미지는 `좌우반전^isFlipped ∘ 반시계 90°^quarterTurns`를 원본에 적용한 결과다.
struct PhotoEditOrientation: Hashable, Sendable {
    var quarterTurns = 0
    var isFlipped = false

    var isIdentity: Bool {
        quarterTurns == 0 && !isFlipped
    }

    /// 보이는 이미지를 반시계 방향으로 90° 돌린다. 반전된 상태에서는 원본 기준 회전 방향이 반대가 된다.
    mutating func rotateCounterclockwise() {
        quarterTurns = (quarterTurns + (isFlipped ? 3 : 1)) % 4
    }

    mutating func flipHorizontally() {
        isFlipped.toggle()
    }

    func orientedSize(of size: CGSize) -> CGSize {
        quarterTurns.isMultiple(of: 2) ? size : CGSize(width: size.height, height: size.width)
    }
}

/// 다시 자를 때 이전 자르기 화면을 복원하기 위한 상태.
///
/// 회전·반전은 Mantis가 애니메이션과 함께 처리하고, 비율을 고르면 지금 방향으로 돌린 원본에서 Mantis를 새로 시작한다.
struct PhotoEditCropperState: Equatable, Sendable {
    var aspectRatio: PhotoEditAspectRatio = .free
    /// 화면에 보이는 최종 방향.
    var orientation = PhotoEditOrientation()
    /// Mantis에 넘긴 이미지에 미리 적용한 방향. 나머지 회전·반전은 `transformation`에 들어 있다.
    var imageOrientation = PhotoEditOrientation()
    var transformation: Transformation?
}

/// Mantis에 넘길 이미지. 화면에는 필터를 적용한 이미지를 보여 주고, 결과는 필터 전 이미지에서 자른다.
struct PhotoEditCropperSource: Identifiable {
    let id = UUID()
    /// `state.imageOrientation`만 적용한 원본.
    let baseImage: UIImage
    /// `baseImage`에 선택한 필터를 적용한 이미지.
    let displayImage: UIImage
    let state: PhotoEditCropperState
}

struct PhotoEditCrop {
    let image: UIImage
    var cropperState: PhotoEditCropperState
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

/// 편집 첫 화면에서 골라 들어가는 도구. 도구 화면에서 취소하면 그 도구에서 바꾼 것만 되돌린다.
enum PhotoEditTool: CaseIterable, Identifiable, Sendable {
    case filter
    case crop

    var id: Self { self }

    var title: String {
        switch self {
        case .filter: "필터"
        case .crop: "자르기·회전"
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
    var activeTool: PhotoEditTool?
    var previewImage: UIImage?
    var thumbnails: [PhotoEditFilter: UIImage] = [:]
    /// 자르기 화면에서 아직 확정하지 않은 비율·회전·반전.
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
    case toolSelected(PhotoEditTool)
    case toolCancelled
    case filterApplied
    case filterSelected(PhotoEditFilter)
    case aspectRatioSelected(PhotoEditAspectRatio)
    case rotateClicked
    case flipClicked
    case cropCompleted(PhotoEditCrop)
    case doneClicked
    case cancelClicked
    case failureMessageDismissed
}
