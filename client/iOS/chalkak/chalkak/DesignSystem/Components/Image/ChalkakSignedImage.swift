import SwiftUI

struct ChalkakSignedImage: View {
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
            .padding(ChalkakSpacing.sm)
        }
    }
}

private enum Metrics {
    static let defaultSignatureSize = CGSize(width: 56, height: 42)
}

#Preview("Signed Image") {
    ChalkakSignedImage(
        imageSource: .system("photo.artframe"),
        signatureSource: .system("signature"),
        contentDescription: "서명이 포함된 전시 사진",
        contentMode: .fit
    )
    .frame(width: 270, height: 360)
    .background(ChalkakColors().inputBackground)
}
