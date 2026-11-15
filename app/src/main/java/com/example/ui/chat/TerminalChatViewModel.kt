package com.example.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessage
import com.example.data.PcmAudioPlayer
import com.example.data.RiadAiGateway
import com.example.data.RiadAiRepository
import com.google.firebase.ai.type.Content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "TerminalChatVM"

data class TerminalChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputDraft: String = "",
    val isGenerating: Boolean = false,
    val loadingStatusText: String = "> Decrypting neural defense vector...",
    val ttsAutoBriefing: Boolean = false,
    val activeTtsMessageId: String? = null,
    val isSynthesizingTts: Boolean = false,
    val sttLanguageTag: String = "en-US",
    val errorBanner: String? = null
)

class TerminalChatViewModel(
    private val repository: RiadAiGateway = RiadAiRepository(),
    private val firstTokenTimeoutMs: Long = 14_000L,
    private val totalTimeoutMs: Long = 60_000L,
    private val fallbackTimeoutMs: Long = 25_000L
) : ViewModel() {

    private var generationJob: Job? = null
    private var ttsJob: Job? = null
    private val audioPlayer = PcmAudioPlayer()

    private val _uiState = MutableStateFlow(
        TerminalChatUiState(
            messages = listOf(
                ChatMessage(
                    id = "boot-welcome",
                    isUser = false,
                    text = buildWelcomeBanner(),
                    timestamp = nowTimestamp()
                )
            )
        )
    )
    val uiState: StateFlow<TerminalChatUiState> = _uiState.asStateFlow()

    fun updateDraft(text: String) {
        _uiState.update { it.copy(inputDraft = text) }
    }

    fun appendVoiceTranscript(transcript: String) {
        if (transcript.isBlank()) return
        _uiState.update { state ->
            val separator = if (state.inputDraft.isBlank()) "" else " "
            state.copy(inputDraft = state.inputDraft + separator + transcript.trim())
        }
    }

    fun toggleSttLanguage() {
        _uiState.update { state ->
            val next = if (state.sttLanguageTag == "en-US") "bn-BD" else "en-US"
            state.copy(sttLanguageTag = next)
        }
    }

    fun toggleTtsAutoBriefing() {
        val next = !_uiState.value.ttsAutoBriefing
        _uiState.update { it.copy(ttsAutoBriefing = next) }
        if (!next) {
            stopTtsPlayback()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorBanner = null) }
    }

    fun sendMessage(overridePrompt: String? = null) {
        val prompt = (overridePrompt ?: _uiState.value.inputDraft).trim()
        if (prompt.isEmpty() || _uiState.value.isGenerating) return

        val priorMessages = _uiState.value.messages
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = prompt,
            timestamp = nowTimestamp()
        )
        val aiMsgId = UUID.randomUUID().toString()
        val aiPlaceholder = ChatMessage(
            id = aiMsgId,
            isUser = false,
            text = "",
            timestamp = nowTimestamp(),
            isStreaming = true
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMsg + aiPlaceholder,
                inputDraft = if (overridePrompt == null) "" else state.inputDraft,
                isGenerating = true,
                loadingStatusText = "> Establishing encrypted uplink...",
                errorBanner = null
            )
        }

        generationJob = viewModelScope.launch {
            val buffer = StringBuilder()
            var blockReasonDetail: String? = null
            val contents = repository.buildConversationContents(priorMessages, prompt)

            val tickerJob = launch {
                var seconds = 0
                while (buffer.isEmpty()) {
                    _uiState.update {
                        it.copy(loadingStatusText = "> Decrypting neural defense vector (${seconds}s)...")
                    }
                    delay(1_000L)
                    seconds++
                }
            }

            try {
                val streamResult = tryCollectStreamWithFirstTokenWatchdog(
                    contents = contents,
                    aiMsgId = aiMsgId,
                    buffer = buffer,
                    onFirstToken = { tickerJob.cancel() },
                    onDiagnostic = { blockReasonDetail = it }
                )

                tickerJob.cancel()

                // If stream timed out before first token, failed softly, or returned empty text -> run direct fallback chain
                if (buffer.isBlank()) {
                    val primaryErr = streamResult.error
                    if (primaryErr != null && isAuthOrConfigFatal(primaryErr)) {
                        throw primaryErr
                    }

                    _uiState.update {
                        it.copy(loadingStatusText = "> Stream stalled. Switching to direct tactical uplink...")
                    }
                    val recoveredText = tryUnaryRecoveryChain(
                        contents = contents,
                        skipPrimaryModel = streamResult.timedOutBeforeFirstToken || primaryErr != null
                    )
                    if (!recoveredText.isNullOrBlank()) {
                        buffer.append(recoveredText)
                    } else if (primaryErr != null) {
                        throw primaryErr
                    } else {
                        val reason = blockReasonDetail ?: "EMPTY_RESPONSE"
                        throw IllegalStateException("Model returned empty output (reason: $reason).")
                    }
                }

                val finalText = buffer.toString().trim()
                updateMessageById(aiMsgId) { msg ->
                    msg.copy(text = finalText, isStreaming = false)
                }
                _uiState.update { it.copy(isGenerating = false) }

                if (_uiState.value.ttsAutoBriefing) {
                    speakMessage(aiMsgId, finalText)
                }
            } catch (e: TimeoutCancellationException) {
                tickerJob.cancel()
                Log.e(TAG, "Chat generation timed out", e)
                val msgText = "Transmission timed out. Verify internet / Firebase App Check and retry."
                markStreamError(aiMsgId, buffer.toString(), msgText)
            } catch (e: CancellationException) {
                tickerJob.cancel()
                throw e
            } catch (e: Exception) {
                tickerJob.cancel()
                Log.e(TAG, "Chat generation failed", e)
                val detail = formatReadableError(e)
                markStreamError(aiMsgId, buffer.toString(), detail)
            } finally {
                tickerJob.cancel()
                // Safety guarantee: never leave an active message spinning in streaming state
                updateMessageById(aiMsgId) { msg ->
                    if (msg.isStreaming) {
                        msg.copy(
                            text = msg.text.ifBlank { "[TRANSMISSION CLOSED WITHOUT OUTPUT]" },
                            isStreaming = false
                        )
                    } else msg
                }
                if (_uiState.value.isGenerating) {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            }
        }
    }

    private data class StreamAttemptOutcome(
        val timedOutBeforeFirstToken: Boolean = false,
        val error: Exception? = null
    )

    private suspend fun tryCollectStreamWithFirstTokenWatchdog(
        contents: List<Content>,
        aiMsgId: String,
        buffer: StringBuilder,
        onFirstToken: () -> Unit,
        onDiagnostic: (String) -> Unit
    ): StreamAttemptOutcome {
        val gotFirstToken = AtomicBoolean(false)
        val firstTokenTimedOut = AtomicBoolean(false)
        var capturedError: Exception? = null

        try {
            withTimeout(totalTimeoutMs) {
                coroutineScope {
                    var streamJob: Job? = null
                    val watchdogJob = launch {
                        delay(firstTokenTimeoutMs)
                        if (!gotFirstToken.get()) {
                            firstTokenTimedOut.set(true)
                            Log.w(TAG, "First token watchdog (${firstTokenTimeoutMs}ms) triggered; cancelling stream for fallback.")
                            streamJob?.cancel(CancellationException("FIRST_TOKEN_TIMEOUT"))
                        }
                    }

                    streamJob = launch {
                        try {
                            repository.streamChatTurn(contents, useFallbackModel = false).collect { chunk ->
                                chunk.promptFeedback?.blockReason?.let { reason ->
                                    onDiagnostic("BLOCKED:${reason.name}")
                                }
                                chunk.candidates.firstOrNull()?.finishReason?.let { finish ->
                                    if (finish.name != "STOP") {
                                        onDiagnostic("FINISH:${finish.name}")
                                    }
                                }
                                val delta = repository.extractChunkText(chunk)
                                if (delta.isNotEmpty()) {
                                    if (gotFirstToken.compareAndSet(false, true)) {
                                        watchdogJob.cancel()
                                        onFirstToken()
                                    }
                                    buffer.append(delta)
                                    updateMessageById(aiMsgId) { msg ->
                                        msg.copy(text = buffer.toString(), isStreaming = true)
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            if (!firstTokenTimedOut.get()) throw e
                        } catch (e: Exception) {
                            capturedError = e
                        } finally {
                            watchdogJob.cancel()
                        }
                    }
                    streamJob.join()
                }
            }
        } catch (e: TimeoutCancellationException) {
            if (buffer.isBlank()) {
                return StreamAttemptOutcome(timedOutBeforeFirstToken = true, error = e)
            }
            throw e
        }

        return StreamAttemptOutcome(
            timedOutBeforeFirstToken = firstTokenTimedOut.get(),
            error = capturedError
        )
    }

    private suspend fun tryUnaryRecoveryChain(
        contents: List<Content>,
        skipPrimaryModel: Boolean
    ): String? {
        if (!skipPrimaryModel) {
            try {
                val primaryUnary = withTimeout(fallbackTimeoutMs) {
                    repository.generateChatTurnUnary(contents, useFallbackModel = false)
                }
                val text = repository.extractChunkText(primaryUnary).trim()
                if (text.isNotEmpty()) return text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Primary unary fallback failed, trying lite model", e)
                if (isAuthOrConfigFatal(e)) throw e
            }
        }

        _uiState.update {
            it.copy(loadingStatusText = "> Engaging fast-response fallback node...")
        }
        return try {
            val liteUnary = withTimeout(fallbackTimeoutMs) {
                repository.generateChatTurnUnary(contents, useFallbackModel = true)
            }
            repository.extractChunkText(liteUnary).trim().ifEmpty { null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Lite fallback model failed", e)
            if (isAuthOrConfigFatal(e)) throw e
            null
        }
    }

    private fun isAuthOrConfigFatal(e: Throwable): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("403") ||
            msg.contains("AppCheck", ignoreCase = true) ||
            msg.contains("App Check", ignoreCase = true) ||
            msg.contains("attestation", ignoreCase = true) ||
            msg.contains("API key", ignoreCase = true)
    }

    private fun formatReadableError(e: Throwable): String {
        val raw = e.localizedMessage ?: e.message ?: "Encrypted uplink failed."
        return when {
            raw.contains("403") || raw.contains("AppCheck", ignoreCase = true) || raw.contains("App Check", ignoreCase = true) ->
                "Firebase App Check / Auth rejected (403): $raw"
            raw.contains("network", ignoreCase = true) || raw.contains("Unable to resolve host", ignoreCase = true) ->
                "Network unreachable: $raw"
            else -> "${e::class.simpleName}: $raw"
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.isStreaming) {
                    msg.copy(
                        text = msg.text.ifBlank { "[TRANSMISSION ABORTED BY OPERATOR]" },
                        isStreaming = false
                    )
                } else msg
            }
            state.copy(messages = updated, isGenerating = false)
        }
    }

    fun clearSession() {
        stopGeneration()
        stopTtsPlayback()
        _uiState.update { state ->
            state.copy(
                messages = listOf(
                    ChatMessage(
                        id = "boot-${System.currentTimeMillis()}",
                        isUser = false,
                        text = buildWelcomeBanner(),
                        timestamp = nowTimestamp()
                    )
                ),
                errorBanner = null
            )
        }
    }

    fun speakMessage(messageId: String, text: String) {
        if (_uiState.value.activeTtsMessageId == messageId) {
            stopTtsPlayback()
            return
        }
        stopTtsPlayback()
        ttsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeTtsMessageId = messageId,
                    isSynthesizingTts = true,
                    errorBanner = null
                )
            }
            try {
                val pcmData = repository.synthesizeBriefingAudio(text)
                _uiState.update { it.copy(isSynthesizingTts = false) }
                audioPlayer.play24kPcm(pcmData)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "TTS briefing failed", e)
                _uiState.update {
                    it.copy(
                        errorBanner = "TTS Briefing Error: ${e.localizedMessage ?: "Audio synthesis failed."}"
                    )
                }
            } finally {
                _uiState.update {
                    if (it.activeTtsMessageId == messageId) {
                        it.copy(activeTtsMessageId = null, isSynthesizingTts = false)
                    } else it
                }
            }
        }
    }

    fun stopTtsPlayback() {
        ttsJob?.cancel()
        ttsJob = null
        audioPlayer.stop()
        _uiState.update { it.copy(activeTtsMessageId = null, isSynthesizingTts = false) }
    }

    private fun markStreamError(aiMsgId: String, partial: String, errorMessage: String) {
        updateMessageById(aiMsgId) { msg ->
            msg.copy(
                text = partial.ifBlank { "**[UPLINK FAULT]** $errorMessage" },
                isStreaming = false,
                isError = partial.isBlank()
            )
        }
        _uiState.update { it.copy(isGenerating = false, errorBanner = errorMessage) }
    }

    private fun updateMessageById(id: String, transform: (ChatMessage) -> ChatMessage) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { if (it.id == id) transform(it) else it }
            )
        }
    }

    private fun nowTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }

    private fun buildWelcomeBanner(): String = """
        ### RIAD AI // RESPONSIVE INTELLIGENT ATTACK DEFENSE A.I.
        **STATUS:** `ARMED` • **MODE:** `WHITE-HAT TACTICAL` • **LANG:** `EN / বাংলা`

        Submit source code for **OWASP Top 10** review, request **Kali Linux** defensive/lab commands, or ask in **English or বাংলা**.
    """.trimIndent()

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }
}
