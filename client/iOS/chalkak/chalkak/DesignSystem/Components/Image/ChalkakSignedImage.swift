import SwiftUI

struct ChalkakSignedImage: View {
    @Environment(\.chalkakTheme) private var theme
    let imageSource: ChalkakImageSource
    let signatureSource: ChalkakImageSource
    var contentDescription: String?
    var contentMode: ContentMode = .fill
    var signatureSize = Metrics.defaultSignatureSize

    var body: some View {
        ChalkakImage(
            source: imageSource,
            contentDescription: contentDescription,
            contentMode: contentMode
        )
        .overlay(alignment: .bottomTrailing) {
            ChalkakImage(
                source: signatureSource,
                contentDescription: nil,
                contentMode: .fit
            )
            .frame(width: signatureSize.width, height: signatureSize.height)
            .padding(theme.spacing.sm)
        }
    }
}

private enum Metrics {
    static let defaultSignatureSize = CGSize(width: 56, height: 42)
}

#Preview("Signed Image", traits: .sizeThatFitsLayout) {
    ChalkakSignedImage(
        imageSource: .asset("preview_photo"),
        signatureSource: .asset("preview_signature"),
        contentDescription: "서명이 포함된 전시 사진",
        contentMode: .fit
    )
    .frame(width: 270, height: 360)
    .background(ChalkakTheme.light.colors.inputBackground)
}
