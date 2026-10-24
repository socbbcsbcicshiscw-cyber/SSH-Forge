package com.sshforge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshSecurityPrimitivesTest {
    @Test
    fun sha256FingerprintUsesOpenSshBase64FormatWithoutPadding() {
        val fingerprint = sha256HostKeyFingerprint(byteArrayOf(1, 2, 3, 4))
        assertEquals("SHA256:n2SnR+G5fxMfq7a0Rylsm28CAeefs8U1bmx36JtqgGo", fingerprint)
        assertFalse(fingerprint.contains('='))
        assertEquals(50, fingerprint.length)
    }

    @Test
    fun boundedOutputStreamRetainsAtMostItsByteLimitAndReportsTruncation() {
        val output = SshCommandClient.BoundedOutputStream(4)
        output.write(byteArrayOf(1, 2, 3))
        output.write(byteArrayOf(4, 5, 6))
        assertEquals(4, output.size())
        assertTrue(output.wasTruncated)
        assertEquals(listOf<Byte>(1, 2, 3, 4), output.toByteArray().toList())

        val exactLimit = SshCommandClient.BoundedOutputStream(4)
        exactLimit.write(byteArrayOf(1, 2, 3, 4))
        assertFalse(exactLimit.wasTruncated)
    }
}
