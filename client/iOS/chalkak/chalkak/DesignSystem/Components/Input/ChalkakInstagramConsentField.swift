import SwiftUI

/// 게시물 작성 화면과 수정 시트의 이야기 칸 아래에 두는 인스타그램 게시 동의 스위치와 인스타그램 ID 칸.
///
/// 스위치 행: 제목 "작가님의 작품을 찰칵 인스타그램에서 만날 수 있게 해도 될까요?" 한 줄만 둔다.
/// 제목은 '제목'·'이야기' 칸 이름과 같은 굵은 캡션 글씨이고, 길면 스위치와 겹치지 않게 줄을 바꾼다.
/// 스위치가 켜지면 앞에 `@`가 붙은 ID 칸과 안내 "작성하신 계정을 올릴 때 태그해드릴게요(선택)"가 펼쳐지고, 꺼지면 둘 다 사라진다.
/// ID 칸 오른쪽에는 제목 칸처럼 글자 수 "0 / 30"을 보여 준다.
/// `errorMessage`가 있으면 안내 자리에 오류 색으로 그 문구를 보여 준다(서버가 ID 형식을 거절했을 때).
/// 값은 받기만 하고 바뀐 값은 `onAgreedChange`·`onInstagramIDChange`로 올린다. ID 칸은 30자까지만 받는다(POST-013, `InstagramConsentInput`과 같은 값).
/// 폭과 바깥 여백은 호출하는 화면이 정한다.
///
/// 예: `ChalkakInstagramConsentField(isAgreed: true, instagramID: "chalkak.daily", errorMessage: nil, ...)`
///     → 켜진 스위치 아래 "@ chalkak.daily     13 / 30" 칸과 태그 안내
struct ChalkakInstagramConsentField: View {
    @Environment(\.chalkakTheme) private var theme

    let isAgreed: Bool
    let instagramID: String
    let errorMessage: String?
    var isEnabled = true
    let onAgreedChange: (Bool) -> Void
    let onInstagramIDChange: (String) -> Void
    var onInstagramIDFocusChange: ((Bool) -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Toggle(isOn: Binding(get: { isAgreed }, set: onAgreedChange)) {
                Text("작가님의 작품을 찰칵 인스타그램에서 만날 수 있게 해도 될까요?")
                    .font(theme.typography.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(theme.colors.textPrimary)
                    .multilineTextAlignment(.leading)
                    // 문구가 길어 스위치 앞 남은 폭에서 줄을 바꾸고, 줄 수만큼 행 높이를 늘린다.
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.trailing, theme.spacing.md)
            }
            .toggleStyle(.switch)
            .tint(theme.colors.actionPrimary)
            .disabled(!isEnabled)
            .frame(minHeight: Metrics.minimumTouchHeight)
            .accessibilityValue(isAgreed ? "켬" : "끔")
            .accessibilityIdentifier("instagramConsent.toggle")

            if isAgreed {
                ChalkakTextField(
                    text: Binding(get: { instagramID }, set: onInstagramIDChange),
                    label: "인스타그램 ID",
                    placeholder: "인스타그램 ID",
                    isEnabled: isEnabled,
                    lineLimit: 1...1,
                    textFont: theme.typography.subheadline,
                    maximumCharacterCount: InstagramConsentInput.instagramIDMaxLength,
                    height: Metrics.fieldHeight,
                    leadingText: "@",
                    onFocusChange: onInstagramIDFocusChange
                )
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.asciiCapable)
                .accessibilityIdentifier("instagramConsent.instagramID")
                .padding(.top, theme.spacing.md)

                Text(errorMessage ?? "작성하신 계정을 올릴 때 태그해드릴게요(선택)")
                    .font(theme.typography.caption)
                    .foregroundStyle(errorMessage == nil ? theme.colors.textMuted : theme.colors.error)
                    .padding(.top, theme.spacing.sm)
                    .accessibilityIdentifier("instagramConsent.hint")
            }
        }
    }
}

private enum Metrics {
    static let minimumTouchHeight: CGFloat = 44
    // 작성 화면의 제목 칸과 같은 한 줄 칸 높이. 빈 칸이 포커스 전후로 움직이지 않게 고정한다(#945).
    static let fieldHeight: CGFloat = 54
}

#Preview("Instagram Consent Field") {
    @Previewable @State var isAgreed = true
    @Previewable @State var instagramID = "chalkak.daily"

    VStack(spacing: ChalkakSpacing.xl) {
        ChalkakInstagramConsentField(
            isAgreed: false,
            instagramID: "",
            errorMessage: nil,
            onAgreedChange: { _ in },
            onInstagramIDChange: { _ in }
        )
        ChalkakInstagramConsentField(
            isAgreed: isAgreed,
            instagramID: instagramID,
            errorMessage: nil,
            onAgreedChange: { isAgreed = $0 },
            onInstagramIDChange: { instagramID = $0 }
        )
        ChalkakInstagramConsentField(
            isAgreed: true,
            instagramID: "chalkak-daily",
            errorMessage: "영문, 숫자, 마침표(.), 밑줄(_)만 쓸 수 있어요",
            onAgreedChange: { _ in },
            onInstagramIDChange: { _ in }
        )
    }
    .padding(ChalkakSpacing.xl)
}
