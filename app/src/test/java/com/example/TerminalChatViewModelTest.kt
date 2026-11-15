package com.example

import com.example.data.ChatMessage
import com.example.data.RiadAiGateway
import com.example.data.ScanDepth
import com.example.data.VulnScanReport
import com.example.ui.chat.TerminalChatViewModel
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.content
import java.util.IdentityHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TerminalChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stalled stream recovers automatically via fallback model without hanging`() = runTest(testDispatcher) {
        val responseTexts = IdentityHashMap<GenerateContentResponse, String>()
        val fakeGateway = object : RiadAiGateway {
            var unaryFallbackCalled = false

            override fun buildConversationContents(
                historyMessages: List<ChatMessage>,
                newPrompt: String
            ): List<Content> = listOf(content("user") { text(newPrompt) })

            override fun streamChatTurn(
                contents: List<Content>,
                useFallbackModel: Boolean
            ): Flow<GenerateContentResponse> = flow {
                // Simulate hung SSE stream
                delay(10_000L)
            }

            override suspend fun generateChatTurnUnary(
                contents: List<Content>,
                useFallbackModel: Boolean
            ): GenerateContentResponse {
                unaryFallbackCalled = useFallbackModel
                return fakeResponse("SQLi mitigated using prepared statements.", responseTexts)
            }

            override fun extractChunkText(response: GenerateContentResponse): String {
                return responseTexts[response].orEmpty()
            }

            override suspend fun synthesizeBriefingAudio(markdownText: String): ByteArray = ByteArray(0)

            override suspend fun analyzeVulnerability(sourceInput: String, depth: ScanDepth): VulnScanReport {
                throw UnsupportedOperationException()
            }
        }

        val vm = TerminalChatViewModel(
            repository = fakeGateway,
            firstTokenTimeoutMs = 200L,
            totalTimeoutMs = 5_000L,
            fallbackTimeoutMs = 1_000L
        )

        vm.sendMessage("Explain SQL injection")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(fakeGateway.unaryFallbackCalled)
        assertFalse(state.isGenerating)
        val lastMessage = state.messages.last()
        assertFalse(lastMessage.isStreaming)
        assertFalse(lastMessage.isError)
        assertEquals("SQLi mitigated using prepared statements.", lastMessage.text)
    }

    @Test
    fun `app check 403 error surfaces immediately without hanging`() = runTest(testDispatcher) {
        val fakeGateway = object : RiadAiGateway {
            override fun buildConversationContents(
                historyMessages: List<ChatMessage>,
                newPrompt: String
            ): List<Content> = listOf(content("user") { text(newPrompt) })

            override fun streamChatTurn(
                contents: List<Content>,
                useFallbackModel: Boolean
            ): Flow<GenerateContentResponse> = flow {
                throw IllegalStateException("HTTP 403 AppCheck token invalid")
            }

            override suspend fun generateChatTurnUnary(
                contents: List<Content>,
                useFallbackModel: Boolean
            ): GenerateContentResponse {
                throw AssertionError("Fallback should not run on fatal 403 App Check error")
            }

            override fun extractChunkText(response: GenerateContentResponse): String = ""
            override suspend fun synthesizeBriefingAudio(markdownText: String): ByteArray = ByteArray(0)
            override suspend fun analyzeVulnerability(sourceInput: String, depth: ScanDepth): VulnScanReport {
                throw UnsupportedOperationException()
            }
        }

        val vm = TerminalChatViewModel(
            repository = fakeGateway,
            firstTokenTimeoutMs = 200L,
            totalTimeoutMs = 5_000L,
            fallbackTimeoutMs = 1_000L
        )

        vm.sendMessage("Hello")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isGenerating)
        assertNotNull(state.errorBanner)
        assertTrue(state.errorBanner!!.contains("403"))
        assertTrue(state.messages.last().isError)
    }

    private fun fakeResponse(
        text: String,
        store: IdentityHashMap<GenerateContentResponse, String>
    ): GenerateContentResponse {
        return GenerateContentResponse(
            candidates = emptyList(),
            promptFeedback = null,
            usageMetadata = null
        ).also { store[it] = text }
    }
}
