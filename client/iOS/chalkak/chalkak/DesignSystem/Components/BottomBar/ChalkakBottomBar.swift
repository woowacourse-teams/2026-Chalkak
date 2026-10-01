import SwiftUI

enum ChalkakBottomBarItem: String, CaseIterable, Identifiable {
    case today
    case display
    case record
    case settings

    var id: Self { self }

    var label: String {
        switch self {
        case .today:
            "오늘"
        case .display:
            "전시"
        case .record:
            "기록"
        case .settings:
            "설정"
        }
    }

    var iconName: String {
        switch self {
        case .today:
            "ic_bottom_today"
        case .display:
            "ic_bottom_display"
        case .record:
            "ic_bottom_record"
        case .settings:
            "ic_bottom_setting"
        }
    }
}

struct ChalkakBottomBar: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let selectedItem: ChalkakBottomBarItem
    @Binding var isCompact: Bool
    let onSelect: (ChalkakBottomBarItem) -> Void
    let onAdd: () -> Void

    init(
        selectedItem: ChalkakBottomBarItem,
        isCompact: Binding<Bool> = .constant(false),
        onSelect: @escaping (ChalkakBottomBarItem) -> Void,
        onAdd: @escaping () -> Void
    ) {
        self.selectedItem = selectedItem
        _isCompact = isCompact
        self.onSelect = onSelect
        self.onAdd = onAdd
    }

    var body: some View {
        HStack(spacing: theme.spacing.none) {
            itemButton(.today)
            itemButton(.display)
            addButton
            itemButton(.record)
            itemButton(.settings)
        }
        .padding(isCompact ? theme.spacing.xs : theme.spacing.sm)
        .glassEffect(.regular, in: Capsule())
        // 축소 중에도 화면의 스크롤 영역 높이를 유지해 오프셋 변화로 다시 확대되지 않게 한다.
        .frame(height: Metrics.expandedHeight, alignment: .bottom)
    }

    private func itemButton(_ item: ChalkakBottomBarItem) -> some View {
        let isSelected = item == selectedItem
        return Button {
            performAction { onSelect(item) }
        } label: {
            Image(item.iconName)
                .resizable()
                .renderingMode(.template)
                .scaledToFit()
                .frame(width: Metrics.iconSize, height: Metrics.iconSize)
                .scaleEffect(isCompact ? Metrics.compactIconSize / Metrics.iconSize : 1)
                .foregroundStyle(theme.colors.iconPrimary)
                .frame(maxWidth: .infinity, minHeight: touchSize)
                .background {
                    if isSelected {
                        Capsule()
                            .fill(theme.colors.iconPrimary.opacity(Metrics.selectionOpacity))
                    }
                }
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(item.label)
        .accessibilityValue(isSelected ? "선택됨" : "")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var addButton: some View {
        Button(action: { performAction(onAdd) }) {
            Image(systemName: "plus")
                .font(.system(size: Metrics.addIconSize, weight: .regular))
                .frame(width: Metrics.addIconSize, height: Metrics.addIconSize)
                .scaleEffect(isCompact ? Metrics.compactAddIconSize / Metrics.addIconSize : 1)
                .foregroundStyle(theme.colors.iconPrimary)
                .frame(maxWidth: .infinity, minHeight: touchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("추가")
    }

    private func performAction(_ action: @escaping () -> Void) {
        guard isCompact else {
            action()
            return
        }
        withAnimation(reduceMotion ? nil : .smooth(duration: Metrics.expansionDuration)) {
            isCompact = false
            action()
        }
    }

    private var touchSize: CGFloat {
        isCompact ? Metrics.compactTouchSize : Metrics.minimumTouchSize
    }
}

private enum Metrics {
    static let iconSize: CGFloat = 26
    static let compactIconSize: CGFloat = 23
    static let addIconSize: CGFloat = 28
    static let compactAddIconSize: CGFloat = 25
    static let minimumTouchSize: CGFloat = 44
    static let compactTouchSize: CGFloat = 44
    static let expandedHeight: CGFloat = 60
    static let selectionOpacity = 0.06
    static let expansionDuration = 0.25
}

private enum PreviewMetrics {
    static let screenWidth: CGFloat = 402
    static let screenHeight: CGFloat = 874
    static let bottomSafeArea: CGFloat = 34
}

#Preview("Bottom Bar") {
    @Previewable @State var selection = ChalkakBottomBarItem.today

    ZStack(alignment: .bottom) {
        ChalkakTheme.light.colors.background
            .ignoresSafeArea()

        VStack(spacing: 0) {
            Spacer(minLength: 0)

            ChalkakBottomBar(
                selectedItem: selection,
                onSelect: { selection = $0 },
                onAdd: {}
            )
            .frame(maxWidth: .infinity)
            .padding(.horizontal, ChalkakSpacing.lg)
            .padding(.bottom, ChalkakSpacing.sm)

            Color.clear
                .frame(height: PreviewMetrics.bottomSafeArea)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    .frame(width: PreviewMetrics.screenWidth, height: PreviewMetrics.screenHeight)
    .chalkakTheme(.light)
}
