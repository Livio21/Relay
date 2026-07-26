package dev.relay.music

import dev.relay.music.extension.repositoryDescriptorUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepositoryShorthandTest {
    private val expected = "https://raw.githubusercontent.com/Livio21/relay-extensions/main/repository.json"

    @Test
    fun ownerSlashRepoBecomesTheRawDescriptorUrl() {
        assertEquals(expected, repositoryDescriptorUrl("Livio21/relay-extensions"))
        assertEquals(expected, repositoryDescriptorUrl("  Livio21/relay-extensions  "))
    }

    @Test
    fun githubPageUrlsAreAccepted() {
        assertEquals(expected, repositoryDescriptorUrl("https://github.com/Livio21/relay-extensions"))
        assertEquals(expected, repositoryDescriptorUrl("https://github.com/Livio21/relay-extensions/"))
        assertEquals(expected, repositoryDescriptorUrl("https://github.com/Livio21/relay-extensions.git"))
        assertEquals(
            "https://raw.githubusercontent.com/Livio21/relay-extensions/dev/repository.json",
            repositoryDescriptorUrl("https://github.com/Livio21/relay-extensions/tree/dev"),
        )
    }

    @Test
    fun directHttpsLinksPassThroughUnchanged() {
        assertEquals(expected, repositoryDescriptorUrl(expected))
        assertEquals(
            "https://example.invalid/repository.json",
            repositoryDescriptorUrl("https://example.invalid/repository.json"),
        )
    }

    @Test
    fun anythingElseIsRejectedRatherThanGuessed() {
        assertNull(repositoryDescriptorUrl(""))
        assertNull(repositoryDescriptorUrl("   "))
        assertNull(repositoryDescriptorUrl("relay-extensions"))
        // Insecure and malformed input must never be silently upgraded to a trusted fetch.
        assertNull(repositoryDescriptorUrl("http://example.invalid/repository.json"))
        assertNull(repositoryDescriptorUrl("https://example.invalid/repo json"))
    }
}
