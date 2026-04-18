package com.arflix.tv.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StreamRepositoryCloudstreamCompatibilityTest {

    @Test
    fun `cloudstream plugin api below supported is accepted`() {
        val isSupported = StreamRepository.isCloudstreamPluginApiVersionSupported(
            pluginApiVersion = 0,
            supportedApiVersion = 1
        )

        assertTrue(isSupported)
    }

    @Test
    fun `cloudstream plugin api equal supported is accepted`() {
        val isSupported = StreamRepository.isCloudstreamPluginApiVersionSupported(
            pluginApiVersion = 1,
            supportedApiVersion = 1
        )

        assertTrue(isSupported)
    }

    @Test
    fun `cloudstream plugin api above supported is rejected by install validation`() {
        val isSupported = StreamRepository.isCloudstreamPluginApiVersionSupported(
            pluginApiVersion = 2,
            supportedApiVersion = 1
        )

        assertFalse(isSupported)

        try {
            StreamRepository.requireSupportedCloudstreamPluginApiVersion(
                pluginApiVersion = 2,
                supportedApiVersion = 1
            )
            fail("Expected install compatibility validation to reject API versions above supported")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
