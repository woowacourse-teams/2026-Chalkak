import Foundation
import Testing

@testable import chalkak

@MainActor
struct InstagramConsentPreferenceStoreTests {
    @Test("계정별로 복원하고 미동의와 빈 ID를 요청 의미로 저장한다")
    func restoresAccountScopedPreference() throws {
        let name = "instagram-consent-tests.\(UUID())"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let first = InstagramConsentPreferenceStore(userID: "a", defaults: defaults, currentUserID: { "a" })
        let second = InstagramConsentPreferenceStore(userID: "b", defaults: defaults, currentUserID: { "b" })
        #expect(first.load() == nil)
        first.save(InstagramConsent(isAgreed: true, instagramID: "chalkak.daily"))
        let reopened = InstagramConsentPreferenceStore(userID: "a", defaults: try #require(UserDefaults(suiteName: name)), currentUserID: { "a" })
        #expect(reopened.load() == InstagramConsent(isAgreed: true, instagramID: "chalkak.daily"))
        #expect(second.load() == nil)
        first.save(InstagramConsent(isAgreed: false, instagramID: "hidden"))
        #expect(first.load() == .notAgreed)
        first.save(InstagramConsent(isAgreed: true, instagramID: "   "))
        #expect(first.load() == InstagramConsent(isAgreed: true, instagramID: nil))
    }

    @Test("다른 계정으로 전환하거나 탈퇴한 뒤 늦은 저장을 무시한다")
    func rejectsStaleWrites() throws {
        let name = "instagram-consent-tests.\(UUID())"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        var current = "a"
        let store = InstagramConsentPreferenceStore(userID: "a", defaults: defaults, currentUserID: { current })
        let agreed = InstagramConsent(isAgreed: true, instagramID: "old")
        store.save(agreed)
        current = "b"
        store.save(.notAgreed)
        #expect(store.load() == nil)
        current = "a"
        #expect(store.load() == agreed)
        InstagramConsentPreferenceStore.remove(userID: "a", defaults: defaults)
        store.save(agreed)
        let reopened = InstagramConsentPreferenceStore(userID: "a", defaults: defaults, currentUserID: { current })
        #expect(reopened.load() == nil)
        reopened.save(.notAgreed)
        #expect(reopened.load() == .notAgreed)
    }

    @Test("새 작성만 캐시로 채우며 명시적 초기 상태는 우선한다")
    func uploadInitialStatePrecedence() throws {
        let name = "instagram-consent-tests.\(UUID())"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let store = InstagramConsentPreferenceStore(userID: "a", defaults: defaults, currentUserID: { "a" })
        store.save(InstagramConsent(isAgreed: true, instagramID: "saved"))
        let fresh = PhotoUploadViewModel(topicDate: Date(), consentPreferenceStore: store)
        #expect(fresh.viewState.instagramConsent == InstagramConsentInput(isAgreed: true, instagramID: "saved"))
        let explicit = PhotoUploadViewModel(topicDate: Date(), initialState: PhotoUploadViewState(), consentPreferenceStore: store)
        #expect(explicit.viewState.instagramConsent == InstagramConsentInput())
        fresh.handle(.instagramIDChanged("draft"))
        #expect(store.load()?.instagramID == "saved")
    }
}
