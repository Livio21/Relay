package dev.relay.music.lastfm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.relay.music.library.UserLibraryDao
import dev.relay.music.model.ListeningOrigin
import dev.relay.music.model.newListeningEventsForImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the Last.fm account surface: desktop-flow authorization, connect/disconnect,
 * history import, profile association, and unlink. Session keys stay inside the
 * Keystore-backed [SessionKeyStore]; nothing here ever persists them elsewhere.
 */
class LastFmAccountController(
    private val scope: CoroutineScope,
    private val api: LastFmApi,
    private val sessionKeyStore: SessionKeyStore,
    private val dao: UserLibraryDao,
    private val openAuthorizationPage: (url: String) -> Boolean,
) {
    var connectionState by mutableStateOf(LastFmConnectionState.SETUP_REQUIRED)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var historyImportMessage by mutableStateOf<String?>(null)
        private set

    private var authorizationToken: String? = null

    /** Recomputes connection state from configuration and stored session, at startup. */
    fun refreshInitialState() {
        connectionState = when {
            !api.isConfigured -> LastFmConnectionState.SETUP_REQUIRED
            sessionKeyStore.read() == null -> LastFmConnectionState.DISCONNECTED
            else -> LastFmConnectionState.CONNECTED
        }
    }

    /** Called when the stored session disappears while the UI believed it connected. */
    fun onSessionKeyRemoved() {
        if (connectionState == LastFmConnectionState.CONNECTED) {
            connectionState = LastFmConnectionState.ERROR
            errorMessage = "Your Last.fm session expired. Connect it again to continue scrobbling."
        }
    }

    fun handleAction() {
        when (connectionState) {
            LastFmConnectionState.SETUP_REQUIRED -> Unit
            LastFmConnectionState.CONNECTED -> {
                sessionKeyStore.clear()
                connectionState = LastFmConnectionState.DISCONNECTED
                errorMessage = null
            }
            LastFmConnectionState.AUTHORIZING -> finishConnection()
            LastFmConnectionState.DISCONNECTED,
            LastFmConnectionState.ERROR,
            -> beginConnection()
        }
    }

    fun importHistory() {
        val session = sessionKeyStore.read() ?: run {
            historyImportMessage = "Connect Last.fm before importing history."
            return
        }
        historyImportMessage = "Importing the latest 1000 Last.fm scrobbles…"
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { api.recentHistory(session.username) }) {
                is LastFmResult.Failure -> historyImportMessage = result.message
                is LastFmResult.Success -> {
                    val imported = withContext(Dispatchers.IO) {
                        val existing = dao.historySnapshot().map { entry -> entry.asListeningEvent() }
                        val candidates = result.value.map { track -> track.asImportedListeningEvent() }
                        val newEvents = newListeningEventsForImport(existing, candidates)
                        newEvents.forEach { event -> dao.addHistory(event.asHistoryEntry()) }
                        dao.ensureLocalProfile("Relay", System.currentTimeMillis())
                        dao.localProfile()?.let { profile ->
                            dao.saveLocalProfile(profile.copy(lastFmUsername = session.username))
                        }
                        newEvents.size
                    }
                    historyImportMessage = if (imported == 0) {
                        "No new Last.fm scrobbles to import."
                    } else {
                        "Imported $imported Last.fm scrobbles into local history."
                    }
                }
            }
        }
    }

    fun saveProfileName(name: String) {
        val displayName = name.trim().take(64)
        if (displayName.isBlank()) return
        scope.launch(Dispatchers.IO) {
            dao.ensureLocalProfile("Relay", System.currentTimeMillis())
            dao.localProfile()?.let { profile -> dao.saveLocalProfile(profile.copy(displayName = displayName)) }
        }
    }

    fun unlink(removeImportedEvents: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                dao.localProfile()?.let { profile ->
                    dao.saveLocalProfile(profile.copy(lastFmUsername = null))
                }
                if (removeImportedEvents) dao.deleteHistoryByOrigin(ListeningOrigin.LASTFM_IMPORT.name)
            }
            historyImportMessage = if (removeImportedEvents) {
                "Last.fm association and imported history removed from this device."
            } else {
                "Last.fm association removed. Imported listening history remains local."
            }
        }
    }

    private fun beginConnection() {
        connectionState = LastFmConnectionState.AUTHORIZING
        errorMessage = null
        authorizationToken = null
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { api.requestToken() }) {
                is LastFmResult.Failure -> {
                    errorMessage = result.message
                    connectionState = LastFmConnectionState.ERROR
                }
                is LastFmResult.Success -> {
                    authorizationToken = result.value
                    if (!openAuthorizationPage(api.authorizationUrl(result.value))) {
                        errorMessage = "Could not open a browser for Last.fm authorization."
                        connectionState = LastFmConnectionState.ERROR
                    }
                }
            }
        }
    }

    private fun finishConnection() {
        val token = authorizationToken ?: run {
            connectionState = LastFmConnectionState.ERROR
            return
        }
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { api.createSession(token) }) {
                is LastFmResult.Failure -> {
                    errorMessage = result.message
                    connectionState = LastFmConnectionState.ERROR
                }
                is LastFmResult.Success -> {
                    sessionKeyStore.save(result.value)
                    authorizationToken = null
                    errorMessage = null
                    connectionState = LastFmConnectionState.CONNECTED
                }
            }
        }
    }
}
