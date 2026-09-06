package com.stemdeck.remote.playback

import com.stemdeck.remote.library.TrashedSongStore
import com.stemdeck.remote.models.Job
import com.stemdeck.remote.models.PairedServer
import com.stemdeck.remote.networking.StemDeckClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Downloads every song's stems in the background, one song at a time, as
 * soon as it appears in the library — so by the time the user taps a song,
 * it's likely already there. Opening a song directly jumps the line via
 * [downloadNow], which starts (or attaches to) that song's download right
 * away rather than waiting behind others still queued.
 *
 * A single shared instance (Hilt `@Singleton`) because it's the one place
 * that should ever be writing stem files to disk — the library screen and
 * the player both read its [statuses] instead of downloading independently,
 * so a song opened mid-background-download is observed rather than
 * re-fetched.
 */
@Singleton
class StemDownloadQueue @Inject constructor(
    private val store: StemFileStore,
    private val trashedSongStore: TrashedSongStore,
) {
    sealed class Status {
        object NotQueued : Status()
        object Queued : Status()
        data class Downloading(val progress: Double) : Status()
        object Downloaded : Status()
        data class Failed(val message: String) : Status()
    }

    private val _statuses = MutableStateFlow<Map<String, Status>>(emptyMap())
    val statuses: StateFlow<Map<String, Status>> get() = _statuses

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: PairedServer? = null
    private val jobsByID = ConcurrentHashMap<String, Job>()
    private val pendingJobIDs = ArrayDeque<String>()
    private val activeTasks = ConcurrentHashMap<String, Deferred<Unit>>()
    private val queueMutex = Mutex()
    private var isDraining = false

    fun configure(server: PairedServer) {
        this.server = server
    }

    fun status(jobID: String): Status = _statuses.value[jobID] ?: Status.NotQueued

    /**
     * Drops any tracked status for [jobID] — used when the user trashes a
     * song ([TrashedSongStore]), so that if it's later restored it's
     * re-evaluated from scratch (its files were deleted along with the
     * status) rather than still reporting stale [Status.Downloaded].
     */
    fun forget(jobID: String) {
        _statuses.update { it - jobID }
    }

    /**
     * Call after every library refresh. Songs already tracked (downloaded,
     * queued, mid-download, or previously failed) are left alone — this
     * only picks up ones that are new to the queue. Trashed songs are
     * skipped entirely: the user explicitly removed them from the mobile
     * library, so StemDeck still having the job shouldn't silently bring
     * its stems back — only an explicit restore (or opening it directly)
     * downloads it again.
     */
    fun syncLibrary(jobs: List<Job>) {
        scope.launch {
            queueMutex.withLock {
                for (job in jobs) {
                    jobsByID[job.id] = job
                    if (_statuses.value.containsKey(job.id)) continue
                    if (trashedSongStore.isTrashed(job.id)) continue
                    if (isFullyDownloaded(job)) {
                        _statuses.update { it + (job.id to Status.Downloaded) }
                    } else {
                        _statuses.update { it + (job.id to Status.Queued) }
                        pendingJobIDs.addLast(job.id)
                    }
                }
            }
            drainQueueIfNeeded()
        }
    }

    /** The user opened this song. Downloads it now, ahead of anything still waiting in the background queue, and waits for it to finish. */
    suspend fun downloadNow(job: Job) {
        jobsByID[job.id] = job
        queueMutex.withLock { pendingJobIDs.remove(job.id) }
        performDownload(job)
    }

    fun isFullyDownloaded(job: Job): Boolean {
        val names = job.stemNames
        if (names.isEmpty()) return false
        return names.all { store.exists(job.id, it) }
    }

    private fun drainQueueIfNeeded() {
        scope.launch {
            queueMutex.withLock {
                if (isDraining || pendingJobIDs.isEmpty()) return@launch
                isDraining = true
            }
            while (true) {
                val nextID = queueMutex.withLock {
                    if (pendingJobIDs.isEmpty()) null else pendingJobIDs.removeFirst()
                } ?: break
                val job = jobsByID[nextID] ?: continue
                try {
                    performDownload(job)
                } catch (e: Exception) {
                    // Failure status already recorded by performDownload; keep draining the rest of the queue.
                }
            }
            queueMutex.withLock { isDraining = false }
        }
    }

    /**
     * One in-flight coroutine per job — the background queue and a direct
     * [downloadNow] call both funnel through here, so if the queue already
     * reached this song, opening it just attaches to that same download
     * instead of starting a duplicate one.
     */
    private suspend fun performDownload(job: Job) {
        if (isFullyDownloaded(job)) {
            _statuses.update { it + (job.id to Status.Downloaded) }
            return
        }
        val existing = activeTasks[job.id]
        if (existing != null) {
            existing.await()
            return
        }
        val currentServer = server ?: return
        val deferred = scope.async { runDownload(job, currentServer) }
        activeTasks[job.id] = deferred
        try {
            deferred.await()
        } finally {
            activeTasks.remove(job.id)
        }
    }

    private suspend fun runDownload(job: Job, server: PairedServer) {
        val jobID = job.id
        val missing = job.stemNames.filterNot { store.exists(jobID, it) }
        if (missing.isEmpty()) {
            _statuses.update { it + (jobID to Status.Downloaded) }
            return
        }

        _statuses.update { it + (jobID to Status.Downloading(0.0)) }
        var completed = 0
        val progressMutex = Mutex()
        try {
            missing.map { name ->
                scope.async {
                    val client = StemDeckClient(server)
                    client.downloadStem(jobID, name, store.url(jobID, name))
                    progressMutex.withLock {
                        completed += 1
                        _statuses.update { it + (jobID to Status.Downloading(completed.toDouble() / missing.size)) }
                    }
                }
            }.awaitAll()

            if (!store.peaksExist(jobID)) {
                try {
                    val client = StemDeckClient(server)
                    val data = client.fetchPeaks(jobID)
                    store.peaksFile(jobID).writeBytes(data)
                } catch (e: Exception) {
                    // Peaks are optional — see StemDeckClient.fetchPeaks.
                }
            }
            _statuses.update { it + (jobID to Status.Downloaded) }
        } catch (e: Exception) {
            _statuses.update { it + (jobID to Status.Failed(describe(e))) }
            throw e
        }
    }

    companion object {
        fun describe(error: Throwable): String = when (error) {
            is StemDeckClient.ClientError.ServerRefused -> error.detail
            is StemDeckClient.ClientError.CertificateRejected ->
                "StemDeck's certificate no longer matches what this app trusted. Disconnect and pair again."
            else -> "Couldn't download stems. Check that StemDeck is still running and reachable on your Wi-Fi."
        }
    }
}
