package dev.relay.music.wallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import dev.relay.music.library.LocalArtworkCache
import dev.relay.music.library.RoomNowPlayingSnapshotStore
import dev.relay.music.library.UserLibraryStore
import dev.relay.music.playback.NowPlayingSnapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val INK = 0xFF101010.toInt()

/** Event-driven Canvas wallpaper. It consumes only Relay's local snapshot and artwork cache. */
class AlbumWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = AlbumWallpaperEngine()

    inner class AlbumWallpaperEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val artworkCache = LocalArtworkCache(File(cacheDir, "relay-artwork"))
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var surfaceReady = false
        private var generation = 0
        private var artwork: Bitmap? = null
        private var averageArtworkColor = INK
        private var snapshot: NowPlayingSnapshot? = null
        private var preset = WallpaperPreset()
        private var audioLevel = 0f
        private var audioBands = FloatArray(12)
        private var pageOffset = 0.5f
        private var receiverRegistered = false
        private val timedRedraw = object : Runnable {
            override fun run() {
                if (!visible || !surfaceReady) return
                drawFrame()
                scheduleTimedRedraw()
            }
        }
        private val snapshotReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_SNAPSHOT_CHANGED, ACTION_PRESET_CHANGED -> refresh()
                    ACTION_AUDIO_LEVEL -> if (preset.soundReactive && !preset.batterySaver) {
                        audioLevel = intent.getFloatExtra(EXTRA_AUDIO_LEVEL, 0f).coerceIn(0f, 1f)
                        audioBands = intent.getFloatArrayExtra(EXTRA_AUDIO_BANDS)
                            ?.take(24)?.map { it.coerceIn(0f, 1f) }?.toFloatArray() ?: FloatArray(12)
                        drawFrame()
                    }
                }
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            if (visible != isVisible) {
                if (isVisible) visibleEngines.incrementAndGet() else visibleEngines.updateAndGet { (it - 1).coerceAtLeast(0) }
                sendBroadcast(Intent(ACTION_VISIBILITY_CHANGED).setPackage(packageName))
            }
            visible = isVisible
            if (isVisible) {
                registerSnapshotReceiver()
                refresh()
            } else {
                unregisterSnapshotReceiver()
                generation++
                handler.removeCallbacks(timedRedraw)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            surfaceReady = true
            if (visible) refresh()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (visible) refresh()
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            pageOffset = xOffset.coerceIn(0f, 1f)
            if (visible && preset.canvas.pageOffset == WallpaperPageOffset.FOLLOW) drawFrame()
        }

        override fun onDestroy() {
            if (visible) {
                visible = false
                visibleEngines.updateAndGet { (it - 1).coerceAtLeast(0) }
                sendBroadcast(Intent(ACTION_VISIBILITY_CHANGED).setPackage(packageName))
            }
            unregisterSnapshotReceiver()
            handler.removeCallbacks(timedRedraw)
            generation++
            scope.cancel()
            artwork?.recycle()
            artwork = null
            super.onDestroy()
        }

        private fun registerSnapshotReceiver() {
            if (receiverRegistered) return
            val filter = IntentFilter(ACTION_SNAPSHOT_CHANGED).apply {
                addAction(ACTION_PRESET_CHANGED)
                addAction(ACTION_AUDIO_LEVEL)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(snapshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(snapshotReceiver, filter)
            }
            receiverRegistered = true
        }

        private fun unregisterSnapshotReceiver() {
            if (!receiverRegistered) return
            runCatching { unregisterReceiver(snapshotReceiver) }
            receiverRegistered = false
        }

        private fun refresh() {
            if (!visible || !surfaceReady) return
            val requestGeneration = ++generation
            val bounds = surfaceHolder.surfaceFrame
            val width = bounds.width().coerceAtLeast(1)
            val height = bounds.height().coerceAtLeast(1)
            scope.launch {
                val dao = UserLibraryStore.database(this@AlbumWallpaperService).userLibraryDao()
                val nextSnapshot = RoomNowPlayingSnapshotStore(dao).read()
                val nextPreset = dao.settingsSnapshot()?.asSettings()?.wallpaperPreset ?: WallpaperPreset()
                val decoded = decodeArtwork(artworkCache.resolve(nextSnapshot?.artworkCacheKey), width, height)
                val filtered = decoded?.let { applyArtworkFilters(it, nextPreset.filters) }
                val average = filtered?.let(::averageColor) ?: INK
                withContext(Dispatchers.Main.immediate) {
                    if (!visible || !surfaceReady || requestGeneration != generation) {
                        filtered?.recycle()
                        return@withContext
                    }
                    artwork?.recycle()
                    artwork = filtered
                    averageArtworkColor = average
                    snapshot = nextSnapshot
                    preset = nextPreset
                    audioLevel = 0f
                    handler.removeCallbacks(timedRedraw)
                    drawFrame()
                    scheduleTimedRedraw()
                }
            }
        }

        private fun scheduleTimedRedraw() {
            handler.removeCallbacks(timedRedraw)
            if (!visible || !surfaceReady) return
            val target = surfaceTarget()
            val hasClock = preset.elements.any { it is WallpaperElement.Clock && it.layout.visibility.includes(target) }
            val hasActiveProgress = snapshot?.isPlaying == true && preset.elements.any {
                it is WallpaperElement.Progress && it.layout.visibility.includes(target)
            }
            val delay = when {
                hasActiveProgress && !preset.batterySaver -> 1_000L
                hasClock -> 60_000L - System.currentTimeMillis() % 60_000L
                else -> return
            }
            handler.postDelayed(timedRedraw, delay.coerceAtLeast(250L))
        }

        private fun drawFrame() {
            if (!visible || !surfaceReady) return
            val canvas = lockCanvas() ?: return
            try {
                val background = when (preset.canvas.background) {
                    WallpaperCanvasBackground.SOLID -> preset.canvas.solidColorArgb.toInt()
                    WallpaperCanvasBackground.ARTWORK_AVERAGE -> averageArtworkColor
                }
                canvas.drawColor(background)
                val target = surfaceTarget()
                val textColor = readableTextColor(background)
                preset.elements.forEach { element ->
                    if (!element.layout.visibility.includes(target)) return@forEach
                    val bounds = wallpaperElementBounds(element.layout, canvas.width.toFloat(), canvas.height.toFloat())
                        .let { RectF(it.left, it.top, it.left + it.width, it.top + it.height) }
                    when (element) {
                        is WallpaperElement.Artwork -> artwork?.let { drawArtwork(canvas, it, bounds, preset.canvas.artworkFit, element.layout.opacity, pageShift(bounds), if (preset.soundReactive && !preset.batterySaver) audioLevel else 0f) }
                        is WallpaperElement.Title -> if (preset.showMetadata) drawTextElement(canvas, snapshot?.title, bounds, element.layout, textColor)
                        is WallpaperElement.Artist -> if (preset.showMetadata) drawTextElement(canvas, snapshot?.artist, bounds, element.layout, textColor)
                        is WallpaperElement.Album -> if (preset.showMetadata) drawTextElement(canvas, snapshot?.album, bounds, element.layout, textColor)
                        is WallpaperElement.Clock -> drawTextElement(canvas, currentClockText(), bounds, element.layout, textColor)
                        is WallpaperElement.Progress -> drawProgress(canvas, snapshot, bounds, element.layout.opacity, textColor)
                    }
                }
                if (artwork == null && preset.elements.any { it is WallpaperElement.Artwork }) drawFallback(canvas, textColor)
                if (preset.soundReactive && !preset.batterySaver && preset.visualizer != WallpaperVisualizer.OFF) {
                    drawVisualizer(canvas, preset.visualizer, audioLevel, audioBands)
                }
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        private fun pageShift(bounds: RectF): Float = if (preset.canvas.pageOffset == WallpaperPageOffset.FOLLOW) {
            (pageOffset - 0.5f) * bounds.width() * -0.2f
        } else 0f

        private fun surfaceTarget(): WallpaperVisibility {
            if (isPreview || Build.VERSION.SDK_INT < 34) return WallpaperVisibility.BOTH
            val flags = wallpaperFlags
            return when {
                flags and WallpaperManager.FLAG_LOCK != 0 && flags and WallpaperManager.FLAG_SYSTEM == 0 -> WallpaperVisibility.LOCK
                flags and WallpaperManager.FLAG_SYSTEM != 0 && flags and WallpaperManager.FLAG_LOCK == 0 -> WallpaperVisibility.HOME
                else -> WallpaperVisibility.BOTH
            }
        }

        private fun lockCanvas(): Canvas? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) surfaceHolder.lockHardwareCanvas() else surfaceHolder.lockCanvas()
        }.getOrElse { runCatching { surfaceHolder.lockCanvas() }.getOrNull() }
    }

    companion object {
        const val ACTION_SNAPSHOT_CHANGED = "dev.relay.music.action.NOW_PLAYING_SNAPSHOT_CHANGED"
        const val ACTION_PRESET_CHANGED = "dev.relay.music.action.WALLPAPER_PRESET_CHANGED"
        const val ACTION_AUDIO_LEVEL = "dev.relay.music.action.WALLPAPER_AUDIO_LEVEL"
        const val ACTION_VISIBILITY_CHANGED = "dev.relay.music.action.WALLPAPER_VISIBILITY_CHANGED"
        const val EXTRA_AUDIO_LEVEL = "audio_level"
        const val EXTRA_AUDIO_BANDS = "audio_bands"
        private val visibleEngines = AtomicInteger()

        fun hasVisibleEngine(): Boolean = visibleEngines.get() > 0
    }
}

internal fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    var sample = 1
    while (width / sample > targetWidth * 2 || height / sample > targetHeight * 2) sample *= 2
    return sample
}

private fun decodeArtwork(file: File?, targetWidth: Int, targetHeight: Int): Bitmap? {
    file ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    })
}

/** Static filters run once per artwork/preset refresh; render frames only reuse the returned bitmap. */
internal fun applyArtworkFilters(source: Bitmap, filters: List<ArtworkFilter>): Bitmap {
    var current = source
    filters.forEach { filter ->
        val next = when (filter) {
            is ArtworkFilter.Grayscale -> colorMatrixBitmap(current, ColorMatrix().apply { setSaturation(1f - filter.amount) })
            is ArtworkFilter.Blur -> blurBitmap(current, filter.radius)
            is ArtworkFilter.Duotone -> colorMatrixBitmap(current, duotoneMatrix(filter))
            is ArtworkFilter.BrightnessContrast -> colorMatrixBitmap(current, brightnessContrastMatrix(filter))
            is ArtworkFilter.Vignette -> vignetteBitmap(current, filter.strength)
            is ArtworkFilter.Grain -> grainBitmap(current, filter.strength, filter.seed)
        }
        if (next !== current) {
            if (current !== source) current.recycle()
            current = next
        }
    }
    if (current !== source) source.recycle()
    return current
}

private fun colorMatrixBitmap(source: Bitmap, matrix: ColorMatrix): Bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { output ->
    Canvas(output).drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(matrix)
    })
}

private fun blurBitmap(source: Bitmap, radius: Float): Bitmap {
    if (radius <= 0f) return source
    val divisor = (1f + radius.coerceIn(0f, 25f) / 2.5f).toInt().coerceIn(2, 11)
    val small = Bitmap.createScaledBitmap(source, (source.width / divisor).coerceAtLeast(1), (source.height / divisor).coerceAtLeast(1), true)
    return Bitmap.createScaledBitmap(small, source.width, source.height, true).also { if (it !== small) small.recycle() }
}

private fun duotoneMatrix(filter: ArtworkFilter.Duotone): ColorMatrix {
    val low = intArrayOf(Color.red(filter.shadowColorArgb.toInt()), Color.green(filter.shadowColorArgb.toInt()), Color.blue(filter.shadowColorArgb.toInt()))
    val high = intArrayOf(Color.red(filter.highlightColorArgb.toInt()), Color.green(filter.highlightColorArgb.toInt()), Color.blue(filter.highlightColorArgb.toInt()))
    val amount = filter.amount.coerceIn(0f, 1f)
    val luminance = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
    val values = FloatArray(20)
    repeat(3) { channel ->
        repeat(3) { input ->
            values[channel * 5 + input] = amount * luminance[input] * (high[channel] - low[channel]) + if (channel == input) 1f - amount else 0f
        }
        values[channel * 5 + 4] = amount * low[channel]
    }
    values[18] = 1f
    return ColorMatrix(values)
}

private fun brightnessContrastMatrix(filter: ArtworkFilter.BrightnessContrast): ColorMatrix {
    val contrast = filter.contrast.coerceIn(0.25f, 2f)
    val offset = (1f - contrast) * 128f + filter.brightness.coerceIn(-1f, 1f) * 255f
    return ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, offset,
        0f, contrast, 0f, 0f, offset,
        0f, 0f, contrast, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    ))
}

private fun vignetteBitmap(source: Bitmap, strength: Float): Bitmap {
    if (strength <= 0f) return source
    return source.copy(Bitmap.Config.ARGB_8888, true).also { output ->
        val radius = max(output.width, output.height) * 0.72f
        Canvas(output).drawCircle(
            output.width / 2f,
            output.height / 2f,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    output.width / 2f,
                    output.height / 2f,
                    radius,
                    intArrayOf(Color.TRANSPARENT, Color.argb((220 * strength).toInt(), 0, 0, 0)),
                    floatArrayOf(0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
    }
}

private fun grainBitmap(source: Bitmap, strength: Float, seed: Int): Bitmap {
    if (strength <= 0f) return source
    return source.copy(Bitmap.Config.ARGB_8888, true).also { output ->
        val count = (output.width.toLong() * output.height * strength / 80).toInt().coerceIn(1, 30_000)
        val light = FloatArray(count * 2)
        val dark = FloatArray(count * 2)
        val random = Random(seed)
        for (index in 0 until count step 2) {
            light[index] = random.nextInt(output.width).toFloat()
            light[index + 1] = random.nextInt(output.height).toFloat()
            dark[index] = random.nextInt(output.width).toFloat()
            dark[index + 1] = random.nextInt(output.height).toFloat()
        }
        val alpha = (80 * strength).toInt().coerceIn(1, 80)
        Canvas(output).apply {
            drawPoints(light, Paint().apply { color = Color.WHITE; this.alpha = alpha })
            drawPoints(dark, Paint().apply { color = Color.BLACK; this.alpha = alpha })
        }
    }
}

private fun averageColor(bitmap: Bitmap): Int {
    val step = max(1, (bitmap.width.toLong() * bitmap.height / 4_096).toInt())
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    var index = 0
    val total = bitmap.width * bitmap.height
    while (index < total) {
        val color = bitmap.getPixel(index % bitmap.width, index / bitmap.width)
        red += Color.red(color)
        green += Color.green(color)
        blue += Color.blue(color)
        count++
        index += step
    }
    return if (count == 0L) INK else Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
}

private fun drawArtwork(
    canvas: Canvas,
    bitmap: Bitmap,
    bounds: RectF,
    fit: WallpaperArtworkFit,
    opacity: Float,
    pageShift: Float,
    audioLevel: Float,
) {
    val shifted = RectF(bounds).apply {
        offset(pageShift, 0f)
        val pulse = 1f + audioLevel * 0.025f
        inset(-width() * (pulse - 1f) / 2f, -height() * (pulse - 1f) / 2f)
    }
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (opacity.coerceIn(0f, 1f) * 255).toInt() }
    canvas.save()
    canvas.clipRect(bounds)
    if (fit == WallpaperArtworkFit.FIT) {
        val scale = min(shifted.width() / bitmap.width, shifted.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val destination = RectF(shifted.centerX() - width / 2, shifted.centerY() - height / 2, shifted.centerX() + width / 2, shifted.centerY() + height / 2)
        canvas.drawBitmap(bitmap, null, destination, paint)
    } else {
        val targetRatio = shifted.width() / shifted.height()
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val source = if (sourceRatio > targetRatio) {
            val width = (bitmap.height * targetRatio).toInt()
            Rect((bitmap.width - width) / 2, 0, (bitmap.width + width) / 2, bitmap.height)
        } else {
            val height = (bitmap.width / targetRatio).toInt()
            Rect(0, (bitmap.height - height) / 2, bitmap.width, (bitmap.height + height) / 2)
        }
        canvas.drawBitmap(bitmap, source, shifted, paint)
    }
    canvas.restore()
}

private fun drawTextElement(canvas: Canvas, raw: String?, bounds: RectF, layout: WallpaperElementLayout, color: Int) {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        alpha = (layout.opacity.coerceIn(0f, 1f) * 255).toInt()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, if (layout.font == WallpaperFont.TITLE || layout.font == WallpaperFont.DISPLAY) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        textSize = min(bounds.height() * 0.82f, when (layout.font) {
            WallpaperFont.METADATA -> 14f
            WallpaperFont.BODY -> 18f
            WallpaperFont.TITLE -> 26f
            WallpaperFont.DISPLAY -> 40f
        } * Resources.getSystem().displayMetrics.scaledDensity)
    }
    val label = ellipsize(text.take(128), paint, bounds.width())
    val x = when (layout.alignment) {
        WallpaperTextAlignment.START -> bounds.left
        WallpaperTextAlignment.CENTER -> bounds.centerX() - paint.measureText(label) / 2f
        WallpaperTextAlignment.END -> bounds.right - paint.measureText(label)
    }
    canvas.drawText(label, x, bounds.top - paint.ascent(), paint)
}

private fun ellipsize(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    val suffix = "…"
    var end = text.length
    while (end > 0 && paint.measureText(text, 0, end) + paint.measureText(suffix) > width) end--
    return text.take(end) + suffix
}

private fun drawProgress(canvas: Canvas, snapshot: NowPlayingSnapshot?, bounds: RectF, opacity: Float, color: Int) {
    val duration = snapshot?.durationMs?.takeIf { it > 0 } ?: return
    val elapsed = if (snapshot.isPlaying) System.currentTimeMillis() - snapshot.updatedAtEpochMs else 0L
    val position = (snapshot.positionMs + elapsed.coerceAtLeast(0)).coerceIn(0, duration)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = (opacity.coerceIn(0f, 1f) * 90).toInt() }
    canvas.drawRect(bounds, paint)
    paint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
    canvas.drawRect(bounds.left, bounds.top, bounds.left + bounds.width() * position / duration.toFloat(), bounds.bottom, paint)
}

private fun currentClockText(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun readableTextColor(background: Int): Int =
    if (Color.red(background) * 299 + Color.green(background) * 587 + Color.blue(background) * 114 > 150_000) Color.BLACK else Color.WHITE

private fun drawFallback(canvas: Canvas, color: Int) {
    canvas.drawText(
        "NO ARTWORK",
        24f,
        canvas.height - 32f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 14f * Resources.getSystem().displayMetrics.scaledDensity
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        },
    )
}

private fun drawVisualizer(canvas: Canvas, style: WallpaperVisualizer, level: Float, bands: FloatArray) {
    if (style == WallpaperVisualizer.WAVE) drawWave(canvas, bands) else drawCanvasBars(canvas, bands, level)
}

private fun drawCanvasBars(canvas: Canvas, bands: FloatArray, level: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 150 }
    val values = if (bands.isEmpty()) FloatArray(12) else bands
    val gap = canvas.width / (values.size * 3f)
    val width = gap * 2f
    values.forEachIndexed { index, band ->
        val height = (canvas.height * 0.18f * band.coerceAtLeast(level * 0.35f)).coerceAtLeast(2f)
        val left = gap * (index * 3 + 1)
        canvas.drawRect(left, canvas.height - height - 24f, left + width, canvas.height - 24f, paint)
    }
}

private fun drawWave(canvas: Canvas, bands: FloatArray) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 165; style = Paint.Style.STROKE; strokeWidth = 3f }
    val path = android.graphics.Path()
    val values = if (bands.isEmpty()) FloatArray(12) else bands
    values.forEachIndexed { index, value ->
        val x = canvas.width * index / (values.size - 1).coerceAtLeast(1).toFloat()
        val y = canvas.height * (0.88f - value.coerceIn(0f, 1f) * 0.16f)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    canvas.drawPath(path, paint)
}
