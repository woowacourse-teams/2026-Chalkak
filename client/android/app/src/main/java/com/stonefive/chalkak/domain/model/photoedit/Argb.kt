package com.stonefive.chalkak.domain.model.photoedit

/** 픽셀 값 하나(Int)에 8비트씩 들어 있는 A·R·G·B(각 0..255)를 꺼내고 합친다. */
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
