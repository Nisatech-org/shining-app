package app.shining.server.samad

import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Samad's certificate chains up to "Certum Trusted Root CA", which Java 17's own list doesn't
 * include (Android only added it in Android 14). This trusts Java's usual roots plus that one,
 * bundled as a resource. It's the same file as the app's res/raw copy, checked against Android
 * 14's system/ca-certificates f8fc53da.0 (SHA-256 FE:76:96:57:...:E1:AE:78:FD).
 */
object SamadTls {

    val sslContext: SSLContext by lazy {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        defaultTrustManager().acceptedIssuers.forEachIndexed { index, cert ->
            trustStore.setCertificateEntry("system-$index", cert)
        }
        trustStore.setCertificateEntry("certum-trusted-root-ca", certumRoot())

        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(trustStore)
        SSLContext.getInstance("TLS").apply { init(null, factory.trustManagers, null) }
    }

    private fun defaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    internal fun certumRoot(): X509Certificate {
        val stream = SamadTls::class.java.getResourceAsStream("/certum_trusted_root_ca.pem")
            ?: error("certum_trusted_root_ca.pem is missing from the server's resources")
        return stream.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
    }
}
