# SSH-Forge privacy notice

**Status:** draft for review; last checked against the current source on 2026-10-25. This text is not a substitute for reviewing the built release, third-party dependencies, or the rules of any app store. Do not publish until the release owner verifies every statement and places this notice at a stable public URL.

## What the app sends

SSH-Forge is a client for a server that the user chooses. When the user taps Connect, the app uses SSH to contact the entered host and port. The selected SSH server receives the username and, after host-key verification, the password needed for authentication. When the user runs a command, that command is sent to the same server. The server and its administrator may process or retain authentication events, commands, and output according to their own policies. SSH-Forge's developer does not receive these values through the app.

For an unknown server key, the app displays its SHA-256 fingerprint and requires the user to approve it before authentication. A changed key is rejected. Users should compare the first fingerprint with one obtained through a separate trusted channel; approving an unknown key by itself does not prove the initial connection is genuine.

## Storage and retention on the device

The app does not intentionally save passwords, connection profiles, commands, or command output. The password field is cleared when a connection attempt starts. The client clears its supplied password byte array, but the Android/Kotlin runtime does not guarantee immediate erasure of every temporary in-memory copy.

An approved server's public host key is recorded in the app's private `known_hosts` file so that later connections can detect a changed key. This file remains in the app's private storage until app data is cleared or the app is uninstalled. Android backup is disabled in the manifest. Command output is shown in the current screen; tapping **Copy output** places it on the device clipboard, which is managed by Android and may remain available there according to device behavior.

## Collection, sharing, and third-party services

The current source and declared app dependencies contain no advertising, analytics, crash-reporting, account, or developer telemetry service. No web API is called by the app; its network function is direct SSH to the host and port supplied by the user. The only manifest permission is `android.permission.INTERNET`.

The app does not request contacts, location, camera, microphone, storage, or device identifiers. The shipped SSH library processes the SSH connection locally and communicates with the selected SSH server. Test-only JUnit and Apache MINA SSHD dependencies are not packaged in the app.

## User choices and contact

Do not connect to a server unless you are authorized to access it. Review remote commands before execution. Users can stop a session with Disconnect and can remove stored host-key records by clearing SSH-Forge's app data or uninstalling the app. Clearing app data also removes other local app state.

The source repository and issue tracker are linked from the project README. There is currently no published release, privacy-policy hosting URL, or dedicated privacy contact. A store submission must provide a verified, public, stable privacy-policy URL; do not rely on this draft's repository path until the release owner has checked the final branch and publication URL.
