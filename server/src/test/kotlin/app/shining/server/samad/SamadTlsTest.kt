package app.shining.server.samad

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SamadTlsTest {

    @Test
    fun `bundled root is the genuine Certum Trusted Root CA`() {
        val root = SamadTls.certumRoot()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(root.encoded)
            .joinToString(":") { "%02X".format(it) }
        // The fingerprint of Android 14's copy (system/ca-certificates f8fc53da.0).
        assertEquals(
            "FE:76:96:57:38:55:77:3E:37:A9:5E:7A:D4:D9:CC:96:C3:01:57:C1:5D:31:76:5B:A9:B1:57:04:E1:AE:78:FD",
            sha256,
        )
    }

    @Test
    fun `ssl context builds with the extra root`() {
        assertNotNull(SamadTls.sslContext)
    }
}
