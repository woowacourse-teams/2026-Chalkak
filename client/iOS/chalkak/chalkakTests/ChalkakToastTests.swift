import SwiftUI
import UIKit
import XCTest
@testable import chalkak

@MainActor
final class ChalkakToastTests: XCTestCase {
    func testToastKeepsScreenPositionWithAndWithoutBottomBar() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        var bottomPositions: [CGFloat] = []

        for bottomBarHeight: CGFloat in [0, 100] {
            let root = ToastTrigger()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.white)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    Color.white.frame(height: bottomBarHeight)
                }
                .chalkakToastHost()
                .chalkakTheme(.light)
            let host = UIHostingController(rootView: root)
            let window = UIWindow(windowScene: scene)
            window.rootViewController = host
            window.makeKeyAndVisible()
            host.view.layoutIfNeeded()
            try await Task.sleep(for: .milliseconds(600))
            host.view.layoutIfNeeded()

            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let image = UIGraphicsImageRenderer(size: host.view.bounds.size, format: format).image { _ in
                host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
            }
            window.isHidden = true
            let bottom = try lastDarkRow(in: image)
            bottomPositions.append(CGFloat(bottom))
            let margin = host.view.bounds.height - CGFloat(bottom + 1)
            XCTAssertEqual(margin, host.view.safeAreaInsets.bottom + 32, accuracy: 2)

            let attachment = XCTAttachment(image: image)
            attachment.name = "Toast with bottom bar height \(Int(bottomBarHeight))"
            attachment.lifetime = .keepAlways
            add(attachment)
        }

        XCTAssertEqual(bottomPositions[0], bottomPositions[1], accuracy: 1)
    }

    private func lastDarkRow(in image: UIImage) throws -> Int {
        let cgImage = try XCTUnwrap(image.cgImage)
        let width = cgImage.width
        let height = cgImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let context = try XCTUnwrap(CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ))
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        for y in stride(from: height - 1, through: height / 2, by: -1) {
            let pixel = (y * width + width / 2) * 4
            if pixels[pixel] < 100 && pixels[pixel + 1] < 100 && pixels[pixel + 2] < 100 {
                return y
            }
        }
        XCTFail("Expected the toast to render in the lower half of the screen")
        return 0
    }
}

private struct ToastTrigger: View {
    @Environment(\.chalkakShowToast) private var showToast

    var body: some View {
        Color.clear.onAppear {
            showToast("사인 반영까지 시간이 좀 걸릴 수 있어요!")
        }
    }
}
