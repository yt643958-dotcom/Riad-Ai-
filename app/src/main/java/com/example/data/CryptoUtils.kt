package com.example.data

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.log2
import kotlin.math.pow

data class JwtDecodedResult(
    val headerJson: String,
    val payloadJson: String,
    val signatureHex: String,
    val algorithm: String,
    val statusNote: String,
    val isWarning: Boolean
)

data class EntropyReport(
    val bits: Double,
    val poolSize: Int,
    val rating: String,
    val crackTimeEstimate: String,
    val hasLower: Boolean,
    val hasUpper: Boolean,
    val hasDigits: Boolean,
    val hasSymbols: Boolean
)

object CryptoUtils {

    private val secureRandom = SecureRandom()

    fun encodeBase64(input: String, urlSafe: Boolean = false): String {
        val flags = if (urlSafe) Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING else Base64.NO_WRAP
        return Base64.encodeToString(input.toByteArray(Charsets.UTF_8), flags)
    }

    fun decodeBase64(input: String): Result<String> = runCatching {
        val cleaned = input.trim()
        val flags = if (cleaned.contains('-') || cleaned.contains('_')) {
            Base64.URL_SAFE or Base64.NO_PADDING
        } else {
            Base64.DEFAULT
        }
        String(Base64.decode(cleaned, flags), Charsets.UTF_8)
    }

    fun decodeJwt(token: String): Result<JwtDecodedResult> = runCatching {
        val parts = token.trim().split(".")
        require(parts.size == 3) { "Invalid JWT structure: expected 3 dot-separated segments." }

        val headerBytes = Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val headerObj = JSONObject(String(headerBytes, Charsets.UTF_8))
        val payloadObj = JSONObject(String(payloadBytes, Charsets.UTF_8))

        val alg = headerObj.optString("alg", "UNKNOWN")
        val warnings = mutableListOf<String>()
        var isWarning = false

        if (alg.equals("none", ignoreCase = true)) {
            warnings.add("CRITICAL: Unsecured 'alg: none' bypass risk!")
            isWarning = true
        } else if (alg.equals("HS256", ignoreCase = true)) {
            warnings.add("INFO: Symmetric HS256 — ensure strong >=256-bit secret.")
        }

        if (payloadObj.has("exp")) {
            val expSec = payloadObj.optLong("exp", 0L)
            val nowSec = System.currentTimeMillis() / 1000L
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val expDate = sdf.format(Date(expSec * 1000L))
            if (expSec < nowSec) {
                warnings.add("EXPIRED at $expDate")
                isWarning = true
            } else {
                warnings.add("VALID until $expDate")
            }
        } else {
            warnings.add("WARNING: Missing 'exp' claim (non-expiring token).")
            isWarning = true
        }

        JwtDecodedResult(
            headerJson = headerObj.toString(2),
            payloadJson = payloadObj.toString(2),
            signatureHex = parts[2].ifEmpty { "<EMPTY SIGNATURE>" },
            algorithm = alg,
            statusNote = warnings.joinToString(" • "),
            isWarning = isWarning
        )
    }

    fun hash(input: String, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun analyzePasswordEntropy(password: String): EntropyReport {
        if (password.isEmpty()) {
            return EntropyReport(
                bits = 0.0,
                poolSize = 0,
                rating = "EMPTY",
                crackTimeEstimate = "Instant",
                hasLower = false,
                hasUpper = false,
                hasDigits = false,
                hasSymbols = false
            )
        }

        val hasLower = password.any { it in 'a'..'z' }
        val hasUpper = password.any { it in 'A'..'Z' }
        val hasDigits = password.any { it in '0'..'9' }
        val hasSymbols = password.any { !it.isLetterOrDigit() }

        var pool = 0
        if (hasLower) pool += 26
        if (hasUpper) pool += 26
        if (hasDigits) pool += 10
        if (hasSymbols) pool += 33
        if (pool == 0) pool = 26

        val bits = password.length * log2(pool.toDouble())
        val rating = when {
            bits < 36 -> "CRITICAL // WEAK"
            bits < 60 -> "MODERATE"
            bits < 80 -> "STRONG"
            else -> "MILITARY GRADE"
        }

        // Offline GPU cluster ~ 10^10 guesses/sec (10 billion/s)
        val guesses = 2.0.pow(bits)
        val seconds = guesses / 1e10
        val estimate = formatDuration(seconds)

        return EntropyReport(
            bits = bits,
            poolSize = pool,
            rating = rating,
            crackTimeEstimate = estimate,
            hasLower = hasLower,
            hasUpper = hasUpper,
            hasDigits = hasDigits,
            hasSymbols = hasSymbols
        )
    }

    fun generateSecurePassword(length: Int = 20): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#\$%^&*()-_=+[]"
        return buildString(length) {
            repeat(length) {
                append(chars[secureRandom.nextInt(chars.length)])
            }
        }
    }

    private fun formatDuration(seconds: Double): String {
        if (seconds < 1.0) return "< 1 second"
        if (seconds < 60.0) return "${seconds.toInt()} seconds"
        val minutes = seconds / 60.0
        if (minutes < 60.0) return "${minutes.toInt()} minutes"
        val hours = minutes / 60.0
        if (hours < 24.0) return "${hours.toInt()} hours"
        val days = hours / 24.0
        if (days < 365.0) return "${days.toInt()} days"
        val years = days / 365.25
        return when {
            years < 1_000 -> "${years.toInt()} years"
            years < 1_000_000 -> "${(years / 1_000).toInt()}K years"
            years < 1_000_000_000 -> "${(years / 1_000_000).toInt()}M years"
            else -> "Centuries+"
        }
    }
}
