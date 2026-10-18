package com.vknxworks.arrctrl.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/** Thrown when a pinned server shows a different certificate than the one the user trusted. */
class PinMismatchException(msg: String, cause: Throwable? = null) : CertificateException(msg, cause)

/**
 * Trust-on-first-use for servers with a self-signed certificate: the user confirms the certificate's SHA-256
 * fingerprint once, and from then on only that exact certificate (or one the system trusts anyway) is accepted.
 * Only ever used for the server the certificate was trusted for; every other host gets normal validation.
 */
object Tls {
    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    /** "ab12cd…" → "AB:12:CD:…" for showing to people. */
    fun pretty(hex: String): String = hex.uppercase().chunked(2).joinToString(":")

    /**
     * Connects to [base] and returns the certificate the server presents. Only the TLS handshake runs:
     * no HTTP request is made, so no API key, cookie or password is ever sent to an unverified server.
     */
    suspend fun fetchLeaf(base: String): X509Certificate = withContext(Dispatchers.IO) {
        val u = base.toHttpUrl()
        require(u.isHttps) { "Only https addresses have a certificate" }
        var captured: Array<out X509Certificate>? = null
        val capture = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("Not a server")
            }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) { captured = chain }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(capture), null) }
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(u.host, u.port), 15_000)
            raw.soTimeout = 15_000
            (ctx.socketFactory.createSocket(raw, u.host, u.port, true) as SSLSocket).use { it.startHandshake() }
        } finally {
            runCatching { raw.close() }
        }
        captured?.firstOrNull() ?: throw CertificateException("The server didn't present a certificate")
    }

    private fun systemTrustManager(): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()

    /**
     * Accepts a chain if its leaf matches [pin]; otherwise falls back to the system's normal checks
     * (so a later switch to a real certificate keeps working). [onMismatch] runs when neither holds.
     */
    class PinnedTrustManager(private val pin: String, private val onMismatch: () -> Unit) : X509ExtendedTrustManager() {
        private val system = systemTrustManager()

        private fun pinned(chain: Array<out X509Certificate>?): Boolean =
            chain?.firstOrNull()?.let { sha256(it).equals(pin, ignoreCase = true) } == true

        private fun orSystem(chain: Array<out X509Certificate>?, check: () -> Unit) {
            if (pinned(chain)) return
            try {
                check()
            } catch (e: CertificateException) {
                onMismatch()
                throw PinMismatchException("The server's certificate changed since you trusted it", e)
            }
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            orSystem(chain) { system.checkServerTrusted(chain, authType) }
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
            orSystem(chain) {
                val ext = system as? X509ExtendedTrustManager
                if (ext != null) ext.checkServerTrusted(chain, authType, socket) else system.checkServerTrusted(chain, authType)
            }
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
            orSystem(chain) {
                val ext = system as? X509ExtendedTrustManager
                if (ext != null) ext.checkServerTrusted(chain, authType, engine) else system.checkServerTrusted(chain, authType)
            }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("Not a server")
        }
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
            throw CertificateException("Not a server")
        }
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
            throw CertificateException("Not a server")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
    }

    /**
     * Normal hostname checks, except that the configured server host also accepts its pinned certificate
     * (self-signed certificates often lack a matching name, e.g. for an IP address).
     */
    fun pinnedHostnameVerifier(host: String, pin: String): HostnameVerifier {
        val default = HttpsURLConnection.getDefaultHostnameVerifier()
        return HostnameVerifier { h, session ->
            if (h.equals(host, ignoreCase = true)) {
                val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
                (leaf != null && sha256(leaf).equals(pin, ignoreCase = true)) || default.verify(h, session)
            } else default.verify(h, session)
        }
    }

    fun sslContext(tm: X509TrustManager): SSLContext =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), null) }
}
