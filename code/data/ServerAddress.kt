package app.captunes.data

import java.net.URI

/**
 * Rules for the server address you type at sign-in. Plain Kotlin with no Android
 * classes, so it can be unit-tested (see tests/ServerAddressTest.kt).
 */
object ServerAddress {
    /** Trims the address and makes sure it starts with a lower-case http:// or https://. */
    fun normalize(raw: String): String {
        val typed = raw.trim()
        // Accept "Https://…" and the like: phone keyboards capitalize the first letter.
        val (scheme, rest) = when {
            typed.startsWith("https://", ignoreCase = true) -> "https://" to typed.substring(8)
            typed.startsWith("http://", ignoreCase = true) -> "http://" to typed.substring(7)
            else -> "http://" to typed
        }
        return scheme + rest.trimEnd('/')
    }

    /**
     * https is always fine. Plain http is only allowed for Tailscale
     * (100.64.0.0/10 or *.ts.net), private home-network ranges and this phone,
     * so a sign-in token never crosses the open internet unencrypted.
     */
    fun isSafe(server: String): Boolean {
        val uri = try { URI(normalize(server)) } catch (e: Exception) { return false }
        val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return false
        if (uri.scheme == "https") return true
        if (host == "localhost" || host.endsWith(".ts.net")) return true
        // IPv6: only this phone, link-local (fe80::/10) and private (fc00::/7, which includes Tailscale).
        if (':' in host) return host == "::1" || PRIVATE_V6.containsMatchIn(host)
        // Single-word names (Tailscale MagicDNS like "mypc") can't be public internet hosts.
        if ('.' !in host) return SINGLE_WORD.matches(host)
        // IPv4: every part must be a number, so "10.0.0.1.example.com" doesn't count.
        val parts = host.split(".").map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false }
        if (parts.size != 4) return false
        val (a, b) = parts[0] to parts[1]
        return a == 10 || a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 100 && b in 64..127)
    }

    private val SINGLE_WORD = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?")
    private val PRIVATE_V6 = Regex("^(f[cd][0-9a-f]{2}|fe[89ab][0-9a-f]):")
}
