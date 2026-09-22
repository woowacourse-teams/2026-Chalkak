import SwiftUI

/// 선택한 날짜의 사진을 폭에 꽉 채워 보여주고 좌상단에 날짜를 표시한다.
/// Android `RecordSelectedPhoto`와 맞춘다(FillWidth, 날짜 M월 d일, textOnImage, padding 15/15).
/// 화면 폭/좌우 여백은 호출부가 관리한다.
struct RecordSelectedPhoto: View {
    @Environment(\.chalkakTheme) private var theme
    let post: RecordPost
    @State private var isStatusMessageVisible = false

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
        .overlay(alignment: .topTrailing) {
            if post.status == .pending {
                pendingStatus
                    .padding(.top, Metrics.statusTopPadding)
                    .padding(.trailing, Metrics.statusTrailingPadding)
            }
        }
        .onChange(of: post.postId) { _, _ in
            isStatusMessageVisible = false
        }
    }

    private var pendingStatus: some View {
        Button {
            withAnimation(.snappy) {
                isStatusMessageVisible.toggle()
            }
        } label: {
            HStack(spacing: theme.spacing.sm) {
                ProgressView()
                    .controlSize(.small)
                    .tint(theme.colors.onActionPrimary)
                    .accessibilityHidden(true)

                Text("사진 반영 중")
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.onActionPrimary)
            }
            .padding(.horizontal, theme.spacing.md)
            .frame(minHeight: Metrics.statusMinimumTouchHeight)
            .background(
                theme.colors.actionPrimary.opacity(Metrics.statusBackgroundOpacity),
                in: theme.shapes.pill
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("사진 반영 중")
        .accessibilityHint("자세한 안내 보기")
        .overlay(alignment: .bottomTrailing) {
            if isStatusMessageVisible {
                VStack(alignment: .trailing, spacing: 0) {
                    Text("사진을 반영하고 있어요.\n표시되기까지 조금 시간이 걸릴 수도 있어요!")
                        .font(theme.typography.footnote)
                        .foregroundStyle(theme.colors.onActionPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(theme.spacing.md)
                        .frame(width: Metrics.bubbleWidth, alignment: .leading)
                        .background(
                            theme.colors.actionPrimary,
                            in: RoundedRectangle(cornerRadius: theme.shapes.button)
                        )

                    RecordStatusBubbleTail()
                        .fill(theme.colors.actionPrimary)
                        .frame(width: Metrics.bubbleTailWidth, height: Metrics.bubbleTailHeight)
                        .padding(.trailing, Metrics.bubbleTailTrailingPadding)
                }
                .offset(y: -Metrics.statusMinimumTouchHeight)
                .transition(.opacity.combined(with: .scale(scale: 0.96, anchor: .bottomTrailing)))
                .accessibilityElement(children: .combine)
            }
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
    static let statusTopPadding: CGFloat = 8
    static let statusTrailingPadding: CGFloat = 12
    static let statusMinimumTouchHeight: CGFloat = 44
    static let statusBackgroundOpacity = 0.88
    static let bubbleWidth: CGFloat = 300
    static let bubbleTailWidth: CGFloat = 14
    static let bubbleTailHeight: CGFloat = 7
    static let bubbleTailTrailingPadding: CGFloat = 24
}

private struct RecordStatusBubbleTail: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.closeSubpath()
        return path
    }
}

#Preview("Record Selected Photo", traits: .sizeThatFitsLayout) {
    RecordSelectedPhoto(post: RecordPreviewData.posts[0])
        .chalkakTheme(.light)
}
