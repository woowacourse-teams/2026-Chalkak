import SwiftUI

/// 피드 상세의 '수정'으로 여는 게시물 수정 시트 내용.
///
/// 맨 위 줄 왼쪽에 '게시물 수정', 오른쪽에 '저장'을 두고, 그 아래 '제목'(한 줄)과 '이야기'(3–8줄) 칸으로 제목과 설명을 함께 고친다.
/// 입력값은 `edit`으로 받기만 하고, 바뀐 글은 `onTitleChange`·`onDescriptionChange`로 뷰모델에 올린다.
/// `.sheet { FeedPostEditSheet(...) }`처럼 시트 안에 넣는다. 시트 높이·손잡이·배경은 이 뷰가 정한다.
/// 아래로 쓸어내리거나 바깥을 누르면 저장하지 않고 닫힌다(닫기 처리는 `.sheet`의 바인딩이 한다).
/// 저장 중(`isSaving`)에는 입력과 닫기를 막는다. 응답이 오기 전에 닫으면 결과를 볼 곳이 없기 때문이다.
///
/// 예: 제목 "제목", 설명 "이야기"인 게시물이면 두 칸에 그 글이 채워진 채 올라온다.
struct FeedPostEditSheet: View {
    @Environment(\.chalkakTheme) private var theme

    let edit: FeedPostEditState
    let isSaving: Bool
    let onTitleChange: (String) -> Void
    let onDescriptionChange: (String) -> Void
    let onSave: () -> Void

    var body: some View {
        let content = VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    sectionLabel("제목")
                        .padding(.bottom, theme.spacing.sm)
                    ChalkakTextField(
                        text: Binding(get: { edit.title }, set: onTitleChange),
                        label: "작품 제목",
                        placeholder: "작품 제목은 선택이에요.",
                        isEnabled: !isSaving,
                        lineLimit: 1...1,
                        textFont: theme.typography.subheadline,
                        maximumCharacterCount: Constants.titleMaxLength,
                        // 서버가 제목·설명 길이를 code point로 검증하므로(POST-005) 같은 단위로 센다.
                        lengthMetric: .unicodeScalars
                    )
                    .accessibilityIdentifier("feed.postEdit.title")
                    .padding(.bottom, theme.spacing.lg)

                    sectionLabel("이야기")
                        .padding(.bottom, theme.spacing.sm)
                    ChalkakTextField(
                        text: Binding(get: { edit.description }, set: onDescriptionChange),
                        label: "작품 이야기",
                        placeholder: "작품 이야기는 선택이에요.",
                        isEnabled: !isSaving,
                        lineLimit: Constants.descriptionLineLimit,
                        textFont: theme.typography.subheadline,
                        maximumCharacterCount: Constants.descriptionMaxLength,
                        lengthMetric: .unicodeScalars
                    )
                    .accessibilityIdentifier("feed.postEdit.description")
                }
                .padding(.horizontal, theme.spacing.xl)
                .padding(.bottom, theme.spacing.xl)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .padding(.top, Metrics.topPadding)
        .presentationDetents([.height(Metrics.sheetHeight)])
        .presentationDragIndicator(.visible)
        // 저장 중에는 아래로 끌거나 바깥을 눌러 닫지 못하게 한다.
        .interactiveDismissDisabled(isSaving)

        if ChalkakPlatformAppearance.usesLiquidGlass {
            content
        } else {
            content
                .background(theme.colors.surfaceElevated)
                .presentationCornerRadius(theme.shapes.sheet)
                .presentationBackground(theme.colors.surfaceElevated)
        }
    }

    /// 맨 위 줄: 왼쪽 '게시물 수정', 오른쪽 '저장'. 저장 중에는 "저장 중..."으로 바꾸고 누를 수 없게 한다.
    private var header: some View {
        HStack {
            Text("게시물 수정")
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: theme.spacing.md)
            Button(action: onSave) {
                Text(isSaving ? "저장 중..." : "저장")
                    .font(theme.typography.headline)
                    .foregroundStyle(isSaving ? theme.colors.textMuted : theme.colors.actionPrimary)
                    .frame(minWidth: Metrics.touchSize, minHeight: Metrics.touchSize, alignment: .trailing)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(isSaving)
            .accessibilityIdentifier("feed.postEdit.save")
        }
        .frame(height: Metrics.headerHeight)
        .padding(.horizontal, theme.spacing.xl)
    }

    private func sectionLabel(_ title: String) -> some View {
        Text(title)
            .font(theme.typography.caption)
            .fontWeight(.semibold)
            .foregroundStyle(theme.colors.textPrimary)
            .accessibilityHidden(true)
    }

    private enum Constants {
        // 서버 입력 규칙(POST-005)과 같은 최대 길이. 전시하기의 이야기는 150자지만 수정은 서버 기준 300자로 받는다(#938 계획).
        static let titleMaxLength = 10
        static let descriptionMaxLength = 300
        static let descriptionLineLimit = 3...8
    }
}

private enum Metrics {
    // 손잡이 아래 제목 줄 + 제목 칸 + 이야기 칸(3줄) + 아래 여백이 들어가는 높이.
    // 키보드가 올라와 시트가 그 위로 밀려도 두 칸과 '저장'이 다 보이는 값으로 화면 확인에서 정했다.
    static let sheetHeight: CGFloat = 360
    static let topPadding: CGFloat = 20
    static let headerHeight: CGFloat = 52
    static let touchSize: CGFloat = 44
}

#Preview("Feed Post Edit Sheet") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            FeedPostEditSheet(
                edit: FeedPostEditState(title: "늦은 오후의 골목", description: "퇴근길에 빛이 벽을 타고 내려오는 걸 보고 멈춰 섰어요."),
                isSaving: false,
                onTitleChange: { _ in },
                onDescriptionChange: { _ in },
                onSave: {}
            )
        }
}
