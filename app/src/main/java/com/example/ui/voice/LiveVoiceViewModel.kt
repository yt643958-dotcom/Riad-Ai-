package com.example.ui.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LiveLinkStatus
import com.example.data.LiveTranscriptItem
import com.example.data.RiadAiPersona
import com.example.data.RiadAiRepository
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "LiveVoiceVM"

data class LiveVoiceUiState(
    val status: LiveLinkStatus = LiveLinkStatus.DISCONNECTED,
    val activeModelName: String = RiadAiPersona.MODEL_LIVE,
    val transcripts: List<LiveTranscriptItem> = emptyList(),
    val textDirectiveDraft: String = "",
    val errorMessage: String? = null,
    val micNotice: String? = null,
    val isMuted: Boolean = false,
    val micSignalLevel: Float = 0f,
    val aiSignalLevel: Float = 0f
)

@OptIn(PublicPreviewAPI::class)
class LiveVoiceViewModel(
    private val repository: RiadAiRepository = RiadAiRepository()
) : ViewModel() {

    private var liveSession: LiveSession? = null
    private var connectJob: Job? = null
    private var receiveJob: Job? = null
    private var micJob: Job? = null
    private var speakingResetJob: Job? = null

    @Volatile
    private var activePlaybackTrack: AudioTrack? = null

    private val _uiState = MutableStateFlow(LiveVoiceUiState())
    val uiState: StateFlow<LiveVoiceUiState> = _uiState.asStateFlow()

    fun updateTextDirective(text: String) {
        _uiState.update { it.copy(textDirectiveDraft = text) }
    }

    fun toggleMute() {
        _uiState.update { state ->
            val nextMuted = !state.isMuted
            state.copy(
                isMuted = nextMuted,
                micSignalLevel = if (nextMuted) 0f else state.micSignalLevel
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connectAndStartConversation() {
        val currentStatus = _uiState.value.status
        if (currentStatus == LiveLinkStatus.CONNECTING ||
            currentStatus == LiveLinkStatus.CONNECTED_LISTENING ||
            currentStatus == LiveLinkStatus.AI_SPEAKING
        ) {
            return
        }

        cleanupActiveSessionSync()
        connectJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = LiveLinkStatus.CONNECTING,
                    errorMessage = null,
                    micNotice = null,
                    micSignalLevel = 0f,
                    aiSignalLevel = 0f
                )
            }

            try {
                val (session, connectedModel) = connectWithModelFallback()
                liveSession = session

                _uiState.update { state ->
                    state.copy(
                        status = LiveLinkStatus.CONNECTED_LISTENING,
                        activeModelName = connectedModel,
                        transcripts = state.transcripts + LiveTranscriptItem(
                            id = UUID.randomUUID().toString(),
                            speaker = "SYSTEM",
                            isAi = true,
                            text = "Encrypted full-duplex audio link active ($connectedModel • ${RiadAiPersona.VOICE_NAME}). Speak in English/বাংলা or send a voice prompt below."
                        )
                    )
                }

                receiveJob = viewModelScope.launch(Dispatchers.IO) {
                    collectLiveResponses(session)
                }
                micJob = viewModelScope.launch(Dispatchers.IO) {
                    streamMicrophoneAudio(session)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Live API connection timed out", e)
                _uiState.update {
                    it.copy(
                        status = LiveLinkStatus.ERROR,
                        errorMessage = "Live voice handshake timed out. Verify internet / Firebase App Check."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live API connection failed", e)
                _uiState.update {
                    it.copy(
                        status = LiveLinkStatus.ERROR,
                        errorMessage = formatLiveError(e)
                    )
                }
            }
        }
    }

    private suspend fun connectWithModelFallback(): Pair<LiveSession, String> {
        return try {
            val primary = repository.createLiveModel(
                modelName = RiadAiPersona.MODEL_LIVE,
                enableTranscription = true
            )
            val session = withTimeout(20_000L) { primary.connect() }
            session to RiadAiPersona.MODEL_LIVE
        } catch (e: CancellationException) {
            throw e
        } catch (primaryErr: Exception) {
            val msg = primaryErr.message.orEmpty()
            val isAuthFatal = msg.contains("403") ||
                msg.contains("AppCheck", ignoreCase = true) ||
                msg.contains("App Check", ignoreCase = true)
            if (isAuthFatal) throw primaryErr

            Log.w(TAG, "Primary live model failed (${RiadAiPersona.MODEL_LIVE}), trying fallback ${RiadAiPersona.MODEL_LIVE_FALLBACK}", primaryErr)
            val fallback = repository.createLiveModel(
                modelName = RiadAiPersona.MODEL_LIVE_FALLBACK,
                enableTranscription = true
            )
            val session = withTimeout(20_000L) { fallback.connect() }
            session to RiadAiPersona.MODEL_LIVE_FALLBACK
        }
    }

    private suspend fun collectLiveResponses(session: LiveSession) {
        val track = create24kPlaybackTrack()
        activePlaybackTrack = track

        try {
            track.play()
            session.receive().collect { message ->
                when (message) {
                    is LiveServerContent -> {
                        val inText = message.inputTranscription?.text?.trim().orEmpty()
                        if (inText.isNotEmpty()) {
                            appendOrMergeTranscript(speaker = "OPERATOR", isAi = false, chunk = inText)
                        }

                        val outText = message.outputTranscription?.text?.trim().orEmpty()
                        if (outText.isNotEmpty()) {
                            appendOrMergeTranscript(speaker = "RIAD AI", isAi = true, chunk = outText)
                        }

                        if (message.interrupted) {
                            try {
                                track.pause()
                                track.flush()
                                track.play()
                            } catch (_: Exception) {
                            }
                            _uiState.update {
                                it.copy(
                                    status = LiveLinkStatus.CONNECTED_LISTENING,
                                    aiSignalLevel = 0f
                                )
                            }
                        }

                        val parts = message.content?.parts.orEmpty()
                        var receivedAudio = false
                        parts.forEach { part ->
                            when (part) {
                                is InlineDataPart -> {
                                    if (part.mimeType.startsWith("audio/") && part.inlineData.isNotEmpty()) {
                                        receivedAudio = true
                                        val level = computePcm16NormalizedLevel(part.inlineData)
                                        _uiState.update {
                                            it.copy(
                                                status = LiveLinkStatus.AI_SPEAKING,
                                                aiSignalLevel = level
                                            )
                                        }
                                        writePcmToTrack(track, part.inlineData)
                                    }
                                }
                                is TextPart -> {
                                    val t = part.text.trim()
                                    if (outText.isEmpty() && t.isNotEmpty()) {
                                        appendOrMergeTranscript(speaker = "RIAD AI", isAi = true, chunk = t)
                                    }
                                }
                                else -> Unit
                            }
                        }

                        if (message.turnComplete || message.generationComplete) {
                            scheduleSpeakingReset(if (receivedAudio) 600L else 150L)
                        } else if (receivedAudio) {
                            scheduleSpeakingReset(1800L)
                        }
                    }
                    is LiveServerGoAway -> {
                        _uiState.update {
                            it.copy(micNotice = "Live session nearing server time limit. Reconnect if audio stops.")
                        }
                    }
                    else -> Unit
                }
            }

            // Flow ended normally
            if (liveSession === session) {
                liveSession = null
                _uiState.update {
                    it.copy(
                        status = LiveLinkStatus.DISCONNECTED,
                        micSignalLevel = 0f,
                        aiSignalLevel = 0f,
                        micNotice = "Voice link closed by server."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Live receive stream failed", e)
            if (liveSession === session) {
                liveSession = null
                _uiState.update {
                    it.copy(
                        status = LiveLinkStatus.ERROR,
                        micSignalLevel = 0f,
                        aiSignalLevel = 0f,
                        errorMessage = formatLiveError(e)
                    )
                }
            }
        } finally {
            if (activePlaybackTrack === track) {
                activePlaybackTrack = null
            }
            releaseTrackSafely(track)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun streamMicrophoneAudio(session: LiveSession) {
        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            _uiState.update {
                it.copy(micNotice = "AudioRecord unavailable on this device. Use text/quick voice prompts below.")
            }
            return
        }

        val bufferSize = maxOf(minBuf * 2, 3200) // ~100ms at 16kHz 16-bit mono
        val recorder = buildBestAudioRecord(sampleRate, bufferSize)
        if (recorder == null) {
            _uiState.update {
                it.copy(micNotice = "Mic hardware could not initialize. Use the prompt bar below for voice replies.")
            }
            return
        }

        var echoCanceler: AcousticEchoCanceler? = null
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (_: Exception) {
        }

        val readBuffer = ByteArray(3200)
        var silentSamplesMs = 0L
        var lastUiUpdateMs = 0L

        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive && liveSession === session) {
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    val isMuted = _uiState.value.isMuted
                    val chunk = readBuffer.copyOf(read)
                    val level = if (isMuted) 0f else computePcm16NormalizedLevel(chunk)

                    if (!isMuted) {
                        if (level < 0.005f) {
                            silentSamplesMs += 100L
                        } else {
                            silentSamplesMs = 0L
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdateMs >= 90L) {
                            lastUiUpdateMs = now
                            val hint = if (silentSamplesMs >= 4_500L) {
                                "No mic audio detected (emulator mic may be silent). Speak louder or tap a prompt below."
                            } else null
                            _uiState.update { state ->
                                state.copy(
                                    micSignalLevel = level,
                                    micNotice = hint
                                )
                            }
                        }

                        session.sendAudioRealtime(
                            InlineData(chunk, "audio/pcm;rate=16000")
                        )
                    } else {
                        delay(60L)
                    }
                } else if (read < 0) {
                    Log.w(TAG, "AudioRecord.read returned error code $read")
                    _uiState.update {
                        it.copy(micNotice = "Mic read interrupted ($read). Use text/quick voice prompts below.")
                    }
                    break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing RECORD_AUDIO permission", e)
            _uiState.update {
                it.copy(micNotice = "Microphone permission required for voice input.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Microphone capture loop failed", e)
            if (liveSession === session) {
                _uiState.update {
                    it.copy(micNotice = "Mic stream stopped: ${e.localizedMessage ?: "Audio error"}. Text voice prompts remain active.")
                }
            }
        } finally {
            try {
                echoCanceler?.release()
            } catch (_: Exception) {
            }
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: Exception) {
            }
            try {
                recorder.release()
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun buildBestAudioRecord(sampleRate: Int, bufferSize: Int): AudioRecord? {
        val sources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )
        for (source in sources) {
            try {
                val record = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    return record
                }
                record.release()
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord source=$source failed to initialize", e)
            }
        }
        return null
    }

    private fun create24kPlaybackTrack(): AudioTrack {
        val sampleRate = 24_000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun writePcmToTrack(track: AudioTrack, data: ByteArray) {
        var offset = 0
        while (offset < data.size && activePlaybackTrack === track) {
            val written = track.write(data, offset, data.size - offset)
            if (written <= 0) break
            offset += written
        }
    }

    private fun releaseTrackSafely(track: AudioTrack?) {
        if (track == null) return
        try {
            track.pause()
            track.flush()
            track.stop()
        } catch (_: Exception) {
        }
        try {
            track.release()
        } catch (_: Exception) {
        }
    }

    private fun computePcm16NormalizedLevel(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        var sumSq = 0.0
        val sampleCount = pcmBytes.size / 2
        var i = 0
        while (i + 1 < pcmBytes.size) {
            val lo = pcmBytes[i].toInt() and 0xFF
            val hi = pcmBytes[i + 1].toInt()
            val sample = (hi shl 8) or lo
            val norm = abs(sample) / 32768.0
            sumSq += norm * norm
            i += 2
        }
        val rms = sqrt(sumSq / sampleCount.coerceAtLeast(1)).toFloat()
        return (rms * 4.5f).coerceIn(0f, 1f)
    }

    private fun scheduleSpeakingReset(delayMs: Long) {
        speakingResetJob?.cancel()
        speakingResetJob = viewModelScope.launch {
            delay(delayMs)
            _uiState.update { state ->
                if (state.status == LiveLinkStatus.AI_SPEAKING) {
                    state.copy(status = LiveLinkStatus.CONNECTED_LISTENING, aiSignalLevel = 0f)
                } else state.copy(aiSignalLevel = 0f)
            }
        }
    }

    private fun appendOrMergeTranscript(speaker: String, isAi: Boolean, chunk: String) {
        _uiState.update { state ->
            val current = state.transcripts.toMutableList()
            val last = current.lastOrNull()
            if (last != null && last.speaker == speaker && last.isAi == isAi) {
                val sep = if (last.text.endsWith(" ") || chunk.startsWith(" ")) "" else " "
                current[current.lastIndex] = last.copy(text = last.text + sep + chunk)
            } else {
                current.add(
                    LiveTranscriptItem(
                        id = UUID.randomUUID().toString(),
                        speaker = speaker,
                        isAi = isAi,
                        text = chunk
                    )
                )
            }
            state.copy(transcripts = current)
        }
    }

    fun sendTextDirective(overrideText: String? = null) {
        val text = (overrideText ?: _uiState.value.textDirectiveDraft).trim()
        val session = liveSession ?: return
        if (text.isEmpty()) return

        if (overrideText == null) {
            _uiState.update { it.copy(textDirectiveDraft = "") }
        }
        appendOrMergeTranscript(speaker = "OPERATOR [TEXT]", isAi = false, chunk = text)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                session.send(text, turnComplete = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending directive over LiveSession", e)
                _uiState.update {
                    it.copy(errorMessage = formatLiveError(e))
                }
            }
        }
    }

    private fun formatLiveError(e: Throwable): String {
        val raw = e.localizedMessage ?: e.message ?: "Failed to establish Live Voice Link."
        return when {
            raw.contains("403") || raw.contains("AppCheck", ignoreCase = true) || raw.contains("App Check", ignoreCase = true) ->
                "Firebase App Check / Auth rejected (403): $raw"
            else -> "${e::class.simpleName}: $raw"
        }
    }

    private fun cleanupActiveSessionSync() {
        connectJob?.cancel()
        connectJob = null
        micJob?.cancel()
        micJob = null
        receiveJob?.cancel()
        receiveJob = null
        speakingResetJob?.cancel()
        speakingResetJob = null
        releaseTrackSafely(activePlaybackTrack)
        activePlaybackTrack = null
        val oldSession = liveSession
        liveSession = null
        if (oldSession != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    oldSession.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun disconnect() {
        cleanupActiveSessionSync()
        _uiState.update { state ->
            state.copy(
                status = LiveLinkStatus.DISCONNECTED,
                micSignalLevel = 0f,
                aiSignalLevel = 0f,
                micNotice = null
            )
        }
    }

    fun clearTranscripts() {
        _uiState.update { it.copy(transcripts = emptyList(), errorMessage = null, micNotice = null) }
    }

    override fun onCleared() {
        super.onCleared()
        micJob?.cancel()
        receiveJob?.cancel()
        releaseTrackSafely(activePlaybackTrack)
        activePlaybackTrack = null
        liveSession = null
    }
}
