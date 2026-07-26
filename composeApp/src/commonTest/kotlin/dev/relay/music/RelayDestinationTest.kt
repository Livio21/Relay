package dev.relay.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayDestinationTest {
    @Test
    fun plainPagesAreNotSubScreens() {
        assertFalse(RelayDestination(LIBRARY_VIEW).hasSubState)
        assertFalse(RelayDestination(PLAYLISTS_VIEW).hasSubState)
        // A playlist selected while another page is showing is leftover state, not a sub-screen.
        assertFalse(RelayDestination(LIBRARY_VIEW, selectedPlaylistId = 5).hasSubState)
    }

    @Test
    fun everySubScreenKindCountsAsSubState() {
        assertTrue(RelayDestination(PLAYLISTS_VIEW, selectedPlaylistId = 5).hasSubState)
        assertTrue(RelayDestination(NOW_PLAYING_VIEW, queueOpen = true).hasSubState)
        assertTrue(RelayDestination(SETTINGS_VIEW, settingsSubmenu = SettingsSubmenu.PLAYBACK).hasSubState)
        assertTrue(RelayDestination(EXTENSIONS_VIEW, extensionsSubmenu = ExtensionsSubmenu.SOURCE_SEARCH).hasSubState)
    }

    @Test
    fun movingToAPageClosesEverySubScreenIncludingAnOpenPlaylist() {
        val deep = RelayDestination(
            view = PLAYLISTS_VIEW,
            selectedPlaylistId = 5,
            settingsSubmenu = SettingsSubmenu.PLAYBACK,
            extensionsSubmenu = ExtensionsSubmenu.DETAILS,
            browsedExtensionId = "org.relay.extensions.fma",
            queueOpen = true,
        )

        val moved = deep.forView(LIBRARY_VIEW)

        assertEquals(RelayDestination(LIBRARY_VIEW), moved)
        assertFalse(moved.hasSubState)
    }
}
