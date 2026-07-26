package dev.relay.music

import dev.relay.music.update.ComponentIdentity
import dev.relay.music.update.ComponentUpdateStatus
import dev.relay.music.update.InstalledComponent
import dev.relay.music.update.AvailableComponent
import dev.relay.music.update.UpdatableComponentKind
import dev.relay.music.update.componentUpdateStatus
import dev.relay.music.update.findComponentUpdates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentUpdatesTest {
    private val identity = ComponentIdentity(UpdatableComponentKind.EXTENSION, "relay.sources", "demo")

    @Test
    fun classifiesSemverAndKeepsUncertainChangesExplicit() {
        assertEquals(ComponentUpdateStatus.UPDATE_AVAILABLE, componentUpdateStatus("1.0.0", "1.0.1", true))
        assertEquals(ComponentUpdateStatus.UPDATE_AVAILABLE, componentUpdateStatus("1.0.0-beta.2", "1.0.0", true))
        assertEquals(ComponentUpdateStatus.DOWNGRADE, componentUpdateStatus("2.0.0", "1.0.0", true))
        assertEquals(ComponentUpdateStatus.VERSION_CHANGE, componentUpdateStatus("nightly", "2026-07-26", true))
        assertEquals(ComponentUpdateStatus.INCOMPATIBLE, componentUpdateStatus("1.0.0", "2.0.0", false))
    }

    @Test
    fun matchesOnlyTheSameComponentIdentity() {
        val updates = findComponentUpdates(
            installed = listOf(InstalledComponent(identity, "1.0.0")),
            candidates = listOf(
                AvailableComponent(identity, "1.1.0", true, "next"),
                AvailableComponent(identity.copy(id = "other"), "3.0.0", true, "other"),
            ),
        )

        assertEquals(1, updates.size)
        assertEquals("next", updates.single().candidate.payload)
        assertTrue(updates.single().isActionable)
    }
}
