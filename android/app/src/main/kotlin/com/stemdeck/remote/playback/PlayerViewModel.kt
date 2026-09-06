package com.stemdeck.remote.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import com.stemdeck.remote.models.Job
import com.stemdeck.remote.models.PairedServer
import com.stemdeck.remote.networking.StemDeckClient
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Drives one song's stem console: downloads (or reads bundled) stems, loads
 * them into a [StemMixerEngine], and exposes everything the UI needs as
 * [StateFlow]s — the direct Android counterpart of the iOS side's
 * `PlayerViewModel` (`ObservableObject` + `@Published`).
 *
 * Owned by [PlaybackCoordinator], not by any single screen — the console can
 * be minimized to the mini player without stopping playback, exactly like
 * the iOS side.
 */
class PlayerViewModel(
    private val context: Context,
    private val job: Job,
    private val server: PairedServer,
    private val stemDownloadQueue: StemDownloadQueue,
    private val stemFileStore: StemFileStore,
    /** Non-null only for a bundled [com.stemdeck.remote.pairing.SampleSongCatalog] entry: stem name -> `asset:///...` URI. When set, [start] skips the download queue and the server-backed peaks fetch entirely — nothing here ever touches the network. */
    private val localStemUris: Map<String, Uri>? = null,
    private val localPeaksAssetPath: String? = null,
) {
    data class StemChannel(
        val id: String,
        val volume: Double = 1.0,
        val isMuted: Boolean = false,
        val isSoloed: Boolean = false,
    ) {
        val levelLabel: String
            get() {
                if (volume <= 0.0001) return "-∞ dB"
                val db = 20 * log10(volume)
                return "%+.1f dB".format(db)
            }
    }

    sealed class State {
        object WaitingForStems : State()
        object LoadingEngine : State()
        object Ready : State()
        data class Failed(val message: String) : State()
    }

    private val _channels = MutableStateFlow<List<StemChannel>>(emptyList())
    val channels: StateFlow<List<StemChannel>> get() = _channels

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> get() = _isPlaying

    private val _state = MutableStateFlow<State>(State.WaitingForStems)
    val state: StateFlow<State> get() = _state

    private val _currentTime = MutableStateFlow(0.0)
    val currentTime: StateFlow<Double> get() = _currentTime

    private val _peaks = MutableStateFlow<Map<String, List<List<Double>>>>(emptyMap())
    val peaks: StateFlow<Map<String, List<List<Double>>>> get() = _peaks

    private val _playbackRate = MutableStateFlow(1.0)
    val playbackRate: StateFlow<Double> get() = _playbackRate

    private val _pitchSemitones = MutableStateFlow(0)
    val pitchSemitones: StateFlow<Int> get() = _pitchSemitones

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> get() = _isLooping

    /** Live post-fader RMS level per stem, 0..1, updated while playing — what makes the LED meters bounce with the music instead of just showing the fader position. */
    private val _liveLevels = MutableStateFlow<Map<String, Double>>(emptyMap())
    val liveLevels: StateFlow<Map<String, Double>> get() = _liveLevels

    /** Never actually populated today — see [ChordAnalyzer]'s doc comment: chord detection is fully ported but not wired into playback yet, matching the iOS app's current (unwired) state. */
    private val _currentChord = MutableStateFlow<String?>(null)
    val currentChord: StateFlow<String?> get() = _currentChord

    private val _isAnalyzingChords = MutableStateFlow(false)
    val isAnalyzingChords: StateFlow<Boolean> get() = _isAnalyzingChords

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> get() = _duration

    val progress: Double
        get() {
            val d = _duration.value
            if (d <= 0) return 0.0
            return (_currentTime.value / d).coerceIn(0.0, 1.0)
        }

    private val engine = StemMixerEngine(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: CoroutineJob? = null

    suspend fun start() {
        _state.value = State.WaitingForStems
        if (job.stemNames.isEmpty()) {
            _state.value = State.Failed("This song has no stems available.")
            return
        }

        if (localStemUris != null) {
            loadLocalPeaksIfNeeded()
        } else {
            try {
                stemDownloadQueue.downloadNow(job)
            } catch (e: Exception) {
                val status = stemDownloadQueue.status(job.id)
                _state.value = State.Failed(
                    if (status is StemDownloadQueue.Status.Failed) status.message else "Couldn't download stems.",
                )
                return
            }
            loadPeaksIfNeeded()
        }

        _state.value = State.LoadingEngine

        try {
            val stems = job.stemNames.map { name ->
                val uri = localStemUris?.get(name) ?: Uri.fromFile(stemFileStore.url(job.id, name))
                StemMixerEngine.Stem(name, uri)
            }
            engine.onStemLevel = { stem, rms -> updateLiveLevel(stem, rms) }
            engine.load(stems) {
                _isPlaying.value = false
                _currentTime.value = 0.0
                _liveLevels.value = emptyMap()
            }
            _duration.value = engine.durationMs / 1000.0
            _channels.value = job.stemNames.map { StemChannel(id = it) }
            _state.value = State.Ready
            startProgressUpdates()
        } catch (e: Exception) {
            _state.value = State.Failed("Couldn't load stems for playback: ${e.message}")
        }
    }

    private suspend fun loadPeaksIfNeeded() {
        val jobID = job.id
        if (!stemFileStore.peaksExist(jobID)) {
            try {
                val client = StemDeckClient(server)
                val data = client.fetchPeaks(jobID)
                stemFileStore.peaksFile(jobID).writeBytes(data)
            } catch (e: Exception) {
                // Peaks are optional — WaveformView just draws nothing without them.
            }
        }
        try {
            val text = stemFileStore.peaksFile(jobID).readText()
            _peaks.value = Json.decodeFromString(text)
        } catch (e: Exception) {
            // Missing/corrupt peaks file — leave peaks empty.
        }
    }

    private fun loadLocalPeaksIfNeeded() {
        val path = localPeaksAssetPath ?: return
        try {
            val text = context.assets.open(path).bufferedReader().use { it.readText() }
            _peaks.value = Json.decodeFromString(text)
        } catch (e: Exception) {
            // Missing/corrupt bundled peaks — leave peaks empty.
        }
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            engine.pause()
            _liveLevels.value = emptyMap()
        } else {
            engine.play()
        }
        _isPlaying.value = engine.isPlaying
    }

    /** RMS of real program material almost never reaches 0dBFS, so mapping all the way up to 0dB would leave the top LEDs essentially unreachable even at a 100% fader — -6dB is a much more realistic "meter's full" reference for loud passages. */
    private fun updateLiveLevel(stem: String, rms: Float) {
        val db = 20 * log10(max(rms, 0.0001f).toDouble())
        val normalized = (db - METER_FLOOR_DB) / (METER_CEILING_DB - METER_FLOOR_DB)
        _liveLevels.update { it + (stem to normalized.coerceIn(0.0, 1.0)) }
    }

    fun seek(timeSeconds: Double) {
        engine.seek((timeSeconds * 1000).toLong())
        _currentTime.value = engine.currentPositionMs() / 1000.0
        _isPlaying.value = engine.isPlaying
    }

    fun setVolume(volume: Double, stem: String) {
        engine.setVolume(stem, volume.toFloat())
        _channels.update { list -> list.map { if (it.id == stem) it.copy(volume = volume) else it } }
    }

    fun toggleMute(stem: String) {
        engine.toggleMute(stem)
        _channels.update { list -> list.map { if (it.id == stem) it.copy(isMuted = engine.isMuted(stem)) else it } }
    }

    fun toggleSolo(stem: String) {
        engine.toggleSolo(stem)
        _channels.update { list -> list.map { it.copy(isSoloed = engine.isSoloed(it.id)) } }
    }

    fun setPlaybackRate(rate: Double) {
        val clamped = rate.coerceIn(SPEED_RANGE.first, SPEED_RANGE.second)
        engine.setRate(clamped.toFloat())
        _playbackRate.value = clamped
    }

    fun setPitch(value: Double) {
        val clamped = Math.round(value).toInt().coerceIn(PITCH_RANGE.first, PITCH_RANGE.last)
        if (clamped == _pitchSemitones.value) return
        engine.setPitchSemitones(clamped)
        _pitchSemitones.value = clamped
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
        engine.isLooping = _isLooping.value
    }

    fun stopPlayback() {
        engine.pause()
        engine.seek(0)
        _isPlaying.value = false
        _currentTime.value = 0.0
        _liveLevels.value = emptyMap()
    }

    fun markIn() = Unit
    fun markOut() = Unit

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                _currentTime.value = engine.currentPositionMs() / 1000.0
                updateCurrentChord()
                delay(50)
            }
        }
    }

    private fun updateCurrentChord() {
        // chordEvents is always empty today — see the currentChord doc comment above.
    }

    fun stop() {
        progressJob?.cancel()
        _isAnalyzingChords.value = false
        engine.stopAndReset()
        _isPlaying.value = false
    }

    companion object {
        val SPEED_RANGE = 0.5 to 1.5
        val PITCH_RANGE = -6..6
        private const val METER_FLOOR_DB = -40.0
        private const val METER_CEILING_DB = -6.0
    }
}
