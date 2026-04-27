package com.arflix.tv.data.repository

import android.content.Context
import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.StreamSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play-flavor no-op stub. Play Store distribution policy restricts dynamic
 * code loading, so the real CloudStream runtime — which does
 * `DexClassLoader` on downloaded `.cs3` files — only exists in the sideload
 * flavor source set (`app/src/sideload/kotlin/...`). This stub shares the
 * same package + class name + public signature so `StreamRepository`'s Hilt
 * injection compiles identically for both flavors; the play APK therefore
 * ships zero dynamic-code-loading bytecode.
 */
@Singleton
class CloudstreamProviderRuntime @Inject constructor(
    @Suppress("UNUSED_PARAMETER") @ApplicationContext context: Context
) {
    suspend fun resolveMovieStreams(
        addons: List<Addon>,
        imdbId: String? = null,
        title: String,
        year: Int?
    ): List<StreamSource> = emptyList()

    suspend fun resolveEpisodeStreams(
        addons: List<Addon>,
        imdbId: String? = null,
        title: String,
        year: Int?,
        season: Int,
        episode: Int,
        airDate: String?
    ): List<StreamSource> = emptyList()
}
