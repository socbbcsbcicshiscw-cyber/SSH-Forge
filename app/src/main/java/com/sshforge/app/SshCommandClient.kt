package com.sshforge.app

import android.content.Context
import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal data class SshConnectionConfig(
    val host: String,
    val port: Int,
    val username: String,
) {
    fun validationError(): String? {
        if (host.isBlank() || host.length > 253) return "Enter a valid server address."
        if (host.any { it.isWhitespace() || it.isISOControl() || it == '/' || it == '\\' || it == '@' }) {
            return "The server address contains an invalid character."
        }
        if (username.isBlank() || username.any { it.isWhitespace() || it.isISOControl() }) {
            return "Enter a valid SSH username."
        }
        if (port !in 1..65535) return "Port must be between 1 and 65535."
        return null
    }
}

internal data class SshCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val outputTruncated: Boolean,
)

internal fun interface HostKeyApproval {
    /** Called during SSH key exchange, before authentication. Return true only after explicit approval. */
    fun approve(host: String, keyType: String, fingerprint: String): Boolean
}

/** A small password-authenticated SSH command client. It never stores the supplied password. */
internal class SshCommandClient(context: Context) {
    private val knownHostsFile = File(context.filesDir, "known_hosts")

    @Volatile
    private var session: Session? = null

    @Volatile
    private var activeChannel: ChannelExec? = null

    val isConnected: Boolean
        get() = session?.isConnected == true

    @Throws(Exception::class)
    fun connect(
        config: SshConnectionConfig,
        password: ByteArray,
        approval: HostKeyApproval,
    ) {
        try {
            config.validationError()?.let { throw IllegalArgumentException(it) }
        if (password.isEmpty()) throw IllegalArgumentException("Enter the SSH password.")
        disconnect()

        if (!knownHostsFile.exists() && !knownHostsFile.createNewFile()) {
            throw IllegalStateException("Could not create the private known-hosts file.")
        }
        if (!knownHostsFile.isFile || !knownHostsFile.canWrite()) {
            throw IllegalStateException("The private known-hosts file is not writable.")
        }

        val jsch = JSch()
        jsch.setKnownHosts(knownHostsFile.absolutePath)
        val storedRepository = jsch.hostKeyRepository
        val candidateSession = jsch.getSession(config.username, config.host, config.port)
        candidateSession.setPassword(password)
        candidateSession.setTimeout(CONNECTION_TIMEOUT_MS)
        candidateSession.setConfig("StrictHostKeyChecking", "yes")
        candidateSession.setConfig("PreferredAuthentications", "password")
        candidateSession.setServerAliveInterval(KEEPALIVE_INTERVAL_MS)
        candidateSession.setServerAliveCountMax(KEEPALIVE_FAILURE_LIMIT)

        candidateSession.setHostKeyRepository(object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                val knownResult = storedRepository.check(host, key)
                if (knownResult != HostKeyRepository.NOT_INCLUDED) {
                    // A changed key is always rejected. Never offer an override for it.
                    return knownResult
                }

                val hostKey = try {
                    HostKey(host, key)
                } catch (_: JSchException) {
                    return HostKeyRepository.NOT_INCLUDED
                }
                val fingerprint = sha256Fingerprint(key)

                val approved = try {
                    // The trust dialog is shown before SSH authentication. Extend the read timeout
                    // only while waiting for the user's decision during key exchange.
                    candidateSession.setTimeout(HOST_KEY_PROMPT_TIMEOUT_MS)
                    approval.approve(config.hostWithPort(), hostKey.type, fingerprint)
                } catch (_: Exception) {
                    false
                } finally {
                    try {
                        candidateSession.setTimeout(CONNECTION_TIMEOUT_MS)
                    } catch (_: JSchException) {
                        // If the socket is already unusable, the handshake will fail closed.
                    }
                }
                if (!approved) return HostKeyRepository.NOT_INCLUDED

                synchronized(storedRepository) {
                    storedRepository.add(hostKey, null as UserInfo?)
                    if (isPersisted(host, hostKey)) return HostKeyRepository.OK
                    // Do not continue on a trust decision that could not be persisted.
                    storedRepository.remove(host, hostKey.type, key)
                }
                return HostKeyRepository.NOT_INCLUDED
            }

            override fun add(hostkey: HostKey, userinfo: UserInfo?) =
                storedRepository.add(hostkey, userinfo)

            override fun remove(host: String?, type: String?) =
                storedRepository.remove(host, type)

            override fun remove(host: String?, type: String?, key: ByteArray?) =
                storedRepository.remove(host, type, key)

            override fun getKnownHostsRepositoryID(): String? =
                storedRepository.getKnownHostsRepositoryID()

            override fun getHostKey(): Array<HostKey> = storedRepository.getHostKey()

            override fun getHostKey(host: String?, type: String?): Array<HostKey> =
                storedRepository.getHostKey(host, type)
        })

        session = candidateSession
        try {
            candidateSession.connect(CONNECTION_TIMEOUT_MS)
            if (!candidateSession.isConnected) {
                throw JSchException("The SSH session did not remain connected.")
            }
            candidateSession.setTimeout(COMMAND_TIMEOUT_MS)
        } catch (exception: Exception) {
            if (session === candidateSession) session = null
            candidateSession.disconnect()
            throw exception
        }
        } finally {
            password.fill(0)
        }
    }

    @Throws(Exception::class)
    fun execute(command: String): SshCommandResult {
        if (command.isBlank()) throw IllegalArgumentException("Enter a command to run on the server.")
        if (command.length > MAX_COMMAND_LENGTH) {
            throw IllegalArgumentException("Commands are limited to $MAX_COMMAND_LENGTH characters.")
        }
        if (command.any { it == '\u0000' }) throw IllegalArgumentException("The command contains an invalid character.")

        val currentSession = session
        if (currentSession?.isConnected != true) {
            throw IllegalStateException("Connect to an SSH server before running a command.")
        }

        val channel = currentSession.openChannel("exec") as ChannelExec
        activeChannel = channel
        val stderr = BoundedOutputStream(MAX_OUTPUT_BYTES_PER_STREAM)
        val stdout = ByteArrayOutputStream()
        val timedOut = AtomicBoolean(false)
        var timeoutExecutor: ScheduledExecutorService? = null
        var timeoutTask: ScheduledFuture<*>? = null
        var truncated = false
        try {
            channel.setCommand(command)
            channel.setErrStream(stderr)
            val output = channel.inputStream
            channel.connect(COMMAND_CONNECT_TIMEOUT_MS)
            val executor = Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "ssh-command-timeout").apply { isDaemon = true }
            }
            timeoutExecutor = executor
            timeoutTask = executor.schedule({
                if (!channel.isClosed) {
                    timedOut.set(true)
                    channel.disconnect()
                }
            }, COMMAND_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            val buffer = ByteArray(4096)
            while (true) {
                val count = output.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val remaining = MAX_OUTPUT_BYTES_PER_STREAM - stdout.size()
                if (remaining > 0) stdout.write(buffer, 0, minOf(count, remaining))
                if (count > remaining) truncated = true
            }
            if (timedOut.get()) {
                throw SshCommandTimeoutException(
                    "The command timed out after 60 seconds.",
                    TimeoutException("Remote command deadline exceeded."),
                )
            }

            val exitCode = channel.exitStatus
            if (!currentSession.isConnected) {
                session = null
                throw IllegalStateException("The SSH connection was lost while running the command.")
            }
            return SshCommandResult(
                stdout = stdout.toString(Charsets.UTF_8.name()),
                stderr = stderr.toByteArray().toString(Charsets.UTF_8.name()),
                exitCode = exitCode,
                outputTruncated = truncated || stderr.wasTruncated,
            )
        } catch (exception: Exception) {
            if (timedOut.get()) {
                throw SshCommandTimeoutException("The command timed out after 60 seconds.", exception)
            }
            val socketTimedOut = generateSequence(exception) { it.cause }
                .any { it is SocketTimeoutException }
            if (socketTimedOut) throw SshCommandTimeoutException("The command timed out after 60 seconds.", exception)
            throw exception
        } finally {
            timeoutTask?.cancel(true)
            timeoutExecutor?.shutdownNow()
            activeChannel = null
            channel.disconnect()
        }
    }

    fun disconnect() {
        activeChannel?.disconnect()
        activeChannel = null
        val oldSession = session
        session = null
        oldSession?.disconnect()
    }

    private fun isPersisted(host: String, key: HostKey): Boolean {
        val expected = "$host ${key.type} ${key.key}"
        return try {
            knownHostsFile.readLines(Charsets.UTF_8).any { it.trim() == expected }
        } catch (_: Exception) {
            false
        }
    }

    private fun sha256Fingerprint(key: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        return "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun SshConnectionConfig.hostWithPort(): String {
        val displayHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return if (port == DEFAULT_SSH_PORT) displayHost else "$displayHost:$port"
    }

    private class BoundedOutputStream(private val limit: Int) : ByteArrayOutputStream() {
        var wasTruncated: Boolean = false
            private set

        override fun write(value: Int) {
            if (size() < limit) super.write(value) else wasTruncated = true
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            val remaining = limit - size()
            if (remaining > 0) super.write(buffer, offset, minOf(length, remaining))
            if (length > remaining) wasTruncated = true
        }
    }

    internal class SshCommandTimeoutException(message: String, cause: Exception) : Exception(message, cause)

    companion object {
        private const val DEFAULT_SSH_PORT = 22
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val HOST_KEY_PROMPT_TIMEOUT_MS = 120_000
        private const val COMMAND_CONNECT_TIMEOUT_MS = 15_000
        private const val COMMAND_TIMEOUT_MS = 60_000
        private const val KEEPALIVE_INTERVAL_MS = 15_000
        private const val KEEPALIVE_FAILURE_LIMIT = 3
        private const val MAX_COMMAND_LENGTH = 4096
        private const val MAX_OUTPUT_BYTES_PER_STREAM = 64 * 1024
    }
}
