import Foundation

enum HomePreviewData {
    static let previewDate = RecordMonth.calendar.date(
        from: DateComponents(year: 2026, month: 8, day: 3)
    )!

    static let heroPhoto = HomeHeroPhoto(
        imageSource: .asset("preview_photo"),
        thumbnailSource: .asset("preview_photo"),
        signatureSource: .asset("preview_signature"),
        isPending: false
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
        state.isAuthenticated = true
        return state
    }()

    static let loadingState: HomeViewState = HomeViewState(date: previewDate)

    static let errorState: HomeViewState = {
        var state = HomeViewState(date: previewDate)
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
            postStatus: { postStatus }
        )
    }()

    static func viewModel(state: HomeViewState) -> HomeViewModel {
        let date = previewDate
        return HomeViewModel(
            initialState: state,
            loader: loader,
            dateProvider: { date },
            isAuthenticated: { state.isAuthenticated }
        )
    }

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
