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
    offlineDownloads: List<OfflineDownload> = emptyList(),
    onDeleteDownload: (String, String) -> Unit = { _, _ -> },
    onDeleteAllDownloads: () -> Unit = {},
    onPickShuffleSeed: ((String) -> Unit)? = null,
    onImportThemePack: (() -> Unit)? = null,
    onApplyThemePack: (String?) -> Unit = {},
    onRemoveThemePack: (String) -> Unit = {},
    submenu: SettingsSubmenu?,
    onSubmenuChange: (SettingsSubmenu?) -> Unit,
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
    }
}

internal enum class SettingsSubmenu { PLAYBACK, AUDIO, TRACKING, METADATA, STORAGE, BACKUP, REPOSITORIES, THEME_PACKS }

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
            if (settings.fadeInMs > 0 || settings.fadeOutMs > 0) {
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
            PlaybackPicker.FADE_IN, PlaybackPicker.FADE_OUT -> ValuePickerDialog(
                title = if (picker == PlaybackPicker.FADE_IN) "FADE IN" else "FADE OUT",
                choices = FADE_DURATIONS.map(::fadeLabel),
                selectedIndex = FADE_DURATIONS.indexOf(if (picker == PlaybackPicker.FADE_IN) settings.fadeInMs else settings.fadeOutMs),
                onSelect = { index ->
                    onAudioSettingsChange?.invoke(
                        if (picker == PlaybackPicker.FADE_IN) settings.copy(fadeInMs = FADE_DURATIONS[index]) else settings.copy(fadeOutMs = FADE_DURATIONS[index]),
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

private enum class PlaybackPicker { SPEED, FADE_IN, FADE_OUT, SHUFFLE_PROFILE }
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
                        "EFFECTS " + effects.joinToString(" · ") { "${it.kind.name} ${it.strength}" } + " — rendered in a later phase",
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
