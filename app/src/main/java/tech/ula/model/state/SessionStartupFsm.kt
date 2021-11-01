package tech.ula.model.state

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.ula.ServerService
import tech.ula.model.entities.App
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository
import tech.ula.model.repositories.DownloadMetadata
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import tech.ula.viewmodel.*
import java.net.UnknownHostException

class SessionStartupFsm(
    ulaDatabase: UlaDatabase,
    private val assetRepository: AssetRepository,
    private val filesystemManager: FilesystemManager,
    private val ulaFiles: UlaFiles,
    private val appsStartupFsm: AppsStartupFsm,
    private val activityContext: Activity,
    private val assetDownloader: AssetDownloader,
    private val storageCalculator: StorageCalculator,
    private val logger: Logger = SentryLogger()
) {

    private val className = "SessionFSM"

    private val state = MutableLiveData<SessionStartupState>().apply { postValue(WaitingForSessionSelection) }

    private val sessionDao = ulaDatabase.sessionDao()
    private val activeSessionsLiveData = sessionDao.findActiveSessions()
    private val activeSessions = mutableListOf<Session>()

    private val filesystemDao = ulaDatabase.filesystemDao()
    private val filesystemsLiveData = filesystemDao.getAllFilesystems()
    private val filesystems = mutableListOf<Filesystem>()

    private val extractionLogger: (String) -> Unit = { line ->
        state.postValue(ExtractingFilesystem(line))
    }

    private lateinit var viewModel: MainActivityViewModel

    init {
        activeSessionsLiveData.observeForever {
            it?.let { list ->
                activeSessions.clear()
                activeSessions.addAll(list)
            }
        }
        filesystemsLiveData.observeForever {
            it?.let { list ->
                filesystems.clear()
                filesystems.addAll(list)
            }
        }
    }

    fun getState(): LiveData<SessionStartupState> {
        return state
    }

    // Exposed for testing purposes. This should not be called during real use cases.
    internal fun setState(newState: SessionStartupState) {
        state.postValue(newState)
    }

    internal fun setViewModel(viewModel: MainActivityViewModel) {
        this.viewModel = viewModel
    }

    fun sessionsAreActive(): Boolean {
        return activeSessions.size > 0
    }

    fun transitionIsAcceptable(event: SessionStartupEvent): Boolean {
        val currentState = state.value!!
        return when (event) {
            is SessionSelected -> currentState is WaitingForSessionSelection
            is RetrieveAssetLists -> currentState is SessionIsReadyForPreparation
            is GenerateDownloads -> currentState is AssetListsRetrievalSucceeded
            is DownloadAssets -> currentState is DownloadsRequired
            is AssetDownloadComplete -> {
                // If we are currently downloading assets, we can handle completed downloads that
                // don't belong to us. Otherwise, we still don't want to post an illegal transition.
                currentState is DownloadingAssets || !assetDownloader.downloadIsForUserland(event.downloadAssetId)
            }
            is SyncDownloadState -> {
//                currentState is WaitingForSessionSelection || currentState is (DownloadingAssets)
                true
            }
            is CopyDownloadsToLocalStorage -> currentState is DownloadsHaveSucceeded
            is VerifyFilesystemAssets -> currentState is NoDownloadsRequired || currentState is LocalDirectoryCopySucceeded
            is VerifyAvailableStorage -> currentState is FilesystemAssetVerificationSucceeded
            is VerifyAvailableStorageComplete -> currentState is VerifyingSufficientStorage || currentState is LowAvailableStorage
            is ExtractFilesystem -> currentState is StorageVerificationCompletedSuccessfully
            is ResetSessionState -> true
        }
    }

    fun submitEvent(event: SessionStartupEvent, coroutineScope: CoroutineScope) = coroutineScope.launch {
        val eventBreadcrumb = UlaBreadcrumb(className, BreadcrumbType.ReceivedEvent, "Event: $event State: ${state.value}")
        logger.addBreadcrumb(eventBreadcrumb)
        if (!transitionIsAcceptable(event)) {
            state.postValue(IncorrectSessionTransition(event, state.value!!))
            return@launch
        }
        when (event) {
            is SessionSelected -> { handleSessionSelected(event.session) }
            is RetrieveAssetLists -> { handleRetrieveAssetLists(event.filesystem) }
            is GenerateDownloads -> { handleGenerateDownloads(event.filesystem, event.assetList) }
            is DownloadAssets -> { handleDownloadAssets(event.downloadRequirements) }
            is AssetDownloadComplete -> { handleAssetsDownloadComplete(event.downloadAssetId) }
            is SyncDownloadState -> { handleSyncDownloadState() }
            is CopyDownloadsToLocalStorage -> { handleCopyDownloadsToLocalDirectories() }
            is VerifyFilesystemAssets -> { handleVerifyFilesystemAssets(event.filesystem) }
            is VerifyAvailableStorage -> { handleVerifyAvailableStorage() }
            is VerifyAvailableStorageComplete -> { handleVerifyAvailableStorageComplete() }
            is ExtractFilesystem -> { handleExtractFilesystem(event.filesystem) }
            is ResetSessionState -> { state.postValue(WaitingForSessionSelection) }
        }
    }

    fun codeRun(event: SessionStartupEvent, codeLang: String?, filePath: String?, coroutineScope: CoroutineScope) = coroutineScope.launch {
        val eventBreadcrumb = UlaBreadcrumb(className, BreadcrumbType.ReceivedEvent, "Event: $event State: ${state.value}")
        logger.addBreadcrumb(eventBreadcrumb)

        val session1 = (event as SessionSelected).session

        val filesystem = findAppsFilesystem("debian")
        val potentialAppSession1 = findAppSession("debian", filesystem.id)

        val credentialsAreSet = filesystem.defaultUsername.isNotEmpty() &&
                filesystem.defaultPassword.isNotEmpty() &&
                filesystem.defaultVncPassword.isNotEmpty()
        if (credentialsAreSet==false) {
            //(appsStartupFsm.getState() as MutableLiveData<AppsStartupState>).postValue(CodeRunUpdatesession(filesystem, session1))
            viewModel.submitAppSelection(App("debian", "Math", "debian", true, true))
            //(viewModel.getState() as MutableLiveData<State>).postValue(FilesystemCredentialsRequired)
            return@launch
        }

        session1.filesystemId = filesystem.id

        session1.username = filesystem.defaultUsername
        session1.password = filesystem.defaultPassword

        session1.filesystemName = potentialAppSession1.filesystemName
        session1.serviceType = potentialAppSession1.serviceType
        session1.active = potentialAppSession1.active

        try {
            withContext(Dispatchers.IO) {
                filesystemManager.writeCodeRunToRequiredLocation(filesystem, codeLang, filePath)
            }
        } catch (err: Exception) {
        }

        if (activeSessions.isNotEmpty()/* || session1.active*/) {
            val serviceIntent = Intent(activityContext, ServerService::class.java)
            serviceIntent.putExtra("type", "killandstart")
            serviceIntent.putExtra("session", activeSessions.get(0))
            serviceIntent.putExtra("newsession", session1)
            activityContext.startService(serviceIntent)
        } else {
            handleSessionSelected(session1)
        }
    }

    private suspend fun findAppsFilesystem(filesystemType: String): Filesystem = withContext(Dispatchers.IO) {
        val potentialAppFilesystem = filesystemDao.findAppsFilesystemByType(filesystemType)

        if (potentialAppFilesystem.isEmpty()) {
            val deviceArchitecture = ulaFiles.getArchType()
            val fsToInsert = Filesystem(0, name = "apps", archType = deviceArchitecture,
                distributionType = filesystemType, isAppsFilesystem = true)
            filesystemDao.insertFilesystem(fsToInsert)
        }

        return@withContext filesystemDao.findAppsFilesystemByType(filesystemType).first()
    }

    private suspend fun findAppSession(filesystemType: String, filesystemId: Long): Session = withContext(Dispatchers.IO) {
        val potentialAppSession = sessionDao.findAppsSession(filesystemType)

        if (potentialAppSession.isEmpty()) {
            val sessionToInsert = Session(id = 0, name = filesystemType, filesystemId = filesystemId, isAppsSession = true)
            sessionDao.insertSession(sessionToInsert)
        }

        return@withContext sessionDao.findAppsSession(filesystemType).first()
    }

    private fun findFilesystemForSession(session: Session): Filesystem {
        return filesystems.find { filesystem -> filesystem.id == session.filesystemId }!!
    }

    private fun handleSessionSelected(session: Session) {
        if (activeSessions.isNotEmpty()) {
            if (activeSessions.contains(session)) {
                state.postValue(SessionIsRestartable(session))
                return
            }

            state.postValue(SingleSessionSupported)
            return
        }

        val filesystem = findFilesystemForSession(session)
        state.postValue(SessionIsReadyForPreparation(session, filesystem))
    }

    private suspend fun handleRetrieveAssetLists(filesystem: Filesystem) {
        state.postValue(RetrievingAssetLists)

        val assetList = assetRepository.getAssetList(filesystem.distributionType)

        if (assetList.isEmpty()) {
            state.postValue(AssetListsRetrievalFailed)
            return
        }

        state.postValue(AssetListsRetrievalSucceeded(assetList))
    }

    private suspend fun handleGenerateDownloads(filesystem: Filesystem, assetList: List<Asset>) {
        state.postValue(GeneratingDownloadRequirements)

        val filesystemNeedsExtraction =
                !filesystemManager.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}") &&
                !filesystem.isCreatedFromBackup

        val downloadRequirements = try {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        } catch (err: UnknownHostException) {
            state.postValue(RemoteUnreachableForGeneration)
            return
        }

        if (downloadRequirements.isEmpty()) {
            state.postValue(NoDownloadsRequired)
            return
        }

        val largeDownloadRequired = downloadRequirements.any { it.filename == "rootfs.tar.gz" }
        state.postValue(DownloadsRequired(downloadRequirements, largeDownloadRequired))
    }

    private fun handleDownloadAssets(downloadRequirements: List<DownloadMetadata>) {
        // If the state isn't updated first, AssetDownloadComplete events will be submitted before
        // the transition is acceptable.
        state.postValue(DownloadingAssets(0, downloadRequirements.size))
        assetDownloader.downloadRequirements(downloadRequirements)
    }

    private fun handleAssetsDownloadComplete(downloadId: Long) {
        val result = assetDownloader.handleDownloadComplete(downloadId)
        handleAssetDownloadState(result)
    }

    private fun handleAssetDownloadState(assetDownloadState: AssetDownloadState) {
        return when (assetDownloadState) {
            // We don't care if some other app has downloaded something, though we may intercept the
            // broadcast from the Download Manager.
            is NonUserlandDownloadFound -> {}
            is CacheSyncAttemptedWhileCacheIsEmpty -> state.postValue(AttemptedCacheAccessWhileEmpty)
            is AllDownloadsCompletedSuccessfully -> state.postValue(DownloadsHaveSucceeded)
            is CompletedDownloadsUpdate -> {
                state.postValue(DownloadingAssets(assetDownloadState.numCompleted, assetDownloadState.numTotal))
            }
            is AssetDownloadFailure -> state.postValue(DownloadsHaveFailed(assetDownloadState.reason))
        }
    }

    private fun handleSyncDownloadState() {
        if (assetDownloader.downloadStateHasBeenCached()) {
            state.postValue(DownloadingAssets(0, 0)) // Reset state so events can be submitted
            handleAssetDownloadState(assetDownloader.syncStateWithCache())
        }
    }

    private suspend fun handleCopyDownloadsToLocalDirectories() {
        state.postValue(CopyingFilesToLocalDirectories)
        try {
            assetDownloader.prepareDownloadsForUse()
        } catch (err: Exception) {
            state.postValue(LocalDirectoryCopyFailed)
            return
        }
        state.postValue(LocalDirectoryCopySucceeded)
    }

    private suspend fun handleVerifyFilesystemAssets(filesystem: Filesystem) = withContext(Dispatchers.IO) {
        state.postValue(VerifyingFilesystemAssets)

        val filesystemDirectoryName = "${filesystem.id}"
        val requiredAssets = assetRepository.getDistributionAssetsForExistingFilesystem(filesystem)
        val allAssetsArePresentOnFilesystem = filesystemManager.areAllRequiredAssetsPresent(filesystemDirectoryName, requiredAssets)
        val lastDownloadedAssetVersion = assetRepository.getLatestDistributionVersion(filesystem.distributionType)
        val filesystemAssetsNeedUpdating = filesystem.versionCodeUsed < lastDownloadedAssetVersion

        if (!allAssetsArePresentOnFilesystem || filesystemAssetsNeedUpdating) {
            if (!assetRepository.assetsArePresentInSupportDirectories(requiredAssets)) {
                state.postValue(AssetsAreMissingFromSupportDirectories)
                return@withContext
            }

            try {
                filesystemManager.copyAssetsToFilesystem(filesystem)
                filesystem.versionCodeUsed = lastDownloadedAssetVersion
                filesystemDao.updateFilesystem(filesystem)
            } catch (err: Exception) {
                state.postValue(FilesystemAssetCopyFailed)
                return@withContext
            }

            if (filesystemManager.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)) {
                filesystemManager.removeRootfsFilesFromFilesystem(filesystemDirectoryName)
            }
        }

        state.postValue(FilesystemAssetVerificationSucceeded)
    }

    private fun handleVerifyAvailableStorage() {
        state.postValue(VerifyingSufficientStorage)

        when (storageCalculator.getAvailableStorageInMB()) {
            in 0..250 -> state.postValue(VerifyingSufficientStorageFailed)
            in 251..1000 -> state.postValue(LowAvailableStorage)
            else -> state.postValue(StorageVerificationCompletedSuccessfully)
        }
    }

    private fun handleVerifyAvailableStorageComplete() {
        state.postValue(StorageVerificationCompletedSuccessfully)
    }

    private suspend fun handleExtractFilesystem(filesystem: Filesystem) {
        val filesystemDirectoryName = "${filesystem.id}"

        if (filesystemManager.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)) {
            filesystemManager.removeRootfsFilesFromFilesystem(filesystemDirectoryName)
            state.postValue(ExtractionHasCompletedSuccessfully)
            return
        }

        val result = filesystemManager.extractFilesystem(filesystem, extractionLogger)
        if (result is FailedExecution) {
            state.postValue(ExtractionFailed(result.reason))
            return
        }

        if (filesystemManager.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)) {
            filesystemManager.removeRootfsFilesFromFilesystem(filesystemDirectoryName)
            state.postValue(ExtractionHasCompletedSuccessfully)
            return
        }

        state.postValue(ExtractionFailed(reason = "Unknown reason."))
    }
}

sealed class SessionStartupState
// One-off events
data class IncorrectSessionTransition(val event: SessionStartupEvent, val state: SessionStartupState) : SessionStartupState()
object WaitingForSessionSelection : SessionStartupState()
object SingleSessionSupported : SessionStartupState()
data class SessionIsRestartable(val session: Session) : SessionStartupState()
data class SessionIsReadyForPreparation(val session: Session, val filesystem: Filesystem) : SessionStartupState()

// Asset retrieval states
sealed class AssetRetrievalState : SessionStartupState()
object RetrievingAssetLists : AssetRetrievalState()
data class AssetListsRetrievalSucceeded(val assetList: List<Asset>) : AssetRetrievalState()
object AssetListsRetrievalFailed : AssetRetrievalState()

// Download requirements generation state
sealed class DownloadRequirementsGenerationState : SessionStartupState()
object GeneratingDownloadRequirements : DownloadRequirementsGenerationState()
data class DownloadsRequired(val downloadsRequired: List<DownloadMetadata>, val largeDownloadRequired: Boolean) : DownloadRequirementsGenerationState()
object NoDownloadsRequired : DownloadRequirementsGenerationState()
object RemoteUnreachableForGeneration : DownloadRequirementsGenerationState()

// Downloading asset states
sealed class DownloadingAssetsState : SessionStartupState()
data class DownloadingAssets(val numCompleted: Int, val numTotal: Int) : DownloadingAssetsState()
object DownloadsHaveSucceeded : DownloadingAssetsState()
data class DownloadsHaveFailed(val reason: DownloadFailureLocalizationData) : DownloadingAssetsState()
object AttemptedCacheAccessWhileEmpty : DownloadingAssetsState()

sealed class CopyingFilesLocallyState : SessionStartupState()
object CopyingFilesToLocalDirectories : CopyingFilesLocallyState()
object LocalDirectoryCopySucceeded : CopyingFilesLocallyState()
object LocalDirectoryCopyFailed : CopyingFilesLocallyState()

sealed class AssetVerificationState : SessionStartupState()
object VerifyingFilesystemAssets : AssetVerificationState()
object FilesystemAssetVerificationSucceeded : AssetVerificationState()
object AssetsAreMissingFromSupportDirectories : AssetVerificationState()
object FilesystemAssetCopyFailed : AssetVerificationState()

sealed class ExtractionState : SessionStartupState()
data class ExtractingFilesystem(val extractionTarget: String) : ExtractionState()
object ExtractionHasCompletedSuccessfully : ExtractionState()
data class ExtractionFailed(val reason: String) : ExtractionState()

sealed class StorageVerificationState : SessionStartupState()
object VerifyingSufficientStorage : StorageVerificationState()
object VerifyingSufficientStorageFailed : StorageVerificationState()
object LowAvailableStorage : StorageVerificationState()
object StorageVerificationCompletedSuccessfully : StorageVerificationState()

sealed class SessionStartupEvent
data class SessionSelected(val session: Session) : SessionStartupEvent()
data class RetrieveAssetLists(val filesystem: Filesystem) : SessionStartupEvent()
data class GenerateDownloads(val filesystem: Filesystem, val assetList: List<Asset>) : SessionStartupEvent()
data class DownloadAssets(val downloadRequirements: List<DownloadMetadata>) : SessionStartupEvent()
data class AssetDownloadComplete(val downloadAssetId: Long) : SessionStartupEvent()
object SyncDownloadState : SessionStartupEvent()
object CopyDownloadsToLocalStorage : SessionStartupEvent()
data class VerifyFilesystemAssets(val filesystem: Filesystem) : SessionStartupEvent()
object VerifyAvailableStorage : SessionStartupEvent()
object VerifyAvailableStorageComplete : SessionStartupEvent()
data class ExtractFilesystem(val filesystem: Filesystem) : SessionStartupEvent()
object ResetSessionState : SessionStartupEvent()
