import Foundation
import Testing
import WebKit
@testable import chalkak

struct IssueWebNavigationPolicyTests {
    private let issueURL = URL(string: "https://chalkak.example/issues/1")!
    private let externalURL = URL(string: "https://example.com/article")!

    @Test("최초 이슈만 웹뷰에서 열고 이후 자동 이동과 새 창은 차단한다")
    func initialLoadAndAutomaticNavigation() {
        var policy = IssueWebNavigationPolicy(initialURL: issueURL)
        #expect(policy.action(for: issueURL, currentURL: nil, navigationType: .other, targetsMainFrame: true) == .allow)
        #expect(policy.action(for: issueURL, currentURL: issueURL, navigationType: .other, targetsMainFrame: true) == .cancel)
        #expect(policy.action(for: externalURL, currentURL: issueURL, navigationType: .other, targetsMainFrame: true) == .cancel)
        #expect(policy.action(for: externalURL, currentURL: issueURL, navigationType: .other, targetsMainFrame: nil) == .cancel)
    }

    @Test("일반 링크와 새 탭 링크 클릭은 외부로 전달한다", arguments: [true, nil] as [Bool?])
    func clickedLinks(targetsMainFrame: Bool?) {
        var policy = IssueWebNavigationPolicy(initialURL: issueURL)
        #expect(policy.action(for: externalURL, currentURL: issueURL, navigationType: .linkActivated, targetsMainFrame: targetsMainFrame) == .openExternally(externalURL))
        let sameHostURL = URL(string: "https://chalkak.example/issues/2")!
        #expect(policy.action(for: sameHostURL, currentURL: issueURL, navigationType: .linkActivated, targetsMainFrame: targetsMainFrame) == .openExternally(sameHostURL))
    }

    @Test("같은 문서의 앵커만 웹뷰에서 이동하며 새 탭 앵커는 외부로 전달한다")
    func anchors() {
        var policy = IssueWebNavigationPolicy(initialURL: issueURL)
        let anchor = URL(string: issueURL.absoluteString + "#details")!
        #expect(policy.action(for: anchor, currentURL: issueURL, navigationType: .linkActivated, targetsMainFrame: true) == .allow)
        #expect(policy.action(for: anchor, currentURL: anchor, navigationType: .linkActivated, targetsMainFrame: true) == .allow)
        #expect(policy.action(for: issueURL, currentURL: anchor, navigationType: .linkActivated, targetsMainFrame: true) == .allow)
        #expect(policy.action(for: anchor, currentURL: issueURL, navigationType: .linkActivated, targetsMainFrame: nil) == .openExternally(anchor))
        let differentQuery = URL(string: issueURL.absoluteString + "?page=2#details")!
        #expect(policy.action(for: differentQuery, currentURL: issueURL, navigationType: .linkActivated, targetsMainFrame: true) == .openExternally(differentQuery))
    }

    @Test("웹 주소가 아닌 링크는 실행하지 않는다", arguments: ["mailto:test@example.com", "tel:123", "javascript:alert(1)", "file:///tmp/a", "https:/missing-host"])
    func rejectsNonWebURLs(value: String) {
        var policy = IssueWebNavigationPolicy(initialURL: issueURL)
        #expect(policy.action(for: URL(string: value), currentURL: issueURL, navigationType: .linkActivated, targetsMainFrame: true) == .cancel)
    }

    @Test("하위 프레임은 앱 밖으로 링크를 열지 않는다")
    func embeddedFrame() {
        var policy = IssueWebNavigationPolicy(initialURL: issueURL)
        #expect(policy.action(for: externalURL, currentURL: issueURL, navigationType: .linkActivated, targetsMainFrame: false) == .allow)
    }
}
