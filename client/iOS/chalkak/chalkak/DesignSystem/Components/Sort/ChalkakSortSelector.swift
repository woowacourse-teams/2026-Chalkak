import SwiftUI

struct ChalkakSortSelector<Option: Hashable>: View {
    @Environment(\.chalkakColors) private var colors

    let options: [Option]
    let selectedOption: Option
    let label: (Option) -> String
    let onSelect: (Option) -> Void

    var body: some View {
        HStack(spacing: ChalkakSpacing.lg) {
            ForEach(options, id: \.self) { option in
                sortButton(for: option)
            }
        }
    }

    private func sortButton(for option: Option) -> some View {
        let isSelected = option == selectedOption

        return Button {
            onSelect(option)
        } label: {
            Text(label(option))
                .font(ChalkakTypography.subheadline)
                .foregroundStyle(
                    isSelected ? colors.textPrimary : colors.textInactive
                )
                .padding(.bottom, ChalkakSpacing.xs)
                .overlay(alignment: .bottom) {
                    if isSelected {
                        Rectangle()
                            .fill(colors.textPrimary)
                            .frame(height: Metrics.indicatorHeight)
                    }
                }
                .frame(minHeight: Metrics.minimumTouchHeight)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityValue(isSelected ? "선택됨" : "")
    }
}

private enum Metrics {
    static let indicatorHeight: CGFloat = 1
    static let minimumTouchHeight: CGFloat = 44
}

private enum PreviewSort: String, CaseIterable {
    case latest = "최신순"
    case popular = "인기순"
    case random = "랜덤순"
}

#Preview("Sort Selector") {
    @Previewable @State var selection = PreviewSort.latest

    ChalkakSortSelector(
        options: PreviewSort.allCases,
        selectedOption: selection,
        label: \.rawValue,
        onSelect: { selection = $0 }
    )
    .padding()
}
