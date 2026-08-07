package dev.relay.music.settings

import dev.relay.music.extension.RepositoryDescriptor
import dev.relay.music.extension.ThemePack
import dev.relay.music.extension.builtInThemePacks
import dev.relay.music.playback.ShuffleProfile
import dev.relay.music.wallpaper.WallpaperPreset
import dev.relay.music.extension.InstalledExtension
import kotlinx.coroutines.flow.StateFlow

enum class BackupSchedule { OFF, WEEKLY, DAILY }

enum class EqualizerPreset {
    FLAT,
    BASS,
    TREBLE,
    VOCAL,
    CUSTOM,
}

const val EQUALIZER_BAND_COUNT = 5
const val EQUALIZER_MIN_LEVEL_MB = -1_200
const val EQUALIZER_MAX_LEVEL_MB = 1_200
const val RELAY_SETTINGS_SCHEMA_VERSION = 13

fun equalizerPresetLevels(preset: EqualizerPreset): List<Int> = when (preset) {
    EqualizerPreset.FLAT, EqualizerPreset.CUSTOM -> List(EQUALIZER_BAND_COUNT) { 0 }
    EqualizerPreset.BASS -> listOf(600, 400, 100, 0, -100)
    EqualizerPreset.TREBLE -> listOf(-100, 0, 100, 400, 600)
    EqualizerPreset.VOCAL -> listOf(-200, 100, 500, 200, -100)
}

fun normalizedEqualizerBands(levels: List<Int>): List<Int> = List(EQUALIZER_BAND_COUNT) { index ->
    levels.getOrNull(index)?.coerceIn(EQUALIZER_MIN_LEVEL_MB, EQUALIZER_MAX_LEVEL_MB) ?: 0
}

data class RelaySettings(
    val schemaVersion: Int = RELAY_SETTINGS_SCHEMA_VERSION,
    val resumeQueue: Boolean = true,
    val playbackSpeed: Float = 1f,
    val fadeInMs: Int = 0,
    val fadeOutMs: Int = 0,
    val crossfadeMs: Int = 0,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val equalizerBandLevels: List<Int> = List(EQUALIZER_BAND_COUNT) { 0 },
    val bassBoostStrength: Int = 0,
    val loudnessNormalization: Boolean = false,
    val shuffleProfiles: List<ShuffleProfile> = listOf(ShuffleProfile()),
    val activeShuffleProfileId: String = "default",
    val storageRootUri: String? = null,
    val backupSchedule: BackupSchedule = BackupSchedule.OFF,
    val autoBackupExpiryDays: Int = 30,
    val downloadStorageLimitGb: Int = 0,
    val downloadAutoCleanup: Boolean = false,
    val trustedRepositories: List<RepositoryDescriptor> = emptyList(),
    val installedExtensions: List<InstalledExtension> = emptyList(),
    /** Non-secret source preference values, keyed by extension ID then setting ID. */
    val sourceSettings: Map<String, Map<String, String>> = emptyMap(),
    /** Imported data-only theme packs and the one currently applied. */
    val themePacks: List<ThemePack> = builtInThemePacks,
    val activeThemePackId: String? = null,
    /** Lock-screen widgets hide title/artist unless the user opts in. */
    val showLockscreenMetadata: Boolean = false,
    /** Static system-wallpaper choices. No artwork, URLs, or playback data is persisted here. */
    val wallpaperPreset: WallpaperPreset = WallpaperPreset(),
)

fun RelaySettings.activeShuffleProfile(): ShuffleProfile =
    shuffleProfiles.firstOrNull { it.id == activeShuffleProfileId }
        ?: shuffleProfiles.firstOrNull()
        ?: ShuffleProfile()

fun RelaySettings.withActiveShuffleProfile(profile: ShuffleProfile): RelaySettings {
    val normalized = profile.copy(
        name = profile.name.trim().take(32).ifBlank { "PROFILE" },
        rules = profile.rules.distinct().take(6),
        seedSalt = profile.seedSalt.trim().take(64),
    )
    val profiles = shuffleProfiles.filterNot { it.id == normalized.id } + normalized
    return copy(shuffleProfiles = profiles, activeShuffleProfileId = normalized.id)
}

fun RelaySettings.withActiveShuffleProfileId(profileId: String): RelaySettings =
    copy(activeShuffleProfileId = shuffleProfiles.firstOrNull { it.id == profileId }?.id ?: activeShuffleProfile().id)

interface SettingsStore {
    val settings: StateFlow<RelaySettings>
    suspend fun save(settings: RelaySettings)
}
