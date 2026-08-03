package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.relay.music.extension.EXTENSION_API_VERSION
import dev.relay.music.extension.ExtensionKind
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.ExtensionDownloadProgress
import dev.relay.music.extension.ExtensionSourceResults
import dev.relay.music.extension.InstalledExtension
import dev.relay.music.extension.RemoteTrackDownloadProgress
import dev.relay.music.extension.RepositoryDescriptor
import dev.relay.music.extension.SourceBrowseRequest
import dev.relay.music.extension.SourceListing
import dev.relay.music.extension.SourceSearchField
import dev.relay.music.extension.SourceSettingDefinition
import dev.relay.music.extension.SourceSettingType
import dev.relay.music.extension.isCompatible
import dev.relay.music.model.Playlist
import dev.relay.music.model.Track
import dev.relay.music.settings.RelaySettings
import dev.relay.music.update.AvailableComponent
import dev.relay.music.update.ComponentIdentity
import dev.relay.music.update.ComponentUpdate
import dev.relay.music.update.ComponentUpdateStatus
import dev.relay.music.update.InstalledComponent
import dev.relay.music.update.findComponentUpdates
import dev.relay.music.update.toUpdatableComponentKind
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayType
import kotlinx.coroutines.delay

internal enum class ExtensionsSubmenu { REPOSITORIES, SOURCE_SEARCH, DETAILS }

internal enum class ExtensionsTab { SOURCES, INSTALLED, AVAILABLE, UPDATES }

internal data class CatalogExtension(
    val repository: RepositoryDescriptor,
    val entry: ExtensionCatalogEntry,
)

private fun InstalledExtension.asInstalledComponent() = InstalledComponent(
    identity = ComponentIdentity(kind.toUpdatableComponentKind(), repositoryId, extensionId),
    version = version,
)

private fun CatalogExtension.asAvailableComponent() = AvailableComponent(
    identity = ComponentIdentity(entry.kind.toUpdatableComponentKind(), repository.id, entry.id),
    version = entry.version,
    isCompatible = entry.isCompatible,
    payload = this,
)

@Composable
internal fun ExtensionsScreen(
    settings: RelaySettings,
    repositoryCatalogs: Map<String, List<ExtensionCatalogEntry>>,
    repositoryMessages: Map<String, String>,
    onAddTrustedRepository: (RepositoryDescriptor) -> Unit,
    onImportRepository: (String) -> Unit,
    onRemoveTrustedRepository: (String) -> Unit,
    onRefreshRepository: (RepositoryDescriptor) -> Unit,
    onInstallExtension: (RepositoryDescriptor, ExtensionCatalogEntry) -> Unit,
    onSetExtensionEnabled: (InstalledExtension, Boolean) -> Unit,
    onUninstallExtension: (InstalledExtension) -> Unit,
    importedRepository: RepositoryDescriptor?,
    repositoryImportMessage: String?,
    repositoryImportVersion: Long,
    submenu: ExtensionsSubmenu?,
    selectedTab: ExtensionsTab,
    selectedExtension: CatalogExtension?,
    onSubmenuChange: (ExtensionsSubmenu?) -> Unit,
    onTabChange: (ExtensionsTab) -> Unit,
    onExtensionSelected: (CatalogExtension) -> Unit,
    extensionSourceResults: List<ExtensionSourceResults>,
    extensionSourceMessage: String?,
    sourceSettingSchemas: Map<String, List<SourceSettingDefinition>>,
    onLoadSourceSettings: (String) -> Unit,
    onSourceSettingsChange: (String, Map<String, String>) -> Unit,
    onSearchExtensionSources: (SourceBrowseRequest) -> Unit,
    onTrackSelected: (Track) -> Unit,
    extensionDownload: ExtensionDownloadProgress?,
    remoteTrackDownload: RemoteTrackDownloadProgress?,
    downloadedRemoteTrackKeys: Set<String>,
    onRefreshExtensions: () -> Unit,
    onDownloadRemoteTrack: (Track) -> Unit,
    playlists: List<Playlist>,
    onAddToPlaylist: (Long, Track) -> Unit,
    onCreateAndAddToPlaylist: (String, Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    browsedExtensionId: String?,
    onBrowseExtension: (String) -> Unit,
    onOpenSupportUrl: (String) -> Unit,
) {
    when (submenu) {
        ExtensionsSubmenu.REPOSITORIES -> RepositorySettings(
            settings = settings,
            repositoryCatalogs = repositoryCatalogs,
            repositoryMessages = repositoryMessages,
            onAddTrustedRepository = onAddTrustedRepository,
            onImportRepository = onImportRepository,
            onRemoveTrustedRepository = onRemoveTrustedRepository,
            importedRepository = importedRepository,
            repositoryImportMessage = repositoryImportMessage,
            repositoryImportVersion = repositoryImportVersion,
            onRefreshRepository = onRefreshRepository,
        )
        ExtensionsSubmenu.SOURCE_SEARCH -> SourceSearchScreen(
            results = extensionSourceResults,
            message = extensionSourceMessage,
            extensionId = browsedExtensionId,
            onSearch = onSearchExtensionSources,
            onTrackSelected = onTrackSelected,
            remoteTrackDownload = remoteTrackDownload,
            downloadedRemoteTrackKeys = downloadedRemoteTrackKeys,
            onDownloadTrack = onDownloadRemoteTrack,
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            onCreateAndAddToPlaylist = onCreateAndAddToPlaylist,
            onPlayNext = onPlayNext,
            onEnqueue = onEnqueue,
        )
        ExtensionsSubmenu.DETAILS -> selectedExtension?.let { extension ->
            ExtensionDetailsScreen(
                extension = extension,
                installed = settings.installedExtensions.firstOrNull {
                    it.repositoryId == extension.repository.id && it.extensionId == extension.entry.id
                },
                download = extensionDownload?.takeIf { it.extensionId == extension.entry.id },
                onInstallExtension = onInstallExtension,
                onSetExtensionEnabled = onSetExtensionEnabled,
                onUninstallExtension = onUninstallExtension,
                onBrowseExtension = onBrowseExtension,
                onOpenSupportUrl = onOpenSupportUrl,
                settingSchema = sourceSettingSchemas[extension.entry.id],
                settingValues = settings.sourceSettings[extension.entry.id].orEmpty(),
                onLoadSourceSettings = onLoadSourceSettings,
                onSourceSettingsChange = onSourceSettingsChange,
            )
        }
        null -> ExtensionCatalogScreen(
            settings = settings,
            repositoryCatalogs = repositoryCatalogs,
            selectedTab = selectedTab,
            onTabChange = onTabChange,
            onOpenRepositories = { onSubmenuChange(ExtensionsSubmenu.REPOSITORIES) },
            onOpenSourceSearch = { onSubmenuChange(ExtensionsSubmenu.SOURCE_SEARCH) },
            onExtensionSelected = onExtensionSelected,
            repositoryMessages = repositoryMessages,
            onRefreshAll = onRefreshExtensions,
            onUninstallExtension = onUninstallExtension,
            onInstallExtension = onInstallExtension,
            onSetExtensionEnabled = onSetExtensionEnabled,
            extensionDownload = extensionDownload,
            onBrowseExtension = onBrowseExtension,
        )
    }
}

@Composable
internal fun ExtensionCatalogScreen(
    settings: RelaySettings,
    repositoryCatalogs: Map<String, List<ExtensionCatalogEntry>>,
    selectedTab: ExtensionsTab,
    onTabChange: (ExtensionsTab) -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenSourceSearch: () -> Unit,
    onExtensionSelected: (CatalogExtension) -> Unit,
    repositoryMessages: Map<String, String>,
    onRefreshAll: () -> Unit,
    onUninstallExtension: (InstalledExtension) -> Unit,
    onInstallExtension: (RepositoryDescriptor, ExtensionCatalogEntry) -> Unit,
    onSetExtensionEnabled: (InstalledExtension, Boolean) -> Unit,
    extensionDownload: ExtensionDownloadProgress?,
    onBrowseExtension: (String) -> Unit,
) {
    var extensionQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val refreshThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val refreshing = repositoryMessages.values.any { it == "REFRESHING…" }
    val catalogExtensions = settings.trustedRepositories.flatMap { repository ->
        repositoryCatalogs[repository.id].orEmpty().map { entry -> CatalogExtension(repository, entry) }
    }
    val matchingExtensions = catalogExtensions.filter { catalogExtension ->
        extensionQuery.isBlank() || listOf(
            catalogExtension.entry.name,
            catalogExtension.entry.id,
            catalogExtension.repository.name,
        ).any { value -> value.contains(extensionQuery.trim(), ignoreCase = true) }
    }
    val componentUpdates = findComponentUpdates(
        installed = settings.installedExtensions.map(InstalledExtension::asInstalledComponent),
        candidates = catalogExtensions.map(CatalogExtension::asAvailableComponent),
    )
    val updatesByIdentity = componentUpdates.associateBy { it.candidate.identity }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(refreshing, refreshThreshold) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount ->
                        if (!refreshing && scrollState.value == 0 && amount > 0) pullDistance += amount
                    },
                    onDragEnd = {
                        if (pullDistance >= refreshThreshold) onRefreshAll()
                        pullDistance = 0f
                    },
                    onDragCancel = { pullDistance = 0f },
                )
            }
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ExtensionsTab.entries.forEach { tab ->
                BasicText(
                    text = tab.name,
                    style = RelayType.Utility.copy(
                        color = if (tab == selectedTab) RelayColors.Signal else RelayColors.Muted,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .border(1.dp, RelayColors.Line)
                        .semantics { contentDescription = "Show ${tab.name.lowercase()} extensions" }
                        .clickable(role = Role.Tab, onClick = { onTabChange(tab) })
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransportAction(
                label = "REPOSITORIES",
                description = "Manage trusted extension repositories",
                enabled = true,
                onClick = onOpenRepositories,
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = "REFRESH",
                description = "Refresh all trusted extension repositories",
                enabled = !refreshing,
                onClick = onRefreshAll,
                modifier = Modifier.weight(1f),
            )
        }
        if (pullDistance > 0f || refreshing) {
            BasicText(
                text = if (refreshing) "REFRESHING…" else "RELEASE TO REFRESH",
                style = RelayType.Utility.copy(color = RelayColors.Muted),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        when (selectedTab) {
            ExtensionsTab.SOURCES -> {
                TransportAction(
                    label = "GLOBAL SEARCH",
                    description = "Search every enabled source at once",
                    enabled = true,
                    onClick = onOpenSourceSearch,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val sources = settings.installedExtensions.filter { it.enabled && it.kind == ExtensionKind.SOURCE }
                if (sources.isEmpty()) {
                    BasicText(
                        "No enabled source extensions. Install one from Available.",
                        style = RelayType.Metadata,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    sources.forEach { installed ->
                        val name = installed.catalogSnapshot?.name ?: catalogExtensions.firstOrNull {
                            it.repository.id == installed.repositoryId && it.entry.id == installed.extensionId
                        }?.entry?.name ?: installed.extensionId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(1.dp, RelayColors.Line)
                                .semantics { contentDescription = "Browse $name" }
                                .clickable(role = Role.Button) { onBrowseExtension(installed.extensionId) }
                                .padding(12.dp),
                        ) {
                            BasicText(name, style = RelayType.Track)
                            BasicText(
                                "${installed.version} · BROWSE",
                                style = RelayType.Metadata,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            ExtensionsTab.INSTALLED -> {
                if (settings.installedExtensions.isEmpty()) {
                    BasicText(
                        "No extensions are installed.",
                        style = RelayType.Metadata,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    settings.installedExtensions.forEach { installed ->
                        InstalledExtensionRow(
                            installed = installed,
                            catalogExtension = catalogExtensions.firstOrNull {
                                it.repository.id == installed.repositoryId && it.entry.id == installed.extensionId &&
                                    it.entry.version == installed.version
                            },
                            componentUpdate = componentUpdates.firstOrNull {
                                it.installed.identity == installed.asInstalledComponent().identity
                            },
                            orphaned = settings.trustedRepositories.none { it.id == installed.repositoryId },
                            download = extensionDownload?.takeIf { it.extensionId == installed.extensionId },
                            onOpen = onExtensionSelected,
                            onUpdate = { extension -> onInstallExtension(extension.repository, extension.entry) },
                            onSetEnabled = onSetExtensionEnabled,
                            onUninstall = onUninstallExtension,
                        )
                    }
                }
            }
            ExtensionsTab.AVAILABLE -> {
                MetadataField("SEARCH EXTENSIONS", extensionQuery, { extensionQuery = it })
                if (settings.trustedRepositories.isEmpty()) {
                    BasicText(
                        "Add a trusted HTTPS repository to browse its signed catalog.",
                        style = RelayType.Metadata,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else if (matchingExtensions.isEmpty()) {
                    BasicText(
                        "No matching extensions. Refresh a repository to retrieve its current catalog.",
                        style = RelayType.Metadata,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    matchingExtensions.sortedByDescending { it.entry.isCompatible }.forEach { extension ->
                        val installed = settings.installedExtensions.firstOrNull {
                            it.repositoryId == extension.repository.id && it.extensionId == extension.entry.id
                        }
                        val update = updatesByIdentity[extension.asAvailableComponent().identity]
                        CatalogExtensionRow(
                            extension = extension,
                            onClick = onExtensionSelected,
                            badge = when {
                                installed == null -> null
                                update == null -> "INSTALLED"
                                else -> update.status.displayName()
                            },
                        )
                    }
                }
            }
            ExtensionsTab.UPDATES -> if (componentUpdates.isEmpty()) {
                BasicText("No updates found.", style = RelayType.Metadata, modifier = Modifier.padding(top = 16.dp))
            } else {
                componentUpdates.forEach { update ->
                    val extension = update.candidate.payload
                    CatalogExtensionRow(
                        extension = extension,
                        onClick = onExtensionSelected,
                        download = extensionDownload?.takeIf { it.extensionId == extension.entry.id },
                        badge = update.status.displayName(),
                        actionLabel = "UPDATE".takeIf { update.isActionable },
                        onAction = { onInstallExtension(extension.repository, extension.entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogExtensionRow(
    extension: CatalogExtension,
    onClick: (CatalogExtension) -> Unit,
    badge: String? = null,
    download: ExtensionDownloadProgress? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, RelayColors.Line)
            .semantics { contentDescription = "Inspect ${extension.entry.name}" }
            .clickable(role = Role.Button) { onClick(extension) }
            .padding(12.dp),
    ) {
        BasicText(extension.entry.name, style = RelayType.Track)
        BasicText(
            listOfNotNull(
                extension.entry.version,
                extension.entry.kind.name.replace('_', ' '),
                extension.repository.name,
                "INCOMPATIBLE".takeUnless { extension.entry.isCompatible },
                badge,
            ).joinToString(" · "),
            style = RelayType.Metadata,
            modifier = Modifier.padding(top = 4.dp),
        )
        when {
            download != null -> ExtensionDownloadBar(download)
            actionLabel != null && onAction != null -> TransportAction(
                label = actionLabel,
                description = "$actionLabel ${extension.entry.name}",
                enabled = true,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun InstalledExtensionRow(
    installed: InstalledExtension,
    catalogExtension: CatalogExtension?,
    componentUpdate: ComponentUpdate<CatalogExtension>?,
    orphaned: Boolean,
    download: ExtensionDownloadProgress?,
    onOpen: (CatalogExtension) -> Unit,
    onUpdate: (CatalogExtension) -> Unit,
    onSetEnabled: (InstalledExtension, Boolean) -> Unit,
    onUninstall: (InstalledExtension) -> Unit,
) {
    val update = componentUpdate?.takeIf { it.isActionable }?.candidate?.payload
    val isThemePack = installed.kind == ExtensionKind.THEME_PACK
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, RelayColors.Line)
            .then(
                if (catalogExtension == null) Modifier else Modifier
                    .semantics { contentDescription = "Inspect ${catalogExtension.entry.name}" }
                    .clickable(role = Role.Button) { onOpen(catalogExtension) },
            )
            .padding(12.dp),
    ) {
        BasicText(installed.catalogSnapshot?.name ?: catalogExtension?.entry?.name ?: installed.extensionId, style = RelayType.Track)
        BasicText(
            listOfNotNull(
                installed.version,
                if (isThemePack) "INSTALLED" else if (installed.enabled) "ENABLED" else "DISABLED",
                "ORPHANED".takeIf { orphaned },
                componentUpdate?.candidate?.version?.let { "${componentUpdate.status.displayName()} $it" },
            ).joinToString(" · "),
            style = RelayType.Metadata,
            modifier = Modifier.padding(top = 4.dp),
        )
        installed.disabledReason?.takeUnless { isThemePack }?.let { reason ->
            BasicText(reason, style = RelayType.Metadata, modifier = Modifier.padding(top = 4.dp))
        }
        if (orphaned) {
            BasicText(
                "Repository removed. This extension will not receive updates.",
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!isThemePack) TransportAction(
            label = if (installed.enabled) "DISABLE" else "ENABLE",
            description = "${if (installed.enabled) "Disable" else "Enable"} ${installed.catalogSnapshot?.name ?: installed.extensionId}",
            enabled = download == null,
            onClick = { onSetEnabled(installed, !installed.enabled) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (orphaned || isThemePack) TransportAction(
            label = if (isThemePack) "REMOVE" else "UNINSTALL",
            description = if (isThemePack) "Remove theme pack ${installed.extensionId}" else "Uninstall orphaned extension ${installed.extensionId}",
            enabled = download == null && (isThemePack || installed.androidPackageName != null),
            onClick = { onUninstall(installed) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        when {
            download != null -> ExtensionDownloadBar(download)
            update != null -> TransportAction(
                label = "UPDATE",
                description = "Update ${update.entry.name} to ${update.entry.version}",
                enabled = true,
                onClick = { onUpdate(update) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

private fun ComponentUpdateStatus.displayName(): String = when (this) {
    ComponentUpdateStatus.CURRENT -> "INSTALLED"
    ComponentUpdateStatus.UPDATE_AVAILABLE -> "UPDATE AVAILABLE"
    ComponentUpdateStatus.VERSION_CHANGE -> "VERSION CHANGE"
    ComponentUpdateStatus.DOWNGRADE -> "OLDER VERSION"
    ComponentUpdateStatus.INCOMPATIBLE -> "INCOMPATIBLE"
}

@Composable
private fun ExtensionDetailsScreen(
    extension: CatalogExtension,
    installed: InstalledExtension?,
    download: ExtensionDownloadProgress?,
    onInstallExtension: (RepositoryDescriptor, ExtensionCatalogEntry) -> Unit,
    onSetExtensionEnabled: (InstalledExtension, Boolean) -> Unit,
    onUninstallExtension: (InstalledExtension) -> Unit,
    onBrowseExtension: (String) -> Unit,
    onOpenSupportUrl: (String) -> Unit,
    settingSchema: List<SourceSettingDefinition>?,
    settingValues: Map<String, String>,
    onLoadSourceSettings: (String) -> Unit,
    onSourceSettingsChange: (String, Map<String, String>) -> Unit,
) {
    val permissions = extension.entry.permissions
        .joinToString(" · ") { permission -> permission.name.replace('_', ' ') }
        .ifBlank { "NONE" }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        BasicText(extension.entry.name, style = RelayType.Title)
        BasicText(extension.entry.version, style = RelayType.Track, modifier = Modifier.padding(top = 8.dp))
        ExtensionDetail("TYPE", extension.entry.kind.name.replace('_', ' '))
        ExtensionDetail("REPOSITORY", extension.repository.name)
        ExtensionDetail("API", "${extension.entry.api.minimum}–${extension.entry.api.maximum}")
        ExtensionDetail("PERMISSIONS", permissions)
        ExtensionDetail("SIGNER", "P-256 key trusted for ${extension.repository.name}")
        extension.entry.androidPackageName?.let { ExtensionDetail("ANDROID PACKAGE", it) }
        extension.entry.androidSigningCertificateSha256?.let { ExtensionDetail("APK SIGNER", it) }
        ExtensionDetail("ARTIFACT", extension.entry.artifactUrl)
        extension.entry.supportUrl?.let { supportUrl ->
            TransportAction(
                label = "SUPPORT",
                description = "Open the support page for ${extension.entry.name}",
                enabled = true,
                onClick = { onOpenSupportUrl(supportUrl) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
        installed?.let {
            val isThemePack = it.kind == ExtensionKind.THEME_PACK
            ExtensionDetail("STATUS", if (isThemePack || it.enabled) "INSTALLED" else "DISABLED — ${it.disabledReason}")
            if (it.enabled && it.kind == dev.relay.music.extension.ExtensionKind.SOURCE) {
                TransportAction(
                    label = "BROWSE MUSIC",
                    description = "Browse music from ${extension.entry.name}",
                    enabled = download == null,
                    onClick = { onBrowseExtension(extension.entry.id) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
            if (!isThemePack) TransportAction(
                label = if (it.enabled) "DISABLE" else "ENABLE",
                description = if (it.enabled) "Disable ${extension.entry.name}" else "Enable ${extension.entry.name} after verification",
                enabled = download == null,
                onClick = { onSetExtensionEnabled(it, !it.enabled) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            TransportAction(
                label = if (isThemePack) "REMOVE" else "UNINSTALL",
                description = if (isThemePack) "Remove ${extension.entry.name} from Relay" else "Uninstall ${extension.entry.name} with Android confirmation",
                enabled = download == null && (isThemePack || it.androidPackageName != null),
                onClick = { onUninstallExtension(it) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (it.enabled && it.kind == ExtensionKind.SOURCE) {
                SourceSettingsSection(
                    extensionId = extension.entry.id,
                    schema = settingSchema,
                    values = settingValues,
                    onLoad = onLoadSourceSettings,
                    onChange = onSourceSettingsChange,
                )
            }
        }
        if (!extension.entry.isCompatible) {
            BasicText(
                "INCOMPATIBLE — requires extension API ${extension.entry.api.minimum}–${extension.entry.api.maximum}; " +
                    "this Relay supports $EXTENSION_API_VERSION.",
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else if (extension.entry.kind == ExtensionKind.THEME_PACK) {
            download?.let { progress -> ExtensionDownloadBar(progress) }
            val installedCurrent = installed?.version == extension.entry.version
            val actionLabel = when {
                download != null -> "DOWNLOADING"
                installedCurrent -> "INSTALLED"
                installed != null -> "UPDATE THEME"
                else -> "INSTALL THEME"
            }
            TransportAction(
                actionLabel,
                "Download, verify, and apply the data-only theme pack ${extension.entry.name}",
                download == null && !installedCurrent,
                { onInstallExtension(extension.repository, extension.entry) },
                Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        } else if (extension.entry.androidPackageName != null) {
            download?.let { progress -> ExtensionDownloadBar(progress) }
            val actionLabel = when {
                download != null -> "DOWNLOADING"
                installed?.version == extension.entry.version && installed.enabled -> "INSTALLED"
                installed != null -> "UPDATE APK"
                else -> "INSTALL APK"
            }
            TransportAction(
                actionLabel,
                "Download and verify ${extension.entry.name} before Android installation confirmation",
                download == null && !(installed?.version == extension.entry.version && installed.enabled),
                { onInstallExtension(extension.repository, extension.entry) },
                Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        } else {
            BasicText(
                "This catalog artifact is not an Android APK.",
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * Schema-driven source preferences, Mihon-style: the source declares fields, Relay renders and
 * stores them. Values persist per extension and are handed back on every source load.
 */
@Composable
private fun SourceSettingsSection(
    extensionId: String,
    schema: List<SourceSettingDefinition>?,
    values: Map<String, String>,
    onLoad: (String) -> Unit,
    onChange: (String, Map<String, String>) -> Unit,
) {
    LaunchedEffect(extensionId) { onLoad(extensionId) }
    when {
        schema == null -> BasicText(
            "LOADING SOURCE SETTINGS…",
            style = RelayType.Utility.copy(color = RelayColors.Muted),
            modifier = Modifier.padding(top = 16.dp),
        )
        schema.isEmpty() -> Unit
        else -> {
            BasicText(
                "SOURCE SETTINGS",
                style = RelayType.Utility.copy(color = RelayColors.Muted),
                modifier = Modifier.padding(top = 24.dp),
            )
            schema.forEach { definition ->
                val current = values[definition.id] ?: definition.defaultValue
                when (definition.type) {
                    SourceSettingType.TEXT -> MetadataField(definition.label.uppercase(), current) { value ->
                        onChange(extensionId, values + (definition.id to value.take(1_024)))
                    }
                    SourceSettingType.TOGGLE -> SettingsChoice(
                        label = definition.label.uppercase(),
                        value = if (current == "true") "ON" else "OFF",
                        description = "Toggle ${definition.label}",
                        onClick = {
                            onChange(extensionId, values + (definition.id to if (current == "true") "false" else "true"))
                        },
                    )
                    SourceSettingType.CHOICE -> SettingsChoice(
                        label = definition.label.uppercase(),
                        value = current.ifEmpty { definition.choices.firstOrNull().orEmpty() }.uppercase(),
                        description = "Cycle ${definition.label}",
                        onClick = {
                            val index = definition.choices.indexOf(current.ifEmpty { definition.choices.firstOrNull() })
                            val next = definition.choices[(index + 1).mod(definition.choices.size)]
                            onChange(extensionId, values + (definition.id to next))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionDownloadBar(progress: ExtensionDownloadProgress) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        BasicText(
            "DOWNLOADING ${progress.name} ${(progress.fraction * 100).toInt()}%",
            style = RelayType.Utility.copy(color = RelayColors.Muted),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(RelayColors.Line)
                .padding(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.fraction)
                    .fillMaxHeight()
                    .background(RelayColors.Signal),
            )
        }
    }
}

@Composable
private fun ExtensionDetail(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        BasicText(label, style = RelayType.Utility.copy(color = RelayColors.Muted))
        BasicText(value, style = RelayType.Metadata, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SourceSearchScreen(
    results: List<ExtensionSourceResults>,
    message: String?,
    extensionId: String?,
    onSearch: (SourceBrowseRequest) -> Unit,
    onTrackSelected: (Track) -> Unit,
    remoteTrackDownload: RemoteTrackDownloadProgress?,
    downloadedRemoteTrackKeys: Set<String>,
    onDownloadTrack: (Track) -> Unit,
    playlists: List<Playlist> = emptyList(),
    onAddToPlaylist: (Long, Track) -> Unit = { _, _ -> },
    onCreateAndAddToPlaylist: (String, Track) -> Unit = { _, _ -> },
    onPlayNext: (Track) -> Unit = {},
    onEnqueue: (Track) -> Unit = {},
) {
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var pickerTrack by remember { mutableStateOf<Track?>(null) }
    var query by remember { mutableStateOf("") }
    var field by remember { mutableStateOf(SourceSearchField.ALL) }
    // Selected browse listing: the extension that owns it plus the listing itself.
    var listing by remember { mutableStateOf<Pair<String, SourceListing>?>(null) }
    var expandedSourceIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(query, field, extensionId, listing) {
        delay(300)
        onSearch(
            SourceBrowseRequest(
                query = query,
                field = field,
                extensionId = listing?.first ?: extensionId,
                listingId = listing?.second?.id,
            ),
        )
    }
    LaunchedEffect(results, query, field) {
        if (query.isNotBlank()) expandedSourceIds = results.mapTo(linkedSetOf()) { it.extensionId }
    }
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        MetadataField("SEARCH MUSIC", query, { query = it; listing = null })
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SourceSearchField.entries.forEach { candidate ->
                BasicText(
                    text = candidate.name,
                    style = RelayType.Utility.copy(color = if (candidate == field) RelayColors.Signal else RelayColors.Muted),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .border(1.dp, RelayColors.Line)
                        .clickable(role = Role.Tab) { field = candidate }
                        .padding(horizontal = 6.dp, vertical = 13.dp),
                )
            }
        }
        message?.let { BasicText(it, style = RelayType.Metadata, modifier = Modifier.padding(top = 12.dp)) }
        results.forEach { result ->
            val expanded = result.extensionId in expandedSourceIds
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .border(1.dp, RelayColors.Line),
            ) {
                BasicText(
                    text = result.extensionName,
                    style = RelayType.Track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button) {
                            expandedSourceIds = if (expanded) expandedSourceIds - result.extensionId else expandedSourceIds + result.extensionId
                        }
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                )
                if (expanded) {
                    if (result.listings.isNotEmpty() && query.isBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            result.listings.forEach { candidate ->
                                val selected = listing?.first == result.extensionId && listing?.second?.id == candidate.id
                                BasicText(
                                    text = candidate.name.uppercase(),
                                    style = RelayType.Utility.copy(color = if (selected) RelayColors.Signal else RelayColors.Muted),
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .heightIn(min = 44.dp)
                                        .border(1.dp, if (selected) RelayColors.Signal else RelayColors.Line)
                                        .clickable(role = Role.Tab) {
                                            listing = if (selected) null else result.extensionId to candidate
                                        }
                                        .padding(horizontal = 10.dp, vertical = 13.dp),
                                )
                            }
                        }
                    }
                    result.tracks.forEach { track ->
                        val downloading = remoteTrackDownload?.takeIf {
                            it.sourceId == track.sourceId && it.trackId == track.id
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            track.artworkUri?.let { artwork ->
                                AsyncImage(
                                    model = artwork,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(40.dp)
                                        .border(1.dp, RelayColors.Line),
                                )
                            }
                            BasicText(
                                text = listOfNotNull(
                                    "${track.title} — ${track.artist}",
                                    track.album,
                                    track.durationMs?.let(::formatDuration),
                                ).joinToString(" · "),
                                style = RelayType.Metadata,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClick = { onTrackSelected(track) },
                                        onLongClick = { menuTrack = track },
                                    )
                                    .padding(vertical = 14.dp),
                            )
                            when {
                                trackKey(track) in downloadedRemoteTrackKeys -> BasicText(
                                    "DOWNLOADED",
                                    style = RelayType.Utility.copy(color = RelayColors.Muted),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                                downloading != null -> BasicText(
                                    if (downloading.totalBytes > 0) "${(downloading.fraction * 100).toInt()}%" else "DOWNLOADING",
                                    style = RelayType.Utility.copy(color = RelayColors.Signal),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                                else -> TransportAction(
                                    "DOWNLOAD",
                                    "Download ${track.title} for offline playback",
                                    true,
                                    { onDownloadTrack(track) },
                                    Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    if (result.hasNextPage && result.tracks.isNotEmpty()) {
                        BasicText(
                            text = "LOAD MORE",
                            style = RelayType.Utility.copy(color = RelayColors.Signal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) {
                                    onSearch(
                                        SourceBrowseRequest(
                                            query = query,
                                            field = field,
                                            extensionId = result.extensionId,
                                            listingId = listing?.takeIf { it.first == result.extensionId }?.second?.id,
                                            page = result.page + 1,
                                        ),
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 15.dp),
                        )
                    }
                }
            }
        }
    }
        menuTrack?.let { track ->
            SourceTrackOptionsMenu(
                track = track,
                onAddToPlaylist = { pickerTrack = track; menuTrack = null },
                onPlayNext = { onPlayNext(track); menuTrack = null },
                onEnqueue = { onEnqueue(track); menuTrack = null },
                onDismiss = { menuTrack = null },
            )
        }
        pickerTrack?.let { track ->
            PlaylistPickerOverlay(
                track = track,
                playlists = playlists,
                onPick = { playlistId -> onAddToPlaylist(playlistId, track); pickerTrack = null },
                onCreateAndAdd = { name -> onCreateAndAddToPlaylist(name, track); pickerTrack = null },
                onDismiss = { pickerTrack = null },
            )
        }
    }
}

@Composable
private fun BoxScope.SourceTrackOptionsMenu(
    track: Track,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(RelayColors.Panel)
            .border(1.dp, RelayColors.Line)
            .padding(12.dp),
    ) {
        BasicText(track.title, style = RelayType.Track)
        BasicText(track.artist, style = RelayType.Metadata, modifier = Modifier.padding(top = 4.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction("PLAYLIST", "Add ${track.title} to a playlist", true, onAddToPlaylist, Modifier.weight(1f))
            TransportAction("PLAY NEXT", "Play ${track.title} after the current track", true, onPlayNext, Modifier.weight(1f))
            TransportAction("QUEUE", "Add ${track.title} to the end of the queue", true, onEnqueue, Modifier.weight(1f))
        }
        TransportAction("CLOSE", "Close track options", true, onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}
