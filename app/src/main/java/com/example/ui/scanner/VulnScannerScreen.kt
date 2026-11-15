package com.example.ui.scanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RiadAiPersona
import com.example.data.ScanDepth
import com.example.data.ScannerUiState
import com.example.data.SeverityLevel
import com.example.data.VulnFinding
import com.example.data.VulnScanReport
import com.example.ui.components.CodeBlockCard
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.MatrixGreenSubtle
import com.example.ui.theme.SeverityHigh
import com.example.ui.theme.SeverityLow
import com.example.ui.theme.SeverityMedium
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VoidBackground

@Composable
fun VulnScannerScreen(
    uiState: VulnScannerScreenState,
    onUpdateSource: (String) -> Unit,
    onSelectDepth: (ScanDepth) -> Unit,
    onLoadPreset: (String) -> Unit,
    onRunScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBackground)
            .imePadding()
            .navigationBarsPadding()
            .padding(12.dp)
    ) {
        val isSplitPane = maxWidth >= 800.dp

        if (isSplitPane) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ScannerInputPane(
                    uiState = uiState,
                    onUpdateSource = onUpdateSource,
                    onSelectDepth = onSelectDepth,
                    onLoadPreset = onLoadPreset,
                    onRunScan = onRunScan,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                ScannerResultsPane(
                    scanState = uiState.scanState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ScannerInputPane(
                    uiState = uiState,
                    onUpdateSource = onUpdateSource,
                    onSelectDepth = onSelectDepth,
                    onLoadPreset = onLoadPreset,
                    onRunScan = onRunScan,
                    modifier = Modifier.fillMaxWidth()
                )
                ScannerResultsPane(
                    scanState = uiState.scanState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ScannerInputPane(
    uiState: VulnScannerScreenState,
    onUpdateSource: (String) -> Unit,
    onSelectDepth: (ScanDepth) -> Unit,
    onLoadPreset: (String) -> Unit,
    onRunScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ElevatedSurface,
        border = BorderStroke(1.dp, SubtleBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "// TARGET PAYLOAD / SOURCE CODE",
                style = MaterialTheme.typography.labelMedium,
                color = MatrixGreen
            )

            // Preset Payloads
            LazyRow(
                contentPadding = PaddingValues(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(RiadAiPersona.SCANNER_PRESETS) { preset ->
                    Surface(
                        color = VoidBackground,
                        border = BorderStroke(1.dp, SubtleBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onLoadPreset(preset.code) }
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Scan Depth Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScanDepth.entries.forEach { depth ->
                    val selected = uiState.selectedDepth == depth
                    Surface(
                        color = if (selected) MatrixGreenSubtle else VoidBackground,
                        border = BorderStroke(1.dp, if (selected) MatrixGreen else SubtleBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectDepth(depth) }
                            .testTag("scan_depth_${depth.name.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = depth.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MatrixGreen else TextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.sourceInput,
                onValueChange = onUpdateSource,
                placeholder = {
                    Text(
                        text = "Paste PHP, JS, Python, SQL, or HTTP headers to audit...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = TextPrimary,
                    lineHeight = 18.sp
                ),
                minLines = 8,
                maxLines = 14,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MatrixGreen,
                    unfocusedBorderColor = SubtleBorder,
                    focusedContainerColor = CodeBackground,
                    unfocusedContainerColor = CodeBackground,
                    cursorColor = MatrixGreen
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vuln_scanner_code_input")
            )

            Button(
                onClick = onRunScan,
                enabled = uiState.scanState !is ScannerUiState.Scanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MatrixGreen,
                    contentColor = VoidBackground,
                    disabledContainerColor = MatrixGreenSubtle,
                    disabledContentColor = MatrixGreen
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("run_vuln_scan_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.scanState is ScannerUiState.Scanning) {
                        "SCANNING STATIC VECTORS..."
                    } else {
                        "RUN STATIC ANALYSIS"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScannerResultsPane(
    scanState: ScannerUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ElevatedSurface,
        border = BorderStroke(1.dp, SubtleBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        when (scanState) {
            ScannerUiState.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MatrixGreen,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "STATIC AUDIT ENGINE READY",
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen
                    )
                    Text(
                        text = "Select a preset or paste source code/HTTP headers and tap RUN STATIC ANALYSIS for structured CWE ratings and hardened patches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            ScannerUiState.Scanning -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "> EXECUTING DEEP AST & TAINT ANALYSIS...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MatrixGreen
                    )
                    LinearProgressIndicator(
                        color = MatrixGreen,
                        trackColor = SubtleBorder,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Checking CWE Top 25, OWASP Top 10, injection sinks, and cryptographic misconfigurations...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            is ScannerUiState.Error -> {
                Surface(
                    color = AlertRed.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AlertRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "SCANNER FAULT",
                            style = MaterialTheme.typography.labelLarge,
                            color = AlertRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = scanState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }
            is ScannerUiState.Success -> {
                ScanReportView(report = scanState.report)
            }
        }
    }
}

@Composable
private fun ScanReportView(report: VulnScanReport) {
    val riskColor = severityColor(report.overallRisk)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Executive Risk Summary Card
        Surface(
            color = VoidBackground,
            border = BorderStroke(1.dp, riskColor.copy(alpha = 0.65f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = riskColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, riskColor),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "RISK: ${report.overallRisk.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = riskColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "${report.language} • SCORE ${report.riskScore}/100",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }

                LinearProgressIndicator(
                    progress = { report.riskScore / 100f },
                    color = riskColor,
                    trackColor = SubtleBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Text(
                    text = report.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }
        }

        // Findings List
        Text(
            text = "// IDENTIFIED VULNERABILITIES (${report.findings.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MatrixGreen
        )

        report.findings.forEach { finding ->
            FindingCard(finding = finding)
        }

        // Patched / Hardened Code Output
        if (report.patchedCode.isNotBlank()) {
            Text(
                text = "// HARDENED PATCHED CODE",
                style = MaterialTheme.typography.labelMedium,
                color = MatrixGreen
            )
            CodeBlockCard(
                language = report.language,
                code = report.patchedCode
            )
        }
    }
}

@Composable
private fun FindingCard(finding: VulnFinding) {
    val color = severityColor(finding.severity)
    Surface(
        color = VoidBackground,
        border = BorderStroke(1.dp, SubtleBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = color.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, color),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = finding.severity.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = finding.cwe,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = finding.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (finding.vulnerableLine.isNotBlank()) {
                Surface(
                    color = AlertRed.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = finding.vulnerableLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = AlertRed,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Text(
                text = finding.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )

            Surface(
                color = MatrixGreenSubtle,
                border = BorderStroke(1.dp, MatrixGreen.copy(alpha = 0.30f)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "PATCH: ${finding.remediation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MatrixGreen,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

private fun severityColor(level: SeverityLevel): Color = when (level) {
    SeverityLevel.CRITICAL -> AlertRed
    SeverityLevel.HIGH -> SeverityHigh
    SeverityLevel.MEDIUM -> SeverityMedium
    SeverityLevel.LOW -> SeverityLow
    SeverityLevel.SECURE -> MatrixGreen
}
