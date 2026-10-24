package com.sshforge.app

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SshCommandClientIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun firstContactRequiresApprovalBeforeAuthenticationAndTrustPersists() {
        val authenticationCalls = AtomicInteger()
        val server = startServer("host-a.key", authenticationCalls)
        val client = newClient()
        try {
            val password = TEST_PASSWORD.toByteArray()
            var approvalCount = 0
            client.connect(config(server), password, HostKeyApproval { host, keyType, fingerprint ->
                approvalCount++
                assertEquals("127.0.0.1:${server.port}", host)
                assertTrue(keyType.isNotBlank())
                assertTrue(fingerprint.matches(Regex("SHA256:[A-Za-z0-9+/]{43}")))
                assertEquals("Approval must happen before the server receives authentication.", 0, authenticationCalls.get())
                true
            })
            assertArrayEquals(ByteArray(password.size), password)
            assertEquals(1, approvalCount)
            assertEquals(1, authenticationCalls.get())
            assertTrue(client.isConnected)
            client.disconnect()

            val secondPassword = TEST_PASSWORD.toByteArray()
            client.connect(config(server), secondPassword, HostKeyApproval { _, _, _ ->
                throw AssertionError("Persisted host key must not prompt again.")
            })
            assertArrayEquals(ByteArray(secondPassword.size), secondPassword)
            assertTrue(client.isConnected)
            assertEquals(2, authenticationCalls.get())
        } finally {
            client.disconnect()
            server.stop()
        }
    }

    @Test
    fun rejectingUnknownFingerprintPreventsAuthenticationAndClearsPassword() {
        val authenticationCalls = AtomicInteger()
        val server = startServer("host-reject.key", authenticationCalls)
        val client = newClient()
        val password = TEST_PASSWORD.toByteArray()
        try {
            val error = runCatching {
                client.connect(config(server), password, HostKeyApproval { _, _, _ -> false })
            }.exceptionOrNull()
            assertNotNull("Rejected unknown key must fail the connection.", error)
            assertEquals(0, authenticationCalls.get())
            assertFalse(client.isConnected)
            assertArrayEquals(ByteArray(password.size), password)
        } finally {
            client.disconnect()
            server.stop()
        }
    }

    @Test
    fun hostKeyTrustIsScopedToHostAndPort() {
        val firstServer = startServer("same-host-first.key", AtomicInteger())
        val secondServer = startServer("same-host-second.key", AtomicInteger())
        val client = newClient()
        try {
            var approvals = 0
            for (server in listOf(firstServer, secondServer)) {
                val password = TEST_PASSWORD.toByteArray()
                client.connect(config(server), password, HostKeyApproval { host, _, _ ->
                    approvals++
                    assertEquals("127.0.0.1:${server.port}", host)
                    true
                })
                assertArrayEquals(ByteArray(password.size), password)
                client.disconnect()
            }
            assertNotEquals(firstServer.port, secondServer.port)
            assertEquals("The same host on another port is a separate trust entry.", 2, approvals)
        } finally {
            client.disconnect()
            firstServer.stop()
            secondServer.stop()
        }
    }

    @Test
    fun changedHostKeyIsRejectedWithoutAnotherApproval() {
        val authenticationCalls = AtomicInteger()
        val server = startServer("host-before-rotation.key", authenticationCalls)
        val client = newClient()
        try {
            client.connect(config(server), TEST_PASSWORD.toByteArray(), HostKeyApproval { _, _, _ -> true })
            val port = server.port
            client.disconnect()

            server.keyPairProvider = hostKeyProvider("host-after-rotation.key")
            var approvalsAfterRotation = 0
            val password = TEST_PASSWORD.toByteArray()
            val error = runCatching {
                client.connect(SshConnectionConfig("127.0.0.1", port, TEST_USERNAME), password, HostKeyApproval { _, _, _ ->
                    approvalsAfterRotation++
                    true
                })
            }.exceptionOrNull()

            assertNotNull("A changed host key must be rejected.", error)
            assertEquals(0, approvalsAfterRotation)
            assertEquals(1, authenticationCalls.get())
            assertFalse(client.isConnected)
            assertArrayEquals(ByteArray(password.size), password)
        } finally {
            client.disconnect()
            server.stop()
        }
    }

    @Test
    fun disconnectDuringFirstUsePromptCancelsConnectionBeforeAuthentication() {
        val authenticationCalls = AtomicInteger()
        val server = startServer("cancel-connect.key", authenticationCalls)
        val client = newClient()
        val promptShown = CountDownLatch(1)
        val releasePrompt = CountDownLatch(1)
        val password = TEST_PASSWORD.toByteArray()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit {
                client.connect(config(server), password, HostKeyApproval { _, _, _ ->
                    promptShown.countDown()
                    releasePrompt.await(3, TimeUnit.SECONDS)
                    true
                })
            }
            assertTrue("Unknown key should reach the approval callback.", promptShown.await(3, TimeUnit.SECONDS))
            client.disconnect()
            releasePrompt.countDown()

            val result = runCatching { future.get(3, TimeUnit.SECONDS) }
            assertTrue("A cancelled connection must fail rather than succeed invisibly.", result.isFailure)
            assertFalse(client.isConnected)
            assertEquals("Authentication must not happen after cancellation.", 0, authenticationCalls.get())
            assertArrayEquals(ByteArray(password.size), password)
        } finally {
            releasePrompt.countDown()
            executor.shutdownNow()
            client.disconnect()
            server.stop()
        }
    }

    @Test
    fun wrongPasswordFailsWithoutLeakingItInTheError() {
        val authenticationCalls = AtomicInteger()
        val server = startServer("wrong-password.key", authenticationCalls)
        val client = newClient()
        val wrongPassword = "not-the-server-password".toByteArray()
        try {
            val error = runCatching {
                client.connect(config(server), wrongPassword, HostKeyApproval { _, _, _ -> true })
            }.exceptionOrNull()
            assertNotNull("Wrong password must fail authentication.", error)
            assertFalse(error.toString().contains("not-the-server-password"))
            assertTrue("Server should receive at least one password attempt.", authenticationCalls.get() >= 1)
            assertFalse(client.isConnected)
            assertArrayEquals(ByteArray(wrongPassword.size), wrongPassword)
        } finally {
            client.disconnect()
            server.stop()
        }
    }

    @Test
    fun networkFailureAndHandshakeTimeoutReleasePasswordAndSession() {
        val refusedPort = ServerSocket(0).use { it.localPort }
        val client = newClient(SshTimeouts(connectionTimeoutMs = 250, hostKeyPromptTimeoutMs = 400))
        val password = TEST_PASSWORD.toByteArray()
        val refusedError = runCatching {
            client.connect(SshConnectionConfig("127.0.0.1", refusedPort, TEST_USERNAME), password, HostKeyApproval { _, _, _ -> true })
        }.exceptionOrNull()
        assertNotNull("A closed local port must return a connection error.", refusedError)
        assertFalse(client.isConnected)
        assertArrayEquals(ByteArray(password.size), password)

        val stalledServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val accepted = CountDownLatch(1)
        val peer = AtomicReference<Socket?>()
        val listener = Thread {
            try {
                val socket = stalledServer.accept()
                peer.set(socket)
                accepted.countDown()
                Thread.sleep(1_500)
            } catch (_: Exception) {
                // Closing the test socket is the expected cleanup path.
            } finally {
                runCatching { peer.get()?.close() }
                runCatching { stalledServer.close() }
            }
        }.apply { isDaemon = true; start() }
        val timedPassword = TEST_PASSWORD.toByteArray()
        try {
            val started = System.nanoTime()
            val timeoutError = runCatching {
                client.connect(
                    SshConnectionConfig("127.0.0.1", stalledServer.localPort, TEST_USERNAME),
                    timedPassword,
                    HostKeyApproval { _, _, _ -> true },
                )
            }.exceptionOrNull()
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertNotNull("A peer that never sends an SSH banner must time out.", timeoutError)
            assertTrue("Handshake should be bounded by the configured timeout.", durationMs < 2_500)
            assertTrue(accepted.await(1, TimeUnit.SECONDS))
            assertFalse(client.isConnected)
            assertArrayEquals(ByteArray(timedPassword.size), timedPassword)
        } finally {
            runCatching { peer.get()?.close() }
            runCatching { stalledServer.close() }
            listener.join(1_000)
            client.disconnect()
        }
    }

    @Test
    fun commandReturnsStdoutStderrAndExitStatusAndBoundsLargeOutput() {
        val server = startServer("command-output.key", AtomicInteger())
        val client = newClient()
        try {
            client.connect(config(server), TEST_PASSWORD.toByteArray(), HostKeyApproval { _, _, _ -> true })
            val result = client.execute("result")
            assertEquals("stdout\n", result.stdout)
            assertEquals("stderr\n", result.stderr)
            assertEquals(7, result.exitCode)
            assertFalse(result.outputTruncated)

            val large = client.execute("large")
            assertEquals(MAX_OUTPUT_BYTES, large.stdout.toByteArray(Charsets.UTF_8).size)
            assertEquals(MAX_OUTPUT_BYTES, large.stderr.toByteArray(Charsets.UTF_8).size)
            assertTrue(large.outputTruncated)
            assertTrue(client.isConnected)
        } finally {
            client.disconnect()
            server.stop()
        }
    }

    @Test
    fun commandTimeoutAndUserDisconnectCloseTheActiveChannel() {
        val started = CountDownLatch(2)
        val server = startServer("command-cancel.key", AtomicInteger(), commandStarted = started)
        val client = newClient(SshTimeouts(commandTimeoutMs = 250))
        try {
            client.connect(config(server), TEST_PASSWORD.toByteArray(), HostKeyApproval { _, _, _ -> true })
            val timeout = runCatching { client.execute("sleep") }.exceptionOrNull()
            assertTrue(timeout is SshCommandClient.SshCommandTimeoutException)
            assertTrue("A timed-out exec channel must not tear down the SSH session.", client.isConnected)

            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<SshCommandResult> { client.execute("sleep") }
                assertTrue("Server command should start before disconnect.", started.await(2, TimeUnit.SECONDS))
                client.disconnect()
                val finished = runCatching { future.get(2, TimeUnit.SECONDS) }
                assertTrue("Disconnect must unblock the in-flight command.", finished.isFailure)
                assertFalse("The command future must finish, not merely time out.", finished.exceptionOrNull() is TimeoutException)
                assertFalse(client.isConnected)
            } finally {
                executor.shutdownNow()
            }
        } finally {
            client.disconnect()
            server.stop()
        }
    }

    private fun newClient(timeouts: SshTimeouts = SshTimeouts()) =
        SshCommandClient(File(temporaryFolder.root, "known_hosts"), timeouts)

    private fun config(server: SshServer) =
        SshConnectionConfig("127.0.0.1", server.port, TEST_USERNAME)

    private fun startServer(
        keyName: String,
        authenticationCalls: AtomicInteger,
        commandStarted: CountDownLatch? = null,
    ): SshServer {
        val server = SshServer.setUpDefaultServer()
        server.host = "127.0.0.1"
        server.port = 0
        server.keyPairProvider = hostKeyProvider(keyName)
        server.passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
            authenticationCalls.incrementAndGet()
            username == TEST_USERNAME && password == TEST_PASSWORD
        }
        server.commandFactory = CommandFactory { _, command -> TestCommand(command, commandStarted) }
        server.start()
        return server
    }

    private fun hostKeyProvider(keyName: String) =
        SimpleGeneratorHostKeyProvider(File(temporaryFolder.root, keyName).toPath()).apply {
            algorithm = "RSA"
            keySize = 2048
        }

    private class TestCommand(
        private val command: String,
        private val commandStarted: CountDownLatch?,
    ) : Command, Runnable {
        private var output: OutputStream? = null
        private var error: OutputStream? = null
        @Volatile private var callback: ExitCallback? = null
        @Volatile private var worker: Thread? = null

        override fun setInputStream(input: InputStream) = Unit
        override fun setOutputStream(out: OutputStream) { output = out }
        override fun setErrorStream(err: OutputStream) { error = err }
        override fun setExitCallback(callback: ExitCallback) { this.callback = callback }

        override fun start(channel: ChannelSession, env: Environment) {
            worker = Thread(this, "ssh-forge-test-command").apply { isDaemon = true; start() }
        }

        override fun destroy(channel: ChannelSession) {
            worker?.interrupt()
        }

        override fun run() {
            var exitCode = 0
            try {
                when (command) {
                    "result" -> {
                        output?.write("stdout\n".toByteArray())
                        error?.write("stderr\n".toByteArray())
                        output?.flush()
                        error?.flush()
                        exitCode = 7
                    }
                    "large" -> {
                        val block = ByteArray(MAX_OUTPUT_BYTES + 16 * 1024) { 'x'.code.toByte() }
                        output?.write(block)
                        error?.write(block)
                        output?.flush()
                        error?.flush()
                    }
                    "sleep" -> {
                        commandStarted?.countDown()
                        while (!Thread.currentThread().isInterrupted) Thread.sleep(20)
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                exitCode = 1
            } finally {
                runCatching { callback?.onExit(exitCode, "", true) }
            }
        }
    }

    companion object {
        private const val TEST_USERNAME = "test-user"
        private const val TEST_PASSWORD = "test-password"
        private const val MAX_OUTPUT_BYTES = 64 * 1024
    }
}
