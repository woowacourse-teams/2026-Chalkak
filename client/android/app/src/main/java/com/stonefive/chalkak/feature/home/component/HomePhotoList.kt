package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post

@Composable
fun HomePhotoList(
    photos: List<Post>,
    likedPhotoIds: Set<String>,
    onLikeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xxl),
    ) {
        items(
            items = photos,
            key = Post::id,
        ) { photo ->
            HomePhotoCard(
                photo = photo,
                isLiked = photo.id in likedPhotoIds,
                onLikeClick = { onLikeClick(photo.id) },
            )
        }
    }
}
