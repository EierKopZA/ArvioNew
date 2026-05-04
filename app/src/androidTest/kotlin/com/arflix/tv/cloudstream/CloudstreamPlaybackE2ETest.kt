package com.arflix.tv.cloudstream

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.di.RepositoryAccessEntryPoint
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.Profile
import com.arflix.tv.data.model.ProfileColors
import com.arflix.tv.data.model.RuntimeKind
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.ProfileRepository
import com.arflix.tv.data.repository.StreamRepository
import com.arflix.tv.util.Constants
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CloudstreamPlaybackE2ETest {

    private data class MovieProbe(
        val query: String,
        val title: String,
        val year: Int
    )

    private data class ResolvedMovieCandidate(
        val mediaId: Int,
        val title: String,
        val year: Int,
        val imdbId: String,
        val addonId: String,
        val addonName: String,
        val streamTitle: String,
        val stream: StreamSource
    )

    @Test
    fun phisherMovieSourceResolvesForPlayback() {
        val deps = entryPoint()
        val candidate = runBlocking {
            val profile = ensureFreshProfile(deps)
            println("Using test profile ${profile.name} (${profile.id})")

            val installedAddons = installValidationPlugins(deps.streamRepository())
            assertTrue("Expected CloudStream plugins to install for validation", installedAddons.isNotEmpty())

            val resolved = findWorkingMovieCandidate(deps, installedAddons)
            assertNotNull("No CloudStream movie candidate resolved from installed providers", resolved)
            resolved!!
        }

        val playbackReady = runBlocking {
            deps.streamRepository().resolveStreamForPlayback(candidate.stream)
                ?: candidate.stream
        }
        val playbackUrl = playbackReady.url?.trim().orEmpty()
        assertTrue(
            "Expected resolved CloudStream source to expose a HTTP playback URL",
            playbackUrl.startsWith("http://", ignoreCase = true) ||
                playbackUrl.startsWith("https://", ignoreCase = true)
        )

        println(
            "Verified CloudStream source is playback-ready addon=${candidate.addonName} " +
                "title=${candidate.title} stream=${candidate.streamTitle} url=${playbackUrl.take(80)}"
        )
    }

    private fun entryPoint(): RepositoryAccessEntryPoint {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        return EntryPointAccessors.fromApplication(context, RepositoryAccessEntryPoint::class.java)
    }

    private suspend fun ensureFreshProfile(deps: RepositoryAccessEntryPoint): Profile {
        val profile = deps.profileRepository().createProfile(
            name = "Cloudstream E2E ${System.currentTimeMillis()}",
            avatarColor = ProfileColors.colors.first()
        )
        deps.profileRepository().setActiveProfile(profile.id)
        deps.profileManager().setCurrentProfileId(profile.id)
        deps.profileManager().setCurrentProfileName(profile.name)
        return profile
    }

    private suspend fun installValidationPlugins(streamRepository: StreamRepository): List<Addon> {
        val (repoUrl, manifest, plugins) = streamRepository
            .addCloudstreamRepository(CLOUDSTREAM_REPO_URL)
            .getOrThrow()

        val installed = mutableListOf<Addon>()
        for (internalName in PRIORITY_PLUGIN_NAMES) {
            val plugin = plugins.firstOrNull { it.internalName.equals(internalName, ignoreCase = true) }
                ?: continue
            val addon = streamRepository.installCloudstreamPlugin(repoUrl, manifest, plugin).getOrThrow()
            installed += addon
        }
        return installed
    }

    private suspend fun findWorkingMovieCandidate(
        deps: RepositoryAccessEntryPoint,
        installedAddons: List<Addon>
    ): ResolvedMovieCandidate? {
        val installedAddonIds = installedAddons.map { it.id }.toSet()

        for (probe in MOVIE_PROBES) {
            val item = findBestSearchMatch(
                mediaRepository = deps.mediaRepository(),
                query = probe.query,
                expectedTitle = probe.title,
                expectedYear = probe.year,
                mediaType = MediaType.MOVIE
            ) ?: continue

            val imdbId = deps.tmdbApi()
                .getMovieExternalIds(item.id, Constants.TMDB_API_KEY)
                .imdbId
                ?.trim()
                .takeUnless { it.isNullOrBlank() }
                ?: continue

            val result = withTimeout(90_000L) {
                deps.streamRepository()
                    .resolveMovieStreamsProgressive(
                        imdbId = imdbId,
                        title = item.title,
                        year = probe.year
                    )
                    .first { it.isFinal }
            }

            val stream = result.streams.firstOrNull { it.addonId in installedAddonIds }
                ?: continue
            val displayTitle = streamDisplayTitle(stream)
            if (displayTitle.isBlank()) continue

            println(
                "Resolved movie candidate title=${item.title} year=${probe.year} " +
                    "addon=${stream.addonName} stream=$displayTitle"
            )

            return ResolvedMovieCandidate(
                mediaId = item.id,
                title = item.title,
                year = probe.year,
                imdbId = imdbId,
                addonId = stream.addonId,
                addonName = stream.addonName.split(" - ").firstOrNull()?.trim().orEmpty()
                    .ifBlank { stream.addonName },
                streamTitle = displayTitle.take(80),
                stream = stream
            )
        }

        return null
    }

    private suspend fun findBestSearchMatch(
        mediaRepository: MediaRepository,
        query: String,
        expectedTitle: String,
        expectedYear: Int,
        mediaType: MediaType
    ): MediaItem? {
        val results = mediaRepository.search(query)
            .filter { it.mediaType == mediaType }
        if (results.isEmpty()) return null

        val normalizedExpected = normalizeTitle(expectedTitle)
        return results.firstOrNull {
            normalizeTitle(it.title) == normalizedExpected && it.year == expectedYear.toString()
        } ?: results.firstOrNull {
            it.year == expectedYear.toString() &&
                normalizeTitle(it.title).contains(normalizedExpected)
        } ?: results.firstOrNull {
            normalizeTitle(it.title) == normalizedExpected
        }
    }

    private fun streamDisplayTitle(stream: StreamSource): String {
        return stream.behaviorHints?.filename
            ?.takeIf { it.isNotBlank() }
            ?: stream.source
    }

    private fun normalizeTitle(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    companion object {
        private const val CLOUDSTREAM_REPO_URL =
            "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json"

        private val PRIORITY_PLUGIN_NAMES = listOf(
            "HDhub4u",
            "FourKHDHub",
            "UHDmoviesProvider",
            "AllMovieLandProvider",
            "AllWish"
        )

        private val MOVIE_PROBES = listOf(
            MovieProbe(query = "Pushpa 2 The Rule", title = "Pushpa 2: The Rule", year = 2024),
            MovieProbe(query = "Jawan", title = "Jawan", year = 2023),
            MovieProbe(query = "Animal", title = "Animal", year = 2023),
            MovieProbe(query = "Stree 2", title = "Stree 2", year = 2024),
            MovieProbe(query = "Dune Part Two", title = "Dune: Part Two", year = 2024),
            MovieProbe(query = "Inception", title = "Inception", year = 2010)
        )
    }
}
