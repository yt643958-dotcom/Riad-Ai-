package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ModuleRoute
import com.example.data.RiadAiPersona
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.MatrixGreenSubtle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VoidBackground
import kotlinx.coroutines.delay

@Composable
fun CrtScanlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val step = 6.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.22f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
            y += step
        }
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.36f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * 0.72f
            )
        )
    }
}

@Composable
fun RiadSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val bootLines = remember {
        listOf(
            "> INITIALIZING RIAD AI KERNEL...",
            "> VERIFYING APP CHECK ATTESTATION...",
            "> LOADING WHITE-HAT ETHICAL GUARDRAILS...",
            "> NEURAL DEFENSE MATRIX: ONLINE // ENCRYPTED"
        )
    }
    var visibleCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(visible) {
        if (visible) {
            for (i in 2..bootLines.size) {
                delay(300)
                visibleCount = i
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "splash_pulse")
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(420)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VoidBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(164.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MatrixGreen.copy(alpha = glowAlpha * 0.45f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension * 0.52f
                        )
                    }
                    Image(
                        painter = painterResource(id = R.drawable.riad_ai_logo),
                        contentDescription = stringResource(R.string.cd_riad_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(132.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.5.dp, MatrixGreen.copy(alpha = glowAlpha), RoundedCornerShape(24.dp))
                    )
                }

                Text(
                    text = "RIAD AI",
                    style = MaterialTheme.typography.displaySmall.copy(
                        color = MatrixGreen,
                        shadow = Shadow(
                            color = MatrixGreen.copy(alpha = 0.55f),
                            blurRadius = 18f
                        )
                    )
                )
                Text(
                    text = stringResource(R.string.app_full_form),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )

                Surface(
                    color = ElevatedSurface,
                    border = BorderStroke(1.dp, SubtleBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        bootLines.take(visibleCount).forEachIndexed { idx, line ->
                            val isLast = idx == bootLines.lastIndex
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isLast) MatrixGreen else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RiadTopBar(
    currentRoute: ModuleRoute,
    showMenuButton: Boolean,
    ttsEnabled: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "online_beacon")
    val beaconAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ElevatedSurface.copy(alpha = 0.90f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showMenuButton) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("drawer_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.cd_open_navigation),
                        tint = MatrixGreen
                    )
                }
            }

            Image(
                painter = painterResource(id = R.drawable.riad_ai_logo),
                contentDescription = stringResource(R.string.cd_riad_logo),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MatrixGreen.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${currentRoute.code} // ${currentRoute.title}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MatrixGreen,
                            shadow = Shadow(
                                color = MatrixGreen.copy(alpha = 0.4f),
                                blurRadius = 10f
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MatrixGreen.copy(alpha = beaconAlpha))
                    )
                    Text(
                        text = "ONLINE // ENCRYPTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1
                    )
                    Surface(
                        color = MatrixGreenSubtle,
                        border = BorderStroke(1.dp, MatrixGreen.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = when (currentRoute) {
                                ModuleRoute.LIVE_VOICE -> RiadAiPersona.MODEL_LIVE
                                else -> RiadAiPersona.MODEL_CHAT
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MatrixGreen,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (currentRoute == ModuleRoute.TERMINAL_CHAT) {
                Surface(
                    color = if (ttsEnabled) MatrixGreenSubtle else VoidBackground,
                    border = BorderStroke(
                        1.dp,
                        if (ttsEnabled) MatrixGreen else SubtleBorder
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleTts)
                        .testTag("tts_briefing_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (ttsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = stringResource(R.string.cd_tts_toggle),
                            tint = if (ttsEnabled) MatrixGreen else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (ttsEnabled) "TTS: ON" else "TTS: OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ttsEnabled) MatrixGreen else TextMuted
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.25f), thickness = 1.dp)
    }
}

@Composable
fun RiadNavigationDrawerContent(
    currentRoute: ModuleRoute,
    onSelectRoute: (ModuleRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(292.dp)
            .background(ElevatedSurface)
            .border(1.dp, SubtleBorder)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Brand Header with Official RIAD AI Logo
        Surface(
            color = VoidBackground,
            border = BorderStroke(1.dp, MatrixGreen.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.riad_ai_logo),
                    contentDescription = stringResource(R.string.cd_riad_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MatrixGreen.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                )
                Column {
                    Text(
                        text = "RIAD AI",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = MatrixGreen,
                            shadow = Shadow(
                                color = MatrixGreen.copy(alpha = 0.45f),
                                blurRadius = 12f
                            )
                        )
                    )
                    Text(
                        text = stringResource(R.string.app_full_form),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "// TACTICAL MODULES",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModuleRoute.entries.forEach { route ->
                val selected = route == currentRoute
                val icon: ImageVector = when (route) {
                    ModuleRoute.TERMINAL_CHAT -> Icons.Default.Terminal
                    ModuleRoute.LIVE_VOICE -> Icons.Default.GraphicEq
                    ModuleRoute.VULN_SCANNER -> Icons.Default.Security
                    ModuleRoute.CRYPTO_HASH -> Icons.Default.Key
                }
                Surface(
                    color = if (selected) MatrixGreenSubtle else VoidBackground,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MatrixGreen else SubtleBorder
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectRoute(route) }
                        .testTag("nav_item_${route.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) MatrixGreen else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${route.code} // ${route.title}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) MatrixGreen else TextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = route.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = true))
        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            color = VoidBackground,
            border = BorderStroke(1.dp, SubtleBorder),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "ETHICAL PROTOCOL // WHITE HAT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MatrixGreen
                )
                Text(
                    text = "EN / বাংলা Auto-Detect • Fenrir Briefing • App Check Armed",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}
