package dev.relay.music.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import dev.relay.music.model.AlbumChartSpec
import dev.relay.music.model.InsightEntry
import java.io.File
import java.io.FileOutputStream

/** Renders a local, text-first chart image; it deliberately does not fetch artwork. */
internal object AlbumChartExporter {
    fun write(context: Context, spec: AlbumChartSpec, entries: List<InsightEntry>) =
        File(context.cacheDir, "chart-exports").also { it.mkdirs() }.let { directory ->
            val file = File(directory, "${spec.id}.png")
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            try {
                Canvas(bitmap).apply {
                    drawColor(INK)
                    drawText("RELAY", MARGIN, 92f, headingPaint)
                    drawText("ALBUM CHART · ${spec.range.name}", MARGIN, 138f, subtitlePaint)
                    entries.forEachIndexed { index, entry ->
                        val top = 190f + index * 122f
                        drawRect(MARGIN, top, WIDTH - MARGIN, top + 102f, rowPaint)
                        drawText((index + 1).toString().padStart(2, '0'), MARGIN + 22f, top + 58f, rankPaint)
                        drawText(ellipsize(entry.label, titlePaint, WIDTH - MARGIN * 2 - 150f), MARGIN + 92f, top + 48f, titlePaint)
                        drawText("${entry.plays} PLAYS", MARGIN + 92f, top + 78f, subtitlePaint)
                    }
                }
                FileOutputStream(file).use { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
            } finally {
                bitmap.recycle()
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

    private fun ellipsize(value: String, paint: Paint, width: Float): String {
        if (paint.measureText(value) <= width) return value
        val count = paint.breakText(value, true, width - paint.measureText("…"), null)
        return value.take(count) + "…"
    }

    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val MARGIN = 64f
    private const val INK = 0xFF050505.toInt()
    private const val PAPER = 0xFFF1F1EC.toInt()
    private const val MUTED = 0xFF92928B.toInt()
    private const val PANEL = 0xFF101010.toInt()
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER; textSize = 54f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 25f; typeface = android.graphics.Typeface.MONOSPACE }
    private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4B88FF.toInt(); textSize = 32f; typeface = android.graphics.Typeface.MONOSPACE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER; textSize = 31f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val rowPaint = Paint().apply { color = PANEL }
}
