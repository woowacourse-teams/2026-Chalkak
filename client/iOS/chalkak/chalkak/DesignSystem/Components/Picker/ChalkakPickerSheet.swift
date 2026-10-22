import SwiftUI

/// 가운데 제목, 오른쪽 "완료" 버튼, 그 아래 휠([ChalkakWheelPicker])로 된 시트 내용.
/// Android `ChalkakPickerBottomSheet`와 역할을 맞춘다.
///
/// - `.sheet { ChalkakPickerSheet(...) }`처럼 시트 안에 넣는다. 시트 높이·손잡이·배경은 이 뷰가 정한다.
/// - 휠을 굴리는 동안의 임시 선택값은 호출부가 들고 있다가 `onConfirm`에서 적용한다.
/// - 아래로 쓸어내려 닫으면 `onConfirm`을 부르지 않는다.
struct ChalkakPickerSheet<Wheels: View>: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    var confirmTitle = "완료"
    let onConfirm: () -> Void
    @ViewBuilder let wheels: () -> Wheels

    var body: some View {
        let content = VStack(spacing: 0) {
            header
            wheels()
                .padding(.horizontal, theme.spacing.lg)
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .padding(.top, Metrics.topPadding)
        .presentationDetents([.height(Metrics.sheetHeight)])
        .presentationDragIndicator(.visible)

        if ChalkakPlatformAppearance.usesLiquidGlass {
            content
        } else {
            content
                .background(theme.colors.surfaceElevated)
                .presentationCornerRadius(theme.shapes.sheet)
                .presentationBackground(theme.colors.surfaceElevated)
        }
    }

    private var header: some View {
        ZStack {
            Text(title)
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
                .accessibilityAddTraits(.isHeader)

            HStack {
                Spacer(minLength: 0)
                Button(action: onConfirm) {
                    Text(confirmTitle)
                        .font(theme.typography.headline)
                        .foregroundStyle(theme.colors.textPrimary)
                        .frame(minWidth: Metrics.touchSize, minHeight: Metrics.touchSize)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .frame(height: Metrics.headerHeight)
        .padding(.horizontal, theme.spacing.lg)
    }
}

private enum Metrics {
    // 손잡이 아래 제목 줄 + 시스템 휠 높이(216pt) + 아래 여백이 들어가는 높이.
    static let sheetHeight: CGFloat = 340
    static let topPadding: CGFloat = 20
    static let headerHeight: CGFloat = 52
    static let touchSize: CGFloat = 44
}

#Preview("Picker Sheet") {
    @Previewable @State var monthIndex = 1
    Color.clear
        .sheet(isPresented: .constant(true)) {
            ChalkakPickerSheet(title: "날짜 선택", onConfirm: {}) {
                ChalkakWheelPicker(columns: [
                    ChalkakWheelColumn(accessibilityLabel: "연도", titles: ["2026년"], selectedIndex: 0, onSelect: { _ in }),
                    ChalkakWheelColumn(
                        accessibilityLabel: "월",
                        titles: ["9월", "10월"],
                        selectedIndex: monthIndex,
                        onSelect: { monthIndex = $0 }
                    ),
                ])
            }
        }
        .chalkakTheme(.light)
}
