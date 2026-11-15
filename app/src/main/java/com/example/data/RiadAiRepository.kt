package com.example.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "RiadAiRepository"

interface RiadAiGateway {
    fun buildConversationContents(historyMessages: List<ChatMessage>, newPrompt: String): List<Content>
    fun streamChatTurn(contents: List<Content>, useFallbackModel: Boolean = false): Flow<GenerateContentResponse>
    suspend fun generateChatTurnUnary(contents: List<Content>, useFallbackModel: Boolean = false): GenerateContentResponse
    fun extractChunkText(response: GenerateContentResponse): String
    suspend fun synthesizeBriefingAudio(markdownText: String): ByteArray
    suspend fun analyzeVulnerability(sourceInput: String, depth: ScanDepth): VulnScanReport
}

@OptIn(PublicPreviewAPI::class)
class RiadAiRepository : RiadAiGateway {

    private val tacticalSafetySettings by lazy {
        listOf(
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.ONLY_HIGH)
        )
    }

    private val fastRequestOptions by lazy {
        RequestOptions(45_000L)
    }

    private fun buildChatModel(modelName: String, level: ThinkingLevel): GenerativeModel {
        return Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = level
                }
            },
            safetySettings = tacticalSafetySettings,
            systemInstruction = content { text(RiadAiPersona.SYSTEM_INSTRUCTION) },
            requestOptions = fastRequestOptions
        )
    }

    private val primaryChatModel by lazy {
        buildChatModel(RiadAiPersona.MODEL_CHAT, ThinkingLevel.LOW)
    }
    private val fallbackChatModel by lazy {
        buildChatModel(RiadAiPersona.MODEL_CHAT_FALLBACK, ThinkingLevel.MINIMAL)
    }

    private val scannerSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "overallRisk" to Schema.enumeration(
                    values = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW", "SECURE"),
                    description = "Highest overall security risk classification"
                ),
                "riskScore" to Schema.integer(
                    description = "Security risk score from 0 (completely secure) to 100 (critical compromise)"
                ),
                "language" to Schema.string(
                    description = "Detected programming language, config format, or protocol"
                ),
                "summary" to Schema.string(
                    description = "Concise executive tactical summary of the security posture"
                ),
                "findings" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "severity" to Schema.enumeration(
                                values = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW"),
                                description = "Finding severity"
                            ),
                            "title" to Schema.string(description = "Vulnerability name"),
                            "cwe" to Schema.string(description = "CWE identifier e.g. CWE-89"),
                            "vulnerableLine" to Schema.string(description = "Exact vulnerable code line or header"),
                            "explanation" to Schema.string(description = "How the flaw works and attacker impact"),
                            "remediation" to Schema.string(description = "Exact defensive fix")
                        )
                    ),
                    description = "List of identified security vulnerabilities"
                ),
                "patchedCode" to Schema.string(
                    description = "Complete hardened and patched version of the submitted code or headers"
                )
            )
        )
    }

    private val scannerModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = RiadAiPersona.MODEL_CHAT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scannerSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            safetySettings = tacticalSafetySettings,
            systemInstruction = content { text(RiadAiPersona.SYSTEM_INSTRUCTION) },
            requestOptions = RequestOptions(60_000L)
        )
    }

    private val ttsModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = RiadAiPersona.MODEL_TTS,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(voice = Voice(RiadAiPersona.VOICE_NAME))
            },
            requestOptions = fastRequestOptions
        )
    }

    fun createLiveModel(
        modelName: String = RiadAiPersona.MODEL_LIVE,
        enableTranscription: Boolean = true
    ): LiveGenerativeModel {
        return Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = modelName,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice(RiadAiPersona.VOICE_NAME))
                if (enableTranscription) {
                    inputAudioTranscription = AudioTranscriptionConfig()
                    outputAudioTranscription = AudioTranscriptionConfig()
                }
            },
            systemInstruction = content { text(RiadAiPersona.LIVE_VOICE_SYSTEM_INSTRUCTION) },
            requestOptions = RequestOptions(30_000L)
        )
    }

    override fun buildConversationContents(
        historyMessages: List<ChatMessage>,
        newPrompt: String
    ): List<Content> {
        val validHistory = historyMessages
            .filter { !it.isStreaming && !it.isError && it.id != "boot-welcome" && !it.id.startsWith("boot-") && it.text.isNotBlank() }
            .takeLast(16)
            .map { msg ->
                content(role = if (msg.isUser) "user" else "model") {
                    text(msg.text)
                }
            }
        return validHistory + content(role = "user") { text(newPrompt) }
    }

    override fun streamChatTurn(
        contents: List<Content>,
        useFallbackModel: Boolean
    ): Flow<GenerateContentResponse> {
        val model = if (useFallbackModel) fallbackChatModel else primaryChatModel
        return model.generateContentStream(contents)
    }

    override suspend fun generateChatTurnUnary(
        contents: List<Content>,
        useFallbackModel: Boolean
    ): GenerateContentResponse {
        val model = if (useFallbackModel) fallbackChatModel else primaryChatModel
        return model.generateContent(contents)
    }

    override fun extractChunkText(response: GenerateContentResponse): String {
        val direct = response.text
        if (!direct.isNullOrEmpty()) return direct
        val partsText = response.candidates.firstOrNull()?.content?.parts
            ?.filterIsInstance<TextPart>()
            ?.joinToString("") { it.text }
            .orEmpty()
        return partsText
    }

    override suspend fun analyzeVulnerability(sourceInput: String, depth: ScanDepth): VulnScanReport {
        val prompt = """
            Perform a static security vulnerability analysis on the following input.
            Scan Depth: ${depth.label} — ${depth.directive}
            Identify all vulnerabilities, assign accurate CWE IDs and severity ratings, and provide a complete patched/hardened replacement.

            INPUT TARGET:
            ```
            $sourceInput
            ```
        """.trimIndent()

        val response = withTimeout(60_000L) {
            scannerModel.generateContent(prompt)
        }
        val rawJson = response.text
            ?: throw IllegalStateException("Scanner model returned empty payload.")

        return parseScanReport(rawJson)
    }

    override suspend fun synthesizeBriefingAudio(markdownText: String): ByteArray {
        val cleanBriefing = sanitizeForTts(markdownText).take(1800)
        if (cleanBriefing.isBlank()) {
            throw IllegalArgumentException("No readable content for TTS briefing.")
        }
        val prompt = "Deliver this cybersecurity briefing in a sharp, calm, authoritative tactical tone:\n$cleanBriefing"
        val response = withTimeout(45_000L) {
            ttsModel.generateContent(prompt)
        }
        val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
        val audioPart = parts.filterIsInstance<InlineDataPart>()
            .firstOrNull { it.mimeType.startsWith("audio/") }
            ?: throw IllegalStateException("TTS model did not return audio PCM data.")
        return audioPart.inlineData
    }

    private fun parseScanReport(rawJson: String): VulnScanReport {
        val json = JSONObject(rawJson)
        val overallRisk = parseSeverity(json.optString("overallRisk", "MEDIUM"))
        val riskScore = json.optInt("riskScore", 50).coerceIn(0, 100)
        val language = json.optString("language", "Unknown")
        val summary = json.optString("summary", "Analysis complete.")
        val patchedCode = json.optString("patchedCode", "")

        val findingsArray = json.optJSONArray("findings")
        val findings = buildList {
            if (findingsArray != null) {
                for (i in 0 until findingsArray.length()) {
                    val item = findingsArray.getJSONObject(i)
                    add(
                        VulnFinding(
                            severity = parseSeverity(item.optString("severity", "MEDIUM")),
                            title = item.optString("title", "Security Finding"),
                            cwe = item.optString("cwe", "CWE-000"),
                            vulnerableLine = item.optString("vulnerableLine", ""),
                            explanation = item.optString("explanation", ""),
                            remediation = item.optString("remediation", "")
                        )
                    )
                }
            }
        }
        return VulnScanReport(
            overallRisk = overallRisk,
            riskScore = riskScore,
            language = language,
            summary = summary,
            findings = findings,
            patchedCode = patchedCode
        )
    }

    private fun parseSeverity(raw: String): SeverityLevel {
        return when (raw.uppercase()) {
            "CRITICAL" -> SeverityLevel.CRITICAL
            "HIGH" -> SeverityLevel.HIGH
            "MEDIUM" -> SeverityLevel.MEDIUM
            "LOW" -> SeverityLevel.LOW
            "SECURE" -> SeverityLevel.SECURE
            else -> SeverityLevel.MEDIUM
        }
    }

    private fun sanitizeForTts(markdown: String): String {
        return markdown
            .replace(Regex("```[\\s\\S]*?```"), " [code block omitted for audio briefing] ")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("[#*>|_~]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

class PcmAudioPlayer {
    @Volatile
    private var activeTrack: AudioTrack? = null

    suspend fun play24kPcm(pcmData: ByteArray) = withContext(Dispatchers.IO) {
        stop()
        if (pcmData.isEmpty()) return@withContext
        val sampleRate = 24_000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
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

        activeTrack = track
        try {
            track.play()
            var offset = 0
            val chunkSize = 4096
            while (offset < pcmData.size && activeTrack === track) {
                val toWrite = minOf(chunkSize, pcmData.size - offset)
                val written = track.write(pcmData, offset, toWrite)
                if (written <= 0) break
                offset += written
            }
            track.stop()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error", e)
        } finally {
            try {
                track.release()
            } catch (_: Exception) {
            }
            if (activeTrack === track) {
                activeTrack = null
            }
        }
    }

    fun stop() {
        val track = activeTrack
        activeTrack = null
        if (track != null) {
            try {
                track.pause()
                track.flush()
                track.release()
            } catch (_: Exception) {
            }
        }
    }
}
