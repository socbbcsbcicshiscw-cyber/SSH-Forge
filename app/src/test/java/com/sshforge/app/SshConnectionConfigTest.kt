package com.sshforge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SshConnectionConfigTest {
    @Test
    fun acceptsDomainAndIpAddressInputs() {
        assertNull(SshConnectionConfig("ssh.example.org", 22, "alice").validationError())
        assertNull(SshConnectionConfig("192.0.2.10", 2222, "alice").validationError())
        assertNull(SshConnectionConfig("2001:db8::10", 22, "alice").validationError())
    }

    @Test
    fun rejectsEmptyOrMalformedConnectionFields() {
        assertEquals(
            "Enter a valid server address.",
            SshConnectionConfig("", 22, "alice").validationError(),
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
            SshConnectionConfig("ssh.example.org", 65536, "alice").validationError(),
        )
    }
}
