package dev.relay.music.desktop

import java.io.File

internal interface DesktopAudio : AutoCloseable {
    fun load(file: File): Boolean
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun positionMs(): Long
    fun durationMs(): Long
    fun setPlaybackSpeed(speed: Float)
}

internal class NativeAudio : DesktopAudio {
    private var handle = create().also { check(it != 0L) { "Could not open an audio device." } }

    @Synchronized override fun load(file: File): Boolean = handle != 0L && load(handle, file.absolutePath)
    @Synchronized override fun play() { if (handle != 0L) play(handle) }
    @Synchronized override fun pause() { if (handle != 0L) pause(handle) }
    @Synchronized override fun seekTo(positionMs: Long) { if (handle != 0L) seek(handle, positionMs.coerceAtLeast(0)) }
    @Synchronized override fun positionMs(): Long = if (handle == 0L) 0 else position(handle)
    @Synchronized override fun durationMs(): Long = if (handle == 0L) 0 else duration(handle)
    @Synchronized override fun setPlaybackSpeed(speed: Float) { if (handle != 0L) speed(handle, speed.coerceIn(0.5f, 2f)) }
    @Synchronized override fun close() { if (handle != 0L) destroy(handle); handle = 0L }

    private external fun create(): Long
    private external fun destroy(handle: Long)
    private external fun load(handle: Long, path: String): Boolean
    private external fun play(handle: Long)
    private external fun pause(handle: Long)
    private external fun seek(handle: Long, positionMs: Long)
    private external fun position(handle: Long): Long
    private external fun duration(handle: Long): Long
    private external fun speed(handle: Long, speed: Float)

    companion object {
        init {
            val library = NativeAudio::class.java.getResourceAsStream("/native/macos-arm64/librelay_audio.dylib")
                ?: error("Relay's macOS audio library is missing.")
            val temporary = File.createTempFile("relay_audio", ".dylib").apply { deleteOnExit() }
            library.use { input -> temporary.outputStream().use(input::copyTo) }
            System.load(temporary.absolutePath)
        }
    }
}
