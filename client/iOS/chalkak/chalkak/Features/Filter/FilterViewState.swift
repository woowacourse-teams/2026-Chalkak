import Foundation

/// 필터 탭에 보이는 필터 한 건. 안드 `FilterItem`과 같은 항목이다.
/// 필터 API가 붙기 전이라 이미지는 앱 에셋(`ChalkakImageSource.asset`)을 쓴다.
///
/// 예: id "sample-0", 이름 "필터 1", 설명 "언제 언제 쓰면 예뻐요", 제작자 사인 자리 "제작자\n사인".
nonisolated struct FilterItem: Identifiable, Equatable, Sendable {
    let id: String
    let name: String
    let description: String
    /// 대표 사진 오른쪽 아래에 얹는 제작자 사인. 사인 이미지가 오기 전까지 글자로 둔다.
    let creatorSignature: String
    /// 목록 카드와 시트 맨 위에 보이는 대표 사진.
    let coverImage: ChalkakImageSource
    /// 시트를 크게 열면 보이는 "필터 제작자가 찍은 사진"(세로 목록).
    let creatorPhotos: [ChalkakImageSource]
    /// 시트를 크게 열면 보이는 "이 필터로 찍은 사진"(3열).
    let usedPhotos: [ChalkakImageSource]
}

/// 필터 탭 상태. 고른 필터가 있으면 상세 시트가 열린다.
/// 즐겨찾기는 화면 안에서만 유지되고 앱을 다시 켜면 사라진다(저장 API 없음).
struct FilterViewState: Equatable, Sendable {
    var filters: [FilterItem] = []
    /// 시트에 띄운 필터 id. nil이면 시트가 닫혀 있다.
    var selectedFilterId: String?
    var favoriteFilterIds: Set<String> = []

    /// `selectedFilterId`에 맞는 필터. 목록에 없는 id면 nil이라 시트가 열리지 않는다.
    var selectedFilter: FilterItem? {
        filters.first { $0.id == selectedFilterId }
    }

    func isFavorite(_ filterId: String) -> Bool {
        favoriteFilterIds.contains(filterId)
    }
}

extension FilterItem {
    /// 필터 API 연동 전 임시 데이터 6개(sample-0 … sample-5). 연동하면 서버 조회로 바꾼다.
    nonisolated static let samples: [FilterItem] = (0..<6).map { index in
        FilterItem(
            id: "sample-\(index)",
            name: "필터 \(index + 1)",
            description: "언제 언제 쓰면 예뻐요",
            creatorSignature: "제작자\n사인",
            coverImage: .asset("preview_photo"),
            creatorPhotos: [
                .asset("preview_sunset"),
                .asset("preview_photo"),
                .asset("preview_sunset"),
                .asset("preview_photo")
            ],
            usedPhotos: [
                .asset("preview_photo"),
                .asset("preview_sunset"),
                .asset("preview_photo")
            ]
        )
    }
}
