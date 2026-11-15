package com.example.data

enum class ModuleRoute(
    val code: String,
    val title: String,
    val subtitle: String
) {
    TERMINAL_CHAT("01", "TERMINAL CHAT", "Encrypted Tactical AI Console"),
    LIVE_VOICE("02", "LIVE VOICE LINK", "Full-Duplex Gemini Live Audio"),
    VULN_SCANNER("03", "VULN SCANNER", "AI Static Code & Header Audit"),
    CRYPTO_HASH("04", "CRYPTO & HASH", "Local Encoding, JWT & Entropy")
}

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestamp: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

enum class ScanDepth(val label: String, val directive: String) {
    QUICK_RECON("QUICK RECON", "Perform a rapid high-impact vulnerability triage focusing on Critical/High exploits."),
    DEEP_AUDIT("DEEP AUDIT", "Perform an exhaustive static security audit covering injection, auth, crypto, config, and business logic."),
    OWASP_STRICT("OWASP STRICT", "Audit strictly against OWASP Top 10 & CWE standards with zero-trust remediation.")
}

enum class SeverityLevel(val label: String) {
    CRITICAL("CRITICAL"),
    HIGH("HIGH"),
    MEDIUM("MEDIUM"),
    LOW("LOW"),
    SECURE("SECURE")
}

data class VulnFinding(
    val severity: SeverityLevel,
    val title: String,
    val cwe: String,
    val vulnerableLine: String,
    val explanation: String,
    val remediation: String
)

data class VulnScanReport(
    val overallRisk: SeverityLevel,
    val riskScore: Int,
    val language: String,
    val summary: String,
    val findings: List<VulnFinding>,
    val patchedCode: String
)

sealed interface ScannerUiState {
    data object Idle : ScannerUiState
    data object Scanning : ScannerUiState
    data class Success(val report: VulnScanReport) : ScannerUiState
    data class Error(val message: String) : ScannerUiState
}

enum class LiveLinkStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_LISTENING,
    AI_SPEAKING,
    ERROR
}

data class LiveTranscriptItem(
    val id: String,
    val speaker: String,
    val isAi: Boolean,
    val text: String
)
