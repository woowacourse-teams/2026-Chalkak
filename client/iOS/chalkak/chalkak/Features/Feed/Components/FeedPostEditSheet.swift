import SwiftUI

/// 피드 상세의 '수정'으로 여는 게시물 수정 시트 내용.
///
/// 맨 위 줄 왼쪽에 '게시물 수정', 오른쪽에 '저장'을 두고, 그 아래 '제목'(한 줄)과 '이야기'(3–8줄) 칸으로 제목과 설명을 함께 고친다.
/// 이야기 칸 아래에는 작성 화면과 같은 인스타그램 게시 동의 스위치와 ID 칸을 현재 값으로 둔다.
/// 입력값은 `edit`으로 받기만 하고, 바뀐 값은 `onTitleChange`·`onDescriptionChange`·`onInstagramUploadAgreedChange`·`onInstagramIDChange`로 뷰모델에 올린다.
/// `.sheet { FeedPostEditSheet(...) }`처럼 시트 안에 넣는다. 시트 높이·손잡이·배경은 이 뷰가 정한다.
/// 아래로 쓸어내리거나 바깥을 누르면 저장하지 않고 닫힌다(닫기 처리는 `.sheet`의 바인딩이 한다).
/// 저장 중(`isSaving`)에는 입력과 닫기를 막는다. 응답이 오기 전에 닫으면 결과를 볼 곳이 없기 때문이다.
///
/// 예: 제목 "제목", 설명 "이야기"인 게시물이면 두 칸에 그 글이 채워진 채 올라온다.
struct FeedPostEditSheet: View {
    @Environment(\.chalkakTheme) private var theme
    // 인스타그램 묶음을 스위치 끔·켬 두 모양으로 그렸을 때의 높이. 문구 줄 수에 따라 달라져 미리 재서 시트 높이에 더한다.
    @State private var instagramConsentOffHeight = Metrics.instagramConsentFallbackHeight
    @State private var instagramConsentOnHeight = Metrics.instagramConsentFallbackHeight

    let edit: FeedPostEditState
    let isSaving: Bool
    let onTitleChange: (String) -> Void
    let onDescriptionChange: (String) -> Void
    let onInstagramUploadAgreedChange: (Bool) -> Void
    let onInstagramIDChange: (String) -> Void
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

                    ChalkakInstagramConsentField(
                        isAgreed: edit.instagramConsent.isAgreed,
                        instagramID: edit.instagramConsent.instagramID,
                        errorMessage: edit.instagramConsent.errorMessage,
                        isEnabled: !isSaving,
                        onAgreedChange: onInstagramUploadAgreedChange,
                        onInstagramIDChange: onInstagramIDChange
                    )
                    .background(alignment: .top) { instagramConsentHeightProbe }
                    .padding(.top, theme.spacing.lg)
                }
                .padding(.horizontal, theme.spacing.xl)
                .padding(.bottom, theme.spacing.xl)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .padding(.top, Metrics.topPadding)
        .presentationDetents(Set([offDetent, onDetent]), selection: detentSelection)
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

    /// 시트 높이는 스위치 끔·켬 두 칸으로 고정해 두고, 스위치에 맞는 칸을 고른다.
    /// 높이 값 자체를 바꾸면(`presentationDetents`에 새 값) `withAnimation` 안에서도 시트가 한 프레임 만에 바뀐다(iOS 26.5 화면 확인, #939 리뷰).
    /// 고른 칸(`selection`)을 바꾸는 것은 시트가 애니메이션으로 움직이므로, 두 높이를 미리 재서 칸은 그대로 두고 고르기만 바꾼다.
    /// 예: 스위치 꺼짐(제목 2줄 행 44) → 360 + 16 + 44 칸, 켜짐 → 360 + 16 + (44 + ID 칸 + 안내) 칸.
    private var offDetent: PresentationDetent {
        .height(Metrics.sheetHeightWithoutInstagramConsent + theme.spacing.lg + instagramConsentOffHeight)
    }

    private var onDetent: PresentationDetent {
        .height(Metrics.sheetHeightWithoutInstagramConsent + theme.spacing.lg + instagramConsentOnHeight)
    }

    /// 스위치 값으로 고를 칸을 정한다. 사람이 시트를 끌어 다른 칸으로 옮긴 값은 받지 않는다(스위치가 기준).
    private var detentSelection: Binding<PresentationDetent> {
        Binding(
            get: { edit.instagramConsent.isAgreed ? onDetent : offDetent },
            set: { _ in }
        )
    }

    /// 인스타그램 묶음을 스위치 끔·켬 두 모양으로 보이지 않게 그려 높이만 잰다.
    /// 지금 보이는 모양만 재면 반대 모양의 높이를 몰라, 스위치를 바꿀 때 칸 높이를 새로 바꿔야 해서다(위 `offDetent`).
    /// 오류 문구는 줄 수가 달라질 수 있어 지금 값을 넘긴다. ID는 한 줄 고정 높이라 비워 둔다.
    private var instagramConsentHeightProbe: some View {
        ZStack(alignment: .top) {
            instagramConsentProbe(isAgreed: false) { instagramConsentOffHeight = $0 }
            instagramConsentProbe(isAgreed: true) { instagramConsentOnHeight = $0 }
        }
        .hidden()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func instagramConsentProbe(isAgreed: Bool, onHeightChange: @escaping (CGFloat) -> Void) -> some View {
        ChalkakInstagramConsentField(
            isAgreed: isAgreed,
            instagramID: "",
            errorMessage: edit.instagramConsent.errorMessage,
            onAgreedChange: { _ in },
            onInstagramIDChange: { _ in }
        )
        .fixedSize(horizontal: false, vertical: true)
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.height
        } action: { height in
            onHeightChange(height)
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
    // 손잡이 아래 제목 줄 + 제목 칸 + 이야기 칸(3줄) + 아래 여백이 들어가는 높이(인스타그램 묶음이 없던 #938 때 화면 확인으로 정한 값).
    // 키보드가 올라와 시트가 그 위로 밀려도 칸들과 '저장'이 다 보이는 값이다. 인스타그램 묶음은 재서 따로 더한다.
    static let sheetHeightWithoutInstagramConsent: CGFloat = 360
    // 묶음 높이를 재기 전 첫 그림에 쓰는 값. 스위치 행의 최소 터치 높이와 같다.
    static let instagramConsentFallbackHeight: CGFloat = 44
    static let topPadding: CGFloat = 20
    static let headerHeight: CGFloat = 52
    static let touchSize: CGFloat = 44
}

#Preview("Feed Post Edit Sheet") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            FeedPostEditSheet(
                edit: FeedPostEditState(
                    title: "늦은 오후의 골목",
                    description: "퇴근길에 빛이 벽을 타고 내려오는 걸 보고 멈춰 섰어요.",
                    instagramConsent: InstagramConsentInput(isAgreed: true, instagramID: "chalkak.daily")
                ),
                isSaving: false,
                onTitleChange: { _ in },
                onDescriptionChange: { _ in },
                onInstagramUploadAgreedChange: { _ in },
                onInstagramIDChange: { _ in },
                onSave: {}
            )
        }
}
