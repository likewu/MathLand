package tech.ula.model.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tech.ula.utils.Logger
import tech.ula.utils.SentryLogger
import tech.ula.utils.UlaFiles
import java.io.IOException
import java.net.UnknownHostException

class UrlProvider {
    fun getBaseUrl(): String {
        return "https://api.github.com/"
    }
}

class GithubApiClient(
    private val ulaFiles: UlaFiles,
    private val urlProvider: UrlProvider = UrlProvider(),
    private val logger: Logger = SentryLogger()
) {
    private val baseUrl = "https://gitlab.com/api/v4/projects/30140969/packages/generic/UserLAnd-Assets-"

    private val client = OkHttpClient()
    private val latestResults: HashMap<String, ReleasesResponse?> = hashMapOf()

    // This function can be used to tune the release used for each asset type for testing purposes.
    private fun getReleaseToUseForRepo(repo: String): String {
        return when (repo) {
            "debian" -> "latest"
            else -> "latest"
        }
    }

    @Throws(IOException::class)
    suspend fun getAssetsListDownloadUrl(repo: String): String = withContext(Dispatchers.IO) {
        /*val result = latestResults[repo] ?: queryLatestRelease(repo)

        return@withContext result.assets.find { it.name == "${ulaFiles.getArchType()}-assets.txt" }!!.downloadUrl*/
        return@withContext baseUrl + repo.substring(0, 1).toUpperCase() + repo.substring(1) + "/latest/" + "${ulaFiles.getArchType()}-assets.txt"
    }

    @Throws(IOException::class)
    suspend fun getLatestReleaseVersion(repo: String): String = withContext(Dispatchers.IO) {
        /*val result = latestResults[repo] ?: queryLatestRelease(repo)

        return@withContext result.tag*/
        "v0.0.3"
    }

    @Throws(IOException::class)
    suspend fun getAssetEndpoint(assetType: String, repo: String): String = withContext(Dispatchers.IO) {
        /*val result = latestResults[repo] ?: queryLatestRelease(repo)
        val assetName = "${ulaFiles.getArchType()}-$assetType"

        return@withContext result.assets.find { it.name == assetName }!!.downloadUrl*/
        return@withContext baseUrl + repo.substring(0, 1).toUpperCase() + repo.substring(1) + "/latest/" + "${ulaFiles.getArchType()}-$assetType"
    }

    // Query latest release data and memoize results.
    @Throws(IOException::class, UnknownHostException::class)
    private suspend fun queryLatestRelease(repo: String): ReleasesResponse = withContext(Dispatchers.IO) {
        val releaseToUse = getReleaseToUseForRepo(repo)
        val base = urlProvider.getBaseUrl()
        val url = base + "repos/CypherpunkArmory/UserLAnd-Assets-$repo/releases/$releaseToUse"
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(ReleasesResponse::class.java)
        val request = Request.Builder()
                .url(url)
                .build()
        val response = try {
            client.newCall(request).execute()
        } catch (err: UnknownHostException) {
            logger.addExceptionBreadcrumb(err)
            throw err
        }
        if (!response.isSuccessful) {
            val err = IOException("Unexpected code: $response")
            logger.addExceptionBreadcrumb(err)
            throw err
        }

        val result = adapter.fromJson(response.body()!!.source())!!
        latestResults[repo] = result
        return@withContext result
    }

    @JsonClass(generateAdapter = true)
    internal data class ReleasesResponse(
        val url: String,
        val name: String,
        @Json(name = "tag_name") val tag: String,
        val assets: List<GithubAsset>
    )

    @JsonClass(generateAdapter = true)
    internal data class GithubAsset(
        val url: String,
        val name: String,
        @Json(name = "browser_download_url") val downloadUrl: String
    )
}