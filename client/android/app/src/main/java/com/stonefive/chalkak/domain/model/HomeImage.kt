package com.stonefive.chalkak.domain.model

import androidx.annotation.DrawableRes

sealed interface HomeImage {
    data class Local(@param:DrawableRes val resourceId: Int) : HomeImage

    data class Remote(val url: String) : HomeImage
}
