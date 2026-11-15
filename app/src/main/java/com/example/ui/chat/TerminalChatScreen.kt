package com.example.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.ChatMessage
import com.example.data.RiadAiPersona
import com.example.ui.components.RiadMarkdownText
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.MatrixGreenSubtle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VoidBackground

@Composable
fun TerminalChatScreen(
    uiState: TerminalChatUiState,
    onUpdateDraft: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    onStopGeneration: () -> Unit,
    onClearSession: () -> Unit,
    onSpeakMessage: (String, String) -> Unit,
    onAppendTranscript: (String) -> Unit,
    onToggleSttLanguage: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var isDictating by remember { mutableStateOf(false) }
    var sttStatusMessage by remember { mutableStateOf<String?>(null) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    val intentSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val spoken = matches?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            onAppendTranscript(spoken)
        }
        isDictating = false
    }

    DisposableEffect(speechRecognizer) {
        if (speechRecognizer != null) {
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    sttStatusMessage = "LISTENING (${uiState.sttLanguageTag})..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isDictating = false
                    sttStatusMessage = null
                }

                override fun onError(error: Int) {
                    isDictating = false
                    sttStatusMessage = "Mic input cancelled or unavailable (code $error)."
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        onAppendTranscript(text)
                    }
                    isDictating = false
                    sttStatusMessage = null
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    fun startDictation() {
        sttStatusMessage = null
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, uiState.sttLanguageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "RIAD AI // Speak in English or বাংলা")
        }
        if (speechRecognizer != null) {
            isDictating = true
            speechRecognizer.startListening(recognizerIntent)
        } else {
            try {
                isDictating = true
                intentSpeechLauncher.launch(recognizerIntent)
            } catch (_: Exception) {
                isDictating = false
                sttStatusMessage = "Speech recognition service unavailable on this device."
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startDictation()
        } else {
            sttStatusMessage = "Microphone permission is required for voice dictation."
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.text?.length) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBackground)
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Error or STT status banner
        AnimatedVisibility(visible = uiState.errorBanner != null || sttStatusMessage != null) {
            val bannerText = uiState.errorBanner ?: sttStatusMessage.orEmpty()
            val isError = uiState.errorBanner != null
            Surface(
                color = if (isError) AlertRed.copy(alpha = 0.14f) else MatrixGreenSubtle,
                border = BorderStroke(1.dp, if (isError) AlertRed else MatrixGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bannerText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) AlertRed else MatrixGreen,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            onDismissError()
                            sttStatusMessage = null
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss notice",
                            tint = if (isError) AlertRed else MatrixGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Chat Message Stream
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                ChatMessageBubble(
                    message = message,
                    loadingStatusText = uiState.loadingStatusText,
                    isSpeaking = uiState.activeTtsMessageId == message.id,
                    isSynthesizingTts = uiState.activeTtsMessageId == message.id && uiState.isSynthesizingTts,
                    onSpeakClick = { onSpeakMessage(message.id, message.text) }
                )
            }
        }

        // Tactical Quick Prompts Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(RiadAiPersona.TACTICAL_PROMPTS) { preset ->
                Surface(
                    color = ElevatedSurface,
                    border = BorderStroke(1.dp, MatrixGreen.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !uiState.isGenerating) {
                            onSendMessage(preset.prompt)
                        }
                        .testTag("quick_prompt_${preset.label.lowercase().replace(" ", "_")}")
                ) {
                    Text(
                        text = "> ${preset.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MatrixGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        maxLines = 1
                    )
                }
            }
        }

        HorizontalDivider(color = SubtleBorder)

        // Tactical Input Console
        Surface(
            color = ElevatedSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = VoidBackground,
                            border = BorderStroke(1.dp, MatrixGreen.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onToggleSttLanguage)
                                .testTag("stt_language_toggle")
                        ) {
                            Text(
                                text = if (uiState.sttLanguageTag == "en-US") "STT: EN-US" else "STT: BN-BD (বাংলা)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MatrixGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onClearSession)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("clear_chat_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear terminal session",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "PURGE LOG",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isDictating) {
                                speechRecognizer?.stopListening()
                                isDictating = false
                                sttStatusMessage = null
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    startDictation()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(
                                if (isDictating) MatrixGreenSubtle else VoidBackground,
                                RoundedCornerShape(8.dp)
                            )
                            .testTag("chat_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isDictating) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = stringResource(R.string.cd_voice_dictation),
                            tint = if (isDictating) MatrixGreen else TextPrimary
                        )
                    }

                    OutlinedTextField(
                        value = uiState.inputDraft,
                        onValueChange = onUpdateDraft,
                        placeholder = {
                            Text(
                                text = "> Ask RIAD AI (EN / বাংলা)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = TextPrimary
                        ),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                keyboardController?.hide()
                                onSendMessage(null)
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MatrixGreen,
                            unfocusedBorderColor = SubtleBorder,
                            focusedContainerColor = VoidBackground,
                            unfocusedContainerColor = VoidBackground,
                            cursorColor = MatrixGreen
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                                    !event.isShiftPressed
                                ) {
                                    if (uiState.inputDraft.isNotBlank() && !uiState.isGenerating) {
                                        keyboardController?.hide()
                                        onSendMessage(null)
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                            .testTag("chat_prompt_input")
                    )

                    Surface(
                        color = if (uiState.isGenerating) AlertRed.copy(alpha = 0.18f) else MatrixGreen,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.isGenerating) AlertRed else MatrixGreen
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (uiState.isGenerating) {
                                    onStopGeneration()
                                } else {
                                    keyboardController?.hide()
                                    onSendMessage(null)
                                }
                            }
                            .testTag("send_chat_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (uiState.isGenerating) "Abort stream" else "Transmit query",
                                tint = if (uiState.isGenerating) AlertRed else VoidBackground
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    loadingStatusText: String,
    isSpeaking: Boolean,
    isSynthesizingTts: Boolean,
    onSpeakClick: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    if (message.isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = SurfaceVariantDark,
                border = BorderStroke(1.dp, MatrixGreen.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.88f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "OPERATOR // CMD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MatrixGreen
                        )
                        Text(
                            text = message.timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    } else {
        Surface(
            color = ElevatedSurface,
            border = BorderStroke(
                1.dp,
                if (message.isError) AlertRed.copy(alpha = 0.7f) else SubtleBorder
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "RIAD AI // ${message.timestamp}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (message.isError) AlertRed else MatrixGreen
                        )
                        if (message.isStreaming) {
                            CircularProgressIndicator(
                                color = MatrixGreen,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "STREAMING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MatrixGreen
                            )
                        }
                    }

                    if (!message.isStreaming && message.text.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onSpeakClick,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("message_tts_button_${message.id}")
                            ) {
                                when {
                                    isSynthesizingTts -> {
                                        CircularProgressIndicator(
                                            color = MatrixGreen,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    isSpeaking -> {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop Fenrir briefing",
                                            tint = MatrixGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Play Fenrir briefing",
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(message.text)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy response",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                if (message.text.isBlank() && message.isStreaming) {
                    Text(
                        text = loadingStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    RiadMarkdownText(markdown = message.text)
                }
            }
        }
    }
}
