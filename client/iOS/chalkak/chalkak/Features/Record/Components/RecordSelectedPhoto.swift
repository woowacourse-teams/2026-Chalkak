import SwiftUI

/// 선택한 날짜의 사진을 폭에 꽉 채워 보여주고 좌상단에 날짜를 표시한다.
/// Android `RecordSelectedPhoto`와 맞춘다(FillWidth, 날짜 M월 d일, textOnImage, padding 15/15).
/// 화면 폭/좌우 여백은 호출부가 관리한다.
struct RecordSelectedPhoto: View {
    @Environment(\.chalkakTheme) private var theme
    let post: RecordPost

    var body: some View {
        ChalkakImage(
            source: post.thumbnailImageSource,
            contentDescription: "\(Self.dateFormatter.string(from: post.topicDate)) 기록 사진",
            contentMode: .fit
        )
        .frame(maxWidth: .infinity)
        .overlay(alignment: .topLeading) {
            Text(Self.dateFormatter.string(from: post.topicDate))
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textOnImage)
                .padding(.leading, Metrics.labelPadding)
                .padding(.top, Metrics.labelPadding)
        }
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "M월 d일"
        return formatter
    }()
}

private enum Metrics {
    static let labelPadding: CGFloat = 15
}

#Preview("Record Selected Photo", traits: .sizeThatFitsLayout) {
    RecordSelectedPhoto(post: RecordPreviewData.posts[0])
        .chalkakTheme(.light)
}
