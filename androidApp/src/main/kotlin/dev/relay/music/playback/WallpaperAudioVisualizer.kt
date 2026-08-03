package dev.relay.music.playback

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.relay.music.wallpaper.AlbumWallpaperService
import kotlin.math.sqrt

/** Low-resolution playback analysis for Relay's own wallpaper; no audio is stored or exported. */
internal class WallpaperAudioVisualizer(
    private val context: Context,
    private val audioSessionId: Int,
) {
    private var visualizer: Visualizer? = null
    private var lastPublishAtMs = 0L

    fun setEnabled(enabled: Boolean) {
        if (!enabled || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            release()
            publish(0f, FloatArray(BAND_COUNT), force = true)
            return
        }
        if (visualizer != null) return
        visualizer = runCatching {
            Visualizer(audioSessionId).apply {
                val range = Visualizer.getCaptureSizeRange()
                captureSize = range.first()
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(visualizer: Visualizer, waveform: ByteArray, samplingRate: Int) = Unit

                        override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) {
                            publish(audioLevelFromFft(fft), audioBandsFromFft(fft))
                        }
                    },
                    minOf(Visualizer.getMaxCaptureRate(), CAPTURE_RATE_MILLI_HZ),
                    false,
                    true,
                )
                this.enabled = true
            }
        }.getOrNull()
    }

    fun release() {
        visualizer?.let { effect -> runCatching { effect.enabled = false; effect.release() } }
        visualizer = null
    }

    private fun publish(level: Float, bands: FloatArray, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublishAtMs < MIN_PUBLISH_INTERVAL_MS) return
        lastPublishAtMs = now
        context.sendBroadcast(
            Intent(AlbumWallpaperService.ACTION_AUDIO_LEVEL)
                .setPackage(context.packageName)
                .putExtra(AlbumWallpaperService.EXTRA_AUDIO_LEVEL, level.coerceIn(0f, 1f))
                .putExtra(AlbumWallpaperService.EXTRA_AUDIO_BANDS, bands),
        )
    }

    private companion object {
        const val BAND_COUNT = 12
        const val CAPTURE_RATE_MILLI_HZ = 30_000
        const val MIN_PUBLISH_INTERVAL_MS = 33L
    }
}

internal fun audioLevelFromWaveform(waveform: ByteArray): Float {
    if (waveform.isEmpty()) return 0f
    var sum = 0.0
    waveform.forEach { sample ->
        val value = (sample.toInt() and 0xFF) - 128
        sum += value * value
    }
    return (sqrt(sum / waveform.size) / 128.0).toFloat().coerceIn(0f, 1f)
}

internal fun audioBandsFromWaveform(waveform: ByteArray, count: Int = 12): FloatArray =
    if (waveform.isEmpty()) FloatArray(count) else FloatArray(count) { band ->
        val start = waveform.size * band / count
        val end = (waveform.size * (band + 1) / count).coerceAtLeast(start + 1).coerceAtMost(waveform.size)
        audioLevelFromWaveform(waveform.copyOfRange(start, end))
    }

internal fun audioLevelFromFft(fft: ByteArray): Float = audioBandsFromFft(fft).maxOrNull() ?: 0f

internal fun audioBandsFromFft(fft: ByteArray, count: Int = 12): FloatArray {
    if (fft.size < 4) return FloatArray(count)
    val binCount = (fft.size / 2 - 1).coerceAtLeast(1)
    return FloatArray(count) { band ->
        val start = 1 + binCount * band / count
        val end = (1 + binCount * (band + 1) / count).coerceAtLeast(start + 1).coerceAtMost(binCount + 1)
        var energy = 0.0
        for (bin in start until end) {
            val real = fft[bin * 2].toInt()
            val imaginary = fft[bin * 2 + 1].toInt()
            energy += real * real + imaginary * imaginary
        }
        (kotlin.math.sqrt(energy / (end - start)) / 48.0).toFloat().coerceIn(0f, 1f)
    }
}
