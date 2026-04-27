package com.arflix.tv.data.repository

import com.arflix.tv.data.model.CloudstreamPluginIndexEntry
import com.arflix.tv.data.model.CloudstreamRepositoryManifest
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudstreamRepositoryService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()

    private val repositoryShortcuts = mapOf(
        "cspr" to "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
        "0094" to "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
        "megarepo" to "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json",
        "3737" to "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json",
        "phisherrepo" to "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json",
        "864" to "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json",
        "csx" to "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json",
        "3670" to "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json",
        "arb" to "https://raw.githubusercontent.com/Abodabodd/re-3arabi/refs/heads/main/repo",
        "343" to "https://raw.githubusercontent.com/Abodabodd/re-3arabi/refs/heads/main/repo",
        "indos" to "https://raw.githubusercontent.com/TeKuma25/IndoStream/builds/repo.json",
        "2149" to "https://raw.githubusercontent.com/TeKuma25/IndoStream/builds/repo.json",
        "extcloud" to "https://raw.githubusercontent.com/duro92/ExtCloud/main/repo.json",
        "cloudx" to "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/repo.json",
        "gior" to "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/builds/repo.json",
        "1740" to "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/builds/repo.json",
        "cncv" to "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/CNC.json",
        "diegon7" to "https://pastebin.com/raw/qndZtL6D",
        "cskarma" to "https://raw.githubusercontent.com/Kraptor123/cs-Karma/refs/heads/master/repo.json",
        "cakes" to "https://codeberg.org/CakesTwix/cloudstream-extensions-uk/raw/branch/master/repo.json",
        "sarapcanagii" to "https://raw.githubusercontent.com/sarapcanagii/Pitipitii/master/repo.json",
        "kingl" to "https://pastebin.com/raw/Cd2g2tfz",
        "ipr" to "https://raw.githubusercontent.com/Gian-Fr/ItalianProvider/builds/repo.json",
        "netmirror" to "https://raw.githubusercontent.com/Sushan64/NetMirror-Extension/refs/heads/builds/Netflix.json",
        "redowan" to "https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json",
        "saim" to "https://raw.githubusercontent.com/saimuelbr/saimuelrepo/refs/heads/main/builds/repo.json",
        "cuxplug" to "https://raw.githubusercontent.com/ycngmn/CuxPlug/refs/heads/main/repo.json",
        "viet" to "https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/repo.json",
        "kraptorcs" to "https://raw.githubusercontent.com/Kraptor123/cs-kraptor/refs/heads/master/repo.json",
        "gpr" to "https://raw.githubusercontent.com/Bnyro/GermanProviders/refs/heads/master/repo.json",
        "luna712" to "https://raw.githubusercontent.com/Luna712/Luna712-CloudStream-Extensions/28885d17ceb7f24782b732b6056085c14c1fd027/repo.json",
        "zzikozz" to "https://raw.githubusercontent.com/zzikozz/frenchCS/refs/heads/main/repo.json",
        "gramflix" to "https://raw.githubusercontent.com/tOntOnbOuLii/GramFlix/main/repo.json"
    )

    private fun requireHttpsUrl(url: String, errorMessage: String): String {
        val parsed = URI(url)
        require(parsed.scheme.equals("https", ignoreCase = true)) { errorMessage }
        return parsed.toString()
    }

    private fun resolvePluginListUrl(baseRepositoryUrl: String, pluginListUrl: String): String {
        val trimmed = pluginListUrl.trim()
        require(trimmed.isNotBlank()) { "Repository contains an empty plugin list URL" }
        val resolved = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            URL(URL(baseRepositoryUrl), trimmed).toString()
        }
        return requireHttpsUrl(resolved, "Plugin list URLs must use HTTPS")
    }

    private fun resolvePluginPackageUrl(basePluginListUrl: String, packageUrl: String): String {
        val trimmed = packageUrl.trim()
        require(trimmed.isNotBlank()) { "Plugin package URL is empty" }
        val resolved = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            URL(URL(basePluginListUrl), trimmed).toString()
        }
        return requireHttpsUrl(resolved, "Plugin packages must use HTTPS")
    }

    private fun repairLegacyGithubRawUrl(url: String): String {
        val parsed = URI(url)
        if (!parsed.host.equals("raw.githubusercontent.com", ignoreCase = true)) {
            return parsed.toString()
        }

        val segments = parsed.path
            .split('/')
            .filter { it.isNotBlank() }
        if (segments.size != 3) {
            return parsed.toString()
        }

        val repoAndBranch = segments[1]
        val splitIndex = repoAndBranch.lastIndexOf('_')
        if (splitIndex <= 0 || splitIndex >= repoAndBranch.lastIndex) {
            return parsed.toString()
        }

        val repo = repoAndBranch.substring(0, splitIndex)
        val branch = repoAndBranch.substring(splitIndex + 1)
        val repairedPath = "/" + listOf(segments[0], repo, branch, segments[2]).joinToString("/")
        return URI(parsed.scheme, parsed.authority, repairedPath, parsed.query, parsed.fragment).toString()
    }

    private fun normalizeRepositoryUrlValue(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotBlank()) { "Repository URL is empty" }
        repositoryShortcuts[trimmed.lowercase()]?.let { return it }
        val cloudstreamRepoPrefix = "cloudstreamrepo://"
        val csRepoPrefix = "https://cs.repo/"
        val expanded = when {
            trimmed.startsWith(cloudstreamRepoPrefix, ignoreCase = true) ->
                trimmed.substring(cloudstreamRepoPrefix.length).let { shortcutOrUrl ->
                    repositoryShortcuts[shortcutOrUrl.trim().lowercase()]
                        ?: "https://$shortcutOrUrl"
                }
            trimmed.startsWith(csRepoPrefix, ignoreCase = true) ->
                trimmed.substring(csRepoPrefix.length).let { decoded ->
                    if (decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)) {
                        decoded
                    } else {
                        "https://$decoded"
                    }
                }
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
        val repaired = repairLegacyGithubRawUrl(expanded)
        return requireHttpsUrl(repaired, "Cloudstream repositories must use HTTPS")
    }

    fun normalizeStoredRepositoryUrl(rawUrl: String?): String? {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return runCatching { normalizeRepositoryUrlValue(trimmed) }
            .getOrDefault(trimmed)
    }

    suspend fun normalizeRepositoryUrl(rawUrl: String): String = withContext(Dispatchers.Default) {
        normalizeRepositoryUrlValue(rawUrl)
    }

    suspend fun fetchRepositoryManifest(url: String): CloudstreamRepositoryManifest = withContext(Dispatchers.IO) {
        val normalized = normalizeRepositoryUrl(url)
        val request = Request.Builder().url(normalized).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Failed to fetch repository (${response.code})" }
            val body = response.body?.string().orEmpty()
            try {
                val parsed = gson.fromJson(body, CloudstreamRepositoryManifest::class.java)
                require(parsed.name.isNotBlank()) { "Repository name missing" }
                require(parsed.manifestVersion > 0) { "Unsupported repository manifest version" }
                require(parsed.pluginLists.isNotEmpty()) { "Repository has no plugin lists" }
                parsed
            } catch (error: JsonSyntaxException) {
                throw IllegalArgumentException("Invalid repository manifest", error)
            }
        }
    }

    suspend fun fetchRepositoryPlugins(url: String): List<CloudstreamPluginIndexEntry> = withContext(Dispatchers.IO) {
        val normalizedRepoUrl = normalizeRepositoryUrl(url)
        val manifest = fetchRepositoryManifest(normalizedRepoUrl)
        manifest.pluginLists.flatMap { pluginListUrl ->
            val resolvedPluginListUrl = resolvePluginListUrl(normalizedRepoUrl, pluginListUrl)
            val request = Request.Builder().url(resolvedPluginListUrl).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                try {
                    gson.fromJson(body, Array<CloudstreamPluginIndexEntry>::class.java)
                        ?.toList()
                        .orEmpty()
                        .mapNotNull { entry ->
                            if (entry.internalName.isBlank() || entry.name.isBlank()) {
                                return@mapNotNull null
                            }
                            runCatching {
                                entry.copy(url = resolvePluginPackageUrl(resolvedPluginListUrl, entry.url))
                            }.getOrNull()
                        }
                } catch (_: JsonSyntaxException) {
                    emptyList()
                }
            }
        }.distinctBy { entry -> "${entry.internalName}:${entry.url}" }
    }

    suspend fun downloadPlugin(
        pluginUrl: String,
        destination: File
    ): File = withContext(Dispatchers.IO) {
        requireHttpsUrl(pluginUrl.trim(), "Plugin packages must use HTTPS")
        destination.parentFile?.mkdirs()
        val tmpDestination = File(destination.parentFile, "${destination.name}.tmp")
        if (tmpDestination.exists()) {
            tmpDestination.delete()
        }
        val request = Request.Builder().url(pluginUrl).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Failed to download plugin (${response.code})" }
            val body = response.body ?: error("Plugin download returned no body")
            FileOutputStream(tmpDestination).use { output ->
                body.byteStream().copyTo(output)
            }
        }
        if (destination.exists()) {
            destination.setWritable(true)
            destination.delete()
        }
        require(tmpDestination.renameTo(destination)) { "Failed to finalize plugin install" }
        destination.setReadOnly()
        destination
    }

    fun sanitizeFileName(value: String): String {
        val cleaned = value
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "plugin" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .take(4)
            .joinToString("") { "%02x".format(it) }
        return "${cleaned}_$digest"
    }
}
