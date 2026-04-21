package com.arflix.tv.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudstreamPluginPickerCompatibilityTest {

    @Test
    fun `unsupported label is null when plugin api is below supported`() {
        val label = cloudstreamPluginUnsupportedLabel(
            pluginApiVersion = 0,
            supportedApiVersion = 1
        )

        assertNull(label)
    }

    @Test
    fun `unsupported label is null when plugin api equals supported`() {
        val label = cloudstreamPluginUnsupportedLabel(
            pluginApiVersion = 1,
            supportedApiVersion = 1
        )

        assertNull(label)
    }

    @Test
    fun `unsupported label is shown when plugin api is above supported`() {
        val label = cloudstreamPluginUnsupportedLabel(
            pluginApiVersion = 2,
            supportedApiVersion = 1
        )

        assertEquals("Unsupported API v2 (app supports up to v1)", label)
    }
}
