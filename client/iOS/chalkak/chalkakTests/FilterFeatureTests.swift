import Testing
@testable import chalkak

/// 필터 탭의 상태 규칙. 필터 API가 없어 앱 안의 샘플 6개(sample-0 … sample-5)로 본다(#1021).
@MainActor
struct FilterViewModelTests {
    @Test("필터 카드를 고르면 그 필터가 선택된다")
    func selectsFilter() {
        // Given 샘플 필터 6개(sample-0 … sample-5)
        // When  sample-2를 고른다
        // Then  선택된 필터의 id가 sample-2, 이름이 "필터 3"
        let viewModel = FilterViewModel()
        #expect(viewModel.viewState.filters.map(\.id) == (0..<6).map { "sample-\($0)" })

        viewModel.selectFilter("sample-2")

        #expect(viewModel.viewState.selectedFilter?.id == "sample-2")
        #expect(viewModel.viewState.selectedFilter?.name == "필터 3")
    }

    @Test("시트를 닫으면 선택이 풀린다")
    func dismissClearsSelection() throws {
        // Given sample-2가 선택된 상태
        // When  시트를 닫는다
        // Then  선택된 필터가 없음(nil)
        let viewModel = FilterViewModel()
        viewModel.selectFilter("sample-2")
        try #require(viewModel.viewState.selectedFilter?.id == "sample-2")

        viewModel.dismissFilter()

        #expect(viewModel.viewState.selectedFilter == nil)
    }

    @Test("별을 누르면 즐겨찾기에 들어가고 다시 누르면 빠진다")
    func togglesFavorite() {
        // Given 즐겨찾기가 비어 있는 상태
        // When  sample-1 별을 누르고, 한 번 더 누른다
        // Then  첫 번째 뒤 즐겨찾기가 {sample-1}, 두 번째 뒤 {}
        let viewModel = FilterViewModel()
        #expect(viewModel.viewState.favoriteFilterIds.isEmpty)

        viewModel.toggleFavorite("sample-1")
        #expect(viewModel.viewState.favoriteFilterIds == ["sample-1"])

        viewModel.toggleFavorite("sample-1")
        #expect(viewModel.viewState.favoriteFilterIds.isEmpty)
    }

    @Test("즐겨찾기는 시트를 닫았다 다시 열어도 유지된다")
    func keepsFavoriteAfterReopen() {
        // Given sample-1을 즐겨찾기한 뒤 시트를 닫은 상태
        // When  sample-1을 다시 고른다
        // Then  sample-1이 즐겨찾기 상태이고, 다른 필터(sample-0)는 즐겨찾기가 아니다
        let viewModel = FilterViewModel()
        viewModel.selectFilter("sample-1")
        viewModel.toggleFavorite("sample-1")
        viewModel.dismissFilter()

        viewModel.selectFilter("sample-1")

        #expect(viewModel.viewState.selectedFilter?.id == "sample-1")
        #expect(viewModel.viewState.isFavorite("sample-1"))
        #expect(!viewModel.viewState.isFavorite("sample-0"))
    }

    @Test("목록에 없는 필터를 고르면 시트가 열리지 않는다")
    func ignoresUnknownFilter() {
        // Given 샘플 필터 6개
        // When  "unknown"을 고른다
        // Then  선택된 필터가 없음(nil)
        let viewModel = FilterViewModel()

        viewModel.selectFilter("unknown")

        #expect(viewModel.viewState.selectedFilter == nil)
    }
}
