package com.arflix.tv.data.repository

import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.AddonType
import com.arflix.tv.data.model.RuntimeKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudstreamProviderRuntimeTest {
    @Test
    fun `resolveMovieStreams maps plugin streams into StreamSource`() = runTest {
        val runtime = CloudstreamProviderRuntime(
            artifactExecutor = object : CloudstreamArtifactExecutor {
                override suspend fun resolveMovieStreams(
                    artifactPath: String,
                    request: CloudstreamMovieRequest
                ): List<CloudstreamResolvedStream> {
                    return listOf(
                        CloudstreamResolvedStream(
                            name = "ProviderLink",
                            quality = "1080p",
                            size = "1.4 GB",
                            sizeBytes = 1_400_000_000L,
                            url = "https://video.example/movie.m3u8",
                            sourceUrls = listOf("https://tracker.example")
                        )
                    )
                }

                override suspend fun resolveEpisodeStreams(
                    artifactPath: String,
                    request: CloudstreamEpisodeRequest
                ): List<CloudstreamResolvedStream> = emptyList()
            }
        )

        val streams = runtime.resolveMovieStreams(
            addons = listOf(testAddon(id = "cloud.1", artifactPath = "/tmp/provider.cs3")),
            title = "Movie",
            year = 2024
        )

        assertEquals(1, streams.size)
        assertEquals("ProviderLink", streams[0].source)
        assertEquals("Cloud Plugin", streams[0].addonName)
        assertEquals("cloud.1", streams[0].addonId)
        assertEquals("https://video.example/movie.m3u8", streams[0].url)
    }

    @Test
    fun `resolveEpisodeStreams continues when one provider crashes`() = runTest {
        val runtime = CloudstreamProviderRuntime(
            artifactExecutor = object : CloudstreamArtifactExecutor {
                override suspend fun resolveMovieStreams(
                    artifactPath: String,
                    request: CloudstreamMovieRequest
                ): List<CloudstreamResolvedStream> = emptyList()

                override suspend fun resolveEpisodeStreams(
                    artifactPath: String,
                    request: CloudstreamEpisodeRequest
                ): List<CloudstreamResolvedStream> {
                    if (artifactPath.contains("bad")) error("Plugin crash")
                    return listOf(
                        CloudstreamResolvedStream(
                            name = "HealthyPlugin",
                            quality = "720p",
                            size = "850 MB",
                            url = "https://video.example/episode.m3u8"
                        )
                    )
                }
            }
        )

        val streams = runtime.resolveEpisodeStreams(
            addons = listOf(
                testAddon(id = "cloud.bad", artifactPath = "/tmp/bad.cs3"),
                testAddon(id = "cloud.good", artifactPath = "/tmp/good.cs3")
            ),
            title = "Series",
            year = 2024,
            season = 1,
            episode = 2,
            airDate = "2024-03-20"
        )

        assertEquals(1, streams.size)
        assertTrue(streams.none { it.addonId == "cloud.bad" })
        assertEquals("cloud.good", streams.first().addonId)
    }

    private fun testAddon(id: String, artifactPath: String): Addon {
        return Addon(
            id = id,
            name = "Cloud Plugin",
            version = "1.0.0",
            description = "Cloudstream plugin",
            isInstalled = true,
            isEnabled = true,
            type = AddonType.CUSTOM,
            runtimeKind = RuntimeKind.CLOUDSTREAM,
            installedArtifactPath = artifactPath
        )
    }
}
