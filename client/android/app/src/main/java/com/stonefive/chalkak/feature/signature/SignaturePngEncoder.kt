package com.stonefive.chalkak.feature.signature

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.ByteArrayOutputStream
import kotlin.math.min

fun interface SignaturePngEncoder {
    fun encode(strokes: List<SignatureStroke>): ByteArray
}

class AndroidSignaturePngEncoder : SignaturePngEncoder {
    override fun encode(strokes: List<SignatureStroke>): ByteArray {
        val drawableStrokes = strokes.filter { it.points.isNotEmpty() }
        require(drawableStrokes.isNotEmpty()) { "저장할 사인이 없습니다." }

        val points = drawableStrokes.flatMap(SignatureStroke::points)
        val minX = points.minOf(SignaturePoint::xRatio)
        val maxX = points.maxOf(SignaturePoint::xRatio)
        val minY = points.minOf(SignaturePoint::yRatio)
        val maxY = points.maxOf(SignaturePoint::yRatio)
        val contentWidth = (maxX - minX).coerceAtLeast(MIN_NORMALIZED_SIZE)
        val contentHeight = (maxY - minY).coerceAtLeast(MIN_NORMALIZED_SIZE)
        val availableWidth = OUTPUT_WIDTH - PADDING_PX * 2f
        val availableHeight = OUTPUT_HEIGHT - PADDING_PX * 2f
        val scale = min(availableWidth / contentWidth, availableHeight / contentHeight)
        val offsetX = (OUTPUT_WIDTH - contentWidth * scale) / 2f
        val offsetY = (OUTPUT_HEIGHT - contentHeight * scale) / 2f

        val bitmap = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SIGNATURE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = STROKE_WIDTH_PX
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        drawableStrokes.forEach { stroke ->
            val mappedPoints = stroke.points.map { point ->
                MappedPoint(
                    x = offsetX + (point.xRatio - minX) * scale,
                    y = offsetY + (point.yRatio - minY) * scale,
                )
            }
            if (mappedPoints.size == 1) {
                val point = mappedPoints.first()
                canvas.drawCircle(
                    point.x,
                    point.y,
                    STROKE_WIDTH_PX / 2f,
                    paint.apply {
                        style = Paint.Style.FILL
                    },
                )
                paint.style = Paint.Style.STROKE
            } else {
                canvas.drawPath(mappedPoints.toSmoothPath(), paint)
            }
        }

        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "사인 이미지를 PNG로 변환하지 못했습니다."
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun List<MappedPoint>.toSmoothPath(): Path = Path().apply {
        moveTo(first().x, first().y)
        for (index in 1 until lastIndex) {
            val current = this@toSmoothPath[index]
            val next = this@toSmoothPath[index + 1]
            quadTo(
                current.x,
                current.y,
                (current.x + next.x) / 2f,
                (current.y + next.y) / 2f,
            )
        }
        lineTo(last().x, last().y)
    }

    private data class MappedPoint(
        val x: Float,
        val y: Float,
    )

    private companion object {
        const val OUTPUT_WIDTH = 1024
        const val OUTPUT_HEIGHT = 512
        const val PADDING_PX = 48
        const val STROKE_WIDTH_PX = 14f
        const val MIN_NORMALIZED_SIZE = 0.02f
        val SIGNATURE_COLOR = Color.WHITE
    }
}
