package com.stonefive.chalkak.feature.display.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post

@Composable
fun DisplayPhotoGrid(
    photos: List<Post>,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier,
        state = state,
        contentPadding = PaddingValues(
            start = 21.dp,
            top = 4.dp,
            end = 21.dp,
            bottom = 36.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 9.dp,
    ) {
        items(
            items = photos,
            key = Post::id,
        ) { photo ->
            DisplayPhotoCard(photo = photo)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 640)
@Composable
private fun DisplayPhotoGridPreview() {
    ChalkakTheme {
        DisplayPhotoGrid(
            photos = previewDisplayPhotos + previewDisplayPhotos.mapIndexed { index, post ->
                post.copy(id = "duplicate-$index")
            },
        )
    }
}
