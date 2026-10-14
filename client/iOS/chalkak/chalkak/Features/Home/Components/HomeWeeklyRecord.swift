import SwiftUI

struct HomeWeeklyRecord: View {
    @Environment(\.chalkakTheme) private var theme
    let days: [HomeWeekDay]
    let status: HomeSectionStatus
    var onOpenRecord: () -> Void
    var onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.lg) {
            HomeSectionHeader(
                title: "이번 주 기록",
                suffix: "\(days.filter { $0.post != nil }.count)/7",
                moreContentDescription: "이번 주 기록 더보기",
                onMoreClick: onOpenRecord
            )

            switch status {
            case .loading:
                HomeStatusRow {
                    ProgressView()
                        .tint(theme.colors.actionPrimary)
                }
            case .error:
                HomeStatusRow {
                    HStack(spacing: theme.spacing.sm) {
                        Text("기록을 불러오지 못했어요")
                            .font(theme.typography.footnote)
                            .foregroundStyle(theme.colors.textSecondary)

                        Button(action: onRetry) {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: Metrics.retryIconSize, weight: .semibold))
                                .foregroundStyle(theme.colors.onActionPrimary)
                                .frame(width: Metrics.retryButtonSize, height: Metrics.retryButtonSize)
                                .background(theme.colors.actionPrimary, in: Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("기록 다시 시도")
                    }
                }
            case .ready:
                HStack(spacing: theme.spacing.sm) {
                    ForEach(days) { day in
                        WeeklyRecordCell(day: day)
                    }
                }
            }
        }
    }
}

private struct WeeklyRecordCell: View {
    @Environment(\.chalkakTheme) private var theme
    let day: HomeWeekDay

    var body: some View {
        theme.colors.calendarCell
            .aspectRatio(1, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .overlay {
                GeometryReader { geometry in
                    if let post = day.post {
                        ChalkakImage(
                            source: post.thumbnailImageSource,
                            contentDescription: nil
                        )
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                        .overlay(alignment: .bottom) {
                            if post.status == .pending {
                                Text("대기")
                                    .font(theme.typography.caption)
                                    .fontWeight(.semibold)
                                    .foregroundStyle(theme.colors.textOnImage)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, Metrics.pendingBadgeVerticalPadding)
                                    .background(theme.colors.scrim)
                            }
                        }
                    } else {
                        Text(day.date.homeWeekdayText)
                            .font(theme.typography.caption)
                            .foregroundStyle(theme.colors.textSecondary)
                            .frame(width: geometry.size.width, height: geometry.size.height)
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.small))
            .accessibilityLabel(accessibilityLabel)
    }

    private var accessibilityLabel: String {
        var text = "\(day.date.homeFullDateText) 기록 \(day.post == nil ? "없음" : "있음")"
        if day.post?.status == .pending { text += ", 검수 대기" }
        return text
    }
}

struct HomeSectionHeader: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    var suffix: String?
    let moreContentDescription: String
    var onMoreClick: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: theme.spacing.xs) {
            Text(title)
                .font(theme.typography.headline)
                .fontWeight(.bold)
                .foregroundStyle(theme.colors.textPrimary)

            if let suffix {
                Text(suffix)
                    .font(theme.typography.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(theme.colors.textSecondary)
            }

            Spacer(minLength: theme.spacing.sm)

            Button(action: onMoreClick) {
                HStack(spacing: theme.spacing.xs) {
                    Text("보러가기")
                        .font(theme.typography.caption)
                    Image(systemName: "chevron.right")
                        .font(.system(size: Metrics.chevronSize, weight: .semibold))
                }
                .foregroundStyle(theme.colors.textPrimary)
                .padding(.vertical, theme.spacing.sm)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(moreContentDescription)
        }
    }
}

struct HomeStatusRow<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        HStack {
            Spacer(minLength: 0)
            content()
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity)
        .frame(height: Metrics.statusHeight)
    }
}

private enum Metrics {
    static let statusHeight: CGFloat = 48
    static let retryButtonSize: CGFloat = 32
    static let retryIconSize: CGFloat = 15
    static let chevronSize: CGFloat = 12
    static let pendingBadgeVerticalPadding: CGFloat = 1
}

private extension Date {
    var homeWeekdayText: String {
        formatted(.dateTime.locale(Locale(identifier: "ko_KR")).weekday(.abbreviated))
    }
}

#Preview("Weekly Record", traits: .sizeThatFitsLayout) {
    HomeWeeklyRecord(
        days: HomePreviewData.contentState.week,
        status: .ready,
        onOpenRecord: {},
        onRetry: {}
    )
    .padding()
    .chalkakTheme(.light)
}
