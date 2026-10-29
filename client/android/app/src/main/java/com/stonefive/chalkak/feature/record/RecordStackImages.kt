package com.stonefive.chalkak.feature.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
 */
@Composable
internal fun rememberRecordStackImages(posts: List<PostCalendarItem>): Map<String, ImageBitmap> {
    val context = LocalContext.current
    val images = remember { mutableStateMapOf<String, ImageBitmap>() }

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
