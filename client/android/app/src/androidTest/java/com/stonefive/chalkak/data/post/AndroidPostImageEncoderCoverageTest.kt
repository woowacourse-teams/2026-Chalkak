package com.stonefive.chalkak.data.post

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import kotlin.math.max
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 커버리지 실측 테스트: "5MB 한도에서 어느 정도의 사진까지 업로드가 성공하나"를 숫자로 뽑는다.
 *
 * 단정(assert)이 목적이 아니라 계측이 목적이다. 각 케이스마다
 *   소스 해상도 / 소스 용량 / 결과(성공·실패) / 출력 해상도 / 출력 용량 / 5MB 대비 여유 / 추정 resize 라운드
 * 를 표로 출력한다. 결과는 logcat 태그 `EncoderCoverage` 와 표준출력에서 확인한다.
 *
 * 실행:
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.stonefive.chalkak.data.post.AndroidPostImageEncoderCoverageTest
 */
@RunWith(AndroidJUnit4::class)
class AndroidPostImageEncoderCoverageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun measuresCoverageAcrossWorstCaseImages() = runBlocking {
        val header = "%-16s | %11s | %9s | %-16s | %11s | %7s | %5s | %6s".format(
            "case",
            "src(px)",
            "src(KB)",
            "result",
            "out(px)",
            "of5MB",
            "round",
            "ms",
        )
        report(header)
        report("-".repeat(header.length))

        CASES.forEach { case ->
            val source = createJpeg(case)
            try {
                val srcKb = source.length() / 1024
                val uri = source.toContentUri()

                // 워밍업 1회(JIT/최초 실행 편차 제거) 후 측정 3회 중앙값.
                (encoder().encode(uri, MAX_BYTES) as? PostImageEncodeResult.Success)?.file?.delete()
                var lastResult: PostImageEncodeResult = PostImageEncodeResult.EncodeFailed
                val timings = LongArray(TIMING_RUNS)
                for (i in 0 until TIMING_RUNS) {
                    val start = System.nanoTime()
                    lastResult = encoder().encode(uri, MAX_BYTES)
                    timings[i] = (System.nanoTime() - start) / 1_000_000
                    (lastResult as? PostImageEncodeResult.Success)?.file?.delete()
                }
                timings.sort()
                val medianMs = timings[TIMING_RUNS / 2]

                val result = encoder().encode(uri, MAX_BYTES)
                val row = when (result) {
                    is PostImageEncodeResult.Success -> {
                        val output = result.file
                        try {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(output.absolutePath, bounds)
                            val outBytes = output.length()
                            val outLongEdge = max(bounds.outWidth, bounds.outHeight)
                            "%-16s | %11s | %9d | %-16s | %11s | %6.1f%% | %5d | %6d".format(
                                case.label,
                                "${case.width}x${case.height}",
                                srcKb,
                                "SUCCESS",
                                "${bounds.outWidth}x${bounds.outHeight}",
                                outBytes * 100.0 / MAX_BYTES,
                                inferRound(case, outLongEdge),
                                medianMs,
                            )
                        } finally {
                            output.delete()
                        }
                    }

                    else -> "%-16s | %11s | %9d | %-16s | %11s | %7s | %5s | %6d".format(
                        case.label,
                        "${case.width}x${case.height}",
                        srcKb,
                        result::class.simpleName ?: "FAILURE",
                        "-",
                        "-",
                        "-",
                        medianMs,
                    )
                }
                report(row)
            } finally {
                source.delete()
            }
        }
    }

    private fun encoder() = AndroidPostImageEncoder(
        contentResolver = context.contentResolver,
        cacheDir = File(context.cacheDir, ENCODER_CACHE_DIRECTORY),
    )

    /**
     * 소스 비트맵을 행(row) 단위로 채워 거대한 IntArray 없이 고해상도 이미지를 생성한다.
     * NOISE = 픽셀마다 난수 → 압축 최악(실측의 상한). SMOOTH = 그라디언트 → 압축 최선(하한).
     */
    private fun createJpeg(case: Case): File {
        val file = File.createTempFile("coverage-src-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(case.width, case.height, Bitmap.Config.ARGB_8888)
        val random = Random(SEED)
        val rowPixels = IntArray(case.width)
        try {
            for (y in 0 until case.height) {
                for (x in 0 until case.width) {
                    rowPixels[x] = when (case.kind) {
                        Kind.NOISE -> 0xFF000000.toInt() or (random.nextInt() and 0x00FFFFFF)

                        Kind.SMOOTH -> {
                            val r = (x * 255 / case.width) and 0xFF
                            val g = (y * 255 / case.height) and 0xFF
                            0xFF000000.toInt() or (r shl 16) or (g shl 8)
                        }
                    }
                }
                bitmap.setPixels(rowPixels, 0, case.width, 0, y, case.width, 1)
            }
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    /** 출력 긴 변으로 몇 번째 0.85배 resize 라운드에서 통과했는지 역산한다(-1 = 추정 불가). */
    private fun inferRound(
        case: Case,
        outLongEdge: Int,
    ): Int {
        // 디코딩 시 inSampleSize(2의 배수)로 긴 변을 4096 이하로 내린 값이 라운드0의 기준.
        val sourceLongEdge = max(case.width, case.height)
        var sampleSize = 1
        while (sourceLongEdge / sampleSize > INITIAL_MAX_LONG_EDGE) {
            sampleSize *= 2
        }
        var edge = (sourceLongEdge / sampleSize).toDouble()
        for (round in 0..MAX_RESCALE_ROUNDS) {
            if (outLongEdge >= edge * 0.97) return round
            edge *= RESCALE_FACTOR
        }
        return -1
    }

    private fun File.toContentUri(): String = android.net.Uri
        .fromFile(this)
        .toString()

    private fun report(line: String) {
        Log.i(TAG, line)
        println(line)
    }

    private enum class Kind { NOISE, SMOOTH }

    private data class Case(
        val label: String,
        val width: Int,
        val height: Int,
        val kind: Kind,
    )

    private companion object {
        const val TAG = "EncoderCoverage"
        const val MAX_BYTES = 5_242_880L
        const val SEED = 42L
        const val TIMING_RUNS = 3
        const val ENCODER_CACHE_DIRECTORY = "post-encoder-coverage"

        // 인코더 상수와 동일하게 유지(라운드 역산용).
        const val INITIAL_MAX_LONG_EDGE = 4096
        const val RESCALE_FACTOR = 0.85
        const val MAX_RESCALE_ROUNDS = 3

        val CASES = listOf(
            // 이론상 진짜 최악: 긴 변 4096 + 정사각형(총 픽셀 최대, 16.8MP) + 순수 노이즈.
            // 압축기에 들어갈 수 있는 가장 큰 비트맵. 이게 통과하면 어떤 사진도 통과한다.
            Case("noise-4096-square", 4096, 4096, Kind.NOISE),
            // 4:3 풀해상도 노이즈(12.6MP).
            Case("noise-4096", 4096, 3072, Kind.NOISE),
            // 큰 소스는 오히려 작아짐: 48MP(8000>4096) → inSampleSize=2 → 4000으로 내려감.
            Case("noise-48mp", 8000, 6000, Kind.NOISE),
            // 16MP 노이즈: 4608>4096 → inSampleSize=2 → 2304으로 내려가 더 작아짐(대비용).
            Case("noise-16mp", 4608, 3456, Kind.NOISE),
            // 최선 기준선: 같은 해상도의 매끈한 그라디언트.
            Case("smooth-4096", 4096, 3072, Kind.SMOOTH),
        )
    }
}
