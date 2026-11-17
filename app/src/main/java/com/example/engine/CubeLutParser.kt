package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.example.model.ColorGradingSpec
import com.example.model.CurvePoint
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class ParsedLut3D(
    val name: String,
    val size: Int,
    val rgbData: FloatArray // size * size * size * 3
)

object CubeLutParser {

    data class BuiltInLut(val id: String, val namePt: String)

    val builtInLuts = listOf(
        BuiltInLut("teal_orange", "Teal & Orange Cinema"),
        BuiltInLut("cyber_neon", "Cyberpunk Neon"),
        BuiltInLut("film_vintage", "Película Vintage 35mm")
    )

    fun parseCubeFromUri(context: Context, uri: Uri): ParsedLut3D? {
        return runCatching {
            val lines = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readLines()
            } ?: return null
            parseCubeLines("LUT Importada", lines)
        }.getOrNull()
    }

    fun parseCubeLines(defaultName: String, lines: List<String>): ParsedLut3D? {
        var title = defaultName
        var size = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        val entries = ArrayList<Float>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            when {
                line.startsWith("TITLE") -> {
                    title = line.removePrefix("TITLE").trim().trim('"').ifBlank { defaultName }
                }
                line.startsWith("LUT_3D_SIZE") -> {
                    size = line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: 0
                }
                line.startsWith("DOMAIN_MIN") -> {
                    val parts = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toFloatOrNull() }
                    if (parts.size >= 3) domainMin = floatArrayOf(parts[0], parts[1], parts[2])
                }
                line.startsWith("DOMAIN_MAX") -> {
                    val parts = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toFloatOrNull() }
                    if (parts.size >= 3) domainMax = floatArrayOf(parts[0], parts[1], parts[2])
                }
                else -> {
                    val nums = line.split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
                    if (nums.size >= 3) {
                        val r = ((nums[0] - domainMin[0]) / (domainMax[0] - domainMin[0]).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
                        val g = ((nums[1] - domainMin[1]) / (domainMax[1] - domainMin[1]).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
                        val b = ((nums[2] - domainMin[2]) / (domainMax[2] - domainMin[2]).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
                        entries.add(r); entries.add(g); entries.add(b)
                    }
                }
            }
        }
        if (size <= 1 || entries.size < size * size * size * 3) return null
        return ParsedLut3D(title, size, entries.toFloatArray())
    }

    fun generateBuiltInLut(id: String): ParsedLut3D {
        val size = 16
        val data = FloatArray(size * size * size * 3)
        var idx = 0
        for (b in 0 until size) {
            val fb = b / (size - 1f)
            for (g in 0 until size) {
                val fg = g / (size - 1f)
                for (r in 0 until size) {
                    val fr = r / (size - 1f)
                    val luma = 0.299f * fr + 0.587f * fg + 0.114f * fb
                    val (or, og, ob) = when (id) {
                        "teal_orange" -> {
                            val shadowBoost = (1f - luma).pow(1.4f)
                            val highlightBoost = luma.pow(1.2f)
                            Triple(
                                (fr * 0.92f + highlightBoost * 0.18f - shadowBoost * 0.08f).coerceIn(0f, 1f),
                                (fg * 0.96f + shadowBoost * 0.06f).coerceIn(0f, 1f),
                                (fb * 0.90f + shadowBoost * 0.20f - highlightBoost * 0.08f).coerceIn(0f, 1f)
                            )
                        }
                        "cyber_neon" -> {
                            Triple(
                                (fr.pow(0.95f) * 0.92f + fb * 0.14f).coerceIn(0f, 1f),
                                (fg.pow(1.08f) * 1.04f).coerceIn(0f, 1f),
                                (fb.pow(0.85f) * 1.15f + (1f - luma) * 0.10f).coerceIn(0f, 1f)
                            )
                        }
                        else -> { // film_vintage
                            Triple(
                                (0.04f + fr * 0.94f).pow(0.92f).coerceIn(0f, 1f),
                                (0.03f + fg * 0.92f).coerceIn(0f, 1f),
                                (0.06f + fb * 0.84f).coerceIn(0f, 1f)
                            )
                        }
                    }
                    data[idx++] = or
                    data[idx++] = og
                    data[idx++] = ob
                }
            }
        }
        return ParsedLut3D(id, size, data)
    }

    /**
     * Packs an NxNxN 3D LUT into a 2D texture atlas of width (N*N) and height N.
     * Each horizontal tile corresponds to a Blue slice b in [0..N-1].
     */
    fun buildAtlasBitmap(lut: ParsedLut3D): Bitmap {
        val n = lut.size
        val bmp = Bitmap.createBitmap(n * n, n, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(n * n * n)
        for (b in 0 until n) {
            for (g in 0 until n) {
                for (r in 0 until n) {
                    val srcIdx = ((b * n * n) + (g * n) + r) * 3
                    val red = (lut.rgbData[srcIdx] * 255f).toInt().coerceIn(0, 255)
                    val green = (lut.rgbData[srcIdx + 1] * 255f).toInt().coerceIn(0, 255)
                    val blue = (lut.rgbData[srcIdx + 2] * 255f).toInt().coerceIn(0, 255)
                    val dstX = b * n + r
                    val dstY = g
                    pixels[dstY * (n * n) + dstX] = Color.argb(255, red, green, blue)
                }
            }
        }
        bmp.setPixels(pixels, 0, n * n, 0, 0, n * n, n)
        return bmp
    }

    /**
     * Builds a 256x1 RGBA 1D LUT texture encoding Master + R/G/B curves for GPU Color Grading.
     */
    fun buildCurvesBitmap(spec: ColorGradingSpec): Bitmap {
        val bmp = Bitmap.createBitmap(256, 1, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(256)
        for (i in 0 until 256) {
            val x = i / 255f
            val m = evalCurve(spec.curveMaster, x)
            val r = (evalCurve(spec.curveRed, m) * 255f).toInt().coerceIn(0, 255)
            val g = (evalCurve(spec.curveGreen, m) * 255f).toInt().coerceIn(0, 255)
            val b = (evalCurve(spec.curveBlue, m) * 255f).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(255, r, g, b)
        }
        bmp.setPixels(pixels, 0, 256, 0, 0, 256, 1)
        return bmp
    }

    fun evalCurve(points: List<CurvePoint>, xIn: Float): Float {
        if (points.isEmpty()) return xIn
        val sorted = points.sortedBy { it.x }
        val x = xIn.coerceIn(0f, 1f)
        if (x <= sorted.first().x) return sorted.first().y.coerceIn(0f, 1f)
        if (x >= sorted.last().x) return sorted.last().y.coerceIn(0f, 1f)
        for (i in 0 until sorted.lastIndex) {
            val p0 = sorted[i]
            val p1 = sorted[i + 1]
            if (x in p0.x..p1.x) {
                val span = max(1e-4f, p1.x - p0.x)
                val t = (x - p0.x) / span
                // Smoothstep Hermite interpolation across control points
                val smooth = t * t * (3f - 2f * t)
                return (p0.y + (p1.y - p0.y) * smooth).coerceIn(0f, 1f)
            }
        }
        return x
    }
}
