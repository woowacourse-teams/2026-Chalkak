import SwiftUI

/// 하단 탭 "필터" 화면. 필터 카드를 2열로 보여 주고, 카드를 누르면 상세 시트(`FilterDetailSheet`)를 연다.
/// 로그인 없이 열린다. 필터 API가 없어 `FilterViewModel`의 샘플 6개를 쓴다(#1021).
/// 하단바는 호출부(`ContentView`)가 화면 아래에 붙이고, 이 화면은 스크롤로 하단바를 접고 펴는 값(`bottomBarCompact`)만 바꾼다.
struct FilterScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast

    @Bindable var viewModel: FilterViewModel
    var bottomBarCompact: Binding<Bool> = .constant(false)

    var body: some View {
        ScrollView {
            LazyVGrid(columns: columns, spacing: theme.spacing.xl) {
                ForEach(viewModel.viewState.filters) { filter in
                    FilterCard(filter: filter, onClick: { viewModel.selectFilter(filter.id) })
                        .accessibilityIdentifier("filter.card.\(filter.id)")
                }
            }
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.vertical, theme.spacing.xl)
        }
        .scrollIndicators(.hidden)
        .chalkakBottomBarScrollBehavior(isCompact: bottomBarCompact)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .sheet(item: selectedFilter) { filter in
            FilterDetailSheetContainer(
                viewModel: viewModel,
                filter: filter,
                onUseClick: {
                    // 필터 적용 화면이 아직 없어 시트를 닫고 안내만 띄운다(안드와 같은 문구).
                    viewModel.dismissFilter()
                    showToast("필터 적용은 곧 만나볼 수 있어요")
                }
            )
        }
    }

    private var columns: [GridItem] {
        Array(
            repeating: GridItem(.flexible(), spacing: theme.spacing.md, alignment: .top),
            count: Metrics.columnCount
        )
    }

    /// 시트는 고른 필터가 있을 때 열리고, 끌어내리기·바깥 누르기로 닫히면 선택을 푼다.
    private var selectedFilter: Binding<FilterItem?> {
        Binding(
            get: { viewModel.viewState.selectedFilter },
            set: { if $0 == nil { viewModel.dismissFilter() } }
        )
    }
}

/// 시트 안에서 즐겨찾기 상태를 읽는 뷰. 시트 내용이 뷰모델을 직접 읽어야 별을 누를 때 바로 다시 그려진다.
private struct FilterDetailSheetContainer: View {
    @Bindable var viewModel: FilterViewModel
    let filter: FilterItem
    let onUseClick: () -> Void

    var body: some View {
        FilterDetailSheet(
            filter: filter,
            isFavorite: viewModel.viewState.isFavorite(filter.id),
            onFavoriteToggle: { viewModel.toggleFavorite(filter.id) },
            onUseClick: onUseClick
        )
    }
}

private enum Metrics {
    static let columnCount = 2
}

#Preview("필터 화면") {
    FilterScreen(viewModel: FilterViewModel())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakBottomBar(selectedItem: .filter, onSelect: { _ in }, onAdd: {})
                .padding(.horizontal, ChalkakSpacing.lg)
        }
        .chalkakTheme(.light)
}
