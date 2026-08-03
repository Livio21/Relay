package dev.relay.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun backRestoresEveryExactDestinationThenHandsOffAtNowPlaying() {
        val destinations = listOf(
            RelayDestination(LIBRARY_VIEW),
            RelayDestination(PLAYLISTS_VIEW, selectedPlaylistId = 5),
            RelayDestination(SETTINGS_VIEW),
            RelayDestination(SETTINGS_VIEW, settingsSubmenu = SettingsSubmenu.PLAYBACK),
            RelayDestination(EXTENSIONS_VIEW),
            RelayDestination(EXTENSIONS_VIEW, extensionsTab = ExtensionsTab.AVAILABLE),
            RelayDestination(EXTENSIONS_VIEW, extensionsSubmenu = ExtensionsSubmenu.DETAILS),
            RelayDestination(NOW_PLAYING_VIEW, queueOpen = true),
            RelayDestination(NOW_PLAYING_VIEW, lyricsOpen = true, playerOptionsOpen = true),
        )
        var navigation = RelayNavigationState()
        destinations.forEach { navigation = navigation.navigate(it) }

        (listOf(RelayDestination(NOW_PLAYING_VIEW)) + destinations).asReversed().forEach { expected ->
            assertEquals(expected, navigation.destination)
            navigation = navigation.back() ?: return@forEach
        }

        assertEquals(RelayDestination(NOW_PLAYING_VIEW), navigation.destination)
        assertFalse(navigation.canGoBack)
        assertNull(navigation.back())
    }

    @Test
    fun navigationIgnoresDuplicatesAndBoundsHistory() {
        val root = RelayNavigationState()
        assertTrue(root.navigate(root.destination) === root)

        var navigation = root
        repeat(100) { index ->
            navigation = navigation.navigate(
                RelayDestination(if (index % 2 == 0) LIBRARY_VIEW else SETTINGS_VIEW),
            )
        }
        assertEquals(64, navigation.backStack.size)
    }
}
