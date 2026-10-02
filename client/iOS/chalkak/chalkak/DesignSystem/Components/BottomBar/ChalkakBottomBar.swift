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
    @GestureState private var dragValue: DragGesture.Value?
    @State private var barWidth: CGFloat = 0
    @State private var lensSlot: CGFloat
    @State private var isSelectionMoving = false
    @State private var selectionVelocity: CGFloat = 0
    @State private var selectionMotionTask: Task<Void, Never>?
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
        _lensSlot = State(initialValue: Self.lensSlot(for: selectedItem))
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
        .padding(barPadding)
        .background {
            GeometryReader { proxy in
                let width = slotWidth(in: proxy.size.width) * (isDragging ? Metrics.dragLensWidthScale : 1)
                Capsule()
                    .fill(.clear)
                    .frame(width: width, height: isDragging ? Metrics.dragLensHeight : touchSize)
                    .glassEffect(
                        .clear.interactive(),
                        in: Capsule()
                    )
                    .overlay {
                        Capsule()
                            .fill(LinearGradient(
                                colors: [.white.opacity(0.28), .clear, .white.opacity(0.08)],
                                startPoint: .topLeading, endPoint: .bottomTrailing
                            ))
                    }
                    .overlay {
                        Capsule().strokeBorder(LinearGradient(
                            colors: [.white.opacity(0.95), .white.opacity(0.2), .white.opacity(0.65)],
                            startPoint: dragVelocity < 0 ? .topTrailing : .topLeading,
                            endPoint: dragVelocity < 0 ? .bottomLeading : .bottomTrailing
                        ), lineWidth: Metrics.dragLensBorderWidth)
                    }
                    .shadow(color: .white.opacity(0.6), radius: 2, y: -1)
                    .shadow(color: theme.colors.iconPrimary.opacity(0.18), radius: 5, y: 2)
                    .scaleEffect(
                        x: 1 + dragStretch * Metrics.dragStretchWidth,
                        y: 1 - dragStretch * Metrics.dragStretchHeight
                    )
                    .animation(reduceMotion ? nil : .smooth(duration: Metrics.dragAnimationDuration), value: isDragging)
                    .position(
                        x: min(max(dragLocation?.x ?? selectedLensX(in: proxy.size.width), barPadding + width / 2), proxy.size.width - barPadding - width / 2),
                        y: proxy.size.height / 2
                    )
                    // 손가락 위치가 연속 갱신돼도 이동 중인 렌즈의 속도를 이어간다.
                    .animation(
                        reduceMotion ? nil : .interactiveSpring(
                            response: Metrics.dragLensResponse,
                            dampingFraction: Metrics.dragLensDamping,
                            blendDuration: Metrics.dragLensBlendDuration
                        ),
                        value: dragLocation?.x
                    )
                    .animation(reduceMotion ? nil : .smooth(duration: Metrics.selectionDuration), value: lensSlot)
            }
            .allowsHitTesting(false)
        }
        .background {
            // 바와 움직이는 렌즈를 각각 렌더링해 렌즈의 반사와 굴절이 묻히지 않게 한다.
            Capsule().fill(.clear).glassEffect(.regular.interactive(), in: Capsule())
        }
        .contentShape(Capsule())
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { barWidth = $0 }
        .coordinateSpace(name: Metrics.dragCoordinateSpace)
        .scaleEffect(isDragging ? Metrics.dragScale : 1, anchor: .bottom)
        .animation(reduceMotion ? nil : .smooth(duration: Metrics.dragAnimationDuration), value: isDragging)
        .animation(reduceMotion ? nil : .smooth(duration: Metrics.expansionDuration), value: isCompact)
        .highPriorityGesture(tabScrubGesture)
        .onChange(of: isDragging) { _, dragging in
            if dragging {
                selectionMotionTask?.cancel()
                isSelectionMoving = false
            }
            guard dragging, isCompact else { return }
            // 외부 폭과 내부 패딩·아이콘 크기를 함께 펼쳐 모든 드래그에 같은 크기를 사용한다.
            withAnimation(reduceMotion ? nil : .smooth(duration: Metrics.expansionDuration)) {
                isCompact = false
            }
        }
        .onChange(of: selectedItem) { previous, current in
            selectionMotionTask?.cancel()
            lensSlot = Self.lensSlot(for: current)
            guard !reduceMotion, !isDragging else {
                isSelectionMoving = false
                return
            }
            selectionVelocity = (lensX(for: current, in: barWidth) - lensX(for: previous, in: barWidth))
                / Metrics.selectionDuration
            isSelectionMoving = true
            selectionMotionTask = Task { @MainActor in
                try? await Task.sleep(for: .seconds(Metrics.selectionDuration))
                guard !Task.isCancelled else { return }
                isSelectionMoving = false
            }
        }
        .onDisappear {
            selectionMotionTask?.cancel()
            isSelectionMoving = false
        }
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
                .scaleEffect(draggedItem == item ? Metrics.dragIconScale : 1)
                .animation(reduceMotion ? nil : .smooth(duration: Metrics.dragAnimationDuration), value: draggedItem)
                .foregroundStyle(theme.colors.iconPrimary)
                .frame(maxWidth: .infinity, minHeight: touchSize)
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
                .scaleEffect(draggedSlot == 2 ? Metrics.dragIconScale : 1)
                .animation(reduceMotion ? nil : .smooth(duration: Metrics.dragAnimationDuration), value: draggedSlot)
                .foregroundStyle(theme.colors.iconPrimary)
                .frame(maxWidth: .infinity, minHeight: touchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("추가")
    }

    private func performAction(_ action: @escaping () -> Void) {
        let duration = isCompact ? Metrics.expansionDuration : Metrics.selectionDuration
        withAnimation(reduceMotion ? nil : .smooth(duration: duration)) {
            isCompact = false
            action()
        }
    }

    private var dragLocation: CGPoint? { dragValue?.location }

    private var dragVelocity: CGFloat {
        dragValue?.velocity.width ?? (isSelectionMoving ? selectionVelocity : 0)
    }

    private var dragStretch: CGFloat {
        reduceMotion || !isDragging ? 0 : min(abs(dragVelocity) / Metrics.dragStretchVelocity, 1)
    }

    private var isDragging: Bool { dragValue != nil }

    private var barPadding: CGFloat { isCompact ? theme.spacing.xs : theme.spacing.sm }

    private var draggedItem: ChalkakBottomBarItem? {
        dragLocation.flatMap { item(at: $0.x) }
    }

    private func selectedLensX(in width: CGFloat) -> CGFloat {
        barPadding + slotWidth(in: width) * (lensSlot + 0.5)
    }

    private func lensX(for item: ChalkakBottomBarItem, in width: CGFloat) -> CGFloat {
        barPadding + slotWidth(in: width) * (Self.lensSlot(for: item) + 0.5)
    }

    private static func lensSlot(for item: ChalkakBottomBarItem) -> CGFloat {
        switch item {
        case .today: 0
        case .display: 1
        case .record: 3
        case .settings: 4
        }
    }

    private func slotWidth(in width: CGFloat) -> CGFloat {
        max(0, width - barPadding * 2) / Metrics.slotCount
    }

    private var draggedSlot: Int? {
        dragLocation.flatMap { slot(at: $0.x) }
    }

    private func slot(at x: CGFloat) -> Int? {
        let width = slotWidth(in: barWidth)
        guard width > 0 else { return nil }
        return min(max(Int(floor((x - barPadding) / width)), 0), Int(Metrics.slotCount) - 1)
    }

    private func item(at x: CGFloat) -> ChalkakBottomBarItem? {
        switch slot(at: x) {
        case 0: return .today
        case 1: return .display
        case 3: return .record
        case 4: return .settings
        default: return nil
        }
    }

    private var tabScrubGesture: some Gesture {
        DragGesture(minimumDistance: Metrics.dragMinimumDistance, coordinateSpace: .named(Metrics.dragCoordinateSpace))
            .updating($dragValue) { value, state, _ in
                // 클릭은 각 버튼이 처리하고, 실제 좌우 드래그가 시작될 때만 렌즈를 이동한다.
                guard state != nil || abs(value.translation.width) > abs(value.translation.height) else { return }
                state = value
            }
            .onEnded { value in
                guard abs(value.translation.width) > abs(value.translation.height) else { return }
                // 드래그 중에는 강조만 이동하고 손을 뗀 위치의 동작만 실행한다.
                if slot(at: value.location.x) == 2 {
                    performAction(onAdd)
                } else if let item = item(at: value.location.x), item != selectedItem {
                    // 이미 손가락을 따라 도착한 렌즈는 탭 이동 애니메이션을 다시 시작하지 않는다.
                    withAnimation(nil) {
                        lensSlot = Self.lensSlot(for: item)
                    }
                    performAction { onSelect(item) }
                }
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
    static let selectionDuration = 0.5
    static let expansionDuration = 0.75
    static let slotCount: CGFloat = 5
    static let dragCoordinateSpace = "chalkakBottomBarDrag"
    static let dragMinimumDistance: CGFloat = 8
    static let dragScale: CGFloat = 1.04
    static let dragIconScale: CGFloat = 1.16
    static let dragLensWidthScale: CGFloat = 1.06
    static let dragLensHeight: CGFloat = 56
    static let dragLensBorderWidth: CGFloat = 1.25
    static let dragStretchVelocity: CGFloat = 1600
    static let dragStretchWidth: CGFloat = 0.08
    static let dragStretchHeight: CGFloat = 0.04
    static let dragAnimationDuration = 0.16
    static let dragLensResponse = 0.42
    static let dragLensDamping = 0.9
    static let dragLensBlendDuration = 0.12
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
