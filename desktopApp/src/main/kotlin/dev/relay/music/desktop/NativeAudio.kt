package dev.relay.music.desktop

import java.io.File

internal class NativeAudio {
    private var handle = create().also { check(it != 0L) { "Could not open an audio device." } }

    fun load(file: File): Boolean = load(handle, file.absolutePath)
    fun play() = play(handle)
    fun pause() = pause(handle)
    fun seekTo(positionMs: Long) = seek(handle, positionMs.coerceAtLeast(0))
    fun positionMs(): Long = position(handle)
    fun durationMs(): Long = duration(handle)
    fun close() { if (handle != 0L) destroy(handle); handle = 0L }

    private external fun create(): Long
    private external fun destroy(handle: Long)
    private external fun load(handle: Long, path: String): Boolean
    private external fun play(handle: Long)
    private external fun pause(handle: Long)
    private external fun seek(handle: Long, positionMs: Long)
    private external fun position(handle: Long): Long
    private external fun duration(handle: Long): Long

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
