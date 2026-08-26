package dev.relay.music.platform

/**
 * In-memory ring buffer of notable events, for the local diagnostics log.
 * Nothing here leaves the device unless the user exports it; never record
 * credentials, stream URLs, or user media paths.
 */
class DiagnosticsLog(private val capacity: Int = 500) {
    private val buffer = ArrayDeque<String>(capacity)
    private val lock = Any()

    fun record(severity: String, message: String, throwable: Throwable? = null) {
        val detail = throwable?.let { " — ${it.javaClass.simpleName}: ${it.message.orEmpty()}" }.orEmpty()
        val line = "${System.currentTimeMillis()} $severity $message$detail"
        synchronized(lock) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(line)
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { buffer.toList() }
}

/**
 * Single place user-facing notices flow through. Behavior matches the previous
 * direct Toast calls, but every notice is also recorded in [DiagnosticsLog] so
 * failures stop vanishing into silent catch blocks.
 */
class UserMessenger(
    private val log: DiagnosticsLog,
    private val showToast: (message: String, longLength: Boolean) -> Unit,
) {
    fun info(message: String, longLength: Boolean = false) {
        log.record("INFO", message)
        showToast(message, longLength)
    }

    fun success(message: String, longLength: Boolean = false) {
        log.record("OK", message)
        showToast(message, longLength)
    }

    fun error(message: String, throwable: Throwable? = null, longLength: Boolean = true) {
        log.record("ERROR", message, throwable)
        showToast(message, longLength)
    }

    /** Records a failure that has no useful user-facing message yet. */
    fun quietError(message: String, throwable: Throwable? = null) {
        log.record("ERROR", message, throwable)
    }
}
