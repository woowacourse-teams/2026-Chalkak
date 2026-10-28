import Foundation

enum HomePreviewData {
    static let previewDate = RecordMonth.calendar.date(
        from: DateComponents(year: 2026, month: 8, day: 3)
    )!

    static let aiReviewResult = AIReviewResult(
        overallScore: 8.7,
        criteria: [AIReviewCriterion(key: "COMPOSITION", label: "구도", score: 8.7)],
        distinctivePerspective: true,
        feedback: AIReviewFeedback(
            summary: "건물의 크기와 높이 차이가 잘 비교되는 구도예요",
            strength: "아래쪽 계단과 겹겹이 쌓인 집들, 위쪽 아파트가 한 화면에 같이 담겼어요.",
            weakness: "맨 아래 담장과 검은 옥상이 골목의 흐름을 끊어요.",
            suggestion: "왼쪽 계단 입구와 붉은 도로는 남기면서 맨 아래 담장이 덜 나오게 담아보세요."
        )
    )

    static let banner = Banner(
        id: "preview-banner",
        title: "함께 만들어주신 찰캌",
        subtitle: "베타테스트에 함께해주셔서 감사합니다",
        webViewURL: URL(string: "https://example.com/issue")!,
        thumbnailURL: URL(string: "https://example.com/banner.jpg")!,
        createdAt: "2026-10-20T08:08:11.001Z"
    )

    static let heroPhoto = HomeHeroPhoto(
        imageSource: .asset("preview_photo"),
        thumbnailSource: .asset("preview_photo"),
        signatureSource: .asset("preview_signature"),
        isPending: false,
        postID: "preview-post"
    )

    static let contentState: HomeViewState = {
        var state = HomeViewState(date: previewDate)
        state.topic = "하늘"
        state.topicStatus = .ready
        state.hero = .photo(heroPhoto)
        state.week = HomeViewState.weekDates(previewDate).enumerated().map { index, date in
            HomeWeekDay(
                date: date,
                post: [0, 1, 3, 5, 6].contains(index)
                    ? RecordPost(
                        postId: "week-\(index)",
                        topicDate: date,
                        thumbnailImageSource: index.isMultiple(of: 2) ? .asset("preview_photo") : .asset("preview_sunset"),
                        status: index == 3 ? .pending : .approved
                    )
                    : nil
            )
        }
        state.recordsStatus = .ready
        state.yesterday = HomeRankingState(status: .ready, photos: photos)
        state.trending = HomeRankingState(status: .ready, photos: photos.reversed())
        state.banners = [banner]
        state.bannerStatus = .ready
        state.isAuthenticated = true
        return state
    }()

    static let loadingState: HomeViewState = HomeViewState(date: previewDate)

    static let errorState: HomeViewState = {
        var state = HomeViewState(date: previewDate)
        state.bannerStatus = .error
        state.topicStatus = .error
        state.hero = .error
        state.recordsStatus = .error
        state.yesterday = HomeRankingState(status: .error)
        state.trending = HomeRankingState(status: .error)
        return state
    }()

    static let loader: HomeDataLoader = {
        let date = previewDate
        let dateLabel = previewDate.homeFullDateText
        let previewPhotos = photos
        let previewBanner = banner
        let records = contentState.week.compactMap(\.post)
        let postStatus = PhotoUploadTodayPostStatus(
            topicDate: date,
            isPosted: false,
            postID: nil,
            moderationStatus: nil
        )

        return HomeDataLoader(
            today: { requestedDate in
                DisplayContent(
                    topicDate: requestedDate,
                    topic: "하늘",
                    page: DisplayPage(photos: previewPhotos, currentPage: 1, hasNext: false, randomSeed: nil)
                )
            },
            ranking: { _ in previewPhotos },
            calendar: { month in RecordCalendar(month: month, posts: records) },
            detail: { postID in
                FeedContent(
                    dateLabel: dateLabel,
                    topicDate: date,
                    topic: "하늘",
                    post: FeedPost(
                        id: postID,
                        originalImageSource: .asset("preview_photo"),
                        signatureImageSource: .asset("preview_signature"),
                        contentDescription: "프리뷰 사진",
                        title: "프리뷰",
                        likeCount: 3,
                        isLiked: false,
                        isOwnedByCurrentUser: true
                    )
                )
            },
            banners: { page, pageSize in BannerPage(currentPage: page, pageSize: pageSize, hasNext: false, banners: [previewBanner]) },
            postStatus: { postStatus }
        )
    }()

    static func viewModel(state: HomeViewState, loader: HomeDataLoader = loader) -> HomeViewModel {
        let date = previewDate
        return HomeViewModel(
            initialState: state,
            loader: loader,
            dateProvider: { date },
            isAuthenticated: { state.isAuthenticated },
            aiReviewRepository: HomePreviewAIReviewRepository()
        )
    }

#if DEBUG
    static func paginatedBannerPage(page: Int, pageSize: Int) -> BannerPage {
        let start = (page - 1) * pageSize + 1
        let banners = (start..<(start + pageSize)).map { index in
            Banner(
                id: "pagination-\(index)",
                title: "이슈 \(index)",
                subtitle: "페이지 \(page)",
                webViewURL: banner.webViewURL,
                thumbnailURL: nil,
                createdAt: banner.createdAt
            )
        }
        return BannerPage(currentPage: page, pageSize: pageSize, hasNext: page < 2, banners: banners)
    }
#endif

    private static let photos: [DisplayPhoto] = (1...5).map { index in
        DisplayPhoto(
            id: "preview-\(index)",
            originalImageSource: index.isMultiple(of: 2) ? .asset("preview_sunset") : .asset("preview_photo"),
            thumbnailImageSource: index.isMultiple(of: 2) ? .asset("preview_sunset") : .asset("preview_photo"),
            signatureOriginalImageSource: .asset("preview_signature"),
            signatureThumbnailImageSource: .asset("preview_signature"),
            contentDescription: "랭킹 사진 \(index)",
            title: "사진 \(index)",
            likeCount: index,
            isLiked: false,
            isOwnedByCurrentUser: false
        )
    }
}

private actor HomePreviewAIReviewRepository: AIReviewRepository {
    private var requested = false

    func fetchReview(postID: String) async throws -> AIReview {
        let result = await MainActor.run { HomePreviewData.aiReviewResult }
        return AIReview(
            status: requested ? .completed : .none,
            requestedAt: nil,
            completedAt: nil,
            canRequestAgain: false,
            result: requested ? result : nil
        )
    }

    func requestReview(postID: String) async throws -> AIReview {
        requested = true
        return AIReview(status: .processing, requestedAt: nil, completedAt: nil, canRequestAgain: false)
    }
}
