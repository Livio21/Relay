package dev.relay.music.extension

/**
 * Resolved media URL → allow-listed source headers, consulted by the playback data source and
 * the download client. Registered when the host resolves an extension track, so the playback
 * service never talks to extension code directly.
 */
object ExtensionMediaHeaders {
    private const val MAX_ENTRIES = 64
    private val entries = LinkedHashMap<String, Map<String, String>>()

    @Synchronized
    fun register(url: String, headers: Map<String, String>) {
        if (headers.isEmpty() || !url.startsWith("https://")) return
        entries.remove(url)
        entries[url] = headers
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun headersFor(url: String): Map<String, String> = entries[url].orEmpty()
}
