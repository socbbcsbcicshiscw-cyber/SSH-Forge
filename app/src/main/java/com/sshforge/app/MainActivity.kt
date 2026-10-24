package com.sshforge.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var sshClient: SshCommandClient
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var commandInput: EditText
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var runButton: Button
    private lateinit var statusText: TextView
    private lateinit var outputText: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var busy = false
    private var operationGeneration = 0
    private var pendingHostKeyDialog: AlertDialog? = null
    private var shouldMonitorConnection = false
    private val connectionMonitor = object : Runnable {
        override fun run() {
            if (!shouldMonitorConnection) return
            if (!sshClient.isConnected) {
                shouldMonitorConnection = false
                statusText.text = "●  Connection lost"
                statusText.setTextColor(COLOR_WARNING)
                outputText.text = "The SSH connection was lost. Reconnect before running another command."
                updateConnectionControls()
            } else {
                mainHandler.postDelayed(this, CONNECTION_MONITOR_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sshClient = SshCommandClient(applicationContext)
        window.statusBarColor = COLOR_BACKGROUND
        window.navigationBarColor = COLOR_BACKGROUND
        setContentView(createContent())
        updateConnectionControls()
    }

    override fun onDestroy() {
        operationGeneration++
        shouldMonitorConnection = false
        mainHandler.removeCallbacks(connectionMonitor)
        pendingHostKeyDialog?.dismiss()
        pendingHostKeyDialog = null
        if (::passwordInput.isInitialized) passwordInput.text.clear()
        sshClient.disconnect()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun createContent(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(28))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        page.addView(TextView(this).apply {
            text = "SSH-FORGE"
            textSize = 24f
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_ACCENT)
            letterSpacing = 0.08f
        })
        page.addView(TextView(this).apply {
            text = "REMOTE COMMAND CLIENT  /  PASSWORD AUTH"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(5), 0, dp(20))
        })

        addSectionTitle(page, "01  SERVER CONNECTION")
        hostInput = addField(page, "Server address", "Host name or IP address", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val portRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        portInput = addField(portRow, "Port", "22", InputType.TYPE_CLASS_NUMBER, weight = 1f)
        portInput.setText("22")
        usernameInput = addField(portRow, "Username", "SSH login", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, weight = 2f)
        page.addView(portRow, matchWrap())
        passwordInput = addField(page, "Password", "Not saved", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
            isSaveEnabled = false
        }

        statusText = TextView(this).apply {
            text = "●  Disconnected"
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_MUTED)
            setPadding(dp(2), dp(8), dp(2), dp(14))
        }
        page.addView(statusText, matchWrap())

        val connectionButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        connectButton = makeButton("Connect", COLOR_ACCENT) { connect() }
        disconnectButton = makeButton("Disconnect", COLOR_BUTTON) { disconnectByUser() }
        connectionButtons.addView(connectButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        connectionButtons.addView(disconnectButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        page.addView(connectionButtons, matchWrap())

        addSectionTitle(page, "02  REMOTE COMMAND").also { it.setPadding(0, dp(28), 0, dp(10)) }
        commandInput = addField(
            page,
            "Command",
            "For example: uname -a",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        ).apply {
            setSingleLine(true)
            isEnabled = false
            isSaveEnabled = false
        }
        runButton = makeButton("Run command", COLOR_ACCENT) { runCommand() }.apply { isEnabled = false }
        page.addView(runButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(4)
        })
        page.addView(TextView(this).apply {
            text = "Commands run on the remote server. Output is limited to 64 KiB per stream (128 KiB total); reads time out after 60 seconds. A timed-out command may continue remotely."
            textSize = 11f
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(8), 0, dp(18))
        }, matchWrap())

        addSectionTitle(page, "03  TERMINAL OUTPUT")
        outputText = TextView(this).apply {
            text = "Waiting for a command."
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_TEXT)
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = panelBackground()
            minHeight = dp(120)
        }
        page.addView(outputText, matchWrap())
        page.addView(TextView(this).apply {
            text = "First-time host keys require your approval. Verify the displayed SHA-256 fingerprint with the server administrator."
            textSize = 11f
            setTextColor(COLOR_MUTED)
            setPadding(dp(2), dp(14), dp(2), 0)
        }, matchWrap())
        page.addView(TextView(this).apply {
            text = "Third-party open-source licenses"
            textSize = 12f
            setTextColor(COLOR_ACCENT)
            setPadding(dp(2), dp(18), dp(2), 0)
            paint.isUnderlineText = true
            setOnClickListener { showThirdPartyLicenses() }
        }, matchWrap())

        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(COLOR_BACKGROUND)
            addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun addSectionTitle(parent: LinearLayout, title: String): TextView = TextView(this).apply {
        text = title
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setTextColor(COLOR_ACCENT)
        setPadding(0, dp(8), 0, dp(10))
        parent.addView(this, matchWrap())
    }

    private fun addField(
        parent: LinearLayout,
        label: String,
        hint: String,
        inputType: Int,
        weight: Float = 0f,
    ): EditText {
        val field = EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            textSize = 15f
            setTextColor(COLOR_TEXT)
            setHintTextColor(COLOR_MUTED)
            backgroundTintList = android.content.res.ColorStateList.valueOf(COLOR_ACCENT)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            if (label == "Port") setSingleLine(true)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (weight > 0f) setPadding(0, 0, dp(8), 0)
        }
        column.addView(TextView(this).apply {
            text = label.uppercase()
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_MUTED)
            setPadding(dp(2), dp(8), 0, dp(2))
        }, matchWrap())
        column.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        val params = if (weight > 0f) {
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        } else {
            matchWrap()
        }
        parent.addView(column, params)
        return field
    }

    private fun makeButton(label: String, color: Int, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setTextColor(if (color == COLOR_ACCENT) COLOR_BACKGROUND else COLOR_TEXT)
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(10).toFloat()
        }
        setOnClickListener { action() }
    }

    private fun connect() {
        val host = hostInput.text.toString().trim()
        val portText = portInput.text.toString().trim()
        val port = portText.toIntOrNull() ?: -1
        val config = SshConnectionConfig(host, port, usernameInput.text.toString().trim())
        val validationError = config.validationError()
        if (validationError != null) {
            showMessage("Check connection details", validationError)
            return
        }
        val passwordBytes = passwordInput.text.toString().toByteArray(Charsets.UTF_8)
        passwordInput.text.clear()
        if (passwordBytes.isEmpty()) {
            showMessage("Password required", "Enter the password for this SSH connection.")
            return
        }

        val operation = ++operationGeneration
        busy = true
        statusText.text = "◌  Connecting — verify the server fingerprint if prompted…"
        statusText.setTextColor(COLOR_WARNING)
        updateConnectionControls()
        worker.execute {
            try {
                sshClient.connect(config, passwordBytes, HostKeyApproval { server, keyType, fingerprint ->
                    awaitHostKeyApproval(server, keyType, fingerprint, operation)
                })
                runOnUiThread {
                    if (operation != operationGeneration) return@runOnUiThread
                    busy = false
                    shouldMonitorConnection = true
                    mainHandler.removeCallbacks(connectionMonitor)
                    mainHandler.postDelayed(connectionMonitor, CONNECTION_MONITOR_INTERVAL_MS)
                    statusText.text = "●  Connected to ${config.hostWithPort()}"
                    statusText.setTextColor(COLOR_ACCENT)
                    outputText.text = "Connection established. Enter one command to run on the remote server."
                    updateConnectionControls()
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    if (operation != operationGeneration) return@runOnUiThread
                    busy = false
                    shouldMonitorConnection = false
                    mainHandler.removeCallbacks(connectionMonitor)
                    statusText.text = "●  Disconnected"
                    statusText.setTextColor(COLOR_MUTED)
                    outputText.text = describeError(exception)
                    updateConnectionControls()
                }
            } finally {
                passwordBytes.fill(0)
            }
        }
    }

    private fun runCommand() {
        val command = commandInput.text.toString()
        if (command.isBlank()) {
            showMessage("Command required", "Enter one command to execute on the connected SSH server.")
            return
        }
        val operation = ++operationGeneration
        busy = true
        statusText.text = "◌  Running command…"
        statusText.setTextColor(COLOR_WARNING)
        updateConnectionControls()
        worker.execute {
            try {
                val result = sshClient.execute(command)
                runOnUiThread {
                    if (operation != operationGeneration) return@runOnUiThread
                    busy = false
                    statusText.text = if (sshClient.isConnected) {
                        "●  Connected  /  exit ${result.exitCode}"
                    } else {
                        "●  Disconnected"
                    }
                    statusText.setTextColor(if (result.exitCode == 0) COLOR_ACCENT else COLOR_WARNING)
                    val content = buildString {
                        if (result.stdout.isNotEmpty()) append(result.stdout)
                        if (result.stderr.isNotEmpty()) {
                            if (isNotEmpty() && last() != '\n') append('\n')
                            append("[stderr]\n")
                            append(result.stderr)
                        }
                        if (isEmpty()) append("(no output)")
                        append("\n\n[exit code: ${result.exitCode}]")
                        if (result.outputTruncated) append("\n[output truncated; limit is 64 KiB per stream]")
                    }
                    outputText.text = content
                    updateConnectionControls()
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    if (operation != operationGeneration) return@runOnUiThread
                    busy = false
                    statusText.text = if (sshClient.isConnected) "●  Connected" else "●  Disconnected"
                    statusText.setTextColor(if (sshClient.isConnected) COLOR_ACCENT else COLOR_MUTED)
                    outputText.text = describeError(exception)
                    updateConnectionControls()
                }
            }
        }
    }

    private fun disconnectByUser() {
        operationGeneration++
        shouldMonitorConnection = false
        mainHandler.removeCallbacks(connectionMonitor)
        pendingHostKeyDialog?.dismiss()
        pendingHostKeyDialog = null
        sshClient.disconnect()
        busy = false
        statusText.text = "●  Disconnected"
        statusText.setTextColor(COLOR_MUTED)
        outputText.text = "Disconnected from SSH. A command already sent may continue on the remote server depending on server behavior."
        updateConnectionControls()
    }

    private fun awaitHostKeyApproval(
        host: String,
        keyType: String,
        fingerprint: String,
        operation: Int,
    ): Boolean {
        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(false)
        val dialogRef = AtomicReference<AlertDialog?>(null)

        runOnUiThread {
            if (operation != operationGeneration || isFinishing || isDestroyed) {
                latch.countDown()
                return@runOnUiThread
            }
            val dialog = AlertDialog.Builder(this)
                .setTitle("Verify SSH server key")
                .setMessage(
                    "Server: $host\nKey type: $keyType\nSHA-256 fingerprint:\n$fingerprint\n\n" +
                        "Only trust this key if the fingerprint matches a value obtained through a separate trusted channel. " +
                        "Trust-on-first-use alone cannot rule out an interception. Authentication has not been sent yet.",
                )
                .setPositiveButton("Trust this key") { _, _ -> approved.set(true) }
                .setNegativeButton("Cancel") { _, _ -> approved.set(false) }
                .create()
            dialog.setOnDismissListener {
                if (pendingHostKeyDialog === dialog) pendingHostKeyDialog = null
                latch.countDown()
            }
            dialog.setOnCancelListener { approved.set(false) }
            dialog.setOnShowListener {
                dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
            }
            dialogRef.set(dialog)
            pendingHostKeyDialog = dialog
            dialog.show()
        }

        val answered = try {
            latch.await(HOST_KEY_DIALOG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!answered) runOnUiThread { dialogRef.get()?.dismiss() }
        return answered && approved.get() && operation == operationGeneration
    }

    private fun updateConnectionControls() {
        val connected = sshClient.isConnected
        connectButton.isEnabled = !busy && !connected
        disconnectButton.isEnabled = connected || busy
        connectButton.alpha = if (connectButton.isEnabled) 1f else 0.5f
        disconnectButton.alpha = if (disconnectButton.isEnabled) 1f else 0.5f
        runButton.alpha = if (runButton.isEnabled) 1f else 0.5f
        hostInput.isEnabled = !busy && !connected
        portInput.isEnabled = !busy && !connected
        usernameInput.isEnabled = !busy && !connected
        passwordInput.isEnabled = !busy && !connected
        commandInput.isEnabled = connected && !busy
        runButton.isEnabled = connected && !busy
    }

    private fun describeError(exception: Exception): String {
        val chain = generateSequence<Throwable>(exception) { it.cause }.toList()
        if (chain.any { it is SocketTimeoutException || it is SshCommandClient.SshCommandTimeoutException }) {
            return "Connection or command timed out. Check the server address and network. If a command had started, it may still be running remotely."
        }
        val message = exception.message.orEmpty()
        return when {
            message.contains("reject HostKey", ignoreCase = true) ->
                "The server host key was not accepted, is unknown, or has changed. No command was run."
            message.contains("Auth fail", ignoreCase = true) || message.contains("authentication", ignoreCase = true) ->
                "SSH authentication failed. Check the username and password."
            message.isNotBlank() -> message
            else -> "SSH operation failed (${exception.javaClass.simpleName})."
        }
    }

    private fun showThirdPartyLicenses() {
        val licenseSections = listOf(
            "JSch — Revised BSD-style license" to "licenses/jsch-2.28.8/LICENSE.txt",
            "JZlib — Revised BSD-style license" to "licenses/jsch-2.28.8/LICENSE.JZlib.txt",
            "jBCrypt — ISC license" to "licenses/jsch-2.28.8/LICENSE.jBCrypt.txt",
        )
        val licenseText = try {
            licenseSections.joinToString("\n\n------------------------------\n\n") { (name, path) ->
                "===== $name =====\n\n" + assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (_: Exception) {
            "Bundled license texts could not be read. See THIRD_PARTY_NOTICES.md in the source repository."
        }
        val content = TextView(this).apply {
            text = licenseText
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_TEXT)
            setTextIsSelectable(true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val scroll = ScrollView(this).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle("Third-party licenses")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(COLOR_PANEL)
        cornerRadius = dp(10).toFloat()
        setStroke(dp(1), COLOR_BORDER)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun SshConnectionConfig.hostWithPort(): String {
        val displayHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return if (port == 22) displayHost else "$displayHost:$port"
    }

    companion object {
        private const val HOST_KEY_DIALOG_TIMEOUT_SECONDS = 100L
        private const val CONNECTION_MONITOR_INTERVAL_MS = 5_000L
        private const val COLOR_BACKGROUND = 0xFF10161E.toInt()
        private const val COLOR_PANEL = 0xFF0B1016.toInt()
        private const val COLOR_BORDER = 0xFF26323D.toInt()
        private const val COLOR_ACCENT = 0xFF58D6A2.toInt()
        private const val COLOR_TEXT = 0xFFE5EDF2.toInt()
        private const val COLOR_MUTED = 0xFF8A9AA8.toInt()
        private const val COLOR_WARNING = 0xFFFFC66D.toInt()
        private const val COLOR_BUTTON = 0xFF26323D.toInt()
    }
}
