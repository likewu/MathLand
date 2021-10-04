package tech.ula.model.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ula.model.entities.App
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import java.io.File
import java.io.IOException
import java.util.Locale

class GithubAppsFetcher(
    private val filesDirPath: String,
    private val httpStream: HttpStream = HttpStream(),
    private val logger: Logger = SentryLogger()
) {

    // Allows destructing of the list of application elements
    private operator fun <T> List<T>.component6() = get(5)
    private operator fun <T> List<T>.component7() = get(6)

    private val branch = "master" // Base off different support branches for testing.
    //private val baseUrl = "https://github.com/CypherpunkArmory/UserLAnd-Assets-Support/raw/$branch/apps"
    private val baseUrl = "https://gitlab.com/leafcolor/packages/-/raw/master/UserLAnd-Assets-Support/apps"

    @Throws(IOException::class)
    suspend fun fetchAppsList(): List<App> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "$baseUrl/apps.txt"
            val numLinesToSkip = 1 // Skip first line which defines schema
            val contents: List<String> = httpStream.toLines(url)
            contents.drop(numLinesToSkip).map { line ->
                // Destructure app fields
                val (
                        name,
                        category,
                        filesystemRequired,
                        supportsCli,
                        supportsGui,
                        isPaidApp,
                        version
                ) = line.toLowerCase(Locale.ENGLISH).split(", ")
                // Construct app
                App(
                        name,
                        category,
                        filesystemRequired,
                        supportsCli.toBoolean(),
                        supportsGui.toBoolean(),
                        isPaidApp.toBoolean(),
                        version.toLong()
                )
            }
        } catch (err: Exception) {
            val exception = IOException("Error getting apps list")
            logger.addExceptionBreadcrumb(exception)
            throw exception
        }
    }

    suspend fun fetchAppIcon(app: App) = withContext(Dispatchers.IO) {
        val directoryAndFilename = "${app.name}/${app.name}.png"
        val file = File("$filesDirPath/apps/$directoryAndFilename")
        val url = "$baseUrl/$directoryAndFilename"
        httpStream.toFile(url, file)
    }

    suspend fun fetchAppDescription(app: App) = withContext(Dispatchers.IO) {
        val directoryAndFilename = "${app.name}/${app.name}.txt"
        val url = "$baseUrl/$directoryAndFilename"
        val file = File("$filesDirPath/apps/$directoryAndFilename")
        httpStream.toTextFile(url, file)
    }

    suspend fun fetchAppScript(app: App) = withContext(Dispatchers.IO) {
        val directoryAndFilename = "${app.name}/${app.name}.sh"
        val url = "$baseUrl/$directoryAndFilename"
        val file = File("$filesDirPath/apps/$directoryAndFilename")
        httpStream.toTextFile(url, file)
    }
}