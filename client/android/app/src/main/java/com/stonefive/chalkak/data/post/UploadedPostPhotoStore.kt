package com.stonefive.chalkak.data.post

import com.stonefive.chalkak.domain.repository.UploadedPostPhotoRepository
import java.io.File

// 서버가 처리를 끝내기 전 홈에 보여 줄 업로드 사진을 앱 캐시 폴더에 게시물 하나만 둔다.
// 앱 프로세스가 새로 시작해 이 보관소가 만들어지면 남은 파일을 지운다.
class UploadedPostPhotoStore(private val directory: File) : UploadedPostPhotoRepository {
    private val lock = Any()

    init {
        synchronized(lock) { directory.deleteRecursively() }
    }

    fun keep(
        postId: String,
        file: File,
    ) {
        if (!postId.matches(SAFE_POST_ID)) return
        synchronized(lock) {
            directory.deleteRecursively()
            directory.mkdirs()
            runCatching { file.copyTo(photoFile(postId), overwrite = true) }
                .onFailure { directory.deleteRecursively() }
        }
    }

    override fun photoPath(postId: String): String? {
        if (!postId.matches(SAFE_POST_ID)) return null
        return synchronized(lock) { photoFile(postId).takeIf(File::isFile)?.absolutePath }
    }

    override fun clear() {
        synchronized(lock) { directory.deleteRecursively() }
    }

    private fun photoFile(postId: String) = File(directory, "$postId.webp")

    private companion object {
        // 게시물 ID를 파일 이름으로 쓰므로 경로 문자가 들어간 ID는 보관하지 않는다.
        val SAFE_POST_ID = Regex("[A-Za-z0-9_-]+")
    }
}
