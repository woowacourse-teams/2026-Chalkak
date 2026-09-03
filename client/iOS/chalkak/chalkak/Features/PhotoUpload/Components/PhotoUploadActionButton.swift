import SwiftUI

struct PhotoUploadActionButton: View {
    @Environment(\.chalkakTheme) private var theme

    let imageName: String
    let description: String
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            Image(imageName)
                .resizable()
                .scaledToFit()
                .frame(width: Metrics.iconSize, height: Metrics.iconSize)
                .frame(width: Metrics.buttonSize, height: Metrics.buttonSize)
                .background(.white.opacity(Metrics.backgroundOpacity), in: Circle())
        }
        .buttonStyle(.plain)
        .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
        .accessibilityLabel(description)
    }
}

private enum Metrics {
    static let buttonSize: CGFloat = 42
    static let iconSize: CGFloat = 22
    static let backgroundOpacity = 0.66
}

#Preview("Photo Upload Action Button") {
    PhotoUploadActionButton(
        imageName: "ic_photo_library",
        description: "앨범에서 사진 선택",
        onClick: {}
    )
    .padding()
    .chalkakTheme(.light)
}
