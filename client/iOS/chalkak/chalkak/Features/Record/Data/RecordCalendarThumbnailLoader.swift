import Foundation
import ImageIO

/// 달력 이미지 스냅샷에 사용할 원격 썸네일을 미리 내려받는다.
/// `AsyncImage`처럼 비동기 뷰에 의존하지 않고, 렌더링 전에 모든 데이터를 확보한다.
enum RecordCalendarThumbnailLoader {
    static func loadData(for posts: [RecordPost]) async -> [RecordPost.ID: Data] {
        await withTaskGroup(of: (RecordPost.ID, Data?).self) { group in
            for post in posts {
                guard case let .remote(url?) = post.thumbnailImageSource else { continue }

                group.addTask {
                    guard let (data, response) = try? await URLSession.shared.data(from: url),
                          let response = response as? HTTPURLResponse,
                          (200..<300).contains(response.statusCode),
                          let imageSource = CGImageSourceCreateWithData(data as CFData, nil),
                          CGImageSourceCreateImageAtIndex(imageSource, 0, nil) != nil
                    else {
                        return (post.postId, nil)
                    }

                    return (post.postId, data)
                }
            }

            var loadedData: [RecordPost.ID: Data] = [:]
            for await (postID, data) in group {
                if let data {
                    loadedData[postID] = data
                }
            }
            return loadedData
        }
    }
}
