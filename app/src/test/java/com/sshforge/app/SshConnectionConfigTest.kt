package com.sshforge.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SshConnectionConfigTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsDomainAndIpAddressInputs() {
        assertNull(SshConnectionConfig("ssh.example.org", 22, "alice").validationError())
        assertNull(SshConnectionConfig("192.0.2.10", 2222, "alice").validationError())
        assertNull(SshConnectionConfig("2001:db8::10", 22, "alice").validationError())
    }

    @Test
    fun rejectsEmptyOrMalformedConnectionFields() {
        assertEquals("Enter a valid server address.", SshConnectionConfig("", 22, "alice").validationError())
        assertEquals(
            "Enter a valid SSH username.",
            SshConnectionConfig("ssh.example.org", 22, "").validationError(),
        )
        assertEquals(
            "The server address contains an invalid character.",
            SshConnectionConfig("ssh.example.org/path", 22, "alice").validationError(),
        )
        assertEquals(
            "Enter a valid SSH username.",
            SshConnectionConfig("ssh.example.org", 22, "two words").validationError(),
        )
        assertEquals(
            "Port must be between 1 and 65535.",
            SshConnectionConfig("ssh.example.org", 0, "alice").validationError(),
        )
        assertEquals(
            "Port must be between 1 and 65535.",
            SshConnectionConfig("ssh.example.org", 65536, "alice").validationError(),
        )
        assertEquals(
            "Enter a valid server address.",
            SshConnectionConfig("x".repeat(254), 22, "alice").validationError(),
        )
    }

    @Test
    fun validatesRemoteCommandsBeforeOpeningAChannel() {
        assertEquals("Enter a command to run on the server.", sshCommandValidationError(""))
        assertEquals("Enter a command to run on the server.", sshCommandValidationError("  \t"))
        assertEquals("The command contains an invalid character.", sshCommandValidationError("echo\u0000bad"))
        assertEquals("Commands are limited to 4096 characters.", sshCommandValidationError("x".repeat(4097)))
        assertNull(sshCommandValidationError("uname -a"))

        val client = SshCommandClient(File(temporaryFolder.root, "known_hosts"))
        assertThrows(IllegalArgumentException::class.java) { client.execute("") }
    }

    @Test
    fun invalidConnectionAttemptStillClearsCallerPasswordBuffer() {
        val password = "temporary-secret".toByteArray()
        val client = SshCommandClient(File(temporaryFolder.root, "known_hosts"))
        assertThrows(IllegalArgumentException::class.java) {
            client.connect(SshConnectionConfig("", 22, "alice"), password, HostKeyApproval { _, _, _ -> true })
        }
        org.junit.Assert.assertArrayEquals(ByteArray(password.size), password)
    }
}
