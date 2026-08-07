package dev.relay.music

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.app.PendingIntent
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.AtomicFile
import android.os.PowerManager
import android.net.ConnectivityManager
import android.provider.Settings
import android.widget.Toast
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections
import java.util.UUID
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.relay.music.library.LocalMusicSource
import dev.relay.music.library.FavoriteTrackEntity
import dev.relay.music.library.ListeningHistoryEntity
import dev.relay.music.library.QueueEntryEntity
import dev.relay.music.library.asHistoryEntry
import dev.relay.music.library.QueueSnapshot
import dev.relay.music.library.UserLibraryStore
import dev.relay.music.library.RoomSettingsStore
import dev.relay.music.library.RelayBackupArchive
import dev.relay.music.library.RelayBackupContents
import dev.relay.music.library.RelayBackupSection
import dev.relay.music.library.RelayRestorePlan
import dev.relay.music.library.RelaySyncArchive
import dev.relay.music.SyncConflictNotice
import dev.relay.music.library.RelayStorage
import dev.relay.music.library.RemoteTrackDownloadClient
import dev.relay.music.library.RemoteTrackDownloadResult
import dev.relay.music.library.OfflineDownloadEntity
import dev.relay.music.library.PlaylistEntryEntity
import dev.relay.music.library.AutomaticBackupScheduler
import dev.relay.music.library.AutomaticBackupFailureStore
import dev.relay.music.library.AlbumChartSpecEntity
import dev.relay.music.library.AlbumChartExporter
import dev.relay.music.library.LanSyncIdentityStore
import dev.relay.music.library.LanSyncTransport
import dev.relay.music.library.RelayMusicTransferArchive
import dev.relay.music.library.TrackContentDigestStore
import dev.relay.music.library.LanPlayTogetherTransport
import dev.relay.music.sync.PlayTogetherCommand
import dev.relay.music.sync.PlayTogetherCoordinator
import dev.relay.music.sync.PlayTogetherApplyResult
import dev.relay.music.wallpaper.AlbumWallpaperService
import dev.relay.music.lastfm.LastFmApi
import dev.relay.music.lastfm.LastFmConnectionState
import dev.relay.music.lastfm.LastFmResult
import dev.relay.music.lastfm.LastFmHistoryTrack
import dev.relay.music.lastfm.SessionKeyStore
import dev.relay.music.metadata.MetadataRepairCoordinator
import dev.relay.music.metadata.MusicBrainzApi
import dev.relay.music.metadata.AppleSearchApi
import dev.relay.music.lyrics.LyricsApi
import dev.relay.music.library.TrackLyricsEntity
import dev.relay.music.library.TrackFlagsEntity
import dev.relay.music.model.MetadataCandidate
import dev.relay.music.model.OfflineDownloadEvictionEntry
import dev.relay.music.model.Track
import dev.relay.music.model.TrackFlags
import dev.relay.music.model.MetadataOverride
import dev.relay.music.model.offlineDownloadsToEvict
import dev.relay.music.extension.RepositoryDescriptor
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.RepositoryCatalogClient
import dev.relay.music.extension.CatalogRefreshResult
import dev.relay.music.extension.RepositoryDescriptorClient
import dev.relay.music.extension.RepositoryImportResult
import dev.relay.music.extension.isSecureExternalUrl
import dev.relay.music.extension.repositoryDescriptorUrl
import dev.relay.music.extension.AndroidExtensionInstaller
import dev.relay.music.extension.AndroidExtensionLoader
import dev.relay.music.extension.ExtensionArtifactClient
import dev.relay.music.extension.ArtifactDownloadResult
import dev.relay.music.extension.ExtensionInstallRequest
import dev.relay.music.extension.asInstalled
import dev.relay.music.extension.disabled
import dev.relay.music.extension.isCompatible
import dev.relay.music.extension.resolvedCatalogEntry
import dev.relay.music.extension.validate
import dev.relay.music.extension.repositoryTrustError
import dev.relay.music.extension.ExtensionSourceResults
import dev.relay.music.extension.ExtensionDownloadProgress
import dev.relay.music.extension.RemoteTrackDownloadProgress
import dev.relay.music.extension.InstalledExtension
import dev.relay.music.extension.ExtensionSourceCoordinator
import dev.relay.music.extension.ExtensionStreamResolver
import dev.relay.music.extension.extensionStreamPlaceholder
import dev.relay.music.extension.SourceBrowseRequest
import dev.relay.music.extension.SourceSettingDefinition
import dev.relay.music.extension.ThemePackReader
import dev.relay.music.extension.THEME_PACK_MAX_BYTES
import dev.relay.music.extension.ExtensionKind
import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.ListeningOrigin
import dev.relay.music.model.LocalProfile
import dev.relay.music.model.AlbumChartSpec
import dev.relay.music.model.InsightsRange
import dev.relay.music.model.albumChartEntries
import dev.relay.music.model.effectiveFingerprint
import dev.relay.music.model.newListeningEventsForImport
import dev.relay.music.model.OfflineDownload
import dev.relay.music.model.Playlist
import dev.relay.music.model.withMetadataOverride
import dev.relay.music.settings.RelaySettings
import dev.relay.music.settings.activeShuffleProfile
import dev.relay.music.settings.BackupSchedule
import dev.relay.music.update.AvailableComponent
import dev.relay.music.update.ComponentIdentity
import dev.relay.music.update.InstalledComponent
import dev.relay.music.update.findComponentUpdates
import dev.relay.music.playback.AndroidPlayerEngine
import dev.relay.music.playback.QueueEdit
import dev.relay.music.playback.appendToQueue
import dev.relay.music.playback.moveInQueue
import dev.relay.music.playback.playNextInQueue
import dev.relay.music.playback.removeFromQueue
import dev.relay.music.playback.shuffleSeedFromBytes
import dev.relay.music.playback.shuffledQueue
import dev.relay.music.playback.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import dev.relay.music.update.toUpdatableComponentKind

private data class PendingPlayTogetherResync(
    val command: PlayTogetherCommand,
    val leaderMinusLocalMs: Long,
    val track: Track?,
)

class MainActivity : ComponentActivity() {
    private val activityScope = MainScope()
    private val localMusicSource by lazy { LocalMusicSource(this) { relaySettings.storageRootUri?.toUri() } }
    private val playerEngine by lazy { AndroidPlayerEngine(this) }
    private val lastFmApi by lazy { LastFmApi(BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_SHARED_SECRET) }
    private val sessionKeyStore by lazy { SessionKeyStore(this) }
    private val musicBrainzApi by lazy { MusicBrainzApi() }
    private val appleSearchApi by lazy { AppleSearchApi() }
    private val lyricsApi by lazy { LyricsApi() }
    private val repositoryCatalogClient by lazy { RepositoryCatalogClient(applicationContext) }
    private val repositoryDescriptorClient by lazy { RepositoryDescriptorClient() }
    private val extensionArtifactClient by lazy { ExtensionArtifactClient(applicationContext) }
    private val extensionInstaller by lazy { AndroidExtensionInstaller(applicationContext) }
    private val extensionLoader by lazy { AndroidExtensionLoader(applicationContext) }
    private val extensionSources by lazy {
        ExtensionSourceCoordinator(extensionLoader, { relaySettings.sourceSettings[it].orEmpty() }, ::disableExtension)
    }
    private val remoteTrackDownloadClient by lazy { RemoteTrackDownloadClient(applicationContext) }
    private val metadataRepair by lazy {
        MetadataRepairCoordinator(applicationContext, userLibraryDao, musicBrainzApi, appleSearchApi)
    }
    private val userLibraryDao by lazy { UserLibraryStore.database(this).userLibraryDao() }
    private val settingsStore by lazy { RoomSettingsStore(userLibraryDao) }
    private var loadJob: Job? = null
    private var sourceSearchJob: Job? = null
    private var tracks by mutableStateOf(emptyList<Track>())
    private var libraryState by mutableStateOf<LibraryUiState>(LibraryUiState.StorageRequired)
    private var lastFmConnectionState by mutableStateOf(LastFmConnectionState.SETUP_REQUIRED)
    private var lastFmErrorMessage by mutableStateOf<String?>(null)
    private var lastFmSessionObserver: AutoCloseable? = null
    private var authorizationToken: String? = null
    private var favoriteTrackKeys by mutableStateOf(emptySet<String>())
    private var trackFlags by mutableStateOf(emptyMap<String, TrackFlags>())
    private var relaySettings by mutableStateOf(RelaySettings())
    private var playlists by mutableStateOf(emptyList<Playlist>())
    private var playlistTracks by mutableStateOf(emptyMap<Long, List<Track>>())
    private var metadataCandidates by mutableStateOf(emptyList<MetadataCandidate>())
    private var repositoryCatalogs by mutableStateOf<Map<String, List<ExtensionCatalogEntry>>>(emptyMap())
    private var repositoryMessages by mutableStateOf<Map<String, String>>(emptyMap())
    private var importedRepository by mutableStateOf<RepositoryDescriptor?>(null)
    private var repositoryImportMessage by mutableStateOf<String?>(null)
    private var repositoryImportVersion by mutableStateOf(0L)
    private var openRepositoryImportVersion by mutableStateOf(0L)
    private var extensionSourceResults by mutableStateOf(emptyList<ExtensionSourceResults>())
    private var extensionSourceMessage by mutableStateOf<String?>(null)
    private var sourceSettingSchemas by mutableStateOf<Map<String, List<SourceSettingDefinition>>>(emptyMap())
    private var extensionDownload by mutableStateOf<ExtensionDownloadProgress?>(null)
    private var remoteTrackDownload by mutableStateOf<RemoteTrackDownloadProgress?>(null)
    private var downloadedRemoteTrackKeys by mutableStateOf(emptySet<String>())
    private var offlineDownloadUris = emptyMap<String, String>()
    private var offlineDownloads by mutableStateOf(emptyList<OfflineDownload>())
    private var syncConflicts by mutableStateOf(emptyList<SyncConflictNotice>())
    private var lanSync by mutableStateOf(LanSyncUiState())
    private var pairedDevices by mutableStateOf(emptyList<PairedDeviceUi>())
    private var pendingLanConfirmation: CompletableDeferred<Boolean>? = null
    private var lanSyncServer: ServerSocket? = null
    private var lanSyncConnection: AutoCloseable? = null
    private var playTogether by mutableStateOf(PlayTogetherUiState())
    private var pendingPlayTogetherConfirmation: CompletableDeferred<Boolean>? = null
    @Volatile private var playTogetherRunning = false
    private var playTogetherConnection: AutoCloseable? = null
    private var playTogetherServer: ServerSocket? = null
    private var pendingPlayTogetherResync: PendingPlayTogetherResync? = null
    private var playHistory by mutableStateOf(emptyList<ListeningHistoryEntity>())
    private var localProfile by mutableStateOf<LocalProfile?>(null)
    private var albumChartSpecs by mutableStateOf(emptyList<AlbumChartSpec>())
    private var lastFmHistoryImportMessage by mutableStateOf<String?>(null)
    private var metadataSearchMessage by mutableStateOf<String?>(null)
    private var metadataCandidateTrackKey by mutableStateOf<String?>(null)
    private var metadataIgnoredTrackKeys by mutableStateOf(emptySet<String>())
    private var lyricsText by mutableStateOf<String?>(null)
    private var lyricsTrackKey by mutableStateOf<String?>(null)
    private var lyricsMessage by mutableStateOf<String?>(null)
    private var queueRestored = false
    private var lastHistoryTrackKey: String? = null
    private var lastQueue: List<Track>? = null
    private var lastQueueIndex = -1
    private var lastQueueWriteElapsedMs = 0L
    private var appBackAction: (() -> Unit)? = null
    private val extensionInstallStatusAction = "$ACTION_EXTENSION_INSTALL_STATUS.${UUID.randomUUID()}"
    private val pendingExtensionInstalls = mutableMapOf<Int, Pair<RepositoryDescriptor, ExtensionCatalogEntry>>()
    private var pendingExtensionUninstallPackage: String? = null
    private var pendingShuffleSeedSalt = ""
    private var pendingBackupSections = RelayBackupSection.all
    private var pendingMusicTransferUris = emptyList<android.net.Uri>()
    private var preparingLanMusic = false
    private var lanMusicArchive: File? = null
    private val trackContentDigests by lazy { TrackContentDigestStore(applicationContext) }
    private val playTogetherCoordinator by lazy {
        PlayTogetherCoordinator(playerEngine, ::resolvePlayTogetherTrack, activityScope)
    }

    private val backupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri ?: return@registerForActivityResult
        activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use {
                        RelayBackupArchive.write(it, userLibraryDao, pendingBackupSections)
                    }
                        ?: error("Could not open the selected backup file.")
                }
            }
            Toast.makeText(this@MainActivity, if (result.isSuccess) "Backup saved." else "Backup failed.", Toast.LENGTH_SHORT).show()
        }
    }

    private val syncExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri ?: return@registerForActivityResult
        activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { RelaySyncArchive.write(it, userLibraryDao) }
                        ?: error("Could not open the selected sync file.")
                }
            }
            Toast.makeText(this@MainActivity, if (result.isSuccess) "Sync file saved." else "Could not create sync file.", Toast.LENGTH_LONG).show()
        }
    }

    private val musicTransferExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@registerForActivityResult
        activityScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { RelayMusicTransferArchive.write(this@MainActivity, pendingMusicTransferUris, it) }
                    ?: error("Could not create music transfer.")
            } }
            Toast.makeText(this@MainActivity, if (result.isSuccess) "Music transfer saved." else "Could not create music transfer.", Toast.LENGTH_LONG).show()
        }
    }
    private val musicTransferSelectionLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        pendingMusicTransferUris = uris
        if (preparingLanMusic) {
            preparingLanMusic = false
            activityScope.launch {
                val result = runCatching { withContext(Dispatchers.IO) {
                    File(cacheDir, "lan-music").apply { mkdirs() }.resolve("selected.relaymusic").also { file ->
                        file.outputStream().use { RelayMusicTransferArchive.write(this@MainActivity, uris, it) }
                    }
                } }
                lanMusicArchive = result.getOrNull()
                lanSync = LanSyncUiState(
                    message = if (lanMusicArchive != null) "Selected music is ready for LAN sync." else "Could not prepare selected music.",
                    preparedMusicBytes = lanMusicArchive?.length(),
                )
                Toast.makeText(this@MainActivity, if (lanMusicArchive != null) "Music ready for the next LAN sync." else "Could not prepare selected music.", Toast.LENGTH_LONG).show()
            }
        } else musicTransferExportLauncher.launch("relay-music-${System.currentTimeMillis()}.relaymusic")
    }
    private val musicTransferImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val root = relaySettings.storageRootUri?.toUri() ?: run { Toast.makeText(this, "Choose a Relay folder before importing music.", Toast.LENGTH_LONG).show(); return@registerForActivityResult }
        activityScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { RelayMusicTransferArchive.read(this@MainActivity, it, root) }
                    ?: error("Could not read music transfer.")
            } }
            if (result.isSuccess) refreshLibrary()
            Toast.makeText(this@MainActivity, result.fold({ "Imported $it music files." }, { "Music transfer is invalid or incomplete." }), Toast.LENGTH_LONG).show()
        }
    }

    private val themePackLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        activityScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { it.readBounded(THEME_PACK_MAX_BYTES).toString(Charsets.UTF_8) }
                }.getOrNull()?.let { json -> ThemePackReader.parse(json) }
            }
            when {
                parsed == null -> Toast.makeText(this@MainActivity, "Could not read that theme pack file.", Toast.LENGTH_LONG).show()
                parsed.isFailure -> Toast.makeText(this@MainActivity, parsed.exceptionOrNull()?.message ?: "Theme pack is invalid.", Toast.LENGTH_LONG).show()
                else -> {
                    val pack = parsed.getOrThrow()
                    if (dev.relay.music.extension.isBuiltInThemePack(pack.id)) {
                        Toast.makeText(this@MainActivity, "Built-in themes cannot be replaced.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    if (relaySettings.installedExtensions.any { it.kind == ExtensionKind.THEME_PACK && it.extensionId == pack.id }) {
                        Toast.makeText(this@MainActivity, "Remove the repository-installed copy before importing this theme manually.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    settingsStore.save(
                        relaySettings.copy(
                            themePacks = dev.relay.music.extension.mergedThemePacks(
                                relaySettings.themePacks.filterNot { it.id == pack.id } + pack,
                            ),
                            activeThemePackId = pack.id,
                        ),
                    )
                    Toast.makeText(this@MainActivity, "Theme \"${pack.name}\" imported and applied.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val shuffleSeedLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        activityScope.launch {
            val profile = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        shuffleSeedFromBytes(input.readBounded(MAX_SEED_IMAGE_BYTES), pendingShuffleSeedSalt)
                    }
                }.getOrNull()
            }
            if (profile == null) {
                Toast.makeText(this@MainActivity, "Could not read that image.", Toast.LENGTH_LONG).show()
                return@launch
            }
            settingsStore.save(
                relaySettings.copy(
                    shuffleProfiles = relaySettings.shuffleProfiles.map { existing ->
                        if (existing.id == relaySettings.activeShuffleProfile().id) {
                            existing.copy(seed = profile.seed, seedLabel = profile.seedLabel, seedSalt = pendingShuffleSeedSalt)
                        } else {
                            existing
                        }
                    },
                ),
            )
            Toast.makeText(this@MainActivity, "Shuffle seed ${profile.seedLabel} saved. The image itself is not stored.", Toast.LENGTH_LONG).show()
        }
    }

    private val extensionInstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    private val extensionUninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val packageName = pendingExtensionUninstallPackage ?: return@registerForActivityResult
        pendingExtensionUninstallPackage = null
        if (isPackageInstalled(packageName)) {
            Toast.makeText(this, "Extension uninstall was cancelled.", Toast.LENGTH_LONG).show()
        } else {
            activityScope.launch {
                settingsStore.save(
                    relaySettings.copy(
                        installedExtensions = relaySettings.installedExtensions.filterNot { it.androidPackageName == packageName },
                    ),
                )
                extensionSourceResults = emptyList()
                extensionSourceMessage = "Extension uninstalled."
            }
        }
    }

    private val extensionInstallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handleExtensionInstallStatus(intent)
    }

    private val wallpaperAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            saveWallpaperSettings(relaySettings)
        } else {
            saveWallpaperSettings(
                relaySettings.copy(wallpaperPreset = relaySettings.wallpaperPreset.copy(soundReactive = false)),
            )
            Toast.makeText(this, "Sound-reactive wallpaper needs microphone permission.", Toast.LENGTH_LONG).show()
        }
    }

    private val storageRootLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        activityScope.launch {
            val result = runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                check(withContext(Dispatchers.IO) { RelayStorage.prepare(this@MainActivity, uri) }) {
                    "Relay could not create its folders here."
                }
                withContext(Dispatchers.IO) {
                    settingsStore.save(relaySettings.copy(storageRootUri = uri.toString()))
                }
                AutomaticBackupScheduler.update(this@MainActivity, relaySettings.backupSchedule)
            }
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) "Relay storage folder selected." else "Could not use that folder.",
                Toast.LENGTH_LONG,
            ).show()
            if (result.isSuccess) refreshLibrary()
        }
    }

    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use(RelayBackupArchive::inspect)
                        ?: error("Could not open the selected backup file.")
                }
            }
            result.fold(
                onSuccess = ::chooseRestoreSections,
                onFailure = {
                    Toast.makeText(this@MainActivity, "Backup is invalid or could not be read.", Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    private val syncImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        activityScope.launch {
            val result = runCatching {
                val incoming = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use(RelaySyncArchive::read)
                        ?: error("Could not open the selected sync file.")
                }
                withContext(Dispatchers.IO) { userLibraryDao.mergeSync(incoming) }
            }
            if (result.isSuccess) refreshLibrary()
            val message = result.fold(
                onSuccess = { merged ->
                    "Sync merged ${merged.importedRecords} records" +
                        if (merged.conflicts.isEmpty()) "." else "; ${merged.conflicts.size} conflicts kept local."
                },
                onFailure = { "Sync file is invalid or could not be merged." },
            )
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(5, 5, 5)),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(5, 5, 5)),
        )
        AutomaticBackupFailureStore.consume(this)?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        lastFmConnectionState = when {
            !lastFmApi.isConfigured -> LastFmConnectionState.SETUP_REQUIRED
            sessionKeyStore.read() == null -> LastFmConnectionState.DISCONNECTED
            else -> LastFmConnectionState.CONNECTED
        }
        lastFmSessionObserver = sessionKeyStore.observeChanges {
            runOnUiThread {
                if (lastFmConnectionState == LastFmConnectionState.CONNECTED && sessionKeyStore.read() == null) {
                    lastFmConnectionState = LastFmConnectionState.ERROR
                    lastFmErrorMessage = "Your Last.fm session expired. Connect it again to continue scrobbling."
                }
            }
        }
        registerExtensionInstallReceiver()
        observeLibraryPersistence()
        refreshPairedDevices()
        handleRepositoryLink(intent)

        setContent {
            val playbackState by playerEngine.state.collectAsState()
            // Plays recorded before snapshots existed still name their track when it is in the library.
            val listeningEvents = remember(playHistory, tracks) {
                val libraryByKey = tracks.associateBy { it.key() }
                playHistory.map { entry ->
                    val known = libraryByKey["${entry.sourceId}\u0000${entry.trackId}"]
                    ListeningEvent(
                        sourceId = entry.sourceId,
                        trackId = entry.trackId,
                        playedAtEpochMs = entry.playedAtEpochMs,
                        title = entry.title ?: known?.title,
                        artist = entry.artist ?: known?.artist,
                        album = entry.album ?: known?.album,
                        durationMs = entry.durationMs ?: known?.durationMs,
                        origin = ListeningOrigin.entries.firstOrNull { it.name == entry.origin } ?: ListeningOrigin.LOCAL,
                        identityFingerprint = entry.identityFingerprint,
                    )
                }
            }
            BackHandler { appBackAction?.invoke() ?: confirmExit() }
            RelayApp(
                state = RelayAppState(
                    tracks = tracks,
                    playback = playbackState,
                    library = libraryState,
                    favoriteTrackKeys = favoriteTrackKeys,
                    trackFlags = trackFlags,
                    settings = relaySettings,
                    playlists = playlists,
                    playlistTracks = playlistTracks,
                    metadataCandidates = metadataCandidates,
                    metadataSearchMessage = metadataSearchMessage,
                    metadataCandidateTrackKey = metadataCandidateTrackKey,
                    metadataIgnoredTrackKeys = metadataIgnoredTrackKeys,
                    lyricsText = lyricsText,
                    lyricsTrackKey = lyricsTrackKey,
                    lyricsMessage = lyricsMessage,
                    lastFmConnectionState = lastFmConnectionState,
                    lastFmErrorMessage = lastFmErrorMessage,
                    lastFmHistoryImportMessage = lastFmHistoryImportMessage,
                    repositoryCatalogs = repositoryCatalogs,
                    repositoryMessages = repositoryMessages,
                    importedRepository = importedRepository,
                    repositoryImportMessage = repositoryImportMessage,
                    repositoryImportVersion = repositoryImportVersion,
                    openRepositoryImportVersion = openRepositoryImportVersion,
                    extensionSourceResults = extensionSourceResults,
                    extensionSourceMessage = extensionSourceMessage,
                    sourceSettingSchemas = sourceSettingSchemas,
                    extensionDownload = extensionDownload,
                    remoteTrackDownload = remoteTrackDownload,
                    downloadedRemoteTrackKeys = downloadedRemoteTrackKeys,
                    offlineDownloads = offlineDownloads,
                    syncConflicts = syncConflicts,
                    lanSync = lanSync,
                    pairedDevices = pairedDevices,
                    playTogether = playTogether,
                    localProfile = localProfile,
                    listeningEvents = listeningEvents,
                    nowEpochMs = System.currentTimeMillis(),
                    albumChartSpecs = albumChartSpecs,
                ),
                actions = RelayAppActions(
                    onTrackSelected = ::selectTrack,
                    onPlayPause = { if (playbackState.isPlaying) playerEngine.pause() else playerEngine.play() },
                    onPrevious = playerEngine::skipPrevious,
                    onNext = playerEngine::skipNext,
                    onSeekTo = playerEngine::seekTo,
                    onRetry = ::refreshLibrary,
                    onFavoriteToggle = ::toggleFavorite,
                    onTrackFlagsChange = ::saveTrackFlags,
                    onResumeQueueChange = ::setResumeQueue,
                    onChooseStorageRoot = { storageRootLauncher.launch(null) },
                    onBackupExport = ::exportBackup,
                    onBackupImport = { backupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    onSyncExport = ::exportSync,
                    onSyncImport = { syncImportLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    onDismissSyncConflict = ::dismissSyncConflict,
                    onUseReceivedSyncConflict = ::useReceivedSyncConflict,
                    onStartLanSyncHost = ::hostLanSync,
                    onJoinLanSync = ::joinLanSync,
                    onConfirmLanSync = ::confirmLanSync,
                    onCancelLanSync = ::cancelLanSync,
                    onUnpairDevice = ::unpairDevice,
                    onSelectMusicTransfer = { musicTransferSelectionLauncher.launch(arrayOf("audio/*")) },
                    onImportMusicTransfer = { musicTransferImportLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    onPrepareLanMusicTransfer = { preparingLanMusic = true; musicTransferSelectionLauncher.launch(arrayOf("audio/*")) },
                    onStartPlayTogetherHost = ::hostPlayTogether,
                    onJoinPlayTogether = ::joinPlayTogether,
                    onConfirmPlayTogether = ::confirmPlayTogether,
                    onLeavePlayTogether = ::leavePlayTogether,
                    onResyncPlayTogether = ::resyncPlayTogether,
                    onBackupScheduleChange = ::setBackupSchedule,
                    onAutoBackupExpiryChange = ::setAutoBackupExpiry,
                    onCreatePlaylist = ::createPlaylist,
                    onAddToPlaylist = ::addToPlaylist,
                    onCreateAndAddToPlaylist = ::createAndAddToPlaylist,
                    onOpenPlaylist = ::openPlaylist,
                    onPlayPlaylist = ::playPlaylist,
                    onRemovePlaylistEntry = { playlistId, index -> updatePlaylistEntries(playlistId) { it.removeAt(index) } },
                    onMovePlaylistEntry = ::movePlaylistEntry,
                    onRenamePlaylist = ::renamePlaylist,
                    onDeletePlaylist = ::deletePlaylist,
                    onSaveMetadataOverride = ::saveMetadataOverride,
                    onSearchMetadata = ::searchMetadata,
                    onMetadataReviewIgnored = ::ignoreMetadataReview,
                    onLoadLyrics = ::loadLyrics,
                    onSaveLyrics = ::saveLyrics,
                    onFetchLyrics = ::fetchLyrics,
                    onLastFmAction = ::handleLastFmAction,
                    onImportLastFmHistory = ::importLastFmHistory,
                    onProfileNameChange = ::saveLocalProfileName,
                    onUnlinkLastFmHistory = ::unlinkLastFmHistory,
                    onCreateAlbumChart = ::createAlbumChart,
                    onRemoveAlbumChart = ::removeAlbumChart,
                    onExportAlbumChart = ::exportAlbumChart,
                    onBackActionChanged = { appBackAction = it },
                    onDebugScrobble = if (
                        BuildConfig.DEBUG && lastFmConnectionState == LastFmConnectionState.CONNECTED && playbackState.currentTrack != null
                    ) ::debugScrobble else null,
                    onAddTrustedRepository = ::addTrustedRepository,
                    onImportRepository = ::importRepository,
                    onRemoveTrustedRepository = ::removeTrustedRepository,
                    onRefreshRepository = ::refreshRepository,
                    onInstallExtension = ::confirmExtensionInstall,
                    onSetExtensionEnabled = ::setExtensionEnabled,
                    onUninstallExtension = ::uninstallExtension,
                    onSearchExtensionSources = ::searchExtensionSources,
                    onLoadSourceSettings = ::loadSourceSettingSchema,
                    onSourceSettingsChange = ::saveSourceSettings,
                    onRefreshExtensions = { refreshAllRepositories(announceUpdates = false) },
                    onDownloadRemoteTrack = ::downloadRemoteTrack,
                    onDeleteDownload = ::deleteDownload,
                    onDeleteAllDownloads = ::deleteAllDownloads,
                    onDownloadLimitChange = ::setDownloadStorageLimit,
                    onDownloadAutoCleanupChange = ::setDownloadAutoCleanup,
                    onEvictExceededDownloads = ::evictExceededDownloads,
                    onAudioSettingsChange = ::saveAudioSettings,
                    onShuffleEnabledChange = playerEngine::setShuffleEnabled,
                    onRepeatModeChange = playerEngine::setRepeatMode,
                    onShuffleQueue = ::shuffleQueue,
                    onPlayQueueIndex = playerEngine::seekToIndex,
                    onRemoveQueueIndex = ::removeQueueIndex,
                    onMoveQueueIndex = ::moveQueueIndex,
                    onClearQueue = { playerEngine.setQueue(emptyList(), 0) },
                    onPlayNext = ::playNext,
                    onEnqueue = ::enqueue,
                    onImportThemePack = { themePackLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    onApplyThemePack = ::applyThemePackId,
                    onRemoveThemePack = ::removeThemePack,
                    onOpenExternalUrl = ::openExternalUrl,
                    onPickShuffleSeed = ::pickShuffleSeed,
                    onWallpaperSettingsChange = ::saveWallpaperSettings,
                    onOpenAlbumWallpaperPicker = ::openAlbumWallpaperPicker,
                ),
            )
        }
    }

    private fun openAlbumWallpaperPicker() {
        startActivity(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, AlbumWallpaperService::class.java),
                ),
        )
    }

    private fun openExternalUrl(value: String) {
        if (!value.isSecureExternalUrl()) {
            Toast.makeText(this, "That support link is not a valid HTTPS address.", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, value.toUri())) }
            .onFailure { Toast.makeText(this, "Could not open that support page.", Toast.LENGTH_LONG).show() }
    }

    override fun onStart() {
        super.onStart()
        refreshLibrary()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRepositoryLink(intent)
    }

    /**
     * `relay://add-repo?url=...` fetches the descriptor and opens it for review. It deliberately
     * stops short of trusting: a link from a web page must not be able to add a signing identity.
     */
    private fun handleRepositoryLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "relay" || data.host != "add-repo") return
        val url = data.getQueryParameter("url")?.let(::repositoryDescriptorUrl)
        if (url == null) {
            Toast.makeText(this, "That repository link is not a valid HTTPS address.", Toast.LENGTH_LONG).show()
            return
        }
        importRepository(url)
        openRepositoryImportVersion += 1
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setMessage("Exit Relay?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("EXIT") { _, _ -> finishAndRemoveTask() }
            .show()
    }

    private fun saveAudioSettings(settings: RelaySettings) {
        playerEngine.setPlaybackSpeed(settings.playbackSpeed)
        activityScope.launch(Dispatchers.IO) {
            settingsStore.save(settings)
        }
    }

    private fun saveWallpaperSettings(settings: RelaySettings) {
        if (settings.wallpaperPreset.soundReactive &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            wallpaperAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        activityScope.launch(Dispatchers.IO) {
            settingsStore.save(settings)
            sendBroadcast(Intent(AlbumWallpaperService.ACTION_PRESET_CHANGED).setPackage(packageName))
        }
    }

    override fun onDestroy() {
        loadJob?.cancel()
        activityScope.cancel()
        lastFmApi.close()
        musicBrainzApi.close()
        appleSearchApi.close()
        lyricsApi.close()
        playerEngine.release()
        runCatching { unregisterReceiver(extensionInstallReceiver) }
        lastFmSessionObserver?.close()
        lastFmSessionObserver = null
        super.onDestroy()
    }

    private fun refreshLibrary() {
        if (relaySettings.storageRootUri == null) {
            tracks = emptyList()
            libraryState = LibraryUiState.StorageRequired
            return
        }
        loadJob?.cancel()
        loadJob = activityScope.launch {
            try {
                val (overrides, lookups) = withContext(Dispatchers.IO) {
                    userLibraryDao.metadataOverrides().associateBy { it.sourceId + "\u0000" + it.trackId } to
                        userLibraryDao.metadataLookups().associateBy { it.sourceId + "\u0000" + it.trackId }
                }
                val sourceTracks = localMusicSource.tracks()
                metadataIgnoredTrackKeys = sourceTracks.filter { track ->
                    lookups[track.key()]?.let { it.suppressed && it.sourceRevision == track.sourceRevision } == true
                }.mapTo(mutableSetOf()) { it.key() }
                tracks = sourceTracks.map { track ->
                    track.withMetadataOverride(overrides[track.key()]?.asOverride())
                }
                libraryState = LibraryUiState.Ready
                restoreQueueIfAvailable()
            } catch (_: SecurityException) {
                libraryState = LibraryUiState.Error("Relay folder access was removed. Choose it again in Settings.")
            } catch (_: Exception) {
                libraryState = LibraryUiState.Error("Could not index music from device storage.")
            }
        }
    }

    private fun observeLibraryPersistence() {
        activityScope.launch(Dispatchers.IO) {
            userLibraryDao.ensureLocalProfile("Relay", System.currentTimeMillis())
        }
        activityScope.launch {
            settingsStore.load()
            relaySettings = settingsStore.settings.value
            val cachedCatalogs = withContext(Dispatchers.IO) {
                relaySettings.trustedRepositories.mapNotNull { repository ->
                    repositoryCatalogClient.cachedCatalog(repository)?.let { repository.id to it.extensions }
                }.toMap()
            }
            repositoryCatalogs = cachedCatalogs
            repositoryMessages = cachedCatalogs.keys.associateWith { "CACHED" }
            reconcileInstalledExtensionSnapshots(disableMissing = false)
            relaySettings.storageRootUri?.toUri()?.let { root ->
                val ready = withContext(Dispatchers.IO) {
                    RelayStorage.prepare(this@MainActivity, root).also { prepared ->
                        if (prepared) RelayStorage.deletePartialDownloads(this@MainActivity, root)
                    }
                }
                if (!ready) {
                    Toast.makeText(
                        this@MainActivity,
                        "Relay could not repair its folder structure. Check storage access in Settings.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            AutomaticBackupScheduler.update(this@MainActivity, settingsStore.settings.value.backupSchedule)
            refreshAllRepositories(announceUpdates = true)
            refreshLibrary()
            settingsStore.settings.collect { settings ->
                val extensionsChanged = settings.installedExtensions != relaySettings.installedExtensions ||
                    settings.sourceSettings != relaySettings.sourceSettings
                relaySettings = settings
                if (extensionsChanged) ExtensionStreamResolver.clearCache()
            }
        }
        activityScope.launch {
            userLibraryDao.playlists().collect { entries ->
                playlists = entries.map { Playlist(it.id, it.name) }
            }
        }
        activityScope.launch {
            userLibraryDao.favorites().collect { favorites ->
                favoriteTrackKeys = favorites.mapTo(mutableSetOf()) { it.key() }
            }
        }
        activityScope.launch {
            userLibraryDao.flags().collect { flags ->
                trackFlags = flags.associate { it.key() to TrackFlags(it.hidden, it.pinned, it.archived) }
            }
        }
        activityScope.launch {
            userLibraryDao.recentHistory().collect { history -> playHistory = history }
        }
        activityScope.launch {
            userLibraryDao.observeLocalProfile().collect { profile ->
                localProfile = profile?.let { LocalProfile(it.displayName, it.createdAtEpochMs, it.lastFmUsername) }
            }
        }
        activityScope.launch {
            userLibraryDao.albumChartSpecs().collect { specs ->
                albumChartSpecs = specs.map { it.asSpec() }
            }
        }
        activityScope.launch {
            userLibraryDao.offlineDownloads().collect { downloads ->
                downloadedRemoteTrackKeys = downloads.mapTo(linkedSetOf()) { "${it.sourceId}\u0000${it.trackId}" }
                offlineDownloadUris = downloads.associate { "${it.sourceId}\u0000${it.trackId}" to it.documentUri }
                offlineDownloads = downloads
                    .sortedByDescending { it.downloadedAtEpochMs }
                    .map { OfflineDownload(it.sourceId, it.trackId, it.title ?: it.trackId, it.sizeBytes) }
            }
        }
        activityScope.launch {
            userLibraryDao.syncConflicts().collect { conflicts ->
                syncConflicts = conflicts.map { SyncConflictNotice(it.id, it.description, it.receivedPayload != null) }
            }
        }
        activityScope.launch {
            playerEngine.state.collect { state -> persistPlaybackState(state) }
        }
    }

    private suspend fun persistPlaybackState(state: dev.relay.music.playback.PlaybackState) {
        state.currentTrack?.let { currentTrack ->
            if (state.isPlaying && lastHistoryTrackKey != currentTrack.key()) {
                lastHistoryTrackKey = currentTrack.key()
                withContext(Dispatchers.IO) {
                    userLibraryDao.addHistory(currentTrack.asHistoryEntry(System.currentTimeMillis()))
                }
            }
        }

        val now = SystemClock.elapsedRealtime()
        val queueChanged = state.queue !== lastQueue || state.currentIndex != lastQueueIndex
        if (queueChanged || !state.isPlaying || now - lastQueueWriteElapsedMs >= QUEUE_SAVE_INTERVAL_MS) {
            lastQueue = state.queue
            lastQueueIndex = state.currentIndex
            lastQueueWriteElapsedMs = now
            withContext(Dispatchers.IO) {
                userLibraryDao.replaceQueue(state.queue, state.currentIndex, state.positionMs)
            }
        }
    }

    private suspend fun restoreQueueIfAvailable() {
        if (queueRestored || !relaySettings.resumeQueue) return
        queueRestored = true
        val snapshot = withContext(Dispatchers.IO) {
            val entries = userLibraryDao.queueEntries()
            val state = userLibraryDao.queueState()
            state?.let { QueueSnapshot(entries, it.currentIndex, it.positionMs) }
        } ?: return
        val availableTracks = tracks.associateBy { track -> track.key() }
        val restoredTracks = mutableListOf<Track>()
        var restoredIndex = 0
        snapshot.entries.forEachIndexed { index, entry ->
            val track = availableTracks["${entry.sourceId}\u0000${entry.trackId}"] ?: entry.asRestoredTrack()
            if (track != null) {
                if (index <= snapshot.currentIndex) restoredIndex = restoredTracks.size
                restoredTracks += track
            }
        }
        if (restoredTracks.isNotEmpty()) {
            playerEngine.restoreQueueIfIdle(
                tracks = restoredTracks,
                startIndex = restoredIndex.coerceIn(0, restoredTracks.lastIndex),
                positionMs = snapshot.positionMs,
            )
        }
    }

    /**
     * Rebuilds a queued track that is not in the local library from its stored snapshot. Remote
     * tracks get a placeholder the playback service resolves; anything unplayable is dropped.
     */
    private fun QueueEntryEntity.asRestoredTrack(): Track? {
        val track = Track(
            id = trackId,
            sourceId = sourceId,
            playbackUri = "",
            title = title ?: trackId,
            artist = artist ?: "Unknown artist",
            album = album,
            albumArtist = albumArtist,
            releaseDate = releaseDate,
            durationMs = durationMs,
            artworkUri = artworkUri,
            artworkHue = artworkHue,
        )
        val uri = offlineDownloadUris[track.key()] ?: track.extensionStreamPlaceholder() ?: return null
        return track.copy(playbackUri = uri)
    }

    private fun toggleFavorite(track: Track) {
        activityScope.launch(Dispatchers.IO) {
            if (userLibraryDao.isFavorite(track.sourceId, track.id)) {
                userLibraryDao.removeFavorite(track.sourceId, track.id)
            } else {
                userLibraryDao.addFavorite(FavoriteTrackEntity(track.sourceId, track.id))
            }
        }
    }

    private fun saveTrackFlags(track: Track, flags: TrackFlags) {
        activityScope.launch(Dispatchers.IO) {
            userLibraryDao.saveTrackFlags(
                TrackFlagsEntity(track.sourceId, track.id, flags.hidden, flags.pinned, flags.archived),
            )
        }
    }

    private fun setResumeQueue(enabled: Boolean) {
        activityScope.launch(Dispatchers.IO) {
            settingsStore.save(relaySettings.copy(resumeQueue = enabled))
        }
    }

    private fun setBackupSchedule(schedule: BackupSchedule) {
        val settings = relaySettings.copy(backupSchedule = schedule)
        activityScope.launch {
            withContext(Dispatchers.IO) { settingsStore.save(settings) }
            AutomaticBackupScheduler.update(this@MainActivity, schedule)
        }
    }

    private fun setAutoBackupExpiry(days: Int) {
        activityScope.launch(Dispatchers.IO) {
            settingsStore.save(relaySettings.copy(autoBackupExpiryDays = days.coerceIn(7, 90)))
        }
    }

    private fun addTrustedRepository(repository: RepositoryDescriptor) {
        repositoryTrustError(relaySettings.trustedRepositories, repository)?.let { error ->
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }
        activityScope.launch {
            settingsStore.save(relaySettings.copy(trustedRepositories = relaySettings.trustedRepositories + repository))
        }
    }

    private fun importRepository(url: String) {
        repositoryImportMessage = "IMPORTING…"
        activityScope.launch {
            when (val result = repositoryDescriptorClient.import(url)) {
                is RepositoryImportResult.Success -> {
                    importedRepository = result.descriptor
                    repositoryImportMessage = "REVIEW DETAILS, THEN TRUST"
                }
                is RepositoryImportResult.Failure -> {
                    importedRepository = null
                    repositoryImportMessage = result.message
                }
            }
            repositoryImportVersion += 1
        }
    }

    private fun removeTrustedRepository(id: String) {
        activityScope.launch {
            settingsStore.save(relaySettings.copy(trustedRepositories = relaySettings.trustedRepositories.filterNot { it.id == id }))
            repositoryCatalogs = repositoryCatalogs - id
            repositoryMessages = repositoryMessages - id
        }
    }

    private fun refreshRepository(repository: RepositoryDescriptor) {
        activityScope.launch {
            refreshRepositoryNow(repository)
            reconcileInstalledExtensionSnapshots(disableMissing = false)
        }
    }

    private fun refreshAllRepositories(announceUpdates: Boolean) {
        activityScope.launch {
            var hasUpdate = false
            relaySettings.trustedRepositories.forEach { repository ->
                hasUpdate = refreshRepositoryNow(repository) || hasUpdate
            }
            reconcileInstalledExtensionSnapshots(disableMissing = true)
            if (announceUpdates && hasUpdate) Toast.makeText(this@MainActivity, "Extension updates available.", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun reconcileInstalledExtensionSnapshots(disableMissing: Boolean) {
        val updated = relaySettings.installedExtensions.map { installed ->
            if (installed.catalogSnapshot != null) return@map installed
            val snapshot = installed.resolvedCatalogEntry(repositoryCatalogs[installed.repositoryId].orEmpty())
            when {
                snapshot != null -> installed.copy(catalogSnapshot = snapshot)
                disableMissing && installed.enabled -> installed.disabled(
                    "Trusted install details are unavailable. Refresh its repository or reinstall the extension.",
                )
                else -> installed
            }
        }
        if (updated != relaySettings.installedExtensions) {
            settingsStore.save(relaySettings.copy(installedExtensions = updated))
            relaySettings = settingsStore.settings.value
        }
    }

    private suspend fun refreshRepositoryNow(repository: RepositoryDescriptor): Boolean {
        if (repositoryMessages[repository.id] == "REFRESHING…") return false
        repositoryMessages = repositoryMessages + (repository.id to "REFRESHING…")
        return when (val result = repositoryCatalogClient.refresh(repository)) {
            is CatalogRefreshResult.Success -> {
                repositoryCatalogs = repositoryCatalogs + (repository.id to result.catalog.extensions)
                repositoryMessages = repositoryMessages + (
                    repository.id to if (result.fromCache) "CACHED — REFRESH FAILED" else "${result.catalog.extensions.size} AVAILABLE"
                )
                !result.fromCache && findComponentUpdates(
                    installed = relaySettings.installedExtensions
                        .filter { it.repositoryId == repository.id }
                        .map { installed ->
                            InstalledComponent(
                                ComponentIdentity(installed.kind.toUpdatableComponentKind(), installed.repositoryId, installed.extensionId),
                                installed.version,
                            )
                        },
                    candidates = result.catalog.extensions.map { entry ->
                        AvailableComponent(
                            ComponentIdentity(entry.kind.toUpdatableComponentKind(), repository.id, entry.id),
                            entry.version,
                            entry.isCompatible,
                            entry,
                        )
                    },
                ).any { it.isActionable }
            }
            is CatalogRefreshResult.Failure -> {
                val message = if (repositoryCatalogs.containsKey(repository.id)) {
                    "CACHED — ${result.message}"
                } else {
                    result.message
                }
                repositoryMessages = repositoryMessages + (repository.id to message)
                false
            }
        }
    }

    private fun confirmExtensionInstall(repository: RepositoryDescriptor, entry: ExtensionCatalogEntry) {
        if (!isCurrentTrustedEntry(repository, entry)) {
            Toast.makeText(this, "Refresh this trusted repository before installing.", Toast.LENGTH_LONG).show()
            return
        }
        if (entry.kind == ExtensionKind.THEME_PACK) {
            if (dev.relay.music.extension.isBuiltInThemePack(entry.id)) {
                Toast.makeText(this, "Built-in themes cannot be replaced.", Toast.LENGTH_LONG).show()
                return
            }
            val conflict = relaySettings.installedExtensions.firstOrNull {
                it.kind == ExtensionKind.THEME_PACK && it.extensionId == entry.id && it.repositoryId != repository.id
            }
            if (conflict != null) {
                Toast.makeText(this, "Remove the copy from ${conflict.repositoryId} before installing this one.", Toast.LENGTH_LONG).show()
                return
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Allow extension installs")
                .setMessage("Relay installs only APKs verified against a trusted repository, but Android still requires your approval before it can request an installation.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("OPEN SETTINGS") { _, _ ->
                    extensionInstallLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()),
                    )
                }
                .show()
            return
        }
        val installed = relaySettings.installedExtensions.firstOrNull {
            it.repositoryId == repository.id && it.extensionId == entry.id
        }
        val addedPermissions = installed?.permissions?.let { entry.permissions - it }.orEmpty()
        val signerChanged = installed?.androidSigningCertificateSha256 != null &&
            installed.androidSigningCertificateSha256 != entry.androidSigningCertificateSha256
        val summary = buildList {
            add("Repository: ${repository.name}")
            add("Type: ${if (entry.kind == ExtensionKind.THEME_PACK) "data-only theme pack" else "Android source extension"}")
            add("Version: ${entry.version}")
            add("Permissions: ${entry.permissions.joinToString { it.name.replace('_', ' ') }.ifBlank { "NONE" }}")
            if (addedPermissions.isNotEmpty()) add("New permissions: ${addedPermissions.joinToString { it.name.replace('_', ' ') }}")
            if (entry.kind == ExtensionKind.THEME_PACK) add("Relay will parse only bounded host-rendered theme options; code, assets, CSS, and shaders are not accepted.")
            if (signerChanged) add("APK signer changed. Android may require replacement instead of an update.")
        }.joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle(
                when {
                    entry.kind == ExtensionKind.THEME_PACK && installed == null -> "Install theme pack?"
                    entry.kind == ExtensionKind.THEME_PACK -> "Update theme pack?"
                    installed == null -> "Install extension?"
                    else -> "Update extension?"
                },
            )
            .setMessage(summary)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton(if (installed == null) "INSTALL" else "UPDATE") { _, _ -> installExtension(repository, entry) }
            .show()
    }

    private fun installExtension(repository: RepositoryDescriptor, entry: ExtensionCatalogEntry) {
        if (extensionDownload != null || !isCurrentTrustedEntry(repository, entry)) return
        activityScope.launch {
            extensionDownload = ExtensionDownloadProgress(entry.id, entry.name, 0, entry.artifactSizeBytes)
            when (val download = extensionArtifactClient.download(entry) { downloaded, total ->
                runOnUiThread {
                    extensionDownload = ExtensionDownloadProgress(entry.id, entry.name, downloaded, total)
                }
            }) {
                is ArtifactDownloadResult.Failure -> Toast.makeText(this@MainActivity, download.message, Toast.LENGTH_LONG).show()
                is ArtifactDownloadResult.Success -> when (entry.kind) {
                    ExtensionKind.THEME_PACK -> installThemePack(repository, entry, download.file)
                    ExtensionKind.SOURCE -> when (val request = extensionInstaller.stage(entry, download.file)) {
                        is ExtensionInstallRequest.Refused -> Toast.makeText(this@MainActivity, request.reason, Toast.LENGTH_LONG).show()
                        is ExtensionInstallRequest.Ready -> {
                            val statusIntent = Intent(extensionInstallStatusAction).setPackage(packageName)
                            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                            val pendingIntent = PendingIntent.getBroadcast(this@MainActivity, request.sessionId, statusIntent, flags)
                            pendingExtensionInstalls[request.sessionId] = repository to entry
                            extensionInstaller.commit(request.sessionId, pendingIntent.intentSender)?.let { error ->
                                pendingExtensionInstalls.remove(request.sessionId)
                                Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            extensionDownload = null
        }
    }

    private fun setExtensionEnabled(installed: InstalledExtension, enabled: Boolean) {
        if (installed.kind != ExtensionKind.SOURCE) return
        activityScope.launch {
            val updated = if (enabled) {
                val entry = installed.resolvedCatalogEntry(repositoryCatalogs[installed.repositoryId].orEmpty())
                if (entry == null) {
                    installed.disabled("Trusted install details are unavailable. Refresh its repository or reinstall the extension.")
                } else {
                    val failure = extensionLoader.load(entry).exceptionOrNull()?.message
                    installed.copy(
                        enabled = failure == null,
                        disabledReason = failure?.take(240),
                        catalogSnapshot = entry,
                    )
                }
            } else {
                installed.disabled("Disabled by user.")
            }
            settingsStore.save(
                relaySettings.copy(
                    installedExtensions = relaySettings.installedExtensions
                        .filterNot { it.repositoryId == updated.repositoryId && it.extensionId == updated.extensionId } + updated,
                ),
            )
            extensionSourceResults = emptyList()
            extensionSourceMessage = if (updated.enabled) "Extension enabled." else "Extension disabled."
            if (enabled && !updated.enabled) {
                Toast.makeText(this@MainActivity, "Extension could not be enabled: ${updated.disabledReason}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun uninstallExtension(installed: InstalledExtension) {
        if (installed.kind == ExtensionKind.THEME_PACK) {
            AlertDialog.Builder(this)
                .setTitle("Remove theme pack?")
                .setMessage("${installed.catalogSnapshot?.name ?: installed.extensionId} will be removed from Relay.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("REMOVE") { _, _ -> removeThemePack(installed.extensionId) }
                .show()
            return
        }
        val packageName = installed.androidPackageName ?: return
        pendingExtensionUninstallPackage = packageName
        extensionUninstallLauncher.launch(
            Intent(Intent.ACTION_DELETE)
                .setData("package:$packageName".toUri())
                .putExtra(Intent.EXTRA_RETURN_RESULT, true),
        )
    }

    private fun registerExtensionInstallReceiver() {
        val filter = IntentFilter(extensionInstallStatusAction)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(extensionInstallReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(extensionInstallReceiver, filter)
        }
    }

    private fun handleExtensionInstallStatus(intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val pending = pendingExtensionInstalls[sessionId] ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation?.let(extensionInstallLauncher::launch)
                    ?: Toast.makeText(this, "Android installer did not provide a confirmation action.", Toast.LENGTH_LONG).show()
            }
            PackageInstaller.STATUS_SUCCESS -> {
                pendingExtensionInstalls.remove(sessionId)
                enableInstalledExtension(pending)
            }
            else -> {
                pendingExtensionInstalls.remove(sessionId)
                Toast.makeText(this, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Extension installation was cancelled.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableInstalledExtension(pending: Pair<RepositoryDescriptor, ExtensionCatalogEntry>) {
        activityScope.launch {
            val result = extensionLoader.load(pending.second)
            val failureMessage = result.exceptionOrNull()?.message
            val disabledReason = when {
                failureMessage != null -> failureMessage.take(240)
                result.isFailure -> "Extension verification failed."
                else -> null
            }
            val installed = pending.second.asInstalled(pending.first.id, disabledReason)
            settingsStore.save(
                relaySettings.copy(
                    installedExtensions = relaySettings.installedExtensions
                        .filterNot { it.repositoryId == installed.repositoryId && it.extensionId == installed.extensionId } + installed,
                ),
            )
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) "Extension installed and enabled." else "Extension was installed but not enabled: ${result.exceptionOrNull()?.message ?: "verification failed."}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private suspend fun installThemePack(repository: RepositoryDescriptor, entry: ExtensionCatalogEntry, artifact: File) {
        runCatching {
            require(isCurrentTrustedEntry(repository, entry)) { "The signed catalog changed; review this theme pack again." }
            val pack = withContext(Dispatchers.IO) {
                ThemePackReader.parseCatalogArtifact(artifact.readText(Charsets.UTF_8), entry.id, entry.version).getOrThrow()
            }
            require(!dev.relay.music.extension.isBuiltInThemePack(pack.id)) { "Built-in themes cannot be replaced." }
            settingsStore.save(relaySettings.withRepositoryThemePack(repository.id, entry, pack))
            pack
        }.fold(
            onSuccess = { Toast.makeText(this, "Theme \"${it.name}\" installed and applied.", Toast.LENGTH_LONG).show() },
            onFailure = { Toast.makeText(this, it.message ?: "Theme pack installation failed.", Toast.LENGTH_LONG).show() },
        )
    }

    private fun isCurrentTrustedEntry(repository: RepositoryDescriptor, entry: ExtensionCatalogEntry): Boolean =
        repository in relaySettings.trustedRepositories && entry in repositoryCatalogs[repository.id].orEmpty() && entry.validate() == null

    private fun searchExtensionSources(request: SourceBrowseRequest) {
        sourceSearchJob?.cancel()
        if (!request.appendsResults) extensionSourceResults = emptyList()
        extensionSourceMessage = if (request.query.isBlank()) "BROWSING…" else "SEARCHING…"
        sourceSearchJob = activityScope.launch {
            val entries = enabledSourceEntries(request.extensionId)
            if (entries.isEmpty()) {
                extensionSourceMessage = "Install a source extension, then refresh its repository if it is not listed here."
                return@launch
            }
            val results = extensionSources.query(entries, request)
            extensionSourceResults = if (request.appendsResults) {
                extensionSourceResults.map { existing ->
                    results.firstOrNull { it.extensionId == existing.extensionId }
                        ?.let { next -> next.copy(tracks = existing.tracks + next.tracks, listings = existing.listings) }
                        ?: existing
                }
            } else {
                results
            }
            extensionSourceMessage =
                if (extensionSourceResults.isEmpty()) "No source results found. Check Installed for disabled extensions." else null
        }
    }

    private fun selectTrack(selected: Track) {
        val localIndex = tracks.indexOf(selected)
        if (localIndex >= 0) {
            playerEngine.setQueue(tracks, localIndex)
            return
        }
        if (!extensionSources.isExtensionTrack(selected)) {
            playerEngine.setQueue(listOf(selected), 0)
            return
        }
        activityScope.launch {
            extensionSources.preparePlayback(selected, enabledSourceEntries()).fold(
                onSuccess = { prepared -> playerEngine.setQueue(listOf(queueableTrack(prepared) ?: prepared), 0) },
                onFailure = { error ->
                    Toast.makeText(this@MainActivity, error.message ?: "Could not start this track.", Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    private fun downloadRemoteTrack(track: Track) {
        if (remoteTrackDownload != null) return
        val rootUri = relaySettings.storageRootUri?.toUri()
        if (rootUri == null) {
            Toast.makeText(this, "Choose a Relay storage folder before downloading music.", Toast.LENGTH_LONG).show()
            return
        }
        activityScope.launch {
            remoteTrackDownload = RemoteTrackDownloadProgress(track.sourceId, track.id, track.title, 0, 0)
            val download = extensionSources.prepareDownload(track, enabledSourceEntries()).getOrElse { error ->
                remoteTrackDownload = null
                Toast.makeText(this@MainActivity, error.message ?: "Could not prepare this download.", Toast.LENGTH_LONG).show()
                return@launch
            }
            when (val result = remoteTrackDownloadClient.download(track, download.url, rootUri, download.headers) { downloaded, total ->
                runOnUiThread {
                    remoteTrackDownload = RemoteTrackDownloadProgress(track.sourceId, track.id, track.title, downloaded, total)
                }
            }) {
                is RemoteTrackDownloadResult.Success -> {
                    withContext(Dispatchers.IO) {
                        val previous = userLibraryDao.offlineDownload(track.sourceId, track.id)
                        userLibraryDao.saveOfflineDownload(
                            OfflineDownloadEntity(
                                sourceId = track.sourceId,
                                trackId = track.id,
                                documentUri = result.documentUri,
                                mimeType = result.mimeType,
                                sizeBytes = result.sizeBytes,
                                downloadedAtEpochMs = System.currentTimeMillis(),
                                title = track.title,
                            ),
                        )
                        previous?.documentUri
                            ?.takeIf { it != result.documentUri }
                            ?.let { uri ->
                                runCatching {
                                    DocumentFile.fromSingleUri(this@MainActivity, uri.toUri())?.delete()
                                }
                            }
                    }
                    refreshLibrary()
                    val limitGb = relaySettings.downloadStorageLimitGb
                    if (relaySettings.downloadAutoCleanup && limitGb > 0) {
                        enforceDownloadStorageLimit(limitGb)
                    }
                    Toast.makeText(this@MainActivity, "Saved to Relay/downloads.", Toast.LENGTH_LONG).show()
                }
                is RemoteTrackDownloadResult.Failure -> {
                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
            remoteTrackDownload = null
        }
    }

    private fun loadSourceSettingSchema(extensionId: String) {
        if (sourceSettingSchemas.containsKey(extensionId)) return
        val entry = relaySettings.installedExtensions
            .firstOrNull { it.extensionId == extensionId }
            ?.let { installed ->
                installed.resolvedCatalogEntry(repositoryCatalogs[installed.repositoryId].orEmpty())
            } ?: return
        activityScope.launch {
            extensionSources.settingDefinitions(entry).onSuccess { schema ->
                sourceSettingSchemas = sourceSettingSchemas + (extensionId to schema)
            }.onFailure {
                sourceSettingSchemas = sourceSettingSchemas + (extensionId to emptyList())
            }
        }
    }

    private fun saveSourceSettings(extensionId: String, values: Map<String, String>) {
        activityScope.launch {
            settingsStore.save(
                relaySettings.copy(sourceSettings = relaySettings.sourceSettings + (extensionId to values)),
            )
        }
    }

    private fun enabledSourceEntries(extensionId: String? = null) = relaySettings.installedExtensions
        .asSequence()
        .filter { it.enabled && it.kind == dev.relay.music.extension.ExtensionKind.SOURCE }
        .filter { extensionId == null || it.extensionId == extensionId }
        .mapNotNull { installed ->
            installed.resolvedCatalogEntry(repositoryCatalogs[installed.repositoryId].orEmpty())
                ?.let { entry -> installed to entry }
        }
        .toList()

    private suspend fun disableExtension(installed: InstalledExtension, error: Throwable) {
        val updated = installed.disabled(error.message ?: error::class.java.simpleName)
        settingsStore.save(
            relaySettings.copy(
                installedExtensions = relaySettings.installedExtensions
                    .filterNot { it.repositoryId == updated.repositoryId && it.extensionId == updated.extensionId } + updated,
            ),
        )
    }

    private fun exportBackup() {
        chooseBackupSections("Create backup", RelayBackupSection.all) { sections ->
            exportBackup(sections)
        }
    }

    private fun exportBackup(sections: Set<RelayBackupSection>) {
        val rootUri = relaySettings.storageRootUri?.toUri()
        if (rootUri == null) {
            pendingBackupSections = sections
            backupExportLauncher.launch("relay-${System.currentTimeMillis()}.relaybackup")
            return
        }
        activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val document = RelayStorage.createManualBackup(
                        this@MainActivity,
                        rootUri,
                        "relay-${System.currentTimeMillis()}.relaybackup",
                    ) ?: error("Relay storage folder is unavailable.")
                    contentResolver.openOutputStream(document.uri)?.use {
                        RelayBackupArchive.write(it, userLibraryDao, sections)
                    }
                        ?: error("Could not create the backup file.")
                }
            }
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) "Manual backup saved in Relay/backups/manual." else "Could not save backup. Check Relay folder access in Settings.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun chooseBackupSections(
        title: String,
        available: Set<RelayBackupSection>,
        onSelected: (Set<RelayBackupSection>) -> Unit,
    ) {
        val ordered = RelayBackupSection.entries.filter(available::contains)
        val selected = available.toMutableSet()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(
                ordered.map { it.displayName }.toTypedArray(),
                BooleanArray(ordered.size) { true },
            ) { _, index, checked ->
                if (checked) selected += ordered[index] else selected -= ordered[index]
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                if (selected.isEmpty()) {
                    Toast.makeText(this, "Select at least one section.", Toast.LENGTH_SHORT).show()
                } else {
                    onSelected(selected.toSet())
                }
            }
            .show()
    }

    private fun chooseRestoreSections(contents: RelayBackupContents) {
        chooseBackupSections("Restore backup", contents.sections) { selected ->
            activityScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val packageNames = contents.backup.settings?.installedExtensions().orEmpty()
                            .mapNotNull { it.androidPackageName }
                            .toSet()
                        contents.restorePlan(
                            selectedSections = selected,
                            currentSettings = userLibraryDao.settingsSnapshot(),
                            installedAndroidPackages = packageNames.filterTo(mutableSetOf(), ::isPackageInstalled),
                        )
                    }
                }
                result.fold(
                    onSuccess = ::showRestorePlan,
                    onFailure = {
                        Toast.makeText(this@MainActivity, "Backup restore plan is invalid.", Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }

    private fun showRestorePlan(plan: RelayRestorePlan) {
        val report = buildList {
            add("Selected: ${plan.selectedSections.sortedBy { it.ordinal }.joinToString { it.displayName }}")
            if (plan.missingRepositories.isNotEmpty()) {
                add("Missing repositories: ${plan.missingRepositories.joinToString()}")
            }
            if (plan.missingPlugins.isNotEmpty()) add("Packages to reinstall: ${plan.missingPlugins.joinToString()}")
            if (plan.trackersRequiringReconnect.isNotEmpty()) {
                add("Reconnect after restore: ${plan.trackersRequiringReconnect.joinToString()}")
                add("The current Last.fm session will be cleared.")
            }
            addAll(plan.warnings)
            add("Existing data in the selected sections will be replaced after a safety backup is created.")
        }.joinToString("\n\n")
        AlertDialog.Builder(this)
            .setTitle("Review restore")
            .setMessage(report)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ -> performRestore(plan) }
            .show()
    }

    private fun performRestore(plan: RelayRestorePlan) {
        activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val safetyBackup = AtomicFile(File(filesDir, "restore-safety/last.relaybackup"))
                    safetyBackup.baseFile.parentFile?.mkdirs()
                    val archiveBytes = ByteArrayOutputStream().also {
                        RelayBackupArchive.write(it, userLibraryDao)
                    }.toByteArray()
                    val output = safetyBackup.startWrite()
                    try {
                        output.write(archiveBytes)
                        safetyBackup.finishWrite(output)
                    } catch (error: Throwable) {
                        safetyBackup.failWrite(output)
                        throw error
                    }
                    userLibraryDao.restoreUserLibrary(plan)
                    userLibraryDao.settingsSnapshot()?.asSettings()?.backupSchedule ?: BackupSchedule.OFF
                }
            }
            result.fold(
                onSuccess = { schedule ->
                    settingsStore.load()
                    relaySettings = settingsStore.settings.value
                    if (plan.trackersRequiringReconnect.isNotEmpty()) {
                        lastFmConnectionState = LastFmConnectionState.DISCONNECTED
                        lastFmErrorMessage = null
                        sessionKeyStore.clear()
                    }
                    AutomaticBackupScheduler.update(this@MainActivity, schedule)
                    refreshLibrary()
                    val followUp = when {
                        plan.missingPlugins.isNotEmpty() -> " Reinstall missing extension packages before enabling them."
                        plan.missingRepositories.isNotEmpty() -> " Review missing extension repositories."
                        plan.trackersRequiringReconnect.isNotEmpty() -> " Reconnect your tracker in Settings."
                        else -> ""
                    }
                    Toast.makeText(this@MainActivity, "Backup restored.$followUp", Toast.LENGTH_LONG).show()
                },
                onFailure = {
                    Toast.makeText(this@MainActivity, "Backup could not be restored; existing data was kept.", Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
    }.isSuccess

    private fun exportSync() {
        val rootUri = relaySettings.storageRootUri?.toUri()
        if (rootUri == null) {
            syncExportLauncher.launch("relay-${System.currentTimeMillis()}.relaysync")
            return
        }
        activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val document = RelayStorage.createSyncBundle(
                        this@MainActivity,
                        rootUri,
                        "relay-${System.currentTimeMillis()}.relaysync",
                    ) ?: error("Relay storage folder is unavailable.")
                    contentResolver.openOutputStream(document.uri)?.use { RelaySyncArchive.write(it, userLibraryDao) }
                        ?: error("Could not create sync file.")
                }
            }
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) "Sync file saved in Relay/sync." else "Could not save sync file. Choose the Relay folder again.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun dismissSyncConflict(id: String) {
        activityScope.launch(Dispatchers.IO) { userLibraryDao.deleteSyncConflict(id) }
    }

    private fun useReceivedSyncConflict(id: String) {
        activityScope.launch {
            val applied = withContext(Dispatchers.IO) { userLibraryDao.resolveSyncConflict(id, useReceived = true) }
            Toast.makeText(this@MainActivity, if (applied) "Received value applied." else "Could not apply the received value.", Toast.LENGTH_LONG).show()
        }
    }

    private fun hostLanSync() {
        if (lanSync.active) return
        if (relaySettings.storageRootUri == null) {
            lanSync = LanSyncUiState(message = "Choose a Relay storage folder before starting LAN sync.")
            return
        }
        activityScope.launch {
            val server = withContext(Dispatchers.IO) { ServerSocket(0) }
            lanSyncServer = server
            lanSync = LanSyncUiState(message = "Waiting for a device…", hostAddress = "${localLanAddress()}:${server.localPort}", active = true, preparedMusicBytes = lanMusicArchive?.length(), warning = lanSessionWarning())
            runLanSync { identity, archive, incoming ->
                LanSyncTransport.hostOnce(
                    server, identity, identity.peers(), ::awaitLanConfirmation, archive, incoming,
                    lanMusicArchive, cacheDir, { file -> importLanMusic(file) },
                    onConnected = { lanSyncConnection = it },
                )
            }
        }
    }

    private fun joinLanSync(value: String) {
        val host = value.substringBeforeLast(':').trim()
        val port = value.substringAfterLast(':', "").toIntOrNull()?.takeIf { it in 1..65535 }
            ?: run { lanSync = LanSyncUiState(message = "Enter HOST:PORT, for example 192.168.1.12:38400."); return }
        if (host.isBlank()) { lanSync = LanSyncUiState(message = "Enter HOST:PORT, for example 192.168.1.12:38400."); return }
        if (relaySettings.storageRootUri == null) {
            lanSync = LanSyncUiState(message = "Choose a Relay storage folder before starting LAN sync.")
            return
        }
        activityScope.launch {
            lanSync = LanSyncUiState(message = "Connecting…", active = true, preparedMusicBytes = lanMusicArchive?.length(), warning = lanSessionWarning())
            runLanSync { identity, archive, incoming ->
                LanSyncTransport.clientOnce(
                    host, port, identity, identity.peers(), ::awaitLanConfirmation, archive, incoming,
                    lanMusicArchive, cacheDir, { file -> importLanMusic(file) },
                    onConnected = { lanSyncConnection = it },
                )
            }
        }
    }

    private suspend fun runLanSync(call: (LanSyncIdentityStore, ByteArray, (ByteArray) -> Unit) -> dev.relay.music.library.LanSyncPeer) {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val identity = LanSyncIdentityStore(this@MainActivity)
                val archive = ByteArrayOutputStream().use { RelaySyncArchive.write(it, userLibraryDao); it.toByteArray() }
                call(identity, archive) { data -> runBlocking { userLibraryDao.mergeSync(RelaySyncArchive.read(ByteArrayInputStream(data))) } }
                    .also(identity::savePeer)
            }
        }
        lanSync = LanSyncUiState(message = if (result.isSuccess) "LAN data sync complete." else "LAN sync failed or was cancelled.")
        lanSyncServer?.close()
        lanSyncServer = null
        lanSyncConnection = null
        if (result.isSuccess) refreshLibrary()
        if (result.isSuccess) refreshPairedDevices()
        lanMusicArchive?.delete()
        lanMusicArchive = null
    }

    private fun awaitLanConfirmation(peer: dev.relay.music.library.LanSyncPeer, code: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        runBlocking(Dispatchers.Main) { pendingLanConfirmation = deferred; lanSync = LanSyncUiState(pairingCode = code, peerFingerprint = peer.id, awaitingConfirmation = true, active = true) }
        return runBlocking { deferred.await() }
    }

    private fun confirmLanSync(accepted: Boolean) {
        pendingLanConfirmation?.complete(accepted)
        pendingLanConfirmation = null
        if (!accepted) cancelLanSync() else lanSync = LanSyncUiState(message = "Pairing confirmed. Syncing…", active = true)
    }

    private fun cancelLanSync() {
        pendingLanConfirmation?.complete(false)
        pendingLanConfirmation = null
        lanSyncConnection?.close()
        lanSyncConnection = null
        lanSyncServer?.close()
        lanSyncServer = null
        lanSync = LanSyncUiState(message = "LAN sync cancelled.")
    }

    private fun refreshPairedDevices() {
        pairedDevices = LanSyncIdentityStore(this).peers().map { PairedDeviceUi(it.id, it.name) }
    }

    private fun unpairDevice(id: String) {
        LanSyncIdentityStore(this).removePeer(id)
        refreshPairedDevices()
    }

    private fun hostPlayTogether() {
        if (playTogetherRunning) return
        if (!playerEngine.state.value.isPlaying || playerEngine.state.value.currentTrack == null) {
            playTogether = PlayTogetherUiState(message = "Start playback before hosting Play Together.")
            return
        }
        playTogetherRunning = true
        activityScope.launch {
            val server = withContext(Dispatchers.IO) { ServerSocket(0) }
            playTogetherServer = server
            playTogether = PlayTogetherUiState(message = "Waiting for a device…", hostAddress = "${localLanAddress()}:${server.localPort}", active = true)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val identity = LanSyncIdentityStore(this@MainActivity)
                    LanPlayTogetherTransport.host(
                        server, identity, identity.peers(), ::awaitPlayTogetherConfirmation,
                        active = { playTogetherRunning }, command = ::currentPlayTogetherCommand,
                        onConnected = { playTogetherConnection = it },
                    ).also(identity::savePeer)
                }
            }
            playTogetherConnection = null
            playTogetherServer = null
            playTogetherRunning = false
            if (result.isSuccess) refreshPairedDevices()
            playTogether = PlayTogetherUiState(message = if (result.isSuccess) "Play Together ended." else "Play Together ended or could not connect.")
        }
    }

    private fun joinPlayTogether(value: String) {
        if (playTogetherRunning) return
        val host = value.substringBeforeLast(':').trim()
        val port = value.substringAfterLast(':', "").toIntOrNull()?.takeIf { it in 1..65535 }
            ?: run { playTogether = PlayTogetherUiState(message = "Enter HOST:PORT, for example 192.168.1.12:38400."); return }
        if (host.isBlank()) { playTogether = PlayTogetherUiState(message = "Enter HOST:PORT, for example 192.168.1.12:38400."); return }
        playTogetherRunning = true
        activityScope.launch {
            playTogether = PlayTogetherUiState(message = "Connecting…", active = true)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val identity = LanSyncIdentityStore(this@MainActivity)
                    LanPlayTogetherTransport.client(
                        host, port, identity, identity.peers(), ::awaitPlayTogetherConfirmation,
                        active = { playTogetherRunning },
                        onCommand = { command, leaderMinusLocal ->
                            val resolved = resolvePlayTogetherTrack(command)
                            val applied = runBlocking(Dispatchers.Main) {
                                playTogetherCoordinator.apply(command, leaderMinusLocal, resolved)
                            }
                            runBlocking(Dispatchers.Main) {
                                playTogether = when (applied) {
                                    PlayTogetherApplyResult.Unavailable -> PlayTogetherUiState(message = "The leader's track is unavailable on this device.", active = true)
                                    is PlayTogetherApplyResult.Applied -> PlayTogetherUiState(
                                        message = if (applied.needsVisibleResync) "Resynced to leader." else "Playing with leader.",
                                        active = true,
                                        driftMs = applied.driftMs,
                                    )
                                    is PlayTogetherApplyResult.ResyncRequired -> {
                                        pendingPlayTogetherResync = PendingPlayTogetherResync(command, leaderMinusLocal, resolved)
                                        PlayTogetherUiState(
                                            message = "Leader is ${applied.driftMs}ms away. Resync when ready.",
                                            active = true,
                                            driftMs = applied.driftMs,
                                            resyncRequired = true,
                                        )
                                    }
                                }
                            }
                        },
                        onConnected = { playTogetherConnection = it },
                    ).also(identity::savePeer)
                }
            }
            playTogetherCoordinator.leave()
            playTogetherConnection = null
            playTogetherRunning = false
            if (result.isSuccess) refreshPairedDevices()
            playTogether = PlayTogetherUiState(message = if (result.isSuccess) "Play Together ended." else "Play Together ended or could not connect.")
        }
    }

    private fun currentPlayTogetherCommand(): PlayTogetherCommand? {
        val state = playerEngine.state.value
        val track = state.currentTrack ?: return null
        return PlayTogetherCommand(
            sourceId = track.sourceId,
            trackId = track.id,
            contentDigest = runCatching { trackContentDigests.digest(track) }.getOrNull(),
            queueIndex = state.currentIndex,
            leaderPositionMs = state.positionMs,
            targetLeaderElapsedMs = SystemClock.elapsedRealtime() + 2_000,
            playing = state.isPlaying,
        )
    }

    private fun resolvePlayTogetherTrack(command: PlayTogetherCommand): Track? =
        tracks.firstOrNull { it.sourceId == command.sourceId && it.id == command.trackId }
            ?: command.contentDigest?.let { trackContentDigests.find(tracks, it) }

    private fun awaitPlayTogetherConfirmation(peer: dev.relay.music.library.LanSyncPeer, code: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        runBlocking(Dispatchers.Main) {
            pendingPlayTogetherConfirmation = deferred
            playTogether = PlayTogetherUiState(pairingCode = code, peerFingerprint = peer.id, awaitingConfirmation = true, active = true)
        }
        return runBlocking { deferred.await() }
    }

    private fun confirmPlayTogether(accepted: Boolean) {
        pendingPlayTogetherConfirmation?.complete(accepted)
        pendingPlayTogetherConfirmation = null
        playTogether = PlayTogetherUiState(message = if (accepted) "Pairing confirmed. Connecting…" else "Play Together pairing cancelled.", active = accepted)
    }

    private fun leavePlayTogether() {
        playTogetherRunning = false
        playTogetherConnection?.close()
        playTogetherConnection = null
        playTogetherServer?.close()
        playTogetherServer = null
        playTogetherCoordinator.leave()
        pendingPlayTogetherResync = null
        playTogether = PlayTogetherUiState(message = "Play Together ended.")
    }

    private fun resyncPlayTogether() {
        val pending = pendingPlayTogetherResync ?: return
        val applied = playTogetherCoordinator.apply(pending.command, pending.leaderMinusLocalMs, pending.track, forceResync = true)
        pendingPlayTogetherResync = null
        playTogether = when (applied) {
            PlayTogetherApplyResult.Unavailable -> PlayTogetherUiState(message = "The leader's track is unavailable on this device.", active = true)
            is PlayTogetherApplyResult.Applied -> PlayTogetherUiState(message = "Resynced to leader.", active = true, driftMs = applied.driftMs)
            is PlayTogetherApplyResult.ResyncRequired -> PlayTogetherUiState(message = "Could not resync yet.", active = true, driftMs = applied.driftMs, resyncRequired = true)
        }
    }

    private fun localLanAddress(): String = Collections.list(NetworkInterface.getNetworkInterfaces())
        .flatMap { Collections.list(it.inetAddresses) }
        .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }?.hostAddress ?: "LOCAL-IP"

    private fun lanSessionWarning(): String? = buildList {
        if (getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) add("Battery Saver can interrupt a large transfer.")
        if (getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true) add("The active network is marked metered.")
    }.takeIf { it.isNotEmpty() }?.joinToString(" ")

    private fun importLanMusic(file: File) {
        try {
            val root = relaySettings.storageRootUri?.toUri() ?: error("Relay storage folder is unavailable.")
            file.inputStream().use { RelayMusicTransferArchive.read(this, it, root) }
        } finally {
            file.delete()
        }
    }

    private fun createPlaylist(name: String) {
        val cleanName = name.trim().take(100)
        if (cleanName.isEmpty()) return
        activityScope.launch(Dispatchers.IO) {
            userLibraryDao.createPlaylist(
                dev.relay.music.library.PlaylistEntity(
                    name = cleanName,
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun addToPlaylist(playlistId: Long, track: Track) {
        activityScope.launch {
            val added = withContext(Dispatchers.IO) { userLibraryDao.appendToPlaylist(playlistId, track) }
            if (playlistTracks.containsKey(playlistId)) reloadPlaylistTracks(playlistId)
            val playlistName = playlists.firstOrNull { it.id == playlistId }?.name ?: "playlist"
            Toast.makeText(
                this@MainActivity,
                if (added) "Added to $playlistName." else "Already in $playlistName.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun createAndAddToPlaylist(name: String, track: Track) {
        val cleanName = name.trim().take(100)
        if (cleanName.isEmpty()) return
        activityScope.launch {
            val playlistId = withContext(Dispatchers.IO) {
                val id = userLibraryDao.createPlaylist(
                    dev.relay.music.library.PlaylistEntity(name = cleanName, createdAtEpochMs = System.currentTimeMillis()),
                )
                userLibraryDao.appendToPlaylist(id, track)
                id
            }
            if (playlistTracks.containsKey(playlistId)) reloadPlaylistTracks(playlistId)
            Toast.makeText(this@MainActivity, "Created \"$cleanName\" and added the track.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPlaylist(playlistId: Long) {
        activityScope.launch { reloadPlaylistTracks(playlistId) }
    }

    private suspend fun reloadPlaylistTracks(playlistId: Long) {
        val entries = withContext(Dispatchers.IO) { userLibraryDao.playlistEntries(playlistId) }
        val libraryByKey = tracks.associateBy { it.key() }
        playlistTracks = playlistTracks + (playlistId to entries.map { entry ->
            libraryByKey[entry.key()] ?: Track(
                id = entry.trackId,
                sourceId = entry.sourceId,
                // Extension tracks carry a placeholder the playback service resolves just in time;
                // a local file that is gone stays blank and is reported as unavailable.
                playbackUri = "",
                title = entry.title ?: entry.trackId,
                artist = entry.artist ?: "Unknown artist",
                album = entry.album,
                durationMs = entry.durationMs,
                artworkUri = entry.artworkUri,
            ).let { track ->
                track.copy(
                    playbackUri = offlineDownloadUris[track.key()] ?: track.extensionStreamPlaceholder().orEmpty(),
                )
            }
        })
    }

    private fun playPlaylist(playlistId: Long, startIndex: Int) {
        activityScope.launch {
            if (playlistTracks[playlistId] == null) reloadPlaylistTracks(playlistId)
            val entries = playlistTracks[playlistId].orEmpty()
            val playable = mutableListOf<Track>()
            var startAt = 0
            entries.forEachIndexed { index, track ->
                if (track.playbackUri.isNotBlank()) {
                    if (index <= startIndex) startAt = playable.size
                    playable += track
                }
            }
            if (playable.isEmpty()) {
                Toast.makeText(this@MainActivity, "None of this playlist's tracks are available.", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (playable.size < entries.size) {
                Toast.makeText(
                    this@MainActivity,
                    "${entries.size - playable.size} tracks are missing from storage and were skipped.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            playerEngine.setQueue(playable, startAt)
        }
    }

    private fun deleteDownload(sourceId: String, trackId: String) {
        activityScope.launch {
            val removed = withContext(Dispatchers.IO) {
                val entity = userLibraryDao.offlineDownload(sourceId, trackId) ?: return@withContext false
                runCatching {
                    DocumentFile.fromSingleUri(this@MainActivity, entity.documentUri.toUri())?.delete()
                }
                userLibraryDao.deleteOfflineDownload(sourceId, trackId)
                true
            }
            if (removed) {
                refreshLibrary()
                Toast.makeText(this@MainActivity, "Download removed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteAllDownloads() {
        activityScope.launch {
            withContext(Dispatchers.IO) {
                userLibraryDao.offlineDownloads().first().forEach { entity ->
                    runCatching {
                        DocumentFile.fromSingleUri(this@MainActivity, entity.documentUri.toUri())?.delete()
                    }
                    userLibraryDao.deleteOfflineDownload(entity.sourceId, entity.trackId)
                }
            }
            refreshLibrary()
            Toast.makeText(this@MainActivity, "Downloads removed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setDownloadStorageLimit(limitGb: Int) {
        activityScope.launch(Dispatchers.IO) {
            settingsStore.save(relaySettings.copy(downloadStorageLimitGb = limitGb))
            if (relaySettings.downloadAutoCleanup && limitGb > 0) {
                enforceDownloadStorageLimit(limitGb)
            }
        }
    }

    private fun setDownloadAutoCleanup(enabled: Boolean) {
        activityScope.launch(Dispatchers.IO) {
            settingsStore.save(relaySettings.copy(downloadAutoCleanup = enabled))
            if (enabled && relaySettings.downloadStorageLimitGb > 0) {
                enforceDownloadStorageLimit(relaySettings.downloadStorageLimitGb)
            }
        }
    }

    private fun evictExceededDownloads() {
        activityScope.launch {
            val limitGb = relaySettings.downloadStorageLimitGb
            if (limitGb <= 0) return@launch
            enforceDownloadStorageLimit(limitGb)
        }
    }

    /**
     * Deletes oldest downloads (by [OfflineDownloadEntity.downloadedAtEpochMs]) until total
     * storage is within [limitGb] GB. Called on limit/cleanup-toggle changes and after each
     * new download when auto-cleanup is enabled.
     */
    private suspend fun enforceDownloadStorageLimit(limitGb: Int) {
        val limitBytes = limitGb * 1024L * 1024L * 1024L
        withContext(Dispatchers.IO) {
            val downloads = userLibraryDao.offlineDownloads().first()
            val byKey = downloads.associateBy { it.sourceId to it.trackId }
            val toEvict = offlineDownloadsToEvict(
                downloads.map {
                    OfflineDownloadEvictionEntry(
                        sourceId = it.sourceId,
                        trackId = it.trackId,
                        sizeBytes = it.sizeBytes,
                        downloadedAtEpochMs = it.downloadedAtEpochMs,
                    )
                },
                limitBytes,
            )
            if (toEvict.isEmpty()) return@withContext
            for ((sourceId, trackId) in toEvict) {
                val download = byKey[sourceId to trackId] ?: continue
                runCatching {
                    DocumentFile.fromSingleUri(this@MainActivity, download.documentUri.toUri())?.delete()
                }
                userLibraryDao.deleteOfflineDownload(sourceId, trackId)
            }
            refreshLibrary()
            withContext(Dispatchers.Main) {
                val evicted = toEvict.size
                Toast.makeText(
                    this@MainActivity,
                    "Removed $evicted download${if (evicted == 1) "" else "s"} to stay within limit.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun removeQueueIndex(index: Int) {
        val state = playerEngine.state.value
        val edit = removeFromQueue(state.queue, state.currentIndex, index) ?: return
        applyQueueEdit(edit, state.isPlaying, state.positionMs, keepPosition = index != state.currentIndex)
    }

    private fun moveQueueIndex(index: Int, delta: Int) {
        val state = playerEngine.state.value
        val edit = moveInQueue(state.queue, state.currentIndex, index, index + delta) ?: return
        applyQueueEdit(edit, state.isPlaying, state.positionMs, keepPosition = true)
    }

    private fun playNext(track: Track) {
        val queueable = queueableTrack(track) ?: return
        val state = playerEngine.state.value
        val edit = playNextInQueue(state.queue, state.currentIndex, queueable)
        applyQueueEdit(edit, state.isPlaying || state.queue.isEmpty(), state.positionMs, keepPosition = true)
        Toast.makeText(this, "Playing next.", Toast.LENGTH_SHORT).show()
    }

    private fun enqueue(track: Track) {
        val queueable = queueableTrack(track) ?: return
        val state = playerEngine.state.value
        val edit = appendToQueue(state.queue, state.currentIndex, queueable)
        applyQueueEdit(edit, state.isPlaying || state.queue.isEmpty(), state.positionMs, keepPosition = true)
        Toast.makeText(this, "Added to queue.", Toast.LENGTH_SHORT).show()
    }

    /** Extension tracks without an eager stream URL queue as a placeholder the service resolves. */
    private fun queueableTrack(track: Track): Track? {
        offlineDownloadUris[track.key()]?.let { return track.copy(playbackUri = it) }
        if (track.playbackUri.isNotBlank()) return track
        val placeholder = track.extensionStreamPlaceholder()
        if (placeholder == null) {
            Toast.makeText(this, "This track has no playable source.", Toast.LENGTH_LONG).show()
            return null
        }
        return track.copy(playbackUri = placeholder)
    }

    // ponytail: queue edits rebuild the whole queue; Media3 moveMediaItem/removeMediaItem is the
    // upgrade path if the brief re-prepare on edit becomes noticeable.
    private fun applyQueueEdit(edit: QueueEdit, playWhenReady: Boolean, positionMs: Long, keepPosition: Boolean) {
        if (edit.restartsPlayback) {
            playerEngine.setQueue(emptyList(), 0)
            return
        }
        playerEngine.setQueue(
            edit.queue,
            edit.currentIndex,
            playWhenReady = playWhenReady,
            startPositionMs = if (keepPosition) positionMs else 0,
        )
    }

    private fun shuffleQueue() {
        val state = playerEngine.state.value
        val fromLibrary = state.queue.isEmpty()
        val queue = if (fromLibrary) tracks else state.queue
        if (queue.size < 2) return
        val currentIndex = if (fromLibrary) -1 else state.currentIndex
        val shuffled = shuffledQueue(queue, currentIndex, relaySettings.activeShuffleProfile())
        // Profile reshuffling produces the playback order itself; native shuffle would scramble it.
        playerEngine.setShuffleEnabled(false)
        playerEngine.setQueue(
            shuffled,
            startIndex = 0,
            playWhenReady = fromLibrary || state.isPlaying,
            startPositionMs = if (currentIndex >= 0) state.positionMs else 0,
        )
    }

    private fun applyThemePackId(themePackId: String?) {
        activityScope.launch { settingsStore.save(relaySettings.copy(activeThemePackId = themePackId)) }
    }

    private fun removeThemePack(themePackId: String) {
        if (dev.relay.music.extension.isBuiltInThemePack(themePackId)) return
        activityScope.launch {
            settingsStore.save(
                relaySettings.withoutThemePack(themePackId),
            )
        }
    }

    private fun pickShuffleSeed(salt: String) {
        pendingShuffleSeedSalt = salt.trim().take(64)
        shuffleSeedLauncher.launch(arrayOf("image/*"))
    }

    private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            require(output.size() + count <= limit) { "Image is larger than Relay's seed limit." }
            output.write(buffer, 0, count)
        }
    }

    private fun updatePlaylistEntries(playlistId: Long, transform: (MutableList<Track>) -> Unit) {
        val current = playlistTracks[playlistId]?.toMutableList() ?: return
        runCatching { transform(current) }.getOrElse { return }
        playlistTracks = playlistTracks + (playlistId to current.toList())
        activityScope.launch(Dispatchers.IO) { userLibraryDao.replacePlaylistEntries(playlistId, current) }
    }

    private fun movePlaylistEntry(playlistId: Long, index: Int, delta: Int) {
        updatePlaylistEntries(playlistId) { entries ->
            val target = index + delta
            require(index in entries.indices && target in entries.indices)
            entries.add(target, entries.removeAt(index))
        }
    }

    private fun renamePlaylist(playlistId: Long, name: String) {
        val cleanName = name.trim().take(100)
        if (cleanName.isEmpty()) return
        activityScope.launch(Dispatchers.IO) { userLibraryDao.renamePlaylist(playlistId, cleanName) }
    }

    private fun deletePlaylist(playlistId: Long) {
        playlistTracks = playlistTracks - playlistId
        activityScope.launch(Dispatchers.IO) { userLibraryDao.deletePlaylist(playlistId) }
    }

    private fun PlaylistEntryEntity.key(): String = "$sourceId\u0000$trackId"

    private fun saveMetadataOverride(track: Track, override: MetadataOverride) {
        activityScope.launch {
            val saved = metadataRepair.saveOverride(track, override)
            tracks = tracks.map { if (it.key() == track.key()) saved.updatedTrack else it }
            metadataIgnoredTrackKeys = metadataIgnoredTrackKeys - track.key()
            playerEngine.refreshMetadata(tracks)
            refreshLibrary()
            saved.artworkError?.let { error ->
                Toast.makeText(this@MainActivity, "Metadata saved, but cover art failed: ${error.message ?: "try again"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun searchMetadata(track: Track, title: String, artist: String, refresh: Boolean) {
        val requestTrackKey = track.key()
        metadataCandidates = emptyList()
        metadataSearchMessage = "Searching MusicBrainz…"
        metadataCandidateTrackKey = requestTrackKey
        activityScope.launch {
            val outcome = metadataRepair.search(track, title, artist, refresh)
            if (metadataCandidateTrackKey != requestTrackKey) return@launch
            when (outcome) {
                is MetadataRepairCoordinator.SearchOutcome.Candidates -> {
                    metadataCandidates = outcome.candidates
                    metadataSearchMessage = when {
                        outcome.candidates.isEmpty() -> "No matching recordings found."
                        outcome.reviewRequired -> "Several results are close. Review the release, duration, disc, and track before applying."
                        else -> null
                    }
                }
                is MetadataRepairCoordinator.SearchOutcome.Failure -> metadataSearchMessage = outcome.message
            }
        }
    }

    private fun ignoreMetadataReview(track: Track) {
        activityScope.launch {
            metadataRepair.suppressReview(track)
            metadataIgnoredTrackKeys = metadataIgnoredTrackKeys + track.key()
        }
    }

    private fun loadLyrics(track: Track) {
        val key = track.key()
        if (lyricsTrackKey == key) return
        lyricsTrackKey = key
        lyricsText = null
        lyricsMessage = null
        activityScope.launch {
            lyricsText = withContext(Dispatchers.IO) { userLibraryDao.lyrics(track.sourceId, track.id)?.content }
        }
    }

    private fun saveLyrics(track: Track, content: String) {
        val key = track.key()
        lyricsTrackKey = key
        lyricsText = content.trim().takeIf { it.isNotEmpty() }
        lyricsMessage = null
        activityScope.launch(Dispatchers.IO) {
            userLibraryDao.saveLyrics(
                TrackLyricsEntity(track.sourceId, track.id, content.trim(), "local", System.currentTimeMillis()),
            )
        }
    }

    private fun fetchLyrics(track: Track) {
        if (track.title.isBlank() || track.artist.isBlank()) return
        val key = track.key()
        lyricsTrackKey = key
        lyricsMessage = "Fetching lyrics…"
        activityScope.launch {
            lyricsApi.fetch(track.artist, track.title).fold(
                onSuccess = { content ->
                    if (lyricsTrackKey != key) return@fold
                    saveLyrics(track, content)
                    lyricsMessage = "Lyrics from Lyrics.ovh."
                },
                onFailure = { error -> if (lyricsTrackKey == key) lyricsMessage = error.message ?: "Could not fetch lyrics." },
            )
        }
    }

    private fun handleLastFmAction() {
        when (lastFmConnectionState) {
            LastFmConnectionState.SETUP_REQUIRED -> Unit
            LastFmConnectionState.CONNECTED -> {
                sessionKeyStore.clear()
                lastFmConnectionState = LastFmConnectionState.DISCONNECTED
                lastFmErrorMessage = null
            }

            LastFmConnectionState.AUTHORIZING -> finishLastFmConnection()
            LastFmConnectionState.DISCONNECTED,
            LastFmConnectionState.ERROR,
            -> beginLastFmConnection()
        }
    }

    private fun importLastFmHistory() {
        val session = sessionKeyStore.read() ?: run {
            lastFmHistoryImportMessage = "Connect Last.fm before importing history."
            return
        }
        lastFmHistoryImportMessage = "Importing the latest 1000 Last.fm scrobbles…"
        activityScope.launch {
            when (val result = withContext(Dispatchers.IO) { lastFmApi.recentHistory(session.username) }) {
                is LastFmResult.Failure -> lastFmHistoryImportMessage = result.message
                is LastFmResult.Success -> {
                    val imported = withContext(Dispatchers.IO) {
                        val existing = userLibraryDao.historySnapshot().map { entry -> entry.asListeningEvent() }
                        val candidates = result.value.map { track -> track.asImportedListeningEvent() }
                        val newEvents = newListeningEventsForImport(existing, candidates)
                        newEvents.forEach { event -> userLibraryDao.addHistory(event.asHistoryEntry()) }
                        userLibraryDao.ensureLocalProfile("Relay", System.currentTimeMillis())
                        userLibraryDao.localProfile()?.let { profile ->
                            userLibraryDao.saveLocalProfile(profile.copy(lastFmUsername = session.username))
                        }
                        newEvents.size
                    }
                    lastFmHistoryImportMessage = if (imported == 0) {
                        "No new Last.fm scrobbles to import."
                    } else {
                        "Imported $imported Last.fm scrobbles into local history."
                    }
                }
            }
        }
    }

    private fun saveLocalProfileName(name: String) {
        val displayName = name.trim().take(64)
        if (displayName.isBlank()) return
        activityScope.launch(Dispatchers.IO) {
            userLibraryDao.ensureLocalProfile("Relay", System.currentTimeMillis())
            userLibraryDao.localProfile()?.let { profile -> userLibraryDao.saveLocalProfile(profile.copy(displayName = displayName)) }
        }
    }

    private fun unlinkLastFmHistory(removeImportedEvents: Boolean) {
        activityScope.launch {
            withContext(Dispatchers.IO) {
                userLibraryDao.localProfile()?.let { profile ->
                    userLibraryDao.saveLocalProfile(profile.copy(lastFmUsername = null))
                }
                if (removeImportedEvents) userLibraryDao.deleteHistoryByOrigin(ListeningOrigin.LASTFM_IMPORT.name)
            }
            lastFmHistoryImportMessage = if (removeImportedEvents) {
                "Last.fm association and imported history removed from this device."
            } else {
                "Last.fm association removed. Imported listening history remains local."
            }
        }
    }

    private fun createAlbumChart(range: InsightsRange, limit: Int) {
        activityScope.launch(Dispatchers.IO) {
            val spec = AlbumChartSpec(
                id = "chart_${UUID.randomUUID().toString().replace("-", "")}",
                range = range,
                limit = limit.coerceIn(1, 16),
                createdAtEpochMs = System.currentTimeMillis(),
            )
            userLibraryDao.saveAlbumChartSpec(
                AlbumChartSpecEntity(spec.id, spec.range.name, spec.metric.name, spec.limit, spec.createdAtEpochMs),
            )
        }
    }

    private fun removeAlbumChart(id: String) {
        activityScope.launch(Dispatchers.IO) { userLibraryDao.deleteAlbumChartSpec(id) }
    }

    private fun exportAlbumChart(spec: AlbumChartSpec) {
        activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val entries = albumChartEntries(playHistory.map { it.asListeningEvent() }, spec, System.currentTimeMillis())
                    AlbumChartExporter.write(this@MainActivity, spec, entries)
                }
            }
            result.onSuccess { uri ->
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "Share album chart",
                    ),
                )
            }.onFailure {
                Toast.makeText(this@MainActivity, "Could not create album chart image.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun beginLastFmConnection() {
        lastFmConnectionState = LastFmConnectionState.AUTHORIZING
        lastFmErrorMessage = null
        authorizationToken = null
        activityScope.launch {
            when (val result = withContext(Dispatchers.IO) { lastFmApi.requestToken() }) {
                is LastFmResult.Failure -> {
                    lastFmErrorMessage = result.message
                    lastFmConnectionState = LastFmConnectionState.ERROR
                }
                is LastFmResult.Success -> {
                    authorizationToken = result.value
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, lastFmApi.authorizationUrl(result.value).toUri()))
                    }.onFailure {
                        lastFmErrorMessage = "Could not open a browser for Last.fm authorization."
                        lastFmConnectionState = LastFmConnectionState.ERROR
                    }
                }
            }
        }
    }

    private fun finishLastFmConnection() {
        val token = authorizationToken ?: run {
            lastFmConnectionState = LastFmConnectionState.ERROR
            return
        }
        activityScope.launch {
            when (val result = withContext(Dispatchers.IO) { lastFmApi.createSession(token) }) {
                is LastFmResult.Failure -> {
                    lastFmErrorMessage = result.message
                    lastFmConnectionState = LastFmConnectionState.ERROR
                }
                is LastFmResult.Success -> {
                    sessionKeyStore.save(result.value)
                    authorizationToken = null
                    lastFmErrorMessage = null
                    lastFmConnectionState = LastFmConnectionState.CONNECTED
                }
            }
        }
    }

    private fun debugScrobble() {
        startService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.ACTION_DEBUG_SCROBBLE))
        Toast.makeText(this, "Debug scrobble requested.", Toast.LENGTH_SHORT).show()
    }

    private fun ListeningHistoryEntity.asListeningEvent() = ListeningEvent(
        sourceId = sourceId,
        trackId = trackId,
        playedAtEpochMs = playedAtEpochMs,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        origin = ListeningOrigin.entries.firstOrNull { it.name == origin } ?: ListeningOrigin.LOCAL,
        identityFingerprint = identityFingerprint,
    )

    private fun LastFmHistoryTrack.asImportedListeningEvent(): ListeningEvent {
        val event = ListeningEvent(
            sourceId = LASTFM_IMPORT_SOURCE_ID,
            trackId = recordingId ?: "$artist\u0000$title",
            playedAtEpochMs = playedAtEpochMs,
            title = title,
            artist = artist,
            album = album,
            origin = ListeningOrigin.LASTFM_IMPORT,
            identityFingerprint = recordingId,
        )
        return event.copy(identityFingerprint = event.identityFingerprint ?: event.effectiveFingerprint())
    }

    private fun ListeningEvent.asHistoryEntry() = ListeningHistoryEntity(
        sourceId = sourceId,
        trackId = trackId,
        playedAtEpochMs = playedAtEpochMs,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        origin = origin.name,
        identityFingerprint = identityFingerprint?.take(512),
    )

    private fun FavoriteTrackEntity.key(): String = "$sourceId\u0000$trackId"
    private fun TrackFlagsEntity.key(): String = "$sourceId\u0000$trackId"

    private fun Track.key(): String = "${sourceId}\u0000$id"

    private companion object {
        const val ACTION_EXTENSION_INSTALL_STATUS = "dev.relay.music.EXTENSION_INSTALL_STATUS"
        const val QUEUE_SAVE_INTERVAL_MS = 15_000L
        const val MAX_SEED_IMAGE_BYTES = 32 * 1024 * 1024
        const val LASTFM_IMPORT_SOURCE_ID = "lastfm"
    }
}

internal fun RelaySettings.withRepositoryThemePack(
    repositoryId: String,
    entry: ExtensionCatalogEntry,
    pack: dev.relay.music.extension.ThemePack,
): RelaySettings {
    require(entry.kind == ExtensionKind.THEME_PACK && entry.id == pack.id && entry.validate() == null) {
        "Theme pack does not match its catalog entry."
    }
    require(installedExtensions.none {
        it.kind == ExtensionKind.THEME_PACK && it.extensionId == entry.id && it.repositoryId != repositoryId
    }) { "A theme pack with this ID is installed from another repository." }
    return copy(
        installedExtensions = installedExtensions.filterNot {
            it.repositoryId == repositoryId && it.extensionId == entry.id
        } + entry.asInstalled(repositoryId),
        themePacks = dev.relay.music.extension.mergedThemePacks(themePacks.filterNot { it.id == pack.id } + pack),
        activeThemePackId = pack.id,
    )
}

internal fun RelaySettings.withoutThemePack(themePackId: String): RelaySettings = copy(
    themePacks = themePacks.filterNot { it.id == themePackId },
    installedExtensions = installedExtensions.filterNot {
        it.kind == ExtensionKind.THEME_PACK && it.extensionId == themePackId
    },
    activeThemePackId = activeThemePackId.takeUnless { it == themePackId },
)
