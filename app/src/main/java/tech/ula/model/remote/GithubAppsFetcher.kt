package tech.ula.model.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ula.model.entities.App
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.FileInputStream
import java.util.Locale
import java.net.SocketTimeoutException

class GithubAppsFetcher(
    private val filesDirPath: String,
    private val appsInputStream: InputStream,
    private val httpStream: HttpStream = HttpStream(),
    private val logger: Logger = SentryLogger()
) {

    // Allows destructing of the list of application elements
    private operator fun <T> List<T>.component6() = get(5)
    private operator fun <T> List<T>.component7() = get(6)

    private val branch = "master" // Base off different support branches for testing.
    //private val baseUrl = "https://github.com/CypherpunkArmory/UserLAnd-Assets-Support/raw/$branch/apps"
    private val baseUrl = "https://gitlab.com/leafcolor/packages/-/raw/master/UserLAnd-Assets-Support/apps"

    private var http_state = true;

    @Throws(IOException::class)
    suspend fun fetchAppsList(): List<App> = withContext(Dispatchers.IO) {
        http_state = true;
        return@withContext try {
            val url = "$baseUrl/apps.txt"
            val numLinesToSkip = 1 // Skip first line which defines schema
            var contents: List<String>;
            try {
                contents = httpStream.toLines(url)
            } catch (err: SocketTimeoutException) {
                val reader = BufferedReader(InputStreamReader(appsInputStream))
                contents = reader.readLines()
                reader.close()
                http_state = false;
            }
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
        try {
            if (http_state) httpStream.toFile(url, file)
        } catch (err: SocketTimeoutException) {
        }
    }

    suspend fun fetchAppDescription(app: App) = withContext(Dispatchers.IO) {
        val directoryAndFilename = "${app.name}/${app.name}.txt"
        val url = "$baseUrl/$directoryAndFilename"
        val file = File("$filesDirPath/apps/$directoryAndFilename")
        try {
            if (http_state) httpStream.toTextFile(url, file)
        } catch (err: SocketTimeoutException) {
        }
    }

    suspend fun fetchAppScript(app: App) = withContext(Dispatchers.IO) {
        val directoryAndFilename = "${app.name}/${app.name}.sh"
        val url = "$baseUrl/$directoryAndFilename"
        val file = File("$filesDirPath/apps/$directoryAndFilename")
        try {
            if (http_state) httpStream.toTextFile(url, file)
        } catch (err: SocketTimeoutException) {
        }
    }
}