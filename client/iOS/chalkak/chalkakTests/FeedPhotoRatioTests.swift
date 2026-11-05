import SwiftUI
import Testing
import UIKit
@testable import chalkak

@MainActor
struct FeedPhotoRatioTests {
    @Test("캐시에서 밀려난 사진으로 열려도 받은 사진의 비율로 칸을 다시 잡는다")
    func uncachedPlaceholderUsesLoadedImageRatio() async throws {
        // 3:4 사진을 캐시에 없는 주소로 넘긴다. 예전에는 기본(정사각) 칸에 그대로 남아 사진이 칸 밖으로 삐져나왔다.
        let size = CGSize(width: 300, height: 400)
        let image = UIGraphicsImageRenderer(size: size).image { context in
            UIColor.systemOrange.setFill()
            context.fill(CGRect(origin: .zero, size: size))
        }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("feed-ratio-\(UUID().uuidString).png")
        try #require(image.pngData()).write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }

        let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let host = UIHostingController(
            rootView: FeedPhoto(post: nil, placeholder: FeedPhotoPlaceholder(imageSource: .remote(url)), onLike: {})
                .frame(width: 402)
                .chalkakTheme(.light)
        )
        host.safeAreaRegions = []
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 402, height: 874)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }

        // 전체 테스트를 병렬로 돌리면 이미지 로딩이 늦어질 수 있어 고정 시간 대신 결과를 기다린다.
        try await waitUntil(timeout: .seconds(10)) {
            abs(host.sizeThatFits(in: CGSize(width: 402, height: 2000)).height - 536) < 2
        }
    }
}
