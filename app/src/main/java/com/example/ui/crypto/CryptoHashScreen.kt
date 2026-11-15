package com.example.ui.crypto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.CryptoUtils
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

private enum class CryptoTab(val label: String) {
    BASE64("BASE64"),
    JWT("JWT DECODER"),
    HASH("HASH GEN"),
    ENTROPY("ENTROPY")
}

@Composable
fun CryptoHashScreen(modifier: Modifier = Modifier) {
    var activeTab by remember { mutableStateOf(CryptoTab.BASE64) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBackground)
            .imePadding()
            .navigationBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sub-module Tab Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CryptoTab.entries.forEach { tab ->
                val selected = tab == activeTab
                Surface(
                    color = if (selected) MatrixGreenSubtle else ElevatedSurface,
                    border = BorderStroke(1.dp, if (selected) MatrixGreen else SubtleBorder),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { activeTab = tab }
                        .testTag("crypto_tab_${tab.name.lowercase()}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MatrixGreen else TextMuted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeTab) {
                CryptoTab.BASE64 -> Base64ToolPane()
                CryptoTab.JWT -> JwtDecoderPane()
                CryptoTab.HASH -> HashGeneratorPane()
                CryptoTab.ENTROPY -> PasswordEntropyPane()
            }
        }
    }
}

@Composable
private fun Base64ToolPane() {
    var input by remember { mutableStateOf("RIAD_AI_ZERO_TRUST_PAYLOAD") }
    var output by remember { mutableStateOf("") }
    var urlSafe by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Surface(
        color = ElevatedSurface,
        border = BorderStroke(1.dp, SubtleBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "// CLIENT-SIDE BASE64 CODEC",
                style = MaterialTheme.typography.labelMedium,
                color = MatrixGreen
            )

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Plaintext or Base64 Input") },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = TextPrimary
                ),
                minLines = 3,
                maxLines = 6,
                colors = terminalFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("base64_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "URL-Safe Alphabet (- _ no padding)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Switch(
                    checked = urlSafe,
                    onCheckedChange = { urlSafe = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VoidBackground,
                        checkedTrackColor = MatrixGreen
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        errorMsg = null
                        output = CryptoUtils.encodeBase64(input, urlSafe)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MatrixGreen,
                        contentColor = VoidBackground
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("base64_encode_btn")
                ) {
                    Text("ENCODE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        CryptoUtils.decodeBase64(input)
                            .onSuccess {
                                errorMsg = null
                                output = it
                            }
                            .onFailure {
                                errorMsg = "Malformed Base64 input: ${it.localizedMessage}"
                            }
                    },
                    border = BorderStroke(1.dp, MatrixGreen),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("base64_decode_btn")
                ) {
                    Text("DECODE", style = MaterialTheme.typography.labelLarge, color = MatrixGreen)
                }
            }

            if (errorMsg != null) {
                Text(
                    text = errorMsg.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed
                )
            }

            if (output.isNotEmpty()) {
                CodeBlockCard(language = "OUTPUT", code = output)
            }
        }
    }
}

@Composable
private fun JwtDecoderPane() {
    val sampleJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJzdWIiOiJvcF8wMV9yaWFkIiwicm9sZSI6InNlY3VyaXR5X2FyY2hpdGVjdCIsImlhdCI6MTczMDgwMDAwMCwiZXhwIjoxOTAwMDAwMDAwfQ." +
        "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

    var tokenInput by remember { mutableStateOf(sampleJwt) }
    val decodedResult = remember(tokenInput) { CryptoUtils.decodeJwt(tokenInput) }

    Surface(
        color = ElevatedSurface,
        border = BorderStroke(1.dp, SubtleBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "// JWT INSPECTOR & CLAIM AUDITOR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MatrixGreen
                )
                Text(
                    text = "LOAD SAMPLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MatrixGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { tokenInput = sampleJwt }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("Paste JWT (header.payload.signature)") },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = TextPrimary
                ),
                minLines = 3,
                maxLines = 5,
                colors = terminalFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("jwt_token_input")
            )

            decodedResult.onSuccess { jwt ->
                Surface(
                    color = if (jwt.isWarning) SeverityMedium.copy(alpha = 0.12f) else MatrixGreenSubtle,
                    border = BorderStroke(1.dp, if (jwt.isWarning) SeverityMedium else MatrixGreen),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "ALG: ${jwt.algorithm}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (jwt.isWarning) SeverityMedium else MatrixGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = jwt.statusNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
                CodeBlockCard(language = "HEADER JSON", code = jwt.headerJson)
                CodeBlockCard(language = "PAYLOAD CLAIMS", code = jwt.payloadJson)
                CodeBlockCard(language = "SIGNATURE (B64URL)", code = jwt.signatureHex)
            }.onFailure { err ->
                Surface(
                    color = AlertRed.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AlertRed),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err.localizedMessage ?: "Invalid JWT token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertRed,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HashGeneratorPane() {
    var input by remember { mutableStateOf("RIAD_AI_VERIFY") }
    val clipboard = LocalClipboardManager.current

    val hashes = remember(input) {
        listOf(
            "SHA-256" to CryptoUtils.hash(input, "SHA-256"),
            "SHA-512" to CryptoUtils.hash(input, "SHA-512"),
            "SHA-1 (LEGACY)" to CryptoUtils.hash(input, "SHA-1"),
            "MD5 (LEGACY)" to CryptoUtils.hash(input, "MD5")
        )
    }

    Surface(
        color = ElevatedSurface,
        border = BorderStroke(1.dp, SubtleBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "// LOCAL CRYPTOGRAPHIC HASH ENGINE",
                style = MaterialTheme.typography.labelMedium,
                color = MatrixGreen
            )

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Input String / Payload") },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = TextPrimary
                ),
                minLines = 2,
                maxLines = 4,
                colors = terminalFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hash_input")
            )

            hashes.forEach { (algo, digest) ->
                Surface(
                    color = CodeBackground,
                    border = BorderStroke(1.dp, SubtleBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = algo,
                                style = MaterialTheme.typography.labelSmall,
                                color = MatrixGreen
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = digest,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(digest)) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy $algo hash",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordEntropyPane() {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val report = remember(password) { CryptoUtils.analyzePasswordEntropy(password) }

    val ratingColor = when {
        report.bits <= 0.0 -> TextMuted
        report.bits < 36.0 -> AlertRed
        report.bits < 60.0 -> SeverityHigh
        report.bits < 80.0 -> SeverityLow
        else -> MatrixGreen
    }

    Surface(
        color = ElevatedSurface,
        border = BorderStroke(1.dp, SubtleBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "// PASSWORD ENTROPY & BRUTE-FORCE ESTIMATOR",
                style = MaterialTheme.typography.labelMedium,
                color = MatrixGreen
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Enter Password / Passphrase") },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = { visible = !visible },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility",
                                tint = TextMuted
                            )
                        }
                        if (password.isNotEmpty()) {
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(password)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy password",
                                    tint = MatrixGreen
                                )
                            }
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = TextPrimary
                ),
                singleLine = true,
                colors = terminalFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entropy_password_input")
            )

            Button(
                onClick = {
                    password = CryptoUtils.generateSecurePassword(20)
                    visible = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MatrixGreen,
                    contentColor = VoidBackground
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_password_button")
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("GENERATE 20-CHAR CSPRNG KEY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }

            Surface(
                color = VoidBackground,
                border = BorderStroke(1.dp, ratingColor.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = report.rating,
                            style = MaterialTheme.typography.labelLarge,
                            color = ratingColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "%.1f BITS".format(report.bits),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (report.bits / 110.0).toFloat().coerceIn(0f, 1f) },
                        color = ratingColor,
                        trackColor = SubtleBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Text(
                        text = "Est. Offline GPU Cluster (10B/s): ${report.crackTimeEstimate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Charset Pool: ${report.poolSize} • Length: ${password.length}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CharsetBadge("a-z", report.hasLower, Modifier.weight(1f))
                        CharsetBadge("A-Z", report.hasUpper, Modifier.weight(1f))
                        CharsetBadge("0-9", report.hasDigits, Modifier.weight(1f))
                        CharsetBadge("!@#", report.hasSymbols, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CharsetBadge(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (active) MatrixGreenSubtle else ElevatedSurface,
        border = BorderStroke(1.dp, if (active) MatrixGreen else SubtleBorder),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MatrixGreen else TextMuted
            )
        }
    }
}

@Composable
private fun terminalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MatrixGreen,
    unfocusedBorderColor = SubtleBorder,
    focusedContainerColor = CodeBackground,
    unfocusedContainerColor = CodeBackground,
    cursorColor = MatrixGreen,
    focusedLabelColor = MatrixGreen,
    unfocusedLabelColor = TextMuted
)
