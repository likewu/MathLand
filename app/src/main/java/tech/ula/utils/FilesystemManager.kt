package tech.ula.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import java.io.File
import java.io.IOException

class FilesystemManager(
    private val ulaFiles: UlaFiles,
    private val busyboxExecutor: BusyboxExecutor,
    private val logger: Logger = SentryLogger()
) {

    private val filesDirPath = ulaFiles.filesDir.path
    private val filesystemExtractionSuccess = ".success_filesystem_extraction"
    private val filesystemExtractionFailure = ".failure_filesystem_extraction"

    private fun getSupportDirectoryPath(targetDirectoryName: String): String {
        return "$filesDirPath/$targetDirectoryName/support"
    }

    @Throws(Exception::class)
    fun copyAssetsToFilesystem(filesystem: Filesystem) {
        val distributionType = filesystem.distributionType
        val targetFilesystemName = "${filesystem.id}"
        val sharedDirectory = File("$filesDirPath/$distributionType")
        val targetDirectory = File("$filesDirPath/$targetFilesystemName/support")
        if (!targetDirectory.exists()) targetDirectory.mkdirs()
        val files = sharedDirectory.listFiles()
        files?.let {
            for (file in files) {
                if (file.name.contains("rootfs") && filesystem.isCreatedFromBackup) continue
                val targetFile = File("${targetDirectory.absolutePath}/${file.name}")
                file.copyTo(targetFile, overwrite = true)
                ulaFiles.makePermissionsUsable(targetDirectory.absolutePath, file.name)
            }
        }
    }

    fun removeRootfsFilesFromFilesystem(targetFilesystemName: String) {
        val supportDirectory = File(getSupportDirectoryPath(targetFilesystemName))
        supportDirectory.walkBottomUp().forEach {
            if (it.name.contains("rootfs.tar.gz")) it.delete()
        }
    }

    suspend fun extractFilesystem(
        filesystem: Filesystem,
        listener: (String) -> Any
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val filesystemDirName = "${filesystem.id}"
        val command = "/support/common/extractFilesystem.sh"
        val env = HashMap<String, String>()
        env["INITIAL_USERNAME"] = filesystem.defaultUsername
        env["INITIAL_PASSWORD"] = filesystem.defaultPassword
        env["INITIAL_VNC_PASSWORD"] = filesystem.defaultVncPassword

        return@withContext busyboxExecutor.executeProotCommand(
            command,
            filesystemDirName,
            commandShouldTerminate = true,
            env = env,
            listener = listener
        )
    }

    suspend fun compressFilesystem(
        filesystem: Filesystem,
        scopedExternalDestination: File,
        listener: (String) -> Any
    ) = withContext(Dispatchers.IO) {
        val filesystemDirName = "${filesystem.id}"
        val command = "/support/common/compressFilesystem.sh"
        val env = HashMap<String, String>()
        env["TAR_PATH"] = scopedExternalDestination.absolutePath

        return@withContext busyboxExecutor.executeProotCommand(
                command,
                filesystemDirName,
                commandShouldTerminate = true,
                env = env,
                listener = listener
        )
    }

    fun isExtractionComplete(targetDirectoryName: String): Boolean {
        val supportPath = getSupportDirectoryPath(targetDirectoryName)
        val success = File("$supportPath/$filesystemExtractionSuccess")
        val failure = File("$supportPath/$filesystemExtractionFailure")
        return success.exists() || failure.exists()
    }

    fun hasFilesystemBeenSuccessfullyExtracted(targetDirectoryName: String): Boolean {
        val supportPath = getSupportDirectoryPath(targetDirectoryName)
        return File("$supportPath/$filesystemExtractionSuccess").exists()
    }

    fun areAllRequiredAssetsPresent(
        targetDirectoryName: String,
        distributionAssetList: List<Asset>
    ): Boolean {
        val supportDirectory = File(getSupportDirectoryPath(targetDirectoryName))
        if (!supportDirectory.exists() || !supportDirectory.isDirectory) return false

        val supportFiles = supportDirectory.listFiles() ?: return false
        val supportDirectoryFileNames = supportFiles.map { it.name }
        return distributionAssetList.all {
            supportDirectoryFileNames.contains(it.name)
        }
    }

    @Throws(IOException::class)
    suspend fun deleteFilesystem(filesystemId: Long) = withContext(Dispatchers.IO) {
        val filesystemDirectory = File("$filesDirPath/$filesystemId")
        if (!filesystemDirectory.exists() || !filesystemDirectory.isDirectory) return@withContext
        // CWD for this script is the files dir, running without proot
        val command = "support/deleteFilesystem.sh ${filesystemDirectory.path}"
        val result = busyboxExecutor.executeScript(command)
        if (result is FailedExecution) {
            val err = IOException()
            logger.addExceptionBreadcrumb(err)
            throw err
        }
    }

    @Throws(IOException::class)
    fun moveAppScriptToRequiredLocation(appName: String, appFilesystem: Filesystem) {
        // Profile.d scripts execute in alphabetical order.
        val fileNameToForceAppScriptToExecuteLast = "zzzzzzzzzzzzzzzz.sh"
        val appScriptSource = File("$filesDirPath/apps/$appName/$appName.sh")
        val appFilesystemProfileDDir = File("$filesDirPath/${appFilesystem.id}/etc/profile.d")
        val appScriptProfileDTarget = File("$appFilesystemProfileDDir/$fileNameToForceAppScriptToExecuteLast")

        try {
            appFilesystemProfileDDir.mkdirs()
            appScriptSource.copyTo(appScriptProfileDTarget, overwrite = true)
        } catch (err: Exception) {
            val exception = IOException()
            logger.addExceptionBreadcrumb(exception)
            throw exception
        }
    }

    @Throws(IOException::class)
    fun writeCodeRunToRequiredLocation(appFilesystem: Filesystem, codeLang: String?, filePath: String?) {
        // Profile.d scripts execute in alphabetical order.
        val fileNameToForceAppScriptToExecuteLast = "zzzzzzzzzzzzzzzz.sh"
        val appFilesystemProfileDDir = File("$filesDirPath/${appFilesystem.id}/etc/profile.d")
        val appScriptProfileDTarget = File("$appFilesystemProfileDDir/$fileNameToForceAppScriptToExecuteLast")

        try {
            appFilesystemProfileDDir.mkdirs()
            appScriptProfileDTarget.writeText("SCRIPT_PATH=\$(realpath \${BASH_SOURCE})\n\n" +
                    "sudo rm -f \$SCRIPT_PATH\n\n" +
                    "PATH=\$PATH:\$HOME/.local/bin\n\n" +
                    //"echo \${PATH} \${HOME}\n\n" +
                    "$codeLang /storage/MathLand$filePath")
            //appScriptProfileDTarget.delete()
        } catch (err: Exception) {
            val exception = IOException()
            logger.addExceptionBreadcrumb(exception)
            throw exception
        }
    }
}
