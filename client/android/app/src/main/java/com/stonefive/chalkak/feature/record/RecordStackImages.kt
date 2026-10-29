package com.stonefive.chalkak.feature.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.stonefive.chalkak.domain.model.PostCalendarItem
import kotlinx.coroutines.launch

/**
 * 묶음은 사진의 원래 비율로 자리를 잡아야 하므로, 썸네일을 미리 받아 크기를 알아 둔다.
 * 책자 표지 색을 구하려고 픽셀을 읽으므로 하드웨어 비트맵을 쓰지 않는다.
 * 받은 썸네일은 기록 탭이 백스택에 있는 동안 [RecordStackImageStore]에 둔다.
 */
@Composable
internal fun rememberRecordStackImages(posts: List<PostCalendarItem>): Map<String, ImageBitmap> {
    val context = LocalContext.current
    val images = viewModel<RecordStackImageStore>().images

    LaunchedEffect(context, posts) {
        val postIds = posts.mapTo(mutableSetOf()) { it.postId }
        images.keys.retainAll(postIds)
        posts
            .filter { it.postId !in images }
            .forEach { post ->
                launch {
                    val request = ImageRequest
                        .Builder(context)
                        .data(post.thumbnailImageUrl)
                        .allowHardware(false)
                        .build()
                    val bitmap = (context.imageLoader.execute(request) as? SuccessResult)
                        ?.image
                        ?.toBitmap()
                        ?: return@launch
                    images[post.postId] = bitmap.asImageBitmap()
                }
            }
    }
    return images
}

/**
 * 묶음 썸네일 보관함. 피드에 다녀오면 기록 화면을 새로 그리는데, 이때 받아 둔 썸네일을 그대로 써야
 * 돌아오는 사진이 첫 프레임부터 보인다. 화면과 함께 버리면 다시 받는 동안 빈 자리가 깜빡인다.
 */
internal class RecordStackImageStore : ViewModel() {
    val images = mutableStateMapOf<String, ImageBitmap>()
}
