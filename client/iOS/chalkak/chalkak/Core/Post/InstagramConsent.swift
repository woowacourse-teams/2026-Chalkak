import Foundation

/// 공식 인스타그램 게시 동의. 게시물 작성·수정 요청에 실어 보내고, 본인 게시물 상세에서 받는다(POST-013).
///
/// `instagramID`는 사용자가 입력한 그대로다. 맨 앞 `@` 제거·소문자 저장·형식 검사는 서버가 한다.
/// 예: 동의하고 ID를 비웠으면 `InstagramConsent(isAgreed: true, instagramID: nil)`
struct InstagramConsent: Equatable, Sendable {
    var isAgreed: Bool
    var instagramID: String?

    static let notAgreed = InstagramConsent(isAgreed: false, instagramID: nil)
}

/// 작성 화면과 수정 시트의 인스타그램 게시 동의 스위치·ID 칸 입력 상태.
///
/// 스위치를 꺼도 입력한 ID 글자는 남겨 두어 다시 켜면 그대로 보이지만, 요청에는 싣지 않는다(`consent`).
/// `errorMessage`는 서버가 ID 형식을 거절했을 때 ID 칸 아래에 보여 줄 문구이고, ID를 고치면 지운다.
struct InstagramConsentInput: Equatable, Sendable {
    var isAgreed = false
    var instagramID = ""
    var errorMessage: String?

    init(isAgreed: Bool = false, instagramID: String = "", errorMessage: String? = nil) {
        self.isAgreed = isAgreed
        self.instagramID = instagramID
        self.errorMessage = errorMessage
    }

    /// 상세에서 받은 동의로 입력을 채운다. ID가 없으면 빈 칸이다.
    init(consent: InstagramConsent) {
        self.init(isAgreed: consent.isAgreed, instagramID: consent.instagramID ?? "")
    }

    /// 요청에 실을 동의. 스위치가 꺼져 있으면 ID를 보내지 않고, 켜져 있어도 공백뿐인 ID는 없음(nil)으로 본다.
    /// 예: 켜짐 + "chalkak.daily" → (true, "chalkak.daily"), 켜짐 + "   " → (true, nil), 꺼짐 + "chalkak.daily" → (false, nil)
    var consent: InstagramConsent {
        guard isAgreed else { return .notAgreed }
        let isBlank = instagramID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return InstagramConsent(isAgreed: true, instagramID: isBlank ? nil : instagramID)
    }

    /// 스위치를 바꾼다. 꺼지면 ID 칸이 보이지 않으므로 ID 오류도 지운다.
    mutating func setAgreed(_ isAgreed: Bool) {
        self.isAgreed = isAgreed
        if !isAgreed {
            errorMessage = nil
        }
    }

    /// ID를 바꾼다. 서버의 ID 최대 길이(30자)까지만 받고, 글자가 바뀌면 이전 ID 오류를 지운다.
    mutating func setInstagramID(_ instagramID: String) {
        let limited = instagramID.limited(to: Self.instagramIDMaxLength, using: .characters)
        guard limited != self.instagramID else { return }
        self.instagramID = limited
        errorMessage = nil
    }

    /// 서버가 ID 형식을 거절했을 때 ID 칸 아래에 오류를 띄운다.
    mutating func showInvalidInstagramID() {
        errorMessage = Self.invalidInstagramIDMessage
    }

    static let instagramIDMaxLength = 30
    static let invalidInstagramIDMessage = "영문, 숫자, 마침표(.), 밑줄(_)만 쓸 수 있어요"
    /// 서버가 ID 형식 오류일 때 보내는 400 응답 message. errorCode가 다른 400과 같아 이 문구로만 구분한다.
    static let serverInvalidInstagramIDMessage = "인스타그램 ID 형식이 올바르지 않습니다."
}
