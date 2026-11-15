package app.captunes.data

import android.content.Context
import android.net.Uri
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Credentials for one Navidrome server.
 *
 * Uses Subsonic token auth: every request carries t = md5(password + salt) and the salt.
 * The password itself isn't kept, but the token works like a password for this server,
 * so it's saved encrypted (see [Vault]) and must never be handed to other apps.
 */
class Session(
    val server: String,
    val user: String,
    private val token: String,
    private val salt: String,
) {
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun url(endpoint: String, params: List<Pair<String, String>> = emptyList()): String {
        val auth = "u=${enc(user)}&t=$token&s=$salt&v=1.16.1&c=CapTunes&f=json"
        val extra = params.joinToString("") { (k, v) -> "&$k=${enc(v)}" }
        return "$server/rest/$endpoint?$auth$extra"
    }

    fun coverUrl(coverArt: String?, size: Int = 600): String? =
        coverArt?.takeIf { it.isNotBlank() }
            ?.let { url("getCoverArt", listOf("id" to it, "size" to size.toString())) }

    /**
     * Original file for formats the phone can play. For ones it can't (WMA, APE…),
     * Navidrome converts to 320k MP3 on the fly; estimateContentLength lets you seek in it.
     */
    fun streamUrl(songId: String, suffix: String? = null, convert: Boolean = needsConversion(suffix)): String =
        if (!convert) url("stream", listOf("id" to songId))
        else url("stream", listOf(
            "id" to songId, "format" to "mp3", "maxBitRate" to "320", "estimateContentLength" to "true",
        ))

    /** True for the "music on this phone" pseudo-session (no server, no login). */
    val isLocal: Boolean get() = server == LOCAL

    companion object {
        const val LOCAL = "local"

        /** Stands in for a login when you use the music stored on this phone. */
        fun local(): Session = Session(LOCAL, "You", "", "")

        /** File types Android's player can't decode on its own. */
        private val UNPLAYABLE = setOf(
            "wma", "asf", "ape", "wv", "mpc", "mp+", "mpp", "tta", "tak", "ofr", "shn",
            "dsf", "dff", "ra", "rm", "aif", "aiff", "aifc", "ac3", "dts",
        )

        fun needsConversion(suffix: String?): Boolean = suffix?.lowercase() in UNPLAYABLE

        fun create(server: String, user: String, password: String): Session {
            val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
            val salt = bytes.joinToString("") { "%02x".format(it) }
            return Session(normalize(server), user.trim(), md5(password + salt), salt)
        }

        fun normalize(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            // Accept "Https://…" and the like: phone keyboards capitalize the first letter.
            return when {
                trimmed.startsWith("https://", ignoreCase = true) -> "https://" + trimmed.substring(8)
                trimmed.startsWith("http://", ignoreCase = true) -> "http://" + trimmed.substring(7)
                else -> "http://$trimmed"
            }
        }

        /**
         * https is always fine. Plain http is only allowed for Tailscale
         * (100.64.0.0/10 or *.ts.net), private home-network ranges and localhost,
         * so a login token never crosses the open internet unencrypted.
         */
        fun isSafeServer(server: String): Boolean {
            val uri = Uri.parse(normalize(server))
            if (uri.scheme == "https") return true
            val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return false
            if (host == "localhost" || host.endsWith(".ts.net")) return true
            // IPv6: only this phone, link-local and private (fc00::/7, which includes Tailscale).
            if (':' in host) {
                return host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")
            }
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

        private fun md5(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }

    /** Saves the session with the token encrypted. If the keystore isn't usable, nothing is saved. */
    internal fun saveTo(context: Context) {
        val sealed = if (isLocal) "" else Vault.encrypt("$token:$salt") ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
            .putString("server", server).putString("user", user)
            .putString("sealed", sealed).apply()
    }
}

private const val PREFS = "captunes_session"

object SessionStore {
    fun load(context: Context): Session? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val server = p.getString("server", null) ?: return null
        val user = p.getString("user", null) ?: return null
        if (server == Session.LOCAL) return Session.local()

        // Older versions saved the token as plain text: encrypt it now and drop the plain copy.
        val oldToken = p.getString("token", null)
        val oldSalt = p.getString("salt", null)
        if (oldToken != null && oldSalt != null) {
            return Session(server, user, oldToken, oldSalt).also { it.saveTo(context) }
        }

        val opened = p.getString("sealed", null)?.let { Vault.decrypt(it) } ?: return null
        val token = opened.substringBefore(':')
        val salt = opened.substringAfter(':', "")
        if (token.isEmpty() || salt.isEmpty()) return null
        return Session(server, user, token, salt)
    }

    fun save(context: Context, session: Session) = session.saveTo(context)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        Vault.forget()
    }
}
