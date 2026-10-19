package com.stonefive.chalkak.domain.model.photoedit

internal object Argb {
    private const val CHANNEL_MASK = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    fun alpha(color: Int): Int = color ushr ALPHA_SHIFT

    fun red(color: Int): Int = color shr RED_SHIFT and CHANNEL_MASK

    fun green(color: Int): Int = color shr GREEN_SHIFT and CHANNEL_MASK

    fun blue(color: Int): Int = color and CHANNEL_MASK

    fun of(
        alpha: Int,
        red: Int,
        green: Int,
        blue: Int,
    ): Int = (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}
