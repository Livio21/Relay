package dev.relay.music.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RepositoryDescriptorClientTest {
    @Test
    fun descriptorParsesAndValidatesBeforeTrusting() {
        val descriptor = parseRepositoryDescriptor(
            """{"schemaVersion":1,"repositoryId":"example.relay","name":"Example","indexUrl":"https://example.invalid/index.json","signingPublicKey":"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEKJJ/1DqMnRtwGEYeFdYiK1zApw7kd66WSJKeTHD74lGWS1zlEL/5kiJzOoJu7Rn5KXdlEJBo2/mDNW8MVnlSjA==","signingAlgorithm":"ECDSA_P256_SHA256"}""",
        )

        assertEquals("example.relay", descriptor.id)
        assertEquals("Example", descriptor.name)
    }

    @Test
    fun descriptorRejectsInsecureCatalogs() {
        assertFailsWith<IllegalArgumentException> {
            parseRepositoryDescriptor(
                """{"schemaVersion":1,"repositoryId":"example.relay","name":"Example","indexUrl":"http://example.invalid/index.json","signingPublicKey":"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEKJJ/1DqMnRtwGEYeFdYiK1zApw7kd66WSJKeTHD74lGWS1zlEL/5kiJzOoJu7Rn5KXdlEJBo2/mDNW8MVnlSjA=="}""",
            )
        }
    }
}
