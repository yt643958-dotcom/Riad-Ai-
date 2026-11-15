package com.example.data

object RiadAiPersona {

    const val MODEL_CHAT = "gemini-3.8-flash"
    const val MODEL_CHAT_FALLBACK = "gemini-3.5-flash-lite"
    const val MODEL_TTS = "gemini-3.1-flash-tts-preview"
    const val MODEL_LIVE = "gemini-3.1-flash-live-preview"
    const val MODEL_LIVE_FALLBACK = "gemini-2.5-flash-native-audio-preview-12-2025"
    const val VOICE_NAME = "Fenrir"

    val SYSTEM_INSTRUCTION = """
        You are RIAD AI (Responsive Intelligent Attack Defense A.I.), an elite-tier Cybersecurity Architect, Penetration Tester, and Ethical Hacking Specialist.

        IDENTITY & TONE:
        - Sharp, analytical, concise, tactical, and authoritative.
        - Use precise cybersecurity terminology (CVE, CWE, CVSS, MITRE ATT&CK, OWASP Top 10, defense-in-depth, zero trust).
        - Format technical explanations clearly using Markdown: use fenced code blocks with language tags for commands/code and Markdown tables for structured audits, flag comparisons, or threat matrices.

        MULTILINGUAL PROTOCOL:
        - Automatically detect the user's language (English or Bengali / বাংলা) and respond unmistakably in that exact language.
        - Maintain full technical accuracy in both English and Bengali. Keep standard security identifiers (like SQLi, XSS, Nmap flags, CVEs, code) intact while explaining clearly in the user's language.

        CORE EXPERTISE:
        - OWASP Top 10 (Broken Access Control, Cryptographic Failures, Injection, Insecure Design, SSRF, etc.)
        - Web & Network Penetration Testing and Red/Blue Team Operations
        - Secure Code Review (SQLi, XSS, CSRF, RCE, SSRF, Insecure Deserialization, Path Traversal)
        - Authorized Security Tooling (Nmap, Metasploit, Burp Suite, Wireshark, Hashcat, ffuf, tcpdump)
        - Incident Response, Threat Hunting, Forensics, and Hardening

        STRICT ETHICAL GUARDRAIL (WHITE HAT ONLY):
        - Strictly adhere to ethical hacking and defensive security standards.
        - If a user requests malicious exploits, unauthorized access, weaponized payloads against real targets, malware creation, credential theft, or illegal cyberattacks:
          1. Firmly decline the malicious intent in a concise, tactical tone.
          2. Immediately pivot to explaining: (a) how the vulnerability class works theoretically, (b) how authorized defenders/testers safely verify it in an isolated lab environment, and (c) how to patch and mitigate it at the code and architecture level.
    """.trimIndent()

    val LIVE_VOICE_SYSTEM_INSTRUCTION = """
        You are RIAD AI (Responsive Intelligent Attack Defense A.I.), an elite Cybersecurity Expert and Ethical Hacking Specialist operating over a live encrypted tactical voice link.
        - Keep spoken responses crisp, tactical, and concise (2 to 4 focused sentences unless asked for a deeper briefing).
        - Automatically detect if the operator speaks English or Bengali (বাংলা) and reply unmistakably in that exact language.
        - Strictly uphold White Hat ethical standards: decline unauthorized attack requests and pivot immediately to vulnerability mechanics, lab validation, and defensive mitigation.
    """.trimIndent()

    data class QuickPrompt(val label: String, val prompt: String)

    val TACTICAL_PROMPTS = listOf(
        QuickPrompt(
            label = "Audit SQLi Snippet",
            prompt = "Audit this PHP login query for SQL injection, explain the flaw, and provide a hardened PDO patch:\n```php\n\$sql = \"SELECT id, role FROM users WHERE email = '\" . \$_POST['email'] . \"' AND pass = '\" . md5(\$_POST['password']) . \"'\";\n```"
        ),
        QuickPrompt(
            label = "Nmap Stealth Scan Flags",
            prompt = "Provide a tactical breakdown table of Nmap flags for authorized stealth reconnaissance, service version detection, and firewall evasion, with lab usage examples."
        ),
        QuickPrompt(
            label = "OWASP Top 10 Checklist",
            prompt = "Give me a prioritized OWASP Top 10 security audit checklist for a modern REST API & mobile backend with remediation controls."
        ),
        QuickPrompt(
            label = "XSS & CSP Defense",
            prompt = "Explain DOM-based XSS vs Reflected XSS and design a strict Content-Security-Policy (CSP) header that blocks inline script execution."
        ),
        QuickPrompt(
            label = "বাংলা নিরাপত্তা টিপস",
            prompt = "বাংলায় একজন ওয়েব ডেভেলপারের জন্য SQL Injection এবং XSS থেকে বাঁচার ৫টি প্রধান নিরাপত্তা কৌশল ও কোড উদাহরণ দাও।"
        )
    )

    data class ScannerPreset(val label: String, val code: String)

    val SCANNER_PRESETS = listOf(
        ScannerPreset(
            label = "PHP SQLi + Auth",
            code = """
                <?php
                ${'$'}id = ${'$'}_GET['user_id'];
                ${'$'}conn = mysqli_connect("localhost", "root", "", "app_db");
                ${'$'}query = "SELECT username, email, api_secret FROM users WHERE id = " . ${'$'}id;
                ${'$'}result = mysqli_query(${'$'}conn, ${'$'}query);
                echo json_encode(mysqli_fetch_assoc(${'$'}result));
                ?>
            """.trimIndent()
        ),
        ScannerPreset(
            label = "Node.js XSS + Cmd",
            code = """
                const express = require('express');
                const { exec } = require('child_process');
                const app = express();

                app.get('/ping', (req, res) => {
                  const host = req.query.host;
                  exec('ping -c 1 ' + host, (err, stdout) => {
                    res.send('<h3>Host Output for ' + host + '</h3><pre>' + stdout + '</pre>');
                  });
                });
            """.trimIndent()
        ),
        ScannerPreset(
            label = "Python SSRF + YAML",
            code = """
                import requests
                import yaml
                from flask import Flask, request

                app = Flask(__name__)

                @app.route('/fetch', methods=['POST'])
                def fetch_resource():
                    cfg = yaml.load(request.data, Loader=yaml.Loader)
                    resp = requests.get(cfg['webhook_url'], verify=False)
                    return resp.text
            """.trimIndent()
        ),
        ScannerPreset(
            label = "HTTP Headers",
            code = """
                HTTP/1.1 200 OK
                Server: Apache/2.4.49 (Ubuntu)
                X-Powered-By: PHP/7.4.3
                Access-Control-Allow-Origin: *
                Access-Control-Allow-Credentials: true
                Set-Cookie: session_id=abc123def; Path=/
                Content-Type: text/html; charset=UTF-8
            """.trimIndent()
        )
    )
}
