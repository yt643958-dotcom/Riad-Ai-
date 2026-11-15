package com.example.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.LiveLinkStatus
import com.example.data.RiadAiPersona
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.MatrixGreenSubtle
import com.example.ui.theme.SeverityHigh
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VoidBackground
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val LIVE_QUICK_VOICE_PROMPTS = listOf(
    "Brief Status" to "RIAD AI, give me a crisp 2-sentence tactical cybersecurity readiness briefing.",
    "SQLi Defense" to "Briefly explain how parameterized queries defeat SQL injection.",
    "বাংলা ব্রিফিং" to "হ্যালো RIAD AI, বাংলায় তোমার পরিচয় দাও এবং ওয়েব সিকিউরিটির ২টি মূল টিপস বলো।"
)

@Composable
fun LiveVoiceScreen(
    uiState: LiveVoiceUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleMute: () -> Unit,
    onUpdateDirective: (String) -> Unit,
    onSendDirective: (String?) -> Unit,
    onClearTranscripts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var permissionDeniedNote by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedNote = null
            onConnect()
        } else {
            permissionDeniedNote = "Microphone permission is required for Live Voice Link."
        }
    }

    LaunchedEffect(uiState.transcripts.size) {
        if (uiState.transcripts.isNotEmpty()) {
            listState.animateScrollToItem(uiState.transcripts.lastIndex)
        }
    }

    val isConnectedOrConnecting = uiState.status == LiveLinkStatus.CONNECTED_LISTENING ||
        uiState.status == LiveLinkStatus.AI_SPEAKING ||
        uiState.status == LiveLinkStatus.CONNECTING

    val isLiveReady = uiState.status == LiveLinkStatus.CONNECTED_LISTENING ||
        uiState.status == LiveLinkStatus.AI_SPEAKING

    val activeSignalLevel = when (uiState.status) {
        LiveLinkStatus.AI_SPEAKING -> maxOf(0.25f, uiState.aiSignalLevel)
        LiveLinkStatus.CONNECTED_LISTENING -> uiState.micSignalLevel
        else -> 0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBackground)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Header Banner
        Surface(
            color = ElevatedSurface,
            border = BorderStroke(
                1.dp,
                when (uiState.status) {
                    LiveLinkStatus.ERROR -> AlertRed
                    LiveLinkStatus.AI_SPEAKING, LiveLinkStatus.CONNECTED_LISTENING -> MatrixGreen
                    LiveLinkStatus.CONNECTING -> SeverityHigh
                    LiveLinkStatus.DISCONNECTED -> SubtleBorder
                }
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (uiState.status == LiveLinkStatus.ERROR) AlertRed else MatrixGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = when (uiState.status) {
                                LiveLinkStatus.DISCONNECTED -> "VOICE LINK // STANDBY"
                                LiveLinkStatus.CONNECTING -> "HANDSHAKE IN PROGRESS..."
                                LiveLinkStatus.CONNECTED_LISTENING -> "DUPLEX UPLINK ACTIVE // LISTENING"
                                LiveLinkStatus.AI_SPEAKING -> "RIAD AI TRANSMITTING // FENRIR"
                                LiveLinkStatus.ERROR -> "UPLINK FAULT"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (uiState.status == LiveLinkStatus.ERROR) AlertRed else MatrixGreen
                        )
                        Text(
                            text = "Model: ${uiState.activeModelName} • Voice: ${RiadAiPersona.VOICE_NAME} • 16k/24k PCM",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Reactive Circular Radar & Frequency Waveform Visualizer
        TacticalRadarVisualizer(
            status = uiState.status,
            signalLevel = activeSignalLevel,
            modifier = Modifier.size(176.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Live Mic / AI Signal Level Bar when connected
        if (isLiveReady) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (uiState.status == LiveLinkStatus.AI_SPEAKING) "AI OUT" else if (uiState.isMuted) "MUTED" else "MIC IN",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.isMuted) AlertRed else MatrixGreen
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ElevatedSurface)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(activeSignalLevel.coerceIn(0.02f, 1f))
                            .background(if (uiState.isMuted) AlertRed else MatrixGreen)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Primary Connect / Mute / Terminate Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (isConnectedOrConnecting) {
                        onDisconnect()
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            permissionDeniedNote = null
                            onConnect()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnectedOrConnecting) AlertRed else MatrixGreen,
                    contentColor = VoidBackground
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("live_voice_toggle_button")
            ) {
                Icon(
                    imageVector = if (isConnectedOrConnecting) Icons.Default.CallEnd else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (uiState.status) {
                        LiveLinkStatus.CONNECTING -> "ABORT HANDSHAKE"
                        LiveLinkStatus.CONNECTED_LISTENING, LiveLinkStatus.AI_SPEAKING -> "TERMINATE LINK"
                        else -> "ESTABLISH LIVE VOICE LINK"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isLiveReady) {
                OutlinedButton(
                    onClick = onToggleMute,
                    border = BorderStroke(1.dp, if (uiState.isMuted) AlertRed else MatrixGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("live_voice_mute_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (uiState.isMuted) "Unmute microphone" else "Mute microphone",
                        tint = if (uiState.isMuted) AlertRed else MatrixGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        val activeError = uiState.errorMessage ?: permissionDeniedNote
        if (activeError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = AlertRed.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AlertRed),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = activeError,
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed,
                    modifier = Modifier.padding(10.dp)
                )
            }
        } else if (uiState.micNotice != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = SeverityHigh.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, SeverityHigh.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.micNotice,
                    style = MaterialTheme.typography.labelSmall,
                    color = SeverityHigh,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live Transcript Feed Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "// LIVE VOICE TRANSCRIPT",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            if (uiState.transcripts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onClearTranscripts)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear transcript",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "CLEAR",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Transcript List
        Surface(
            color = ElevatedSurface,
            border = BorderStroke(1.dp, SubtleBorder),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.transcripts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tap ESTABLISH LIVE VOICE LINK to initiate hands-free bilingual cybersecurity briefings with RIAD AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.transcripts, key = { it.id }) { item ->
                        Surface(
                            color = if (item.isAi) VoidBackground else MatrixGreenSubtle,
                            border = BorderStroke(
                                1.dp,
                                if (item.isAi) SubtleBorder else MatrixGreen.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = item.speaker,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MatrixGreen
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Voice Prompts + Text Directive Injection while Live Session is active
        if (isLiveReady) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(LIVE_QUICK_VOICE_PROMPTS) { (label, prompt) ->
                    Surface(
                        color = ElevatedSurface,
                        border = BorderStroke(1.dp, MatrixGreen.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSendDirective(prompt) }
                    ) {
                        Text(
                            text = "> $label",
                            style = MaterialTheme.typography.labelSmall,
                            color = MatrixGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.textDirectiveDraft,
                    onValueChange = onUpdateDirective,
                    placeholder = {
                        Text(
                            text = "> Send text prompt for live Fenrir audio reply...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = TextPrimary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            keyboardController?.hide()
                            onSendDirective(null)
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MatrixGreen,
                        unfocusedBorderColor = SubtleBorder,
                        focusedContainerColor = ElevatedSurface,
                        unfocusedContainerColor = ElevatedSurface,
                        cursorColor = MatrixGreen
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                                !event.isShiftPressed
                            ) {
                                if (uiState.textDirectiveDraft.isNotBlank()) {
                                    keyboardController?.hide()
                                    onSendDirective(null)
                                }
                                true
                            } else false
                        }
                )
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        onSendDirective(null)
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(MatrixGreen, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send text to live voice session",
                        tint = VoidBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun TacticalRadarVisualizer(
    status: LiveLinkStatus,
    signalLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_anim")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    LiveLinkStatus.AI_SPEAKING -> 1600
                    LiveLinkStatus.CONNECTING -> 1100
                    LiveLinkStatus.CONNECTED_LISTENING -> 3000
                    else -> 6000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f - 4.dp.toPx()

            val ringRadii = listOf(0.56f, 0.76f, 0.96f)
            ringRadii.forEach { factor ->
                drawCircle(
                    color = MatrixGreen.copy(alpha = 0.22f),
                    radius = maxR * factor,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            if (status != LiveLinkStatus.DISCONNECTED) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.75f to Color.Transparent,
                        1f to MatrixGreen.copy(alpha = 0.35f),
                        center = center
                    ),
                    startAngle = sweepAngle,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(center.x - maxR * 0.96f, center.y - maxR * 0.96f),
                    size = androidx.compose.ui.geometry.Size(maxR * 1.92f, maxR * 1.92f)
                )
            }

            val barCount = 48
            val baseRadius = maxR * 0.62f
            val baseAmp = when (status) {
                LiveLinkStatus.AI_SPEAKING -> maxR * (0.18f + 0.16f * signalLevel)
                LiveLinkStatus.CONNECTED_LISTENING -> maxR * (0.09f + 0.22f * signalLevel)
                LiveLinkStatus.CONNECTING -> maxR * 0.13f
                LiveLinkStatus.ERROR -> maxR * 0.08f
                LiveLinkStatus.DISCONNECTED -> maxR * 0.05f
            }
            val barColor = if (status == LiveLinkStatus.ERROR) AlertRed else MatrixGreen

            for (i in 0 until barCount) {
                val angleRad = (i * 2.0 * PI / barCount).toFloat()
                val harmonic = (
                    0.5f +
                        0.3f * sin(phase * 2f + i * 0.55f) +
                        0.2f * cos(phase * 3f - i * 0.35f)
                    ).coerceIn(0.15f, 1.0f)
                val barLen = baseAmp * harmonic
                val start = Offset(
                    x = center.x + baseRadius * cos(angleRad),
                    y = center.y + baseRadius * sin(angleRad)
                )
                val end = Offset(
                    x = center.x + (baseRadius + barLen) * cos(angleRad),
                    y = center.y + (baseRadius + barLen) * sin(angleRad)
                )
                drawLine(
                    color = barColor.copy(alpha = if (status == LiveLinkStatus.DISCONNECTED) 0.35f else 0.92f),
                    start = start,
                    end = end,
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }

        Image(
            painter = painterResource(id = R.drawable.riad_ai_logo),
            contentDescription = stringResource(R.string.cd_riad_logo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .border(
                    2.dp,
                    if (status == LiveLinkStatus.ERROR) AlertRed else MatrixGreen,
                    CircleShape
                )
        )
    }
}
