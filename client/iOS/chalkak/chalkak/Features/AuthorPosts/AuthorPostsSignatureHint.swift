import Foundation

/// 사인을 누를 수 있다고 알려 주는 안내 시트를 더 띄우지 않아도 되는지 기억한다.
/// 시트를 닫거나 사인을 누른 순간 기록한다. 로그아웃해도 지우지 않는다.
struct AuthorPostsSignatureHint {
    static let title = "사인을 눌러보세요!"
    static let message = "이제 사인별로 작가님들의 사진을 모아볼 수 있어요"

    private static let shownKey = "authorPosts.signatureHint.shown"
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var hasShown: Bool {
        defaults.bool(forKey: Self.shownKey)
    }

    func markShown() {
        defaults.set(true, forKey: Self.shownKey)
    }
}
