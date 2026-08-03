package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import dev.relay.music.model.OfflineDownload
import dev.relay.music.model.Track
import dev.relay.music.extension.RepositoryDescriptor
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.repositoryDescriptorUrl
import dev.relay.music.extension.validate
import dev.relay.music.lastfm.LastFmConnectionState
import dev.relay.music.playback.ShuffleGrouping
import dev.relay.music.playback.MissingShuffleValue
import dev.relay.music.playback.ShuffleProfile
import dev.relay.music.playback.newShuffleProfile
import dev.relay.music.settings.RelaySettings
import dev.relay.music.wallpaper.WallpaperArtworkFit
import dev.relay.music.wallpaper.ArtworkFilter
import dev.relay.music.wallpaper.WallpaperAnchor
import dev.relay.music.wallpaper.WallpaperCanvasBackground
import dev.relay.music.wallpaper.WallpaperElement
import dev.relay.music.wallpaper.WallpaperFont
import dev.relay.music.wallpaper.WallpaperPageOffset
import dev.relay.music.wallpaper.WallpaperPreset
import dev.relay.music.wallpaper.WallpaperTextAlignment
import dev.relay.music.wallpaper.WallpaperVisibility
import dev.relay.music.wallpaper.WallpaperVisualizer
import dev.relay.music.wallpaper.defaultWallpaperElement
import dev.relay.music.wallpaper.label
import dev.relay.music.wallpaper.wallpaperElementBounds
import dev.relay.music.wallpaper.withLayout
import dev.relay.music.settings.activeShuffleProfile
import dev.relay.music.settings.withActiveShuffleProfile
import dev.relay.music.settings.withActiveShuffleProfileId
import dev.relay.music.settings.BackupSchedule
import dev.relay.music.settings.EqualizerPreset
import dev.relay.music.settings.equalizerPresetLevels
import dev.relay.music.settings.normalizedEqualizerBands
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.asThemeColor
import dev.relay.music.ui.RelayType
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(
    settings: RelaySettings,
    onResumeQueueChange: (Boolean) -> Unit,
    onChooseStorageRoot: () -> Unit,
    onBackupExport: () -> Unit,
    onBackupImport: () -> Unit,
    onSyncExport: (() -> Unit)? = null,
    onSyncImport: (() -> Unit)? = null,
    syncConflicts: List<SyncConflictNotice> = emptyList(),
    onDismissSyncConflict: (String) -> Unit = {},
    onUseReceivedSyncConflict: (String) -> Unit = {},
    lanSync: LanSyncUiState = LanSyncUiState(),
    pairedDevices: List<PairedDeviceUi> = emptyList(),
    playTogether: PlayTogetherUiState = PlayTogetherUiState(),
    onStartLanSyncHost: () -> Unit = {},
    onJoinLanSync: (String) -> Unit = {},
    onConfirmLanSync: (Boolean) -> Unit = {},
    onCancelLanSync: () -> Unit = {},
    onUnpairDevice: (String) -> Unit = {},
    onSelectMusicTransfer: () -> Unit = {},
    onImportMusicTransfer: () -> Unit = {},
    onPrepareLanMusicTransfer: () -> Unit = {},
    onStartPlayTogetherHost: () -> Unit = {},
    onJoinPlayTogether: (String) -> Unit = {},
    onConfirmPlayTogether: (Boolean) -> Unit = {},
    onLeavePlayTogether: () -> Unit = {},
    onResyncPlayTogether: () -> Unit = {},
    onBackupScheduleChange: (BackupSchedule) -> Unit,
    onAutoBackupExpiryChange: (Int) -> Unit,
    connectionState: LastFmConnectionState,
    errorMessage: String?,
    onDebugScrobble: (() -> Unit)?,
    onLastFmAction: () -> Unit,
    onAddTrustedRepository: (RepositoryDescriptor) -> Unit,
    onImportRepository: (String) -> Unit,
    onRemoveTrustedRepository: (String) -> Unit,
    repositoryCatalogs: Map<String, List<ExtensionCatalogEntry>>,
    repositoryMessages: Map<String, String>,
    importedRepository: RepositoryDescriptor?,
    repositoryImportMessage: String?,
    repositoryImportVersion: Long,
    onRefreshRepository: (RepositoryDescriptor) -> Unit,
    onAudioSettingsChange: ((RelaySettings) -> Unit)?,
    onWallpaperSettingsChange: ((RelaySettings) -> Unit)? = null,
    offlineDownloads: List<OfflineDownload> = emptyList(),
    onDeleteDownload: (String, String) -> Unit = { _, _ -> },
    onDeleteAllDownloads: () -> Unit = {},
    onPickShuffleSeed: ((String) -> Unit)? = null,
    onImportThemePack: (() -> Unit)? = null,
    onApplyThemePack: (String?) -> Unit = {},
    onRemoveThemePack: (String) -> Unit = {},
    submenu: SettingsSubmenu?,
    onSubmenuChange: (SettingsSubmenu?) -> Unit,
    onOpenAlbumWallpaperPicker: (() -> Unit)? = null,
) {
    fun toggle(item: SettingsSubmenu) = onSubmenuChange(if (submenu == item) null else item)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SettingsAccordion("PLAYBACK", submenu == SettingsSubmenu.PLAYBACK, { toggle(SettingsSubmenu.PLAYBACK) }) {
            PlaybackSettings(settings, onResumeQueueChange, onAudioSettingsChange, onPickShuffleSeed)
        }
        onAudioSettingsChange?.let {
            SettingsAccordion("AUDIO", submenu == SettingsSubmenu.AUDIO, { toggle(SettingsSubmenu.AUDIO) }) {
                AudioSettings(settings, onAudioSettingsChange)
            }
        }
        SettingsAccordion("TRACKING", submenu == SettingsSubmenu.TRACKING, { toggle(SettingsSubmenu.TRACKING) }) {
            TrackingSettings(connectionState, errorMessage, onDebugScrobble, onLastFmAction)
        }
        SettingsAccordion("METADATA", submenu == SettingsSubmenu.METADATA, { toggle(SettingsSubmenu.METADATA) }) {
            MetadataSettings()
        }
        SettingsAccordion("STORAGE", submenu == SettingsSubmenu.STORAGE, { toggle(SettingsSubmenu.STORAGE) }) {
            StorageSettings(settings.storageRootUri != null, onChooseStorageRoot, offlineDownloads, onDeleteDownload, onDeleteAllDownloads)
        }
        SettingsAccordion("BACKUP AND RESTORE", submenu == SettingsSubmenu.BACKUP, { toggle(SettingsSubmenu.BACKUP) }) {
            BackupSettings(settings, onBackupExport, onBackupImport, onBackupScheduleChange, onAutoBackupExpiryChange)
        }
        if (onSyncExport != null && onSyncImport != null) {
            SettingsAccordion("DEVICE SYNC", submenu == SettingsSubmenu.SYNC, { toggle(SettingsSubmenu.SYNC) }) {
                SyncSettings(onSyncExport, onSyncImport, syncConflicts, onDismissSyncConflict, onUseReceivedSyncConflict, lanSync, pairedDevices, onStartLanSyncHost, onJoinLanSync, onConfirmLanSync, onCancelLanSync, onUnpairDevice, onSelectMusicTransfer, onImportMusicTransfer, onPrepareLanMusicTransfer, playTogether, onStartPlayTogetherHost, onJoinPlayTogether, onConfirmPlayTogether, onLeavePlayTogether, onResyncPlayTogether)
            }
        }
        SettingsAccordion("SOURCE REPOSITORIES", submenu == SettingsSubmenu.REPOSITORIES, { toggle(SettingsSubmenu.REPOSITORIES) }) {
            RepositorySettings(
                settings,
                repositoryCatalogs,
                repositoryMessages,
                onAddTrustedRepository,
                onImportRepository,
                onRemoveTrustedRepository,
                importedRepository,
                repositoryImportMessage,
                repositoryImportVersion,
                onRefreshRepository,
            )
        }
        SettingsAccordion("THEME PACKS", submenu == SettingsSubmenu.THEME_PACKS, { toggle(SettingsSubmenu.THEME_PACKS) }) {
            ThemePackSettings(settings, onImportThemePack, onApplyThemePack, onRemoveThemePack)
        }
        onOpenAlbumWallpaperPicker?.let { openPicker ->
            SettingsAccordion("WALLPAPER", submenu == SettingsSubmenu.WALLPAPER, { toggle(SettingsSubmenu.WALLPAPER) }) {
                WallpaperSettings(settings, onWallpaperSettingsChange, openPicker)
            }
        }
    }
}

internal enum class SettingsSubmenu { PLAYBACK, AUDIO, TRACKING, METADATA, STORAGE, BACKUP, SYNC, REPOSITORIES, THEME_PACKS, WALLPAPER }

@Composable
private fun SettingsAccordion(label: String, expanded: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    SettingsMenuItem(label, expanded, onClick)
    AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
    }
}

@Composable
internal fun SettingsMenuItem(label: String, expanded: Boolean, onClick: () -> Unit) {
    BasicText(
        "$label ${if (expanded) "−" else "+"}",
        style = RelayType.Track,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(1.dp, RelayColors.Line)
            .semantics { contentDescription = "${if (expanded) "Close" else "Open"} $label settings" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 18.dp),
    )
}

@Composable
internal fun PlaybackSettings(
    settings: RelaySettings,
    onResumeQueueChange: (Boolean) -> Unit,
    onAudioSettingsChange: ((RelaySettings) -> Unit)?,
    onPickShuffleSeed: ((String) -> Unit)? = null,
) {
    var picker by remember { mutableStateOf<PlaybackPicker?>(null) }
    var editingShuffleProfile by remember { mutableStateOf(false) }
    val profile = settings.activeShuffleProfile()
    Column {
        BasicText("PLAYBACK", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = RelayColors.Line)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("RESTORE QUEUE", style = RelayType.Track, modifier = Modifier.weight(1f))
            BasicText(
                text = if (settings.resumeQueue) "ON" else "OFF",
                style = RelayType.Utility.copy(color = RelayColors.Signal),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Restore queue ${if (settings.resumeQueue) "on" else "off"}" }
                    .clickable(role = Role.Switch, onClick = { onResumeQueueChange(!settings.resumeQueue) })
                    .padding(start = 12.dp),
            )
        }
        onAudioSettingsChange?.let { onChange ->
            SettingsChoice(
                "SPEED",
                "${settings.playbackSpeed}×",
                "Choose playback speed",
            ) {
                picker = PlaybackPicker.SPEED
            }
            SettingsChoice(
                "FADE IN",
                fadeLabel(settings.fadeInMs),
                "Choose track fade in duration",
            ) {
                picker = PlaybackPicker.FADE_IN
            }
            SettingsChoice(
                "FADE OUT",
                fadeLabel(settings.fadeOutMs),
                "Choose track fade out duration",
            ) {
                picker = PlaybackPicker.FADE_OUT
            }
            SettingsChoice(
                "CROSSFADE",
                fadeLabel(settings.crossfadeMs),
                "Choose overlap duration between compatible tracks",
            ) {
                picker = PlaybackPicker.CROSSFADE
            }
            if (settings.crossfadeMs > 0) {
                BasicText(
                    "Crossfade replaces fade-out when it can overlap tracks. Effects use sequential fades; a failed preload returns to a normal transition.",
                    style = RelayType.Metadata,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else if (settings.fadeInMs > 0 || settings.fadeOutMs > 0) {
                BasicText(
                    "Fades apply when tracks change. They do not overlap tracks.",
                    style = RelayType.Metadata,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            SettingsChoice(
                "SHUFFLE PROFILE",
                profile.name,
                "Choose a saved shuffle profile",
            ) {
                picker = PlaybackPicker.SHUFFLE_PROFILE
            }
            SettingsChoice(
                "EDIT SHUFFLE PROFILE",
                if (profile.rules.isEmpty()) "NORMAL" else profile.rules.joinToString(" → "),
                "Edit shuffle rules and seed for ${profile.name}",
            ) {
                editingShuffleProfile = true
            }
        }
        when (picker) {
            PlaybackPicker.SPEED -> ValuePickerDialog(
                title = "PLAYBACK SPEED",
                choices = PLAYBACK_SPEEDS.map { "${it}×" },
                selectedIndex = PLAYBACK_SPEEDS.indexOf(settings.playbackSpeed),
                onSelect = { index -> onAudioSettingsChange?.invoke(settings.copy(playbackSpeed = PLAYBACK_SPEEDS[index])); picker = null },
                onDismiss = { picker = null },
            )
            PlaybackPicker.FADE_IN, PlaybackPicker.FADE_OUT, PlaybackPicker.CROSSFADE -> ValuePickerDialog(
                title = when (picker) {
                    PlaybackPicker.FADE_IN -> "FADE IN"
                    PlaybackPicker.FADE_OUT -> "FADE OUT"
                    PlaybackPicker.CROSSFADE -> "CROSSFADE"
                    else -> error("Unexpected playback picker")
                },
                choices = FADE_DURATIONS.map(::fadeLabel),
                selectedIndex = FADE_DURATIONS.indexOf(
                    when (picker) {
                        PlaybackPicker.FADE_IN -> settings.fadeInMs
                        PlaybackPicker.FADE_OUT -> settings.fadeOutMs
                        PlaybackPicker.CROSSFADE -> settings.crossfadeMs
                        else -> 0
                    },
                ),
                onSelect = { index ->
                    onAudioSettingsChange?.invoke(
                        when (picker) {
                            PlaybackPicker.FADE_IN -> settings.copy(fadeInMs = FADE_DURATIONS[index])
                            PlaybackPicker.FADE_OUT -> settings.copy(fadeOutMs = FADE_DURATIONS[index])
                            PlaybackPicker.CROSSFADE -> settings.copy(crossfadeMs = FADE_DURATIONS[index])
                            else -> settings
                        },
                    )
                    picker = null
                },
                onDismiss = { picker = null },
            )
            PlaybackPicker.SHUFFLE_PROFILE -> ValuePickerDialog(
                title = "SHUFFLE PROFILE",
                choices = settings.shuffleProfiles.map { it.name } + "NEW PROFILE",
                selectedIndex = settings.shuffleProfiles.indexOfFirst { it.id == profile.id },
                onSelect = { index ->
                    if (index == settings.shuffleProfiles.size) {
                        onAudioSettingsChange?.invoke(settings.withActiveShuffleProfile(newShuffleProfile(settings.shuffleProfiles)))
                    } else {
                        onAudioSettingsChange?.invoke(settings.withActiveShuffleProfileId(settings.shuffleProfiles[index].id))
                    }
                    picker = null
                },
                onDismiss = { picker = null },
            )
            null -> Unit
        }
        if (editingShuffleProfile) {
            ShuffleProfileEditorDialog(
                profile = profile,
                canDelete = settings.shuffleProfiles.size > 1,
                onSave = { updated ->
                    onAudioSettingsChange?.invoke(settings.withActiveShuffleProfile(updated))
                    editingShuffleProfile = false
                },
                onDelete = {
                    val remaining = settings.shuffleProfiles.filterNot { it.id == profile.id }
                    onAudioSettingsChange?.invoke(
                        settings.copy(
                            shuffleProfiles = remaining,
                            activeShuffleProfileId = remaining.first().id,
                        ),
                    )
                    editingShuffleProfile = false
                },
                onPickSeed = onPickShuffleSeed?.let { pickSeed -> { updated: ShuffleProfile ->
                    onAudioSettingsChange?.invoke(settings.withActiveShuffleProfile(updated))
                    pickSeed(updated.seedSalt)
                } },
                onDismiss = { editingShuffleProfile = false },
            )
        }
    }
}

@Composable
internal fun AudioSettings(settings: RelaySettings, onAudioSettingsChange: ((RelaySettings) -> Unit)?) {
    var picker by remember { mutableStateOf<AudioPicker?>(null) }
    var draftBands by remember(settings.equalizerBandLevels) {
        mutableStateOf(normalizedEqualizerBands(settings.equalizerBandLevels))
    }
    Column {
        BasicText("AUDIO", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
        BasicText("Relay applies these effects to its own playback session.", style = RelayType.Metadata)
        onAudioSettingsChange?.let { onChange ->
            SettingsChoice(
                "EQUALIZER",
                if (settings.equalizerEnabled) "ON" else "OFF",
                "Toggle equalizer",
            ) { onChange(settings.copy(equalizerEnabled = !settings.equalizerEnabled)) }
            if (settings.equalizerEnabled) {
                SettingsChoice(
                    "PRESET",
                    settings.equalizerPreset.name,
                    "Choose equalizer preset",
                ) {
                    picker = AudioPicker.PRESET
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(232.dp).padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    draftBands.forEachIndexed { index, level ->
                        key(EQUALIZER_BAND_LABELS[index]) {
                            EqualizerBandSlider(
                                label = EQUALIZER_BAND_LABELS[index],
                                levelMb = level,
                                onValueChange = { updatedLevel ->
                                    draftBands = draftBands.toMutableList().apply { this[index] = updatedLevel }
                                },
                                onValueChangeFinished = {
                                    onChange(
                                        settings.copy(
                                            equalizerPreset = EqualizerPreset.CUSTOM,
                                            equalizerBandLevels = normalizedEqualizerBands(draftBands),
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            SettingsChoice(
                "BASS BOOST",
                "${settings.bassBoostStrength / 10}%",
                "Choose bass boost strength",
            ) { picker = AudioPicker.BASS_BOOST }
        }
        when (picker) {
            AudioPicker.PRESET -> ValuePickerDialog(
                title = "EQUALIZER PRESET",
                choices = EQUALIZER_PRESETS.map { it.name },
                selectedIndex = EQUALIZER_PRESETS.indexOf(settings.equalizerPreset),
                onSelect = { index ->
                    val preset = EQUALIZER_PRESETS[index]
                    onAudioSettingsChange?.invoke(settings.copy(equalizerPreset = preset, equalizerBandLevels = equalizerPresetLevels(preset)))
                    picker = null
                },
                onDismiss = { picker = null },
            )
            AudioPicker.BASS_BOOST -> ValuePickerDialog(
                title = "BASS BOOST",
                choices = BASS_BOOST_STRENGTHS.map { "${it / 10}%" },
                selectedIndex = BASS_BOOST_STRENGTHS.indexOf(settings.bassBoostStrength),
                onSelect = { index ->
                    onAudioSettingsChange?.invoke(settings.copy(bassBoostStrength = BASS_BOOST_STRENGTHS[index]))
                    picker = null
                },
                onDismiss = { picker = null },
            )
            null -> Unit
        }
    }
}

@Composable
internal fun EqualizerBandSlider(
    label: String,
    levelMb: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    var heightPx by remember { mutableIntStateOf(0) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val progress = ((levelMb + 1_200).toFloat() / 2_400).coerceIn(0f, 1f)
    fun update(y: Float) {
        currentOnValueChange(
            (((1f - y / heightPx) * 2_400 - 1_200) / 100f)
                .roundToInt()
                .times(100)
                .coerceIn(-1_200, 1_200),
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(label, style = RelayType.Utility, maxLines = 1)
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(44.dp)
                .height(160.dp)
                .border(1.dp, RelayColors.Line)
                .onSizeChanged { heightPx = it.height }
                .pointerInput(heightPx) {
                    detectDragGestures(
                        onDragStart = { offset -> if (heightPx > 0) update(offset.y) },
                        onDrag = { change, _ ->
                            if (heightPx > 0) {
                                change.consume()
                                update(change.position.y)
                            }
                        },
                        onDragEnd = { currentOnValueChangeFinished() },
                        onDragCancel = {},
                    )
                }
                .semantics {
                    contentDescription = "$label equalizer band"
                    progressBarRangeInfo = ProgressBarRangeInfo(levelMb.toFloat(), -1_200f..1_200f, 23)
                    setProgress { target ->
                        onValueChange(target.roundToInt().coerceIn(-1_200, 1_200))
                        onValueChangeFinished()
                        true
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .background(RelayColors.Signal),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .absoluteOffset { IntOffset(0, -((heightPx - 12) * progress).roundToInt()) }
                    .size(12.dp)
                    .background(RelayColors.Paper),
            )
        }
        BasicText(levelLabel(levelMb), style = RelayType.Utility, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ValuePickerDialog(
    title: String,
    choices: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RelayColors.Panel)
                .border(1.dp, RelayColors.Line)
                .padding(12.dp),
        ) {
            BasicText(title, style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
            choices.forEachIndexed { index, choice ->
                SettingsChoice(choice, if (index == selectedIndex) "SELECTED" else "", "Select $choice") { onSelect(index) }
            }
            TransportAction("CANCEL", "Close $title selector", true, onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun ShuffleProfileEditorDialog(
    profile: ShuffleProfile,
    canDelete: Boolean,
    onSave: (ShuffleProfile) -> Unit,
    onDelete: () -> Unit,
    onPickSeed: ((ShuffleProfile) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var draft by remember(profile) { mutableStateOf(profile) }
    var confirmDelete by remember(profile) { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .background(RelayColors.Panel)
                .border(1.dp, RelayColors.Line)
                .padding(12.dp),
        ) {
            BasicText("SHUFFLE PROFILE", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
            MetadataField("PROFILE NAME", draft.name) { draft = draft.copy(name = it.take(32)) }
            BasicText("ORDERED RULES", style = RelayType.Track, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            if (draft.rules.isEmpty()) {
                BasicText("No rules: unbiased normal shuffle.", style = RelayType.Metadata)
            }
            draft.rules.forEachIndexed { index, rule ->
                SettingsChoice(
                "RULE ${index + 1}",
                rule.name,
                "Change shuffle rule ${index + 1}",
            ) {
                    val choices = ShuffleGrouping.entries.filter { it == rule || it !in draft.rules }
                    val next = choices[(choices.indexOf(rule) + 1) % choices.size]
                    draft = draft.copy(rules = draft.rules.toMutableList().apply { this[index] = next })
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TransportAction("UP", "Move rule ${index + 1} up", index > 0, {
                        draft = draft.copy(rules = draft.rules.toMutableList().apply {
                            val value = removeAt(index); add(index - 1, value)
                        })
                    }, Modifier.weight(1f))
                    TransportAction("DOWN", "Move rule ${index + 1} down", index < draft.rules.lastIndex, {
                        draft = draft.copy(rules = draft.rules.toMutableList().apply {
                            val value = removeAt(index); add(index + 1, value)
                        })
                    }, Modifier.weight(1f))
                    TransportAction("REMOVE", "Remove rule ${index + 1}", true, {
                        draft = draft.copy(rules = draft.rules.toMutableList().apply { removeAt(index) })
                    }, Modifier.weight(1f))
                }
            }
            TransportAction(
                "ADD RULE",
                "Add an ordered metadata shuffle rule",
                draft.rules.size < ShuffleGrouping.entries.size,
                {
                    ShuffleGrouping.entries.firstOrNull { it !in draft.rules }?.let { rule ->
                        draft = draft.copy(rules = draft.rules + rule)
                    }
                },
                Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SettingsChoice(
                "MISSING METADATA",
                draft.missingValue.name,
                "Choose where tracks with missing metadata appear",
            ) {
                draft = draft.copy(
                    missingValue = if (draft.missingValue == MissingShuffleValue.LAST) MissingShuffleValue.FIRST else MissingShuffleValue.LAST,
                )
            }
            BasicText("IMAGE SEED", style = RelayType.Track, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            MetadataField("OPTIONAL SALT", draft.seedSalt) { draft = draft.copy(seedSalt = it.take(64)) }
            BasicText(
                draft.seedLabel?.let { "SEED $it — image bytes stay on this device." }
                    ?: "No fixed seed — each reshuffle is random.",
                style = RelayType.Metadata,
            )
            TransportAction(
                if (draft.seed == null) "SELECT SEED IMAGE" else "REPLACE SEED IMAGE",
                "Choose an image to create a repeatable shuffle seed",
                onPickSeed != null,
                { onPickSeed?.invoke(draft) },
                Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (draft.seed != null) {
                TransportAction(
                    "CLEAR IMAGE SEED",
                    "Return this shuffle profile to random order",
                    true,
                    { draft = draft.copy(seed = null, seedLabel = null) },
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            TransportAction("SAVE", "Save shuffle profile", true, { onSave(draft) }, Modifier.fillMaxWidth().padding(top = 16.dp))
            if (canDelete) {
                TransportAction(
                    if (confirmDelete) "CONFIRM DELETE PROFILE" else "DELETE PROFILE",
                    "Delete ${profile.name}",
                    true,
                    { if (confirmDelete) onDelete() else confirmDelete = true },
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            TransportAction("CANCEL", "Close shuffle profile editor", true, onDismiss, Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
internal fun SettingsChoice(label: String, value: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(1.dp, RelayColors.Line)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = RelayType.Track, modifier = Modifier.weight(1f))
        BasicText(value, style = RelayType.Utility.copy(color = RelayColors.Signal))
    }
}

@Composable
private fun WallpaperSettings(
    settings: RelaySettings,
    onSettingsChange: ((RelaySettings) -> Unit)?,
    onOpenPicker: () -> Unit,
) {
    var selectedElementLabel by remember { mutableStateOf(settings.wallpaperPreset.elements.firstOrNull()?.label ?: "ARTWORK") }
    var selectedFilterLabel by remember { mutableStateOf(settings.wallpaperPreset.filters.firstOrNull()?.label ?: "GRAYSCALE") }
    var picker by remember { mutableStateOf<WallpaperPickerRequest?>(null) }
    val preset = settings.wallpaperPreset
    val selectedElement = preset.elements.firstOrNull { it.label == selectedElementLabel }
    val selectedFilter = preset.filters.firstOrNull { it.label == selectedFilterLabel }
    fun save(updated: WallpaperPreset) = onSettingsChange?.invoke(settings.copy(wallpaperPreset = updated))
    fun choose(title: String, values: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        picker = WallpaperPickerRequest(title, values, selected) { index -> onSelect(index); picker = null }
    }
    fun replaceSelected(element: WallpaperElement) = save(preset.copy(elements = preset.elements.map {
        if (it.label == selectedElementLabel) element else it
    }))
    fun replaceSelectedFilter(filter: ArtworkFilter) = save(preset.copy(filters = preset.filters.map {
        if (it.label == selectedFilterLabel) filter else it
    }))

    Column {
        BasicText("ALBUM ART WALLPAPER", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
        BasicText("Uses only Relay's cached artwork and playback snapshot. Filters are static data and are cached between track or preset changes.", style = RelayType.Metadata)
        WallpaperPreview(preset, Modifier.fillMaxWidth().height(320.dp).padding(top = 12.dp))
        preset.warnings.forEach { warning ->
            BasicText(warning, style = RelayType.Metadata.copy(color = RelayColors.Signal), modifier = Modifier.padding(top = 8.dp))
        }
        onSettingsChange?.let { onChange ->
            SettingsChoice(
                "CANVAS",
                preset.canvas.background.name.replace('_', ' '),
                "Choose a solid or artwork-derived canvas",
            ) {
                val values = WallpaperCanvasBackground.entries
                val next = values[(values.indexOf(preset.canvas.background) + 1) % values.size]
                save(preset.copy(canvas = preset.canvas.copy(background = next)))
            }
            if (preset.canvas.background == WallpaperCanvasBackground.SOLID) {
                SettingsChoice(
                    "SOLID COLOR",
                    if (preset.canvas.solidColorArgb == 0xFFF3F0E8L) "PAPER" else "INK",
                    "Choose the solid wallpaper canvas color",
                ) {
                    val next = if (preset.canvas.solidColorArgb == 0xFFF3F0E8L) 0xFF101010 else 0xFFF3F0E8
                    save(preset.copy(canvas = preset.canvas.copy(solidColorArgb = next)))
                }
            }
            SettingsChoice(
                "ARTWORK CROP",
                preset.canvas.artworkFit.name,
                "Choose whether artwork fills or fits the wallpaper",
            ) {
                val next = if (preset.canvas.artworkFit == WallpaperArtworkFit.FILL) WallpaperArtworkFit.FIT else WallpaperArtworkFit.FILL
                save(preset.copy(canvas = preset.canvas.copy(artworkFit = next)))
            }
            SettingsChoice(
                "PAGE OFFSET",
                preset.canvas.pageOffset.name,
                "Choose whether home-screen pages shift artwork",
            ) {
                val next = if (preset.canvas.pageOffset == WallpaperPageOffset.FIXED) WallpaperPageOffset.FOLLOW else WallpaperPageOffset.FIXED
                save(preset.copy(canvas = preset.canvas.copy(pageOffset = next)))
            }
            SettingsChoice(
                "WALLPAPER METADATA",
                if (preset.showMetadata) "SHOW" else "HIDE",
                "Show or hide title, artist, and album wallpaper elements",
            ) {
                save(preset.copy(showMetadata = !preset.showMetadata))
            }
            SettingsChoice(
                "LOCK SCREEN INFO",
                if (settings.showLockscreenMetadata) "SHOW" else "HIDE",
                "Show or hide title and artist in lock-screen widgets",
            ) {
                onChange(settings.copy(showLockscreenMetadata = !settings.showLockscreenMetadata))
            }
            SettingsChoice(
                "BATTERY SAVER",
                if (preset.batterySaver) "ON" else "OFF",
                "Disable audio animation and continuous progress redraws",
            ) {
                save(preset.copy(batterySaver = !preset.batterySaver))
            }

            BasicText("ELEMENTS", style = RelayType.Track, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            if (preset.elements.isNotEmpty()) {
                SettingsChoice("ELEMENT", selectedElement?.label ?: preset.elements.first().label, "Choose an element to edit") {
                    choose("WALLPAPER ELEMENT", preset.elements.map(WallpaperElement::label), preset.elements.indexOf(selectedElement).coerceAtLeast(0)) {
                        selectedElementLabel = preset.elements[it].label
                    }
                }
            }
            selectedElement?.let { element ->
                val layout = element.layout
                @Composable fun percentPicker(label: String, value: Float, values: List<Int>, update: (Float) -> Unit) {
                    SettingsChoice(label, "${(value * 100).roundToInt()}%", "Set $label for ${element.label}") {
                        choose(label, values.map { "$it%" }, values.indexOf((value * 100).roundToInt()).coerceAtLeast(0)) { update(values[it] / 100f) }
                    }
                }
                percentPicker("X", layout.x, NORMALIZED_STEPS) { replaceSelected(element.withLayout(layout.copy(x = it))) }
                percentPicker("Y", layout.y, NORMALIZED_STEPS) { replaceSelected(element.withLayout(layout.copy(y = it))) }
                percentPicker("WIDTH", layout.width, SIZE_STEPS) { replaceSelected(element.withLayout(layout.copy(width = it))) }
                percentPicker("HEIGHT", layout.height, HEIGHT_STEPS) { replaceSelected(element.withLayout(layout.copy(height = it))) }
                percentPicker("OPACITY", layout.opacity, NORMALIZED_STEPS) { replaceSelected(element.withLayout(layout.copy(opacity = it))) }
                SettingsChoice("ANCHOR", layout.anchor.name.replace('_', ' '), "Set anchor for ${element.label}") {
                    val values = WallpaperAnchor.entries
                    choose("ANCHOR", values.map { it.name.replace('_', ' ') }, values.indexOf(layout.anchor)) { replaceSelected(element.withLayout(layout.copy(anchor = values[it]))) }
                }
                SettingsChoice("VISIBLE ON", layout.visibility.name, "Set visibility for ${element.label}") {
                    val values = WallpaperVisibility.entries
                    choose("VISIBLE ON", values.map(WallpaperVisibility::name), values.indexOf(layout.visibility)) { replaceSelected(element.withLayout(layout.copy(visibility = values[it]))) }
                }
                if (element !is WallpaperElement.Artwork && element !is WallpaperElement.Progress) {
                    SettingsChoice("FONT", layout.font.name, "Set font for ${element.label}") {
                        val values = WallpaperFont.entries
                        choose("FONT", values.map(WallpaperFont::name), values.indexOf(layout.font)) { replaceSelected(element.withLayout(layout.copy(font = values[it]))) }
                    }
                    SettingsChoice("ALIGN", layout.alignment.name, "Set text alignment for ${element.label}") {
                        val values = WallpaperTextAlignment.entries
                        choose("ALIGN", values.map(WallpaperTextAlignment::name), values.indexOf(layout.alignment)) { replaceSelected(element.withLayout(layout.copy(alignment = values[it]))) }
                    }
                }
                val index = preset.elements.indexOf(element)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TransportAction("BACK", "Move ${element.label} behind", index > 0, {
                        save(preset.copy(elements = preset.elements.swap(index, index - 1)))
                    }, Modifier.weight(1f))
                    TransportAction("FRONT", "Move ${element.label} forward", index < preset.elements.lastIndex, {
                        save(preset.copy(elements = preset.elements.swap(index, index + 1)))
                    }, Modifier.weight(1f))
                    TransportAction("REMOVE", "Remove ${element.label}", true, {
                        val remaining = preset.elements.filterNot { it.label == element.label }
                        selectedElementLabel = remaining.firstOrNull()?.label ?: "ARTWORK"
                        save(preset.copy(elements = remaining))
                    }, Modifier.weight(1f))
                }
            }
            val missingElements = WALLPAPER_ELEMENT_LABELS.filter { label -> preset.elements.none { it.label == label } }
            TransportAction("ADD ELEMENT", "Add a wallpaper composition element", missingElements.isNotEmpty(), {
                choose("ADD ELEMENT", missingElements, -1) { index ->
                    selectedElementLabel = missingElements[index]
                    save(preset.copy(elements = preset.elements + defaultWallpaperElement(missingElements[index])))
                }
            }, Modifier.fillMaxWidth().padding(top = 8.dp))

            BasicText("FILTERS", style = RelayType.Track, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            if (preset.filters.isNotEmpty()) {
                SettingsChoice("FILTER", selectedFilter?.label ?: preset.filters.first().label, "Choose an artwork filter to edit") {
                    choose("ARTWORK FILTER", preset.filters.map(ArtworkFilter::label), preset.filters.indexOf(selectedFilter).coerceAtLeast(0)) {
                        selectedFilterLabel = preset.filters[it].label
                    }
                }
            }
            selectedFilter?.let { filter ->
                when (filter) {
                    is ArtworkFilter.Grayscale -> FilterAmountChoice(filter.amount) {
                        choose("FILTER AMOUNT", NORMALIZED_STEPS.map { "$it%" }, NORMALIZED_STEPS.indexOf((filter.amount * 100).roundToInt()).coerceAtLeast(0)) { replaceSelectedFilter(filter.copy(amount = NORMALIZED_STEPS[it] / 100f)) }
                    }
                    is ArtworkFilter.Blur -> SettingsChoice("RADIUS", "${filter.radius.roundToInt()}", "Set blur radius") {
                        choose("BLUR RADIUS", FILTER_STEPS.map(Int::toString), FILTER_STEPS.indexOf(filter.radius.roundToInt()).coerceAtLeast(0)) { replaceSelectedFilter(filter.copy(radius = FILTER_STEPS[it].toFloat())) }
                    }
                    is ArtworkFilter.Duotone -> {
                        FilterAmountChoice(filter.amount) {
                            choose("FILTER AMOUNT", NORMALIZED_STEPS.map { "$it%" }, NORMALIZED_STEPS.indexOf((filter.amount * 100).roundToInt()).coerceAtLeast(0)) { replaceSelectedFilter(filter.copy(amount = NORMALIZED_STEPS[it] / 100f)) }
                        }
                        SettingsChoice("COLORS", if (filter.shadowColorArgb == 0xFF112244L) "BLUE / ORANGE" else "INK / PAPER", "Choose duotone colors") {
                            replaceSelectedFilter(if (filter.shadowColorArgb == 0xFF112244L) filter.copy(shadowColorArgb = 0xFF101010, highlightColorArgb = 0xFFF3F0E8) else filter.copy(shadowColorArgb = 0xFF112244, highlightColorArgb = 0xFFFFAA55))
                        }
                    }
                    is ArtworkFilter.BrightnessContrast -> {
                        SettingsChoice("BRIGHTNESS", "${(filter.brightness * 100).roundToInt()}%", "Set brightness") {
                            choose("BRIGHTNESS", SIGNED_STEPS.map { "$it%" }, SIGNED_STEPS.indexOf((filter.brightness * 100).roundToInt()).coerceAtLeast(0)) { replaceSelectedFilter(filter.copy(brightness = SIGNED_STEPS[it] / 100f)) }
                        }
                        SettingsChoice("CONTRAST", "${(filter.contrast * 100).roundToInt()}%", "Set contrast") {
                            choose("CONTRAST", CONTRAST_STEPS.map { "$it%" }, CONTRAST_STEPS.indexOf((filter.contrast * 100).roundToInt()).coerceAtLeast(0)) { replaceSelectedFilter(filter.copy(contrast = CONTRAST_STEPS[it] / 100f)) }
                        }
                    }
                    is ArtworkFilter.Vignette -> FilterAmountChoice(filter.strength) {
                        choose("FILTER AMOUNT", NORMALIZED_STEPS.map { "$it%" }, NORMALIZED_STEPS.indexOf((filter.strength * 100).roundToInt()).coerceAtLeast(0)) { replaceSelectedFilter(filter.copy(strength = NORMALIZED_STEPS[it] / 100f)) }
                    }
                    is ArtworkFilter.Grain -> FilterAmountChoice(filter.strength) {
                        choose("FILTER AMOUNT", NORMALIZED_STEPS.map { "$it%" }, NORMALIZED_STEPS.indexOf((filter.strength * 100).roundToInt()).coerceAtLeast(0)) { replaceSelectedFilter(filter.copy(strength = NORMALIZED_STEPS[it] / 100f)) }
                    }
                }
                TransportAction("REMOVE FILTER", "Remove ${filter.label}", true, {
                    val remaining = preset.filters.filterNot { it.label == filter.label }
                    selectedFilterLabel = remaining.firstOrNull()?.label ?: "GRAYSCALE"
                    save(preset.copy(filters = remaining))
                }, Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            val missingFilters = WALLPAPER_FILTER_LABELS.filter { label -> preset.filters.none { it.label == label } }
            TransportAction("ADD FILTER", "Add a cached static artwork filter", missingFilters.isNotEmpty(), {
                choose("ADD FILTER", missingFilters, -1) { index ->
                    selectedFilterLabel = missingFilters[index]
                    save(preset.copy(filters = preset.filters + defaultArtworkFilter(missingFilters[index])))
                }
            }, Modifier.fillMaxWidth().padding(top = 8.dp))

            SettingsChoice(
                "VISUALIZER",
                preset.visualizer.name,
                "Choose the wallpaper visualizer",
            ) {
                val values = WallpaperVisualizer.entries
                choose("VISUALIZER", values.map(WallpaperVisualizer::name), values.indexOf(preset.visualizer)) {
                    save(preset.copy(visualizer = values[it], soundReactive = values[it] != WallpaperVisualizer.OFF || preset.soundReactive))
                }
            }
            SettingsChoice(
                "SOUND REACTIVE",
                if (preset.soundReactive) "ON" else "OFF",
                "Allow the wallpaper to react to Relay playback; Android will request microphone permission",
            ) {
                save(preset.copy(soundReactive = !preset.soundReactive))
            }
            TransportAction("RESET PRESET", "Reset wallpaper composition and filters", true, {
                selectedElementLabel = "ARTWORK"
                selectedFilterLabel = "GRAYSCALE"
                save(WallpaperPreset())
            }, Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        TransportAction(
            "OPEN SYSTEM PICKER",
            "Preview and enable Relay album art wallpaper",
            true,
            onOpenPicker,
            Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
    picker?.let { request ->
        ValuePickerDialog(request.title, request.choices, request.selectedIndex, request.onSelect) { picker = null }
    }
}

@Composable
private fun WallpaperPreview(preset: WallpaperPreset, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(if (preset.canvas.background == WallpaperCanvasBackground.ARTWORK_AVERAGE) RelayColors.Line else RelayColors.Ink).border(1.dp, RelayColors.Line)) {
        preset.elements.forEach { element ->
            val bounds = wallpaperElementBounds(element.layout, 1f, 1f)
            Box(
                Modifier
                    .absoluteOffset(x = maxWidth * bounds.left, y = maxHeight * bounds.top)
                    .width(maxWidth * bounds.width)
                    .height(maxHeight * bounds.height)
                    .graphicsLayer(alpha = element.layout.opacity)
                    .background(if (element is WallpaperElement.Artwork) RelayColors.Line else RelayColors.Panel)
                    .border(1.dp, RelayColors.Paper),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(element.label, style = RelayType.Utility, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FilterAmountChoice(value: Float, onClick: () -> Unit) {
    SettingsChoice("AMOUNT", "${(value * 100).roundToInt()}%", "Set filter amount") {
        onClick()
    }
}

private data class WallpaperPickerRequest(
    val title: String,
    val choices: List<String>,
    val selectedIndex: Int,
    val onSelect: (Int) -> Unit,
)

private val ArtworkFilter.label: String
    get() = when (this) {
        is ArtworkFilter.Grayscale -> "GRAYSCALE"
        is ArtworkFilter.Blur -> "BLUR"
        is ArtworkFilter.Duotone -> "DUOTONE"
        is ArtworkFilter.BrightnessContrast -> "BRIGHTNESS / CONTRAST"
        is ArtworkFilter.Vignette -> "VIGNETTE"
        is ArtworkFilter.Grain -> "GRAIN"
    }

private fun defaultArtworkFilter(label: String): ArtworkFilter = when (label) {
    "BLUR" -> ArtworkFilter.Blur()
    "DUOTONE" -> ArtworkFilter.Duotone()
    "BRIGHTNESS / CONTRAST" -> ArtworkFilter.BrightnessContrast()
    "VIGNETTE" -> ArtworkFilter.Vignette()
    "GRAIN" -> ArtworkFilter.Grain()
    else -> ArtworkFilter.Grayscale()
}

private fun <T> List<T>.swap(first: Int, second: Int): List<T> = toMutableList().apply {
    val item = this[first]
    this[first] = this[second]
    this[second] = item
}

private val WALLPAPER_ELEMENT_LABELS = listOf("ARTWORK", "TITLE", "ARTIST", "ALBUM", "CLOCK", "PROGRESS")
private val WALLPAPER_FILTER_LABELS = listOf("GRAYSCALE", "BLUR", "DUOTONE", "BRIGHTNESS / CONTRAST", "VIGNETTE", "GRAIN")
private val NORMALIZED_STEPS = (0..100 step 10).toList()
private val SIZE_STEPS = listOf(1, 10, 25, 50, 75, 100)
private val HEIGHT_STEPS = listOf(1, 5, 10, 25, 50, 75, 100)
private val FILTER_STEPS = listOf(0, 5, 10, 15, 20, 25)
private val SIGNED_STEPS = listOf(-100, -50, -25, 0, 25, 50, 100)
private val CONTRAST_STEPS = listOf(25, 50, 75, 100, 125, 150, 200)

@Composable
private fun SyncSettings(
    onExport: () -> Unit,
    onImport: () -> Unit,
    conflicts: List<SyncConflictNotice>,
    onDismissConflict: (String) -> Unit,
    onUseReceived: (String) -> Unit,
    lanSync: LanSyncUiState,
    pairedDevices: List<PairedDeviceUi>,
    onStartLanSyncHost: () -> Unit,
    onJoinLanSync: (String) -> Unit,
    onConfirmLanSync: (Boolean) -> Unit,
    onCancelLanSync: () -> Unit,
    onUnpairDevice: (String) -> Unit,
    onSelectMusicTransfer: () -> Unit,
    onImportMusicTransfer: () -> Unit,
    onPrepareLanMusicTransfer: () -> Unit,
    playTogether: PlayTogetherUiState,
    onStartPlayTogetherHost: () -> Unit,
    onJoinPlayTogether: (String) -> Unit,
    onConfirmPlayTogether: (Boolean) -> Unit,
    onLeavePlayTogether: () -> Unit,
    onResyncPlayTogether: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var playTogetherAddress by remember { mutableStateOf("") }
    Column {
        BasicText("WIRED DATA SYNC", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
        BasicText(
            "Transfer a .relaysync file with USB, removable storage, or a mounted folder. It merges library data; music files and credentials stay on this device.",
            style = RelayType.Metadata,
        )
        TransportAction("EXPORT SYNC FILE", "Create a data-only sync bundle", true, onExport, Modifier.fillMaxWidth().padding(top = 16.dp))
        TransportAction("IMPORT SYNC FILE", "Merge a data-only sync bundle", true, onImport, Modifier.fillMaxWidth().padding(top = 8.dp))
        BasicText("LAN DATA SYNC", style = RelayType.Track, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        BasicText(
            "Both devices must confirm the same six-digit code. This transfers only Relay data; music and playback stay separate.",
            style = RelayType.Metadata,
        )
        lanSync.hostAddress?.let { BasicText("HOST AT $it", style = RelayType.Utility.copy(color = RelayColors.Signal), modifier = Modifier.padding(top = 8.dp)) }
        lanSync.message?.let { BasicText(it, style = RelayType.Metadata, modifier = Modifier.padding(top = 8.dp)) }
        lanSync.warning?.let { BasicText("NOTE · $it", style = RelayType.Metadata, modifier = Modifier.padding(top = 4.dp)) }
        if (!lanSync.active && !lanSync.awaitingConfirmation) {
            TransportAction("HOST LAN SYNC", "Host one encrypted local-network data sync session", true, onStartLanSyncHost, Modifier.fillMaxWidth().padding(top = 8.dp))
            BasicTextField(
                value = address,
                onValueChange = { address = it.take(128) },
                singleLine = true,
                textStyle = RelayType.Metadata.copy(color = RelayColors.Paper),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, RelayColors.Line).padding(12.dp)
                    .semantics { contentDescription = "LAN sync host address and port" },
            )
            TransportAction("JOIN LAN SYNC", "Join an encrypted LAN sync host", address.isNotBlank(), { onJoinLanSync(address) }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (lanSync.awaitingConfirmation) {
            BasicText("VERIFY CODE · ${lanSync.pairingCode}", style = RelayType.Title, modifier = Modifier.padding(top = 12.dp))
            BasicText("Peer ${lanSync.peerFingerprint}. Confirm only if both devices show this code.", style = RelayType.Metadata, modifier = Modifier.padding(top = 4.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportAction("CONFIRM", "Confirm this LAN sync pairing code", true, { onConfirmLanSync(true) }, Modifier.weight(1f))
                TransportAction("CANCEL", "Cancel LAN sync pairing", true, { onConfirmLanSync(false) }, Modifier.weight(1f))
            }
        }
        if (lanSync.active && !lanSync.awaitingConfirmation) {
            TransportAction("CANCEL LAN SYNC", "Stop this local-network sync session", true, onCancelLanSync, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (pairedDevices.isNotEmpty()) {
            BasicText("PAIRED DEVICES", style = RelayType.Track, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            pairedDevices.forEach { device ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(device.name, style = RelayType.Metadata, modifier = Modifier.weight(1f).padding(top = 12.dp))
                    TransportAction("UNPAIR", "Remove ${device.name} from trusted Relay devices", true, { onUnpairDevice(device.id) }, Modifier.weight(1f))
                }
            }
        }
        BasicText("SELECTED MUSIC TRANSFER", style = RelayType.Track, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        BasicText("Choose local music files to export. Imports verify each file before indexing it in Relay/music.", style = RelayType.Metadata)
        lanSync.preparedMusicBytes?.let { bytes ->
            BasicText("READY FOR LAN · ${bytes / (1024 * 1024)} MB", style = RelayType.Utility, modifier = Modifier.padding(top = 4.dp))
        }
        TransportAction("EXPORT SELECTED MUSIC", "Choose local music files and create a verified transfer archive", true, onSelectMusicTransfer, Modifier.fillMaxWidth().padding(top = 8.dp))
        TransportAction("IMPORT MUSIC ARCHIVE", "Import a verified selected-music transfer archive", true, onImportMusicTransfer, Modifier.fillMaxWidth().padding(top = 8.dp))
        TransportAction("PREPARE MUSIC FOR LAN", "Choose music to include in the next encrypted LAN sync", true, onPrepareLanMusicTransfer, Modifier.fillMaxWidth().padding(top = 8.dp))
        BasicText("PLAY TOGETHER", style = RelayType.Track, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        BasicText("Start the current track on a paired device. Each device plays its own matching local file; Relay never sends audio or stream links.", style = RelayType.Metadata)
        playTogether.hostAddress?.let { BasicText("HOST AT $it", style = RelayType.Utility.copy(color = RelayColors.Signal), modifier = Modifier.padding(top = 8.dp)) }
        playTogether.message?.let { BasicText(it, style = RelayType.Metadata, modifier = Modifier.padding(top = 8.dp)) }
        playTogether.driftMs?.let { BasicText("LAST DRIFT ${it}MS", style = RelayType.Utility, modifier = Modifier.padding(top = 4.dp)) }
        if (!playTogether.active && !playTogether.awaitingConfirmation) {
            TransportAction("HOST PLAY TOGETHER", "Host one foreground synchronized-playback session", true, onStartPlayTogetherHost, Modifier.fillMaxWidth().padding(top = 8.dp))
            BasicTextField(
                value = playTogetherAddress,
                onValueChange = { playTogetherAddress = it.take(128) },
                singleLine = true,
                textStyle = RelayType.Metadata.copy(color = RelayColors.Paper),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, RelayColors.Line).padding(12.dp)
                    .semantics { contentDescription = "Play Together host address and port" },
            )
            TransportAction("JOIN PLAY TOGETHER", "Join a foreground synchronized-playback host", playTogetherAddress.isNotBlank(), { onJoinPlayTogether(playTogetherAddress) }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (playTogether.awaitingConfirmation) {
            BasicText("VERIFY CODE · ${playTogether.pairingCode}", style = RelayType.Title, modifier = Modifier.padding(top = 12.dp))
            BasicText("Peer ${playTogether.peerFingerprint}. Confirm only if both devices show this code.", style = RelayType.Metadata, modifier = Modifier.padding(top = 4.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportAction("CONFIRM", "Confirm this Play Together pairing code", true, { onConfirmPlayTogether(true) }, Modifier.weight(1f))
                TransportAction("CANCEL", "Cancel Play Together pairing", true, { onConfirmPlayTogether(false) }, Modifier.weight(1f))
            }
        }
        if (playTogether.active) {
            if (playTogether.resyncRequired) {
                TransportAction("RESYNC", "Seek to the leader's current position", true, onResyncPlayTogether, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            TransportAction("LEAVE PLAY TOGETHER", "Stop this synchronized-playback session", true, onLeavePlayTogether, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (conflicts.isNotEmpty()) {
            BasicText("CONFLICTS", style = RelayType.Track, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            BasicText("Local values were preserved. Choose which version to keep for each item.", style = RelayType.Metadata)
            conflicts.forEach { conflict ->
                SettingsChoice(conflict.description, "KEEP LOCAL", "Dismiss sync conflict ${conflict.description}") {
                    onDismissConflict(conflict.id)
                }
                if (conflict.canUseReceived) {
                    TransportAction(
                        "USE RECEIVED VALUE",
                        "Replace local value for ${conflict.description}",
                        true,
                        { onUseReceived(conflict.id) },
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

private enum class PlaybackPicker { SPEED, FADE_IN, FADE_OUT, CROSSFADE, SHUFFLE_PROFILE }
private enum class AudioPicker { PRESET, BASS_BOOST }

private val PLAYBACK_SPEEDS = listOf(0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 2f)
private val FADE_DURATIONS = listOf(0, 250, 500, 1_000, 2_000, 4_000)
private val EQUALIZER_PRESETS = listOf(EqualizerPreset.FLAT, EqualizerPreset.BASS, EqualizerPreset.TREBLE, EqualizerPreset.VOCAL)
private val BASS_BOOST_STRENGTHS = listOf(0, 250, 500, 750, 1_000)

internal fun fadeLabel(durationMs: Int): String = if (durationMs == 0) "OFF" else "${durationMs} MS"

internal fun levelLabel(levelMb: Int): String = "${if (levelMb > 0) "+" else ""}${levelMb / 100} DB"

internal val EQUALIZER_BAND_LABELS = listOf("LOW", "LOW MID", "MID", "HIGH MID", "HIGH")

@Composable
internal fun StorageSettings(
    hasStorageRoot: Boolean,
    onChooseStorageRoot: () -> Unit,
    downloads: List<OfflineDownload> = emptyList(),
    onDeleteDownload: (String, String) -> Unit = { _, _ -> },
    onDeleteAllDownloads: () -> Unit = {},
) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    Column {
        BasicText("STORAGE", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
        BasicText(
            if (hasStorageRoot) "Relay indexes music and downloads here. Sync and backups remain separate." else "Choose a Relay folder for music, downloads, sync, and backups.",
            style = RelayType.Metadata,
        )
        TransportAction(
            if (hasStorageRoot) "CHANGE FOLDER" else "CHOOSE FOLDER",
            "Choose Relay storage folder",
            true,
            onChooseStorageRoot,
            Modifier.fillMaxWidth().padding(top = 16.dp),
        )

        BasicText("DOWNLOADS", style = RelayType.Track, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        BasicText(
            text = if (downloads.isEmpty()) {
                "No downloads yet. Use DOWNLOAD on a source track to keep it offline."
            } else {
                "${downloads.size} FILES · ${formatFileSize(downloads.sumOf { it.sizeBytes })}"
            },
            style = RelayType.Metadata,
        )
        if (downloads.isNotEmpty()) {
            BasicText(
                "Downloaded tracks play from storage instead of streaming.",
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 4.dp),
            )
            TransportAction(
                label = if (confirmDeleteAll) "CONFIRM DELETE ALL" else "DELETE ALL DOWNLOADS",
                description = "Delete every downloaded file",
                enabled = true,
                onClick = { if (confirmDeleteAll) { onDeleteAllDownloads(); confirmDeleteAll = false } else confirmDeleteAll = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            downloads.forEach { download ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(1.dp, RelayColors.Line)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(download.title, style = RelayType.Track)
                        BasicText(
                            formatFileSize(download.sizeBytes),
                            style = RelayType.Utility.copy(color = RelayColors.Muted),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    TransportAction(
                        label = "DELETE",
                        description = "Delete the download for ${download.title}",
                        enabled = true,
                        onClick = { onDeleteDownload(download.sourceId, download.trackId) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BackupSettings(
    settings: RelaySettings,
    onBackupExport: () -> Unit,
    onBackupImport: () -> Unit,
    onBackupScheduleChange: (BackupSchedule) -> Unit,
    onAutoBackupExpiryChange: (Int) -> Unit,
) {
    Column {
        BasicText("BACKUP AND RESTORE", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction(
                label = "MANUAL EXPORT",
                description = "Create a manual Relay backup",
                enabled = true,
                onClick = onBackupExport,
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = "RESTORE",
                description = "Restore Relay backup",
                enabled = true,
                onClick = onBackupImport,
                modifier = Modifier.weight(1f),
            )
        }
        BasicText(
            "Manual backups stay until you remove them.",
            style = RelayType.Metadata,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (settings.storageRootUri != null) {
            Row(
                modifier = Modifier.fillMaxWidth().border(1.dp, RelayColors.Line).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("AUTO BACKUP", style = RelayType.Track, modifier = Modifier.weight(1f))
                BasicText(
                    settings.backupSchedule.name,
                    style = RelayType.Utility.copy(color = RelayColors.Signal),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Automatic backup ${settings.backupSchedule.name.lowercase()}" }
                        .clickable(role = Role.Button) { onBackupScheduleChange(settings.backupSchedule.next()) }
                        .padding(start = 12.dp),
                )
            }
            if (settings.backupSchedule != BackupSchedule.OFF) {
                Row(
                    modifier = Modifier.fillMaxWidth().border(1.dp, RelayColors.Line).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText("DELETE AUTO AFTER", style = RelayType.Track, modifier = Modifier.weight(1f))
                    BasicText(
                        settings.autoBackupExpiryDays.label(),
                        style = RelayType.Utility.copy(color = RelayColors.Signal),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Delete automatic backups after ${settings.autoBackupExpiryDays} days" }
                            .clickable(role = Role.Button) { onAutoBackupExpiryChange(settings.autoBackupExpiryDays.nextExpiryDays()) }
                            .padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RepositorySettings(
    settings: RelaySettings,
    repositoryCatalogs: Map<String, List<ExtensionCatalogEntry>>,
    repositoryMessages: Map<String, String>,
    onAddTrustedRepository: (RepositoryDescriptor) -> Unit,
    onImportRepository: (String) -> Unit,
    onRemoveTrustedRepository: (String) -> Unit,
    importedRepository: RepositoryDescriptor?,
    repositoryImportMessage: String?,
    repositoryImportVersion: Long,
    onRefreshRepository: (RepositoryDescriptor) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var descriptorUrl by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var indexUrl by remember { mutableStateOf("") }
    var signingKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repositoryImportVersion) {
        importedRepository ?: return@LaunchedEffect
        id = importedRepository.id
        name = importedRepository.name
        indexUrl = importedRepository.indexUrl
        signingKey = importedRepository.signingPublicKey
        error = null
    }
    Column {
        BasicText("SOURCE REPOSITORIES", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
        BasicText("Trusted repositories can be restored, but never install or enable sources automatically.", style = RelayType.Metadata)
        settings.trustedRepositories.forEach { repository ->
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).border(1.dp, RelayColors.Line).padding(12.dp)) {
                BasicText(repository.name, style = RelayType.Track)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransportAction("REFRESH", "Refresh ${repository.name}", true, { onRefreshRepository(repository) }, Modifier.weight(1f))
                    TransportAction("REMOVE", "Remove ${repository.name}", true, { onRemoveTrustedRepository(repository.id) }, Modifier.weight(1f))
                }
                repositoryMessages[repository.id]?.let { BasicText(it, style = RelayType.Metadata, modifier = Modifier.padding(top = 8.dp)) }
                repositoryCatalogs[repository.id].orEmpty().forEach { extension ->
                    BasicText(
                        "${extension.name} ${extension.version} — ${extension.kind.name}",
                        style = RelayType.Metadata,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        MetadataField("ADD REPOSITORY (OWNER/REPO OR URL)", descriptorUrl, { descriptorUrl = it; error = null })
        val resolvedUrl = repositoryDescriptorUrl(descriptorUrl)
        if (resolvedUrl != null && resolvedUrl != descriptorUrl.trim()) {
            BasicText("Reads $resolvedUrl", style = RelayType.Metadata, modifier = Modifier.padding(top = 8.dp))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction("PASTE", "Paste a repository link from the clipboard", true, {
                scope.launch {
                    clipboard.readPlainText()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        descriptorUrl = it
                        error = null
                    }
                }
            }, Modifier.weight(1f))
            TransportAction("IMPORT", "Read a repository.json descriptor for review", resolvedUrl != null, {
                resolvedUrl?.let(onImportRepository)
            }, Modifier.weight(1f))
        }
        repositoryImportMessage?.let {
            BasicText(it, style = RelayType.Metadata, modifier = Modifier.padding(top = 8.dp))
        }
        BasicText("Review imported details before trusting the repository.", style = RelayType.Metadata, modifier = Modifier.padding(top = 12.dp))
        MetadataField("REPOSITORY ID", id, { id = it.lowercase(); error = null })
        MetadataField("NAME", name, { name = it; error = null })
        MetadataField("HTTPS INDEX URL", indexUrl, { indexUrl = it; error = null })
        MetadataField("P-256 PUBLIC KEY (BASE64)", signingKey, { signingKey = it; error = null })
        TransportAction("TRUST REPOSITORY", "Save this repository identity", true, {
            val repository = RepositoryDescriptor(id.trim(), name.trim(), indexUrl.trim(), signingKey.trim())
            error = repository.validate() ?: when {
                settings.trustedRepositories.any { it.id == repository.id } -> "A repository with this ID is already trusted."
                else -> null
            }
            if (error == null) {
                onAddTrustedRepository(repository)
                id = ""; name = ""; indexUrl = ""; signingKey = ""
            }
        }, Modifier.fillMaxWidth().padding(top = 12.dp))
        error?.let { BasicText(it, style = RelayType.Metadata.copy(color = RelayColors.Danger), modifier = Modifier.padding(top = 8.dp)) }
    }
}

internal fun BackupSchedule.next(): BackupSchedule = when (this) {
    BackupSchedule.OFF -> BackupSchedule.WEEKLY
    BackupSchedule.WEEKLY -> BackupSchedule.DAILY
    BackupSchedule.DAILY -> BackupSchedule.OFF
}

internal fun Int.nextExpiryDays(): Int = when (this) {
    7 -> 30
    30 -> 90
    else -> 7
}

internal fun Int.label(): String = when (this) {
    7 -> "1 WEEK"
    30 -> "1 MONTH"
    90 -> "3 MONTHS"
    else -> "$this DAYS"
}

@Composable
internal fun TrackingSettings(
    connectionState: LastFmConnectionState,
    errorMessage: String?,
    onDebugScrobble: (() -> Unit)?,
    onLastFmAction: () -> Unit,
) {
    val action = when (connectionState) {
        LastFmConnectionState.SETUP_REQUIRED -> null
        LastFmConnectionState.DISCONNECTED -> "CONNECT LAST.FM"
        LastFmConnectionState.AUTHORIZING -> "FINISH CONNECTION"
        LastFmConnectionState.CONNECTED -> "DISCONNECT LAST.FM"
        LastFmConnectionState.ERROR -> "RETRY LAST.FM"
    }
    Column {
        BasicText("TRACKING", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
        BasicText("LAST.FM ${if (connectionState == LastFmConnectionState.CONNECTED) "CONNECTED" else "NOT CONNECTED"}", style = RelayType.Metadata)
        action?.let { TransportAction(it, it.lowercase(), true, onLastFmAction, Modifier.fillMaxWidth().padding(top = 16.dp)) }
        onDebugScrobble?.let { TransportAction("DEBUG SCROBBLE", "Send debug scrobble", true, it, Modifier.fillMaxWidth().padding(top = 8.dp)) }
        errorMessage?.let { BasicText(it, style = RelayType.Metadata.copy(color = RelayColors.Danger), modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
internal fun MetadataSettings() {
    Column {
        BasicText("METADATA", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
        BasicText("MUSICBRAINZ · METADATA AND RELEASE IDS", style = RelayType.Metadata)
        BasicText("COVER ART ARCHIVE · ALBUM ART", style = RelayType.Metadata, modifier = Modifier.padding(top = 8.dp))
        BasicText("APPLE SEARCH · ARTWORK FALLBACK", style = RelayType.Metadata, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun ThemePackSettings(
    settings: RelaySettings,
    onImportThemePack: (() -> Unit)?,
    onApplyThemePack: (String?) -> Unit,
    onRemoveThemePack: (String) -> Unit,
) {
    Column {
        BasicText("THEME PACKS", style = RelayType.Track, modifier = Modifier.padding(bottom = 8.dp))
        BasicText("Theme Packs are data-only colors and assets. They cannot run code.", style = RelayType.Metadata)
        onImportThemePack?.let {
            TransportAction(
                label = "IMPORT THEME PACK",
                description = "Import a theme pack JSON file",
                enabled = true,
                onClick = it,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        TransportAction(
            label = if (settings.activeThemePackId == null) "RELAY DEFAULT · ACTIVE" else "RESET TO RELAY DEFAULT",
            description = "Use Relay's built-in colors",
            enabled = settings.activeThemePackId != null,
            onClick = { onApplyThemePack(null) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        settings.themePacks.forEach { pack ->
            val active = pack.id == settings.activeThemePackId
            val builtIn = dev.relay.music.extension.isBuiltInThemePack(pack.id)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .border(1.dp, if (active) RelayColors.Signal else RelayColors.Line)
                    .padding(12.dp),
            ) {
                BasicText(
                    pack.name.uppercase() + if (active) " · ACTIVE" else "",
                    style = RelayType.Track.copy(color = if (active) RelayColors.Signal else RelayColors.Paper),
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    listOf(
                        pack.colors.ink, pack.colors.panel, pack.colors.line, pack.colors.paper,
                        pack.colors.muted, pack.colors.signal, pack.colors.danger,
                    ).forEach { hex ->
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(24.dp)
                                .background(hex.asThemeColor(RelayColors.Ink))
                                .border(1.dp, RelayColors.Line),
                        )
                    }
                }
                pack.presentation.effects.takeIf { it.isNotEmpty() }?.let { effects ->
                    BasicText(
                        "EFFECTS " + effects.joinToString(" · ") { "${it.kind.name} ${it.strength}" },
                        style = RelayType.Utility.copy(color = RelayColors.Muted),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TransportAction(
                        label = "APPLY",
                        description = "Apply theme ${pack.name}",
                        enabled = !active,
                        onClick = { onApplyThemePack(pack.id) },
                        modifier = Modifier.weight(1f),
                    )
                    if (!builtIn) {
                        TransportAction(
                            label = "REMOVE",
                            description = "Remove theme ${pack.name}",
                            enabled = true,
                            onClick = { onRemoveThemePack(pack.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
