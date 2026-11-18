package com.example.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.engine.EvaluatedLayer
import com.example.model.AssetItem
import com.example.model.AssetType
import com.example.model.LayerType
import com.example.model.TextRangeMode
import com.example.model.VectorFillType
import com.example.model.VectorShapePreset
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class LayerRasterizer(private val context: Context) {

    private val imageCache = LruCache<String, Bitmap>(24)
    private val videoFrameCache = LruCache<String, Bitmap>(48)
    private val maskCache = LruCache<String, Bitmap>(16)
    private val fontCache = HashMap<String, Typeface>()

    fun rasterizeLayerBitmap(
        evaluated: EvaluatedLayer,
        assets: List<AssetItem>,
        compWidth: Int,
        compHeight: Int,
        audioAmplitude: Float
    ): Bitmap? {
        val layer = evaluated.layer
        val rawBmp = when (layer.type) {
            LayerType.VECTOR -> drawVectorBitmap(evaluated)
            LayerType.TEXT -> drawTextBitmap(evaluated)
            LayerType.IMAGE -> loadImageBitmap(layer.assetId, assets)
            LayerType.VIDEO -> loadVideoFrameBitmap(layer.assetId, assets, evaluated.localTimeMs)
            LayerType.AUDIO -> {
                if (layer.audioSpec?.visualizerEnabled == true) {
                    drawAudioVisualizerBitmap(assets.firstOrNull { it.id == layer.assetId }, evaluated.localTimeMs, compWidth, audioAmplitude)
                } else null
            }
            LayerType.NULL, LayerType.CAMERA_3D, LayerType.LIGHT_3D, LayerType.ADJUSTMENT, LayerType.PRE_COMP -> null
        } ?: return null

        val cutoutUri = layer.aiCutoutMaskUri
        if (!cutoutUri.isNullOrBlank()) {
            val maskBmp = loadMaskBitmap(cutoutUri)
            if (maskBmp != null) {
                return applyAlphaCutoutMask(rawBmp, maskBmp)
            }
        }
        return rawBmp
    }

    private fun loadMaskBitmap(uriStr: String): Bitmap? {
        maskCache.get(uriStr)?.let { if (!it.isRecycled) return it }
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }?.also { bmp ->
                maskCache.put(uriStr, bmp)
            }
        }.getOrNull()
    }

    private fun applyAlphaCutoutMask(source: Bitmap, mask: Bitmap): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val dstRect = android.graphics.Rect(0, 0, out.width, out.height)
        canvas.drawBitmap(mask, null, dstRect, paint)
        return out
    }

    private fun drawVectorBitmap(ev: EvaluatedLayer): Bitmap {
        val spec = ev.layer.vectorSpec!!
        val pad = (ev.strokeWidth * 2f + 32f).toInt().coerceAtLeast(32)
        val bmpW = (spec.width + pad * 2).toInt().coerceIn(64, 1400)
        val bmpH = (spec.height + pad * 2).toInt().coerceIn(64, 1400)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val cx = bmpW / 2f
        val cy = bmpH / 2f
        val halfW = spec.width / 2f
        val halfH = spec.height / 2f

        val path = Path()
        when (spec.preset) {
            VectorShapePreset.RECTANGLE -> {
                val rect = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
                path.addRoundRect(rect, spec.cornerRadius, spec.cornerRadius, Path.Direction.CW)
            }
            VectorShapePreset.CIRCLE -> {
                val rx = halfW
                val ry = halfH
                path.addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW)
            }
            VectorShapePreset.STAR -> {
                val pts = spec.starPoints.coerceIn(3, 12)
                val outerR = min(halfW, halfH)
                val innerR = outerR * 0.45f
                for (i in 0 until pts * 2) {
                    val angle = Math.toRadians((i * (360.0 / (pts * 2))) - 90.0)
                    val r = if (i % 2 == 0) outerR else innerR
                    val px = cx + (cos(angle) * r).toFloat()
                    val py = cy + (sin(angle) * r).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            VectorShapePreset.POLYGON -> {
                val sides = spec.starPoints.coerceIn(3, 12)
                val r = min(halfW, halfH)
                for (i in 0 until sides) {
                    val angle = Math.toRadians((i * (360.0 / sides)) - 90.0)
                    val px = cx + (cos(angle) * r).toFloat()
                    val py = cy + (sin(angle) * r).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            VectorShapePreset.FREEHAND -> {
                if (spec.points.isNotEmpty()) {
                    val pts = spec.points
                    path.moveTo(cx + pts[0].x, cy + pts[0].y)
                    for (i in 1 until pts.size) {
                        val prev = pts[i - 1]
                        val cur = pts[i]
                        if (prev.outHandleX != 0f || prev.outHandleY != 0f || cur.inHandleX != 0f || cur.inHandleY != 0f) {
                            path.cubicTo(
                                cx + prev.x + prev.outHandleX, cy + prev.y + prev.outHandleY,
                                cx + cur.x + cur.inHandleX, cy + cur.y + cur.inHandleY,
                                cx + cur.x, cy + cur.y
                            )
                        } else {
                            path.lineTo(cx + cur.x, cy + cur.y)
                        }
                    }
                    if (spec.closed && pts.size > 2) path.close()
                } else {
                    path.addRoundRect(RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH), 24f, 24f, Path.Direction.CW)
                }
            }
        }

        // Fill Paint
        if (spec.fillType != VectorFillType.NONE) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val c1 = parseColorSafe(spec.fillColorHex, Color.CYAN)
            val c2 = parseColorSafe(spec.fillColorEndHex, Color.MAGENTA)
            when (spec.fillType) {
                VectorFillType.SOLID -> fillPaint.color = c1
                VectorFillType.LINEAR_GRADIENT -> {
                    fillPaint.shader = LinearGradient(
                        cx - halfW, cy - halfH, cx + halfW, cy + halfH,
                        c1, c2, Shader.TileMode.CLAMP
                    )
                }
                VectorFillType.RADIAL_GRADIENT -> {
                    fillPaint.shader = RadialGradient(
                        cx, cy, min(halfW, halfH).coerceAtLeast(10f),
                        c1, c2, Shader.TileMode.CLAMP
                    )
                }
                VectorFillType.NONE -> Unit
            }
            canvas.drawPath(path, fillPaint)
        }

        // Stroke Paint with Trim Paths via PathMeasure
        if (ev.strokeWidth > 0.2f) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = ev.strokeWidth
                color = parseColorSafe(spec.strokeColorHex, Color.WHITE)
                strokeCap = if (spec.strokeCapRound) Paint.Cap.ROUND else Paint.Cap.SQUARE
                strokeJoin = Paint.Join.ROUND
                if (spec.dashLength > 0.5f) {
                    pathEffect = DashPathEffect(floatArrayOf(spec.dashLength, spec.dashLength * 0.7f), 0f)
                }
            }

            val startFrac = (ev.trimStart + ev.trimOffset).coerceIn(0f, 1f)
            val endFrac = (ev.trimEnd + ev.trimOffset).coerceIn(0f, 1f)
            if (startFrac <= 0.001f && endFrac >= 0.999f) {
                canvas.drawPath(path, strokePaint)
            } else if (endFrac > startFrac) {
                val measure = PathMeasure(path, false)
                val trimmed = Path()
                val len = measure.length
                measure.getSegment(len * startFrac, len * endFrac, trimmed, true)
                canvas.drawPath(trimmed, strokePaint)
            }
        }
        return bmp
    }

    private fun drawTextBitmap(ev: EvaluatedLayer): Bitmap {
        val spec = ev.layer.textSpec!!
        val typeface = resolveTypeface(spec.fontFamilyKey, spec.customFontUri)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = ev.textFontSize
            letterSpacing = ev.textTracking / 100f
            color = parseColorSafe(spec.colorHex, Color.WHITE)
            textAlign = Paint.Align.LEFT
        }
        val strokePaint = if (spec.strokeWidth > 0.2f) {
            Paint(textPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = spec.strokeWidth
                color = parseColorSafe(spec.strokeColorHex, Color.CYAN)
                strokeJoin = Paint.Join.ROUND
            }
        } else null

        val content = spec.content.ifEmpty { "Texto" }
        val lines = content.split("\n")
        val maxLineW = lines.maxOfOrNull { textPaint.measureText(it) }?.coerceAtLeast(80f) ?: 200f
        val lineH = ev.textFontSize * 1.28f
        val pad = 80f
        val bmpW = (maxLineW + pad * 2f).toInt().coerceIn(120, 1800)
        val bmpH = (lines.size * lineH + pad * 2f + kotlin.math.abs(ev.textAnimOffsetY) * 2f).toInt().coerceIn(120, 1200)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Tokenize according to TextRangeMode
        val totalChars = content.replace("\n", "").length.coerceAtLeast(1)
        var globalIdx = 0
        val baseY = (bmpH - lines.size * lineH) / 2f + ev.textFontSize * 0.85f

        lines.forEachIndexed { lineIdx, line ->
            val lineWidth = textPaint.measureText(line)
            var cursorX = (bmpW - lineWidth) / 2f
            val yPos = baseY + lineIdx * lineH

            val units: List<String> = when (spec.rangeMode) {
                TextRangeMode.CHARACTER -> line.map { it.toString() }
                TextRangeMode.WORD -> line.split(" ").mapIndexed { idx, w -> if (idx < line.split(" ").lastIndex) "$w " else w }
                TextRangeMode.LINE -> listOf(line)
            }
            val totalUnits = when (spec.rangeMode) {
                TextRangeMode.CHARACTER -> totalChars
                TextRangeMode.WORD -> content.split(" ").size.coerceAtLeast(1)
                TextRangeMode.LINE -> lines.size.coerceAtLeast(1)
            }

            for (unit in units) {
                val uWidth = textPaint.measureText(unit)
                val normPos = (globalIdx.toFloat() / totalUnits.toFloat()).coerceIn(0f, 1f)
                val shifted = ((normPos - ev.textRangeOffset) % 1f + 1f) % 1f
                val inRange = when {
                    ev.textRangeEnd <= ev.textRangeStart + 0.0001f -> false
                    ev.textRangeEnd >= 0.999f -> shifted >= ev.textRangeStart
                    else -> shifted >= ev.textRangeStart && shifted < ev.textRangeEnd
                }
                val selFactor = if (inRange) 1f else 0f

                val wigX = if (ev.textWiggly > 0.1f && inRange) {
                    sin((ev.localTimeMs / 120.0) + globalIdx * 1.7).toFloat() * ev.textWiggly
                } else 0f
                val wigY = if (ev.textWiggly > 0.1f && inRange) {
                    cos((ev.localTimeMs / 140.0) + globalIdx * 2.3).toFloat() * ev.textWiggly
                } else 0f

                val charScale = 1f + (ev.textAnimScale - 1f) * selFactor
                val charOffsetY = ev.textAnimOffsetY * (1f - selFactor)
                val charRot = ev.textAnimRotation * (1f - selFactor)
                val charAlpha = ((if (inRange) 1f else ev.textAnimOpacity) * 255f).toInt().coerceIn(0, 255)
                val drawUnit = if (!inRange && ev.textScramble > 0.1f) scrambleUnit(unit, globalIdx, ev.localTimeMs) else unit

                textPaint.alpha = charAlpha
                strokePaint?.alpha = charAlpha

                canvas.save()
                canvas.translate(cursorX + uWidth / 2f + wigX, yPos + charOffsetY + wigY)
                if (charRot != 0f) canvas.rotate(charRot)
                canvas.scale(charScale, charScale)
                if (strokePaint != null) {
                    canvas.drawText(drawUnit, -uWidth / 2f, 0f, strokePaint)
                }
                canvas.drawText(drawUnit, -uWidth / 2f, 0f, textPaint)
                canvas.restore()

                cursorX += uWidth
                globalIdx++
            }
        }
        return bmp
    }

    private fun drawAudioVisualizerBitmap(
        asset: AssetItem?,
        timeMs: Long,
        compWidth: Int,
        audioAmp: Float
    ): Bitmap {
        val w = 720
        val h = 240
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Color.parseColor("#00E5FF"), Color.parseColor("#7C3AED"), Shader.TileMode.CLAMP)
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 10f
        }
        val bars = 36
        val spacing = w.toFloat() / bars
        val wf = asset?.waveform ?: emptyList()
        val dur = asset?.durationMs ?: 8000L
        val baseIdx = ((timeMs.toFloat() / dur.coerceAtLeast(1L)) * wf.size).toInt()
        for (i in 0 until bars) {
            val sample = if (wf.isNotEmpty()) {
                wf[(baseIdx + i).coerceIn(0, wf.lastIndex)]
            } else {
                (0.25f + 0.75f * kotlin.math.abs(sin(i * 0.45 + timeMs / 160.0).toFloat()) * (0.4f + audioAmp))
            }
            val barH = (sample * (h * 0.85f)).coerceAtLeast(12f)
            val x = i * spacing + spacing / 2f
            canvas.drawLine(x, h / 2f - barH / 2f, x, h / 2f + barH / 2f, paint)
        }
        return bmp
    }

    private fun loadImageBitmap(assetId: String?, assets: List<AssetItem>): Bitmap? {
        val asset = assets.firstOrNull { it.id == assetId && it.type == AssetType.IMAGE } ?: return null
        imageCache.get(asset.id)?.let { if (!it.isRecycled) return it }
        return runCatching {
            val uri = Uri.parse(asset.uri)
            val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }?.let { bmp ->
                val scaled = constrainBitmapSize(bmp, 1440)
                val finalBmp = if (scaled.config == Bitmap.Config.ARGB_8888) scaled else scaled.copy(Bitmap.Config.ARGB_8888, false)
                imageCache.put(asset.id, finalBmp)
                finalBmp
            }
        }.getOrNull()
    }

    private fun loadVideoFrameBitmap(assetId: String?, assets: List<AssetItem>, timeMs: Long): Bitmap? {
        val asset = assets.firstOrNull { it.id == assetId && it.type == AssetType.VIDEO } ?: return null
        val quantizedMs = (timeMs / 40L) * 40L // ~25fps key cache quantization for smooth scrubbing
        val key = "${asset.id}_$quantizedMs"
        videoFrameCache.get(key)?.let { if (!it.isRecycled) return it }
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(asset.uri))
            val frame = retriever.getFrameAtTime(
                quantizedMs.coerceIn(0L, asset.durationMs) * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            )
            retriever.release()
            frame?.let {
                val scaled = constrainBitmapSize(it, 1080)
                videoFrameCache.put(key, scaled)
                scaled
            }
        }.getOrNull()
    }

    private fun constrainBitmapSize(bmp: Bitmap, maxDim: Int): Bitmap {
        if (bmp.width <= maxDim && bmp.height <= maxDim) return bmp
        val ratio = min(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
        val nw = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val nh = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, nw, nh, true)
    }

    private fun resolveTypeface(key: String, customUri: String?): Typeface {
        if (!customUri.isNullOrBlank()) {
            fontCache[customUri]?.let { return it }
            runCatching {
                val path = Uri.parse(customUri).path
                if (path != null) {
                    val tf = Typeface.createFromFile(path)
                    fontCache[customUri] = tf
                    return tf
                }
            }
        }
        fontCache[key]?.let { return it }
        val bundled = when (key) {
            "jetbrains_mono" -> R.font.jetbrains_mono
            "plus_jakarta_sans" -> R.font.plus_jakarta_sans
            "space_grotesk" -> R.font.space_grotesk
            else -> null
        }
        if (bundled != null) {
            val tf = runCatching { ResourcesCompat.getFont(context, bundled) }.getOrNull()
            if (tf != null) {
                fontCache[key] = tf
                return tf
            }
        }
        val font = com.example.model.StudioFonts.info(key)
        val family = font?.androidFamily
        val style = if (font?.bold == true) Typeface.BOLD else Typeface.NORMAL
        val tf = if (!family.isNullOrBlank()) {
            Typeface.create(family, style) ?: Typeface.DEFAULT
        } else {
            runCatching { ResourcesCompat.getFont(context, R.font.space_grotesk) }.getOrNull()
                ?: Typeface.DEFAULT_BOLD
        }
        fontCache[key] = tf
        return tf
    }

    private fun scrambleUnit(unit: String, index: Int, timeMs: Long): String {
        if (unit.isBlank()) return unit
        val glyphs = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789#$%"
        val seed = (timeMs / 42L + index * 13L).toInt()
        return buildString(unit.length) {
            unit.forEachIndexed { i, ch ->
                if (ch.isWhitespace()) append(ch)
                else append(glyphs[kotlin.math.abs(seed + i * 7 + ch.code) % glyphs.length])
            }
        }
    }

    fun parseColorSafe(hex: String, fallback: Int): Int {
        return runCatching { Color.parseColor(hex) }.getOrDefault(fallback)
    }
}
