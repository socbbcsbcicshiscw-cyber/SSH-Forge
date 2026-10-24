package com.sshforge.app

import android.content.Context
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
import java.util.concurrent.CancellationException
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

internal fun sshCommandValidationError(command: String): String? = when {
    command.isBlank() -> "Enter a command to run on the server."
    command.length > MAX_COMMAND_LENGTH -> "Commands are limited to $MAX_COMMAND_LENGTH characters."
    command.any { it == '\u0000' } -> "The command contains an invalid character."
    else -> null
}

internal fun sha256HostKeyFingerprint(key: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(key)
    return "SHA256:" + java.util.Base64.getEncoder().withoutPadding().encodeToString(digest)
}

internal data class SshTimeouts(
    val connectionTimeoutMs: Int = 15_000,
    val hostKeyPromptTimeoutMs: Int = 120_000,
    val commandConnectTimeoutMs: Int = 15_000,
    val commandTimeoutMs: Long = 60_000,
    val keepAliveIntervalMs: Int = 15_000,
    val keepAliveFailureLimit: Int = 3,
) {
    init {
        require(connectionTimeoutMs > 0)
        require(hostKeyPromptTimeoutMs > 0)
        require(commandConnectTimeoutMs > 0)
        require(commandTimeoutMs in 1..Int.MAX_VALUE.toLong())
        require(keepAliveIntervalMs > 0)
        require(keepAliveFailureLimit > 0)
    }
}

private const val MAX_COMMAND_LENGTH = 4096

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
internal class SshCommandClient(
    private val knownHostsFile: File,
    private val timeouts: SshTimeouts = SshTimeouts(),
) {
    constructor(context: Context) : this(File(context.filesDir, "known_hosts"))

    private class ConnectionAttempt {
        val cancelled = AtomicBoolean(false)

        @Volatile
        var candidateSession: Session? = null
    }

    private val stateLock = Any()

    @Volatile
    private var activeConnectAttempt: ConnectionAttempt? = null

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
        var attempt: ConnectionAttempt? = null
        var candidateSession: Session? = null
        try {
            config.validationError()?.let { throw IllegalArgumentException(it) }
            if (password.isEmpty()) throw IllegalArgumentException("Enter the SSH password.")

            val newAttempt = ConnectionAttempt()
            attempt = newAttempt
            val oldResources = synchronized(stateLock) {
                activeConnectAttempt?.cancelled?.set(true)
                val previousCandidate = activeConnectAttempt?.candidateSession
                val previousSession = session
                val previousChannel = activeChannel
                activeConnectAttempt = newAttempt
                session = null
                activeChannel = null
                Triple(previousChannel, previousSession, previousCandidate)
            }
            oldResources.first?.disconnect()
            oldResources.second?.disconnect()
            if (oldResources.third !== oldResources.second) oldResources.third?.disconnect()

            if (!knownHostsFile.exists() && !knownHostsFile.createNewFile()) {
                throw IllegalStateException("Could not create the private known-hosts file.")
            }
            if (!knownHostsFile.isFile || !knownHostsFile.canWrite()) {
                throw IllegalStateException("The private known-hosts file is not writable.")
            }

            val jsch = JSch()
            jsch.setKnownHosts(knownHostsFile.absolutePath)
            val storedRepository = jsch.hostKeyRepository
            val pendingSession = jsch.getSession(config.username, config.host, config.port)
            candidateSession = pendingSession
            pendingSession.setConfig("StrictHostKeyChecking", "yes")
            pendingSession.setConfig("PreferredAuthentications", "password")
            // setServerAliveInterval also changes JSch's read timeout, so set the actual
            // connection timeout after configuring keep-alives.
            pendingSession.setServerAliveInterval(timeouts.keepAliveIntervalMs)
            pendingSession.setServerAliveCountMax(timeouts.keepAliveFailureLimit)
            pendingSession.setTimeout(timeouts.connectionTimeoutMs)

            pendingSession.setHostKeyRepository(object : HostKeyRepository {
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
                    val fingerprint = sha256HostKeyFingerprint(key)

                    val approved = try {
                        ensureAttemptActive(newAttempt)
                        // The trust dialog is shown before SSH authentication. Extend the read timeout
                        // only while waiting for the user's decision during key exchange.
                        pendingSession.setTimeout(timeouts.hostKeyPromptTimeoutMs)
                        approval.approve(config.hostWithPort(), hostKey.type, fingerprint)
                    } catch (_: Exception) {
                        false
                    } finally {
                        try {
                            pendingSession.setTimeout(timeouts.connectionTimeoutMs)
                        } catch (_: JSchException) {
                            // If the socket is already unusable, the handshake will fail closed.
                        }
                    }
                    if (!approved) return HostKeyRepository.NOT_INCLUDED
                    ensureAttemptActive(newAttempt)

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

            val registered = synchronized(stateLock) {
                if (activeConnectAttempt !== newAttempt || newAttempt.cancelled.get()) {
                    false
                } else {
                    newAttempt.candidateSession = pendingSession
                    session = pendingSession
                    true
                }
            }
            if (!registered) {
                pendingSession.disconnect()
                throw CancellationException("The SSH connection was cancelled.")
            }

            ensureAttemptActive(newAttempt)
            // Delay the JSch-owned password copy until the handshake is about to start. JSch clears
            // its own copy in Session.connect's finally block; this client clears the caller's copy.
            pendingSession.setPassword(password)
            pendingSession.connect(timeouts.connectionTimeoutMs)
            ensureAttemptActive(newAttempt)
            if (!pendingSession.isConnected) {
                throw JSchException("The SSH session did not remain connected.")
            }
            pendingSession.setTimeout(timeouts.commandTimeoutMs.toInt())

            val completed = synchronized(stateLock) {
                if (activeConnectAttempt !== newAttempt || newAttempt.cancelled.get()) {
                    false
                } else {
                    activeConnectAttempt = null
                    true
                }
            }
            if (!completed) {
                pendingSession.disconnect()
                throw CancellationException("The SSH connection was cancelled.")
            }
        } catch (exception: Exception) {
            synchronized(stateLock) {
                if (session === candidateSession) session = null
                if (activeConnectAttempt === attempt) activeConnectAttempt = null
            }
            candidateSession?.disconnect()
            throw exception
        } finally {
            synchronized(stateLock) {
                if (activeConnectAttempt === attempt) activeConnectAttempt = null
            }
            password.fill(0)
        }
    }

    private fun ensureAttemptActive(attempt: ConnectionAttempt) {
        if (attempt.cancelled.get() || activeConnectAttempt !== attempt) {
            throw CancellationException("The SSH connection was cancelled.")
        }
    }

    @Throws(Exception::class)
    fun execute(command: String): SshCommandResult {
        sshCommandValidationError(command)?.let { throw IllegalArgumentException(it) }

        val currentSession = session
        if (currentSession?.isConnected != true) {
            throw IllegalStateException("Connect to an SSH server before running a command.")
        }

        val channel = currentSession.openChannel("exec") as ChannelExec
        val channelRegistered = synchronized(stateLock) {
            if (session !== currentSession || !currentSession.isConnected) {
                false
            } else {
                activeChannel = channel
                true
            }
        }
        if (!channelRegistered) {
            channel.disconnect()
            throw IllegalStateException("The SSH connection was disconnected before the command started.")
        }
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
            channel.connect(timeouts.commandConnectTimeoutMs)
            val executor = Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "ssh-command-timeout").apply { isDaemon = true }
            }
            timeoutExecutor = executor
            timeoutTask = executor.schedule({
                if (!channel.isClosed) {
                    timedOut.set(true)
                    channel.disconnect()
                }
            }, timeouts.commandTimeoutMs, TimeUnit.MILLISECONDS)
            val buffer = ByteArray(4096)
            while (true) {
                val count = output.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val remaining = MAX_OUTPUT_BYTES_PER_STREAM - stdout.size()
                if (remaining > 0) stdout.write(buffer, 0, minOf(count, remaining))
                if (count > remaining) truncated = true
            }
            // JSch can expose remote EOF before the subsequent exit-status message has been
            // processed. Wait for that status (or channel close if the server omits it).
            while (channel.exitStatus < 0 && !channel.isClosed && !timedOut.get()) {
                try {
                    Thread.sleep(10)
                } catch (exception: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw JSchException("Waiting for the remote command to finish was interrupted.", exception)
                }
            }
            if (timedOut.get()) {
                throw SshCommandTimeoutException(
                    commandTimeoutMessage(),
                    TimeoutException("Remote command deadline exceeded."),
                )
            }

            val exitCode = channel.exitStatus
            if (!currentSession.isConnected) {
                synchronized(stateLock) {
                    if (session === currentSession) session = null
                }
                throw IllegalStateException("The SSH connection was lost while running the command.")
            }
            return SshCommandResult(
                stdout = stdout.toString(Charsets.UTF_8.name()),
                stderr = String(stderr.toByteArray(), Charsets.UTF_8),
                exitCode = exitCode,
                outputTruncated = truncated || stderr.wasTruncated,
            )
        } catch (exception: Exception) {
            if (timedOut.get()) {
                throw SshCommandTimeoutException(commandTimeoutMessage(), exception)
            }
            val socketTimedOut = generateSequence<Throwable>(exception) { it.cause }
                .any { it is SocketTimeoutException }
            if (socketTimedOut) throw SshCommandTimeoutException(commandTimeoutMessage(), exception)
            throw exception
        } finally {
            timeoutTask?.cancel(true)
            timeoutExecutor?.shutdownNow()
            synchronized(stateLock) {
                if (activeChannel === channel) activeChannel = null
            }
            channel.disconnect()
        }
    }

    fun disconnect() {
        val resources = synchronized(stateLock) {
            val attempt = activeConnectAttempt
            attempt?.cancelled?.set(true)
            activeConnectAttempt = null
            val candidate = attempt?.candidateSession
            val oldSession = session
            val oldChannel = activeChannel
            session = null
            activeChannel = null
            Triple(oldChannel, oldSession, candidate)
        }
        resources.first?.disconnect()
        resources.second?.disconnect()
        if (resources.third !== resources.second) resources.third?.disconnect()
    }

    private fun commandTimeoutMessage(): String =
        if (timeouts.commandTimeoutMs % 1000L == 0L) {
            "The command timed out after ${timeouts.commandTimeoutMs / 1000} seconds."
        } else {
            "The command timed out after ${timeouts.commandTimeoutMs} milliseconds."
        }

    private fun isPersisted(host: String, key: HostKey): Boolean {
        val expected = "$host ${key.type} ${key.key}"
        return try {
            knownHostsFile.readLines(Charsets.UTF_8).any { it.trim() == expected }
        } catch (_: Exception) {
            false
        }
    }

    private fun SshConnectionConfig.hostWithPort(): String {
        val displayHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return if (port == DEFAULT_SSH_PORT) displayHost else "$displayHost:$port"
    }

    internal class BoundedOutputStream(private val limit: Int) : ByteArrayOutputStream() {
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
        private const val MAX_OUTPUT_BYTES_PER_STREAM = 64 * 1024
    }
}
