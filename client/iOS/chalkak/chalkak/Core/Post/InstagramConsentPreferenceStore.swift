import Foundation

/// 다음 작성 화면의 기본값. 게시물 자체의 동의는 서버 상세가 기준이다.
@MainActor
struct InstagramConsentPreferenceStore {
    private let userID: String
    private let defaults: UserDefaults
    private let currentUserID: @MainActor () -> String?
    private let revision: String?

    init(
        userID: String,
        defaults: UserDefaults = .standard,
        currentUserID: @escaping @MainActor () -> String? = { KeychainSessionStore.userID() }
    ) {
        self.userID = userID
        self.defaults = defaults
        self.currentUserID = currentUserID
        self.revision = defaults.string(forKey: Self.revisionKey(userID))
    }

    func load() -> InstagramConsent? {
        guard isCurrent,
              let data = defaults.data(forKey: Self.valueKey(userID)),
              let record = try? JSONDecoder().decode(Record.self, from: data)
        else { return nil }
        return record.consent
    }

    func save(_ consent: InstagramConsent) {
        guard isCurrent else { return }
        let normalized = InstagramConsentInput(consent: consent).consent
        guard let data = try? JSONEncoder().encode(Record(agreed: normalized.isAgreed, instagramID: normalized.instagramID))
        else { return }
        defaults.set(data, forKey: Self.valueKey(userID))
    }

    /// 탈퇴 전에 만든 저장소가 늦은 응답으로 삭제한 값을 되살리지 못하게 한다.
    static func remove(userID: String, defaults: UserDefaults = .standard) {
        defaults.set(UUID().uuidString, forKey: revisionKey(userID))
        defaults.removeObject(forKey: valueKey(userID))
    }

    private var isCurrent: Bool {
        !userID.isEmpty && currentUserID() == userID
            && defaults.string(forKey: Self.revisionKey(userID)) == revision
    }

    private static func valueKey(_ userID: String) -> String { "instagramConsent.preference.\(userID)" }
    private static func revisionKey(_ userID: String) -> String { "instagramConsent.revision.\(userID)" }

    private struct Record: Codable {
        let agreed: Bool
        let instagramID: String?

        var consent: InstagramConsent {
            InstagramConsentInput(consent: InstagramConsent(isAgreed: agreed, instagramID: instagramID)).consent
        }
    }
}
