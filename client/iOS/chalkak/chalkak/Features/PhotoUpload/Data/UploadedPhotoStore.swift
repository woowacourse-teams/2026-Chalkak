import Foundation

/// 서버가 처리 중인 오늘 게시물의 사진을 홈에서 바로 보여 주려고 올린 파일을 보관한다.
/// 게시물 하나만 두고, 앱이 새로 시작해 보관소를 만들 때 남은 파일을 지운다.
nonisolated struct UploadedPhotoStore: Sendable {
    static let shared = UploadedPhotoStore(
        directory: FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("UploadedPhoto", isDirectory: true)
    )

    let directory: URL

    init(directory: URL) {
        self.directory = directory
        removeAll()
    }

    func save(_ data: Data, postID: String) {
        removeAll()
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try data.write(to: fileURL(for: postID), options: .atomic)
        } catch {
            removeAll()
        }
    }

    func url(for postID: String) -> URL? {
        let url = fileURL(for: postID)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    func removeAll() {
        try? FileManager.default.removeItem(at: directory)
    }

    private func fileURL(for postID: String) -> URL {
        let name = postID.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? postID
        return directory.appendingPathComponent("\(name).webp", isDirectory: false)
    }
}
