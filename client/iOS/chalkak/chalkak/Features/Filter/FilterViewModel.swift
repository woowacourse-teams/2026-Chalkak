import Foundation
import Observation

/// 필터 탭의 고르기·닫기·즐겨찾기를 맡는다. 안드 `FilterViewModel`과 같은 동작이다.
/// 서버 호출이 없어 모두 동기로 상태만 바꾼다.
@MainActor
@Observable
final class FilterViewModel {
    private(set) var viewState: FilterViewState

    /// - Parameter filters: 목록에 보일 필터. 기본값은 샘플 6개(`FilterItem.samples`).
    init(filters: [FilterItem] = FilterItem.samples) {
        viewState = FilterViewState(filters: filters)
    }

    /// 카드를 눌러 시트를 연다. 목록에 없는 id는 무시해 시트가 열리지 않는다.
    func selectFilter(_ filterId: String) {
        guard viewState.filters.contains(where: { $0.id == filterId }) else { return }
        viewState.selectedFilterId = filterId
    }

    /// 시트를 닫는다(끌어내리기, 바깥 누르기, 사용 버튼). 즐겨찾기는 그대로 둔다.
    func dismissFilter() {
        viewState.selectedFilterId = nil
    }

    /// 별을 누를 때마다 즐겨찾기에 넣고 뺀다.
    func toggleFavorite(_ filterId: String) {
        if viewState.favoriteFilterIds.contains(filterId) {
            viewState.favoriteFilterIds.remove(filterId)
        } else {
            viewState.favoriteFilterIds.insert(filterId)
        }
    }
}
