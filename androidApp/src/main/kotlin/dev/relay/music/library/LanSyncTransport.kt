package dev.relay.music.library

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.security.MessageDigest
import dev.relay.music.sync.RelayLanProtocol

/** One visible, bounded LAN data-sync session. Pairing is supplied by [LanSecureSocket]. */
internal object LanSyncTransport {
    private const val PROTOCOL = RelayLanProtocol.DATA_SYNC_V1
    private const val MAX_ARCHIVE_BYTES = 16 * 1024 * 1024
    private const val MAX_MUSIC_ARCHIVE_BYTES = 8L * 1024 * 1024 * 1024
    private const val MUSIC_CHUNK_BYTES = 64 * 1024
    private const val DATA = "data"
    private const val MUSIC = "music"

    fun hostOnce(
        server: ServerSocket,
        identity: LanSyncIdentity,
        knownPeers: List<LanSyncPeer>,
        confirm: (LanSyncPeer, String) -> Boolean,
        outgoingArchive: ByteArray,
        onIncomingArchive: (ByteArray) -> Unit,
        outgoingMusicArchive: File? = null,
        temporaryDirectory: File = File(System.getProperty("java.io.tmpdir")),
        onIncomingMusicArchive: (File) -> Unit = {},
        onConnected: (AutoCloseable) -> Unit = {},
    ): LanSyncPeer = LanSecureSocket.host(server, PROTOCOL, identity, knownPeers, confirm).use { session ->
        onConnected(session)
        onIncomingArchive(session.receive(DATA, MAX_ARCHIVE_BYTES))
        session.send(DATA, outgoingArchive, MAX_ARCHIVE_BYTES)
        session.readMusic(temporaryDirectory, onIncomingMusicArchive)
        session.writeMusic(outgoingMusicArchive)
        session.peer
    }

    fun clientOnce(
        host: String,
        port: Int,
        identity: LanSyncIdentity,
        knownPeers: List<LanSyncPeer>,
        confirm: (LanSyncPeer, String) -> Boolean,
        outgoingArchive: ByteArray,
        onIncomingArchive: (ByteArray) -> Unit,
        outgoingMusicArchive: File? = null,
        temporaryDirectory: File = File(System.getProperty("java.io.tmpdir")),
        onIncomingMusicArchive: (File) -> Unit = {},
        onConnected: (AutoCloseable) -> Unit = {},
    ): LanSyncPeer = LanSecureSocket.client(host, port, PROTOCOL, identity, knownPeers, confirm).use { session ->
        onConnected(session)
        session.send(DATA, outgoingArchive, MAX_ARCHIVE_BYTES)
        onIncomingArchive(session.receive(DATA, MAX_ARCHIVE_BYTES))
        session.writeMusic(outgoingMusicArchive)
        session.readMusic(temporaryDirectory, onIncomingMusicArchive)
        session.peer
    }

    private fun LanSecureSocket.writeMusic(archive: File?) {
        val size = archive?.takeIf { it.isFile }?.length() ?: -1L
        require(size == -1L || size in 1..MAX_MUSIC_ARCHIVE_BYTES) { "Music transfer is too large." }
        val digest = archive?.let(::sha256) ?: ByteArray(32)
        send(MUSIC, ByteBuffer.allocate(Long.SIZE_BYTES + digest.size).putLong(size).put(digest).array(), Long.SIZE_BYTES + digest.size)
        if (archive == null) return
        val offset = ByteBuffer.wrap(receive(MUSIC, Long.SIZE_BYTES)).long
        require(offset in 0..size) { "Invalid Relay music resume offset." }
        FileInputStream(archive).use { input ->
            input.channel.position(offset)
            val buffer = ByteArray(MUSIC_CHUNK_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                send(MUSIC, buffer.copyOf(count), MUSIC_CHUNK_BYTES)
            }
        }
    }

    private fun LanSecureSocket.readMusic(directory: File, onComplete: (File) -> Unit) {
        val header = ByteBuffer.wrap(receive(MUSIC, Long.SIZE_BYTES + 32))
        val size = header.long
        if (size == -1L) return
        require(size in 1..MAX_MUSIC_ARCHIVE_BYTES && (directory.isDirectory || directory.mkdirs())) { "Invalid music transfer." }
        val expectedDigest = ByteArray(32).also(header::get)
        val temporary = File(directory, "relay-music-${expectedDigest.hex()}.part")
        val existing = temporary.takeIf { it.isFile && it.length() in 0..size }?.length() ?: 0L
        if (temporary.exists() && temporary.length() != existing) temporary.delete()
        send(MUSIC, ByteBuffer.allocate(Long.SIZE_BYTES).putLong(existing).array(), Long.SIZE_BYTES)
        try {
            FileOutputStream(temporary, existing > 0).use { output ->
                var copied = existing
                while (copied < size) {
                    val chunk = receive(MUSIC, MUSIC_CHUNK_BYTES)
                    require(chunk.isNotEmpty() && copied + chunk.size <= size) { "Invalid music transfer chunk." }
                    output.write(chunk)
                    copied += chunk.size
                }
            }
            require(sha256(temporary).contentEquals(expectedDigest)) { "Music transfer checksum failed." }
            onComplete(temporary)
        } catch (error: Throwable) {
            if (error is IllegalArgumentException || error is SecurityException) temporary.delete()
            throw error
        }
    }

    private fun sha256(file: File): ByteArray = MessageDigest.getInstance("SHA-256").also { digest ->
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
    }.digest()

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
