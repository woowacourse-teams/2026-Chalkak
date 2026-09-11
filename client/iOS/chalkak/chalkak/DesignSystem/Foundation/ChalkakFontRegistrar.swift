import CoreText
import Foundation

@MainActor
enum ChalkakFontRegistrar {
    private static let resources: [(name: String, fileExtension: String)] = [
        ("Pretendard-Regular", "otf"),
        ("Pretendard-SemiBold", "otf"),
        ("Pretendard-Bold", "otf"),
        ("Continuous-Regular", "otf"),
        ("GriunXHangeulBanguri-Regular", "ttf")
    ]

    static func registerFonts(in bundle: Bundle = .main) {
        resources.forEach { resource in
            guard let url = bundle.url(
                forResource: resource.name,
                withExtension: resource.fileExtension
            ) else {
                assertionFailure(
                    "Missing bundled font: \(resource.name).\(resource.fileExtension)"
                )
                return
            }

            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }
}
