package com.stemdeck.remote.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stemdeck.remote.models.Job
import com.stemdeck.remote.models.PairedServer
import com.stemdeck.remote.networking.PairingStore
import com.stemdeck.remote.networking.StemDeckClient
import com.stemdeck.remote.playback.StemDownloadQueue
import com.stemdeck.remote.playback.StemFileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class LibraryLoadState {
    object Idle : LibraryLoadState()
    object Loading : LibraryLoadState()
    object Loaded : LibraryLoadState()
    object Unreachable : LibraryLoadState()
    object CertificateChanged : LibraryLoadState()
    data class Refused(val detail: String) : LibraryLoadState()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val cache: LibraryCache,
    private val downloadQueue: StemDownloadQueue,
    private val stemFileStore: StemFileStore,
    val folderStore: LibraryFolderStore,
    val trashedSongStore: TrashedSongStore,
    private val pairingStore: PairingStore,
) : ViewModel() {

    fun forgetServer() = pairingStore.forget()

    /**
     * Removes a song from the mobile library view only — never calls
     * StemDeck. Deletes its locally cached stems/peaks/chords to reclaim
     * space, forgets its download status so a future restore re-evaluates
     * from scratch, and records it in [TrashedSongStore] so a background
     * sync won't silently re-download it while StemDeck still has the job.
     */
    fun trashSong(job: Job) {
        trashedSongStore.trash(job)
        stemFileStore.deleteAll(job.id)
        downloadQueue.forget(job.id)
    }

    fun restoreSong(jobID: String) = trashedSongStore.restore(jobID)

    private val _songs = MutableStateFlow<List<Job>>(emptyList())
    val songs: StateFlow<List<Job>> get() = _songs

    private val _state = MutableStateFlow<LibraryLoadState>(LibraryLoadState.Idle)
    val state: StateFlow<LibraryLoadState> get() = _state

    /**
     * True when [songs] is the last cached listing rather than a live one —
     * StemDeck couldn't be reached, but there's a known library to show (and
     * already-downloaded songs in it still open and play fine).
     */
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> get() = _isOffline

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> get() = _searchText

    private val _sortOption = MutableStateFlow(SongSortOption.DATE_ADDED_NEWEST)
    val sortOption: StateFlow<SongSortOption> get() = _sortOption

    /**
     * What the list actually shows — [songs] filtered by [searchText] (a
     * plain case-insensitive title substring match) and ordered by
     * [sortOption]. Both are pure display transforms; the fetched `songs`
     * underneath is untouched.
     */
    val displayedSongs: StateFlow<List<Job>> = combine(_songs, _searchText, _sortOption) { songs, search, sort ->
        val matching = if (search.isBlank()) songs else songs.filter { (it.title ?: "").contains(search, ignoreCase = true) }
        sort.sort(matching)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private var client: StemDeckClient? = null
    private var server: PairedServer? = null

    fun setSearchText(text: String) { _searchText.value = text }
    fun setSortOption(option: SongSortOption) { _sortOption.value = option }

    /**
     * Cache-first: shows the last known library immediately (as "offline"
     * until [refresh] proves otherwise) instead of a blank/loading screen
     * while the live fetch is still in flight.
     */
    fun configure(server: PairedServer) {
        downloadQueue.configure(server)
        if (server == this.server) return
        this.server = server
        client = StemDeckClient(server)
        val cached = cache.load()
        if (!cached.isNullOrEmpty()) {
            _songs.value = cached
            _isOffline.value = true
            _state.value = LibraryLoadState.Loaded
        } else {
            _songs.value = emptyList()
            _state.value = LibraryLoadState.Idle
        }
    }

    fun refresh() {
        val client = client ?: return
        viewModelScope.launch {
            _state.value = LibraryLoadState.Loading
            try {
                val fetched = client.fetchLibrary()
                    .filter { it.isAvailable }
                    .sortedByDescending { it.createdAt ?: 0.0 }
                _songs.value = fetched
                _isOffline.value = false
                _state.value = LibraryLoadState.Loaded
                cache.save(fetched)
                // As soon as songs are known, queue their stems for
                // background download (Netflix/Prime-style "downloading" /
                // "queued" rows — see StemDownloadQueue) instead of waiting
                // for the user to open each one.
                downloadQueue.syncLibrary(fetched)
            } catch (e: StemDeckClient.ClientError.CertificateRejected) {
                // Not a transient connectivity blip — needs an explicit
                // re-pair, so surface it even if a cache exists rather than
                // masking it.
                _state.value = LibraryLoadState.CertificateChanged
            } catch (e: Exception) {
                val cached = cache.load()
                if (!cached.isNullOrEmpty()) {
                    _songs.value = cached
                    _isOffline.value = true
                    _state.value = LibraryLoadState.Loaded
                    downloadQueue.syncLibrary(_songs.value)
                } else if (e is StemDeckClient.ClientError.ServerRefused) {
                    _state.value = LibraryLoadState.Refused(e.detail)
                } else {
                    _state.value = LibraryLoadState.Unreachable
                }
            }
        }
    }
}
