package dev.relay.music.library

import dev.relay.music.extension.ApiRange
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.ExtensionKind
import dev.relay.music.extension.ExtensionPermission
import dev.relay.music.extension.asInstalled
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class RelayBackupArchiveTest {
    @Test
    fun archiveRoundTripIncludesSourceSettingsButNotStorageRoot() {
        val backup = emptyBackup(
            favorites = listOf(FavoriteTrackEntity("local", "track-1")),
            settings = RelaySettingsEntity(
                storageRootUri = "content://device-only",
                sourceSettingsJson = """{"example.source":{"quality":"lossless"}}""",
            ),
        )

        val contents = RelayBackupArchive.inspect(ByteArrayInputStream(write(backup)))

        assertEquals(RelayBackupSection.all, contents.sections)
        assertEquals(backup.favorites, contents.backup.favorites)
        assertEquals(mapOf("quality" to "lossless"), contents.backup.settings?.sourceSettings()?.get("example.source"))
        assertEquals(null, contents.backup.settings?.storageRootUri)
    }

    @Test
    fun changedPayloadFailsChecksumPreflight() {
        val archive = write(emptyBackup(favorites = listOf(FavoriteTrackEntity("local", "track-1"))))
        val changed = rewrite(archive) { name, bytes ->
            if (name == "library.json") bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() } else bytes
        }

        assertFailsWith<IllegalArgumentException> {
            RelayBackupArchive.inspect(ByteArrayInputStream(changed))
        }
    }

    @Test
    fun futureArchiveVersionFailsBeforePayloadIsAccepted() {
        val changed = rewrite(write(emptyBackup())) { name, bytes ->
            if (name == "manifest.json") {
                JSONObject(bytes.toString(Charsets.UTF_8))
                    .put("schemaVersion", Int.MAX_VALUE)
                    .toString()
                    .toByteArray()
            } else {
                bytes
            }
        }

        assertFailsWith<IllegalArgumentException> {
            RelayBackupArchive.inspect(ByteArrayInputStream(changed))
        }
    }

    @Test
    fun legacyVersionOneArchiveRemainsReadable() {
        val backup = emptyBackup(favorites = listOf(FavoriteTrackEntity("local", "legacy")))
        val legacy = rewrite(write(backup)) { name, bytes ->
            if (name == "manifest.json") {
                JSONObject(bytes.toString(Charsets.UTF_8))
                    .put("schemaVersion", 1)
                    .apply { remove("backupSections") }
                    .toString()
                    .toByteArray()
            } else {
                bytes
            }
        }

        assertEquals(backup.favorites, RelayBackupArchive.inspect(ByteArrayInputStream(legacy)).backup.favorites)
    }

    @Test
    fun partialArchiveContainsAndPlansOnlySelectedSections() {
        val backup = emptyBackup(
            favorites = listOf(FavoriteTrackEntity("local", "favorite")),
            history = listOf(ListeningHistoryEntity(sourceId = "local", trackId = "played", playedAtEpochMs = 10)),
            settings = RelaySettingsEntity(sourceSettingsJson = """{"example.source":{"quality":"high"}}"""),
        )
        val selected = setOf(RelayBackupSection.HISTORY, RelayBackupSection.EXTENSIONS)

        val contents = RelayBackupArchive.inspect(ByteArrayInputStream(write(backup, selected)))
        val plan = contents.restorePlan(setOf(RelayBackupSection.HISTORY))

        assertEquals(selected, contents.sections)
        assertTrue(contents.backup.favorites.isEmpty())
        assertEquals(backup.history, contents.backup.history)
        assertEquals(setOf(RelayBackupSection.HISTORY), plan.selectedSections)
        assertFailsWith<IllegalArgumentException> {
            contents.restorePlan(setOf(RelayBackupSection.PLAYLISTS))
        }
    }

    @Test
    fun restorePlanReportsMissingDependenciesAndTrackerReconnect() {
        val installed = testCatalogEntry().asInstalled("missing.repository")
        val backup = emptyBackup(
            settings = RelaySettingsEntity(installedExtensionsJson = listOf(installed).toInstalledExtensionsJson()),
            profile = LocalProfileEntity(displayName = "Relay", createdAtEpochMs = 1, lastFmUsername = "listener"),
        )
        val contents = RelayBackupArchive.inspect(
            ByteArrayInputStream(write(backup, setOf(RelayBackupSection.EXTENSIONS, RelayBackupSection.PROFILE))),
        )

        val plan = contents.restorePlan(installedAndroidPackages = emptySet())

        assertEquals(listOf("missing.repository"), plan.missingRepositories)
        assertEquals(listOf("example.source"), plan.missingPlugins)
        assertEquals(listOf("Last.fm (listener)"), plan.trackersRequiringReconnect)
        assertTrue(plan.warnings.any { "disabled" in it })
        assertTrue(plan.warnings.any { "signing identities" in it })
    }

    @Test
    fun restoredExtensionEnablementIsNeverAppliedSilently() {
        val installed = testCatalogEntry().asInstalled("example.repository")

        val restored = RelaySettingsEntity(
            installedExtensionsJson = listOf(installed).toInstalledExtensionsJson(),
        ).withRestoredExtensionsDisabled().installedExtensions().single()

        assertFalse(restored.enabled)
        assertTrue(restored.disabledReason.orEmpty().contains("review"))
    }

    @Test
    fun malformedQueueIsRejectedBeforeRestore() {
        val backup = emptyBackup().copy(
            queueEntries = listOf(QueueEntryEntity(position = 1, sourceId = "local", trackId = "1")),
            queueState = QueueStateEntity(currentIndex = 0, positionMs = 0),
        )

        assertFailsWith<IllegalArgumentException> { backup.validate() }
    }

    @Test
    fun installedExtensionCatalogSnapshotSurvivesSettingsRoundTrip() {
        val entry = testCatalogEntry()
        val installed = entry.asInstalled("example.repository")

        assertEquals(
            listOf(installed),
            RelaySettingsEntity(installedExtensionsJson = listOf(installed).toInstalledExtensionsJson()).installedExtensions(),
        )
    }

    @Test
    fun futureOrMalformedSettingsFailPreflight() {
        assertFailsWith<IllegalArgumentException> {
            emptyBackup(settings = RelaySettingsEntity(schemaVersion = Int.MAX_VALUE)).validate()
        }
        assertFails {
            emptyBackup(settings = RelaySettingsEntity(sourceSettingsJson = "{not-json")).validate()
        }
    }

    @Test
    fun oversizedMetadataFailsPreflight() {
        val metadata = MetadataOverrideEntity(
            sourceId = "local",
            trackId = "1",
            title = "x".repeat(1_025),
            artist = null,
            album = null,
            albumArtist = null,
            artworkUri = null,
            musicBrainzId = null,
            trackNumber = null,
            discNumber = null,
        )

        assertFailsWith<IllegalArgumentException> {
            emptyBackup(metadataOverrides = listOf(metadata)).validate()
        }
    }

    private fun emptyBackup(
        favorites: List<FavoriteTrackEntity> = emptyList(),
        history: List<ListeningHistoryEntity> = emptyList(),
        metadataOverrides: List<MetadataOverrideEntity> = emptyList(),
        settings: RelaySettingsEntity? = null,
        profile: LocalProfileEntity? = null,
    ) = UserLibraryBackup(
        favorites = favorites,
        history = history,
        flags = emptyList(),
        playlists = emptyList(),
        playlistEntries = emptyList(),
        queueEntries = emptyList(),
        queueState = null,
        metadataOverrides = metadataOverrides,
        settings = settings,
        profile = profile,
    )

    private fun testCatalogEntry() = ExtensionCatalogEntry(
        id = "example.source",
        name = "Example Source",
        version = "1.2.3",
        kind = ExtensionKind.SOURCE,
        api = ApiRange(2, 2),
        artifactUrl = "https://example.invalid/source.apk",
        artifactSha256 = "a".repeat(64),
        artifactSizeBytes = 42,
        permissions = setOf(ExtensionPermission.NETWORK),
        androidPackageName = "dev.relay.example.source",
        androidSigningCertificateSha256 = "b".repeat(64),
        supportUrl = "https://example.invalid/support",
    )

    private fun write(
        backup: UserLibraryBackup,
        sections: Set<RelayBackupSection> = RelayBackupSection.all,
    ): ByteArray = ByteArrayOutputStream().also { RelayBackupArchive.write(it, backup, sections) }.toByteArray()

    private fun rewrite(archive: ByteArray, transform: (String, ByteArray) -> ByteArray): ByteArray {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += entry.name to zip.readBytes()
                zip.closeEntry()
            }
        }
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(transform(name, bytes))
                    zip.closeEntry()
                }
            }
        }.toByteArray()
    }
}
