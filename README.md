# SSH-Forge

SSH-Forge is an early-stage Android **SSH client** for connecting to SSH servers from an Android device.

> **Development status: first implementation in progress.** A basic password-authenticated, single-command client has been added to the source tree. It has not yet been compiled or tested on an Android device in this environment, so it is not yet a verified or installable app. There is no published APK or release.

## Implemented in source (not yet build/runtime verified)

- Connection form for a server host/IP, port, username, and password.
- Password-based SSH connection using the maintained `com.github.mwiede:jsch` library.
- An explicit SHA-256 host-key fingerprint confirmation before SSH authentication on first contact. Accepted host keys are stored in the app's private data directory; a changed key is rejected.
- Execution of one user-entered remote command, with stdout, stderr, exit status, and a bounded output display.
- Connection status, a disconnect action, connection/command timeouts, and basic error messages.
- A bundled third-party license viewer for the SSH library notices.

Interactive terminal sessions, SSH-key authentication, saved connection profiles, and SFTP are **not implemented**.

## Development requirements

- JDK 17
- Android SDK Platform 37 and Android build tools 36.0.0
- Android Gradle Plugin 9.4.0 and Gradle 9.6.0 (the included Gradle Wrapper selects Gradle 9.6.0)
- Android Studio 2026.1.4 (Quail 4) or another version compatible with AGP 9.4

The app uses `applicationId`/namespace `com.sshforge.app`, `minSdk` 26, and `targetSdk` 37. The SSH dependency is pinned to `com.github.mwiede:jsch:2.28.8`; JUnit 4.13.2 is used for local unit tests. AGP 9.4 provides built-in Kotlin support.

### Build and test

From the repository root, with JDK 17 and the Android SDK installed and configured:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK, if the build succeeds, is written by Gradle under `app/build/outputs/apk/debug/`. The Gradle Wrapper distribution checksum is pinned in `gradle/wrapper/gradle-wrapper.properties`. This repository's current development environment did not have Java or an Android SDK available, so a successful build or test run has **not** been confirmed here.

## Installation

There is no verified APK or published release to install. Do not install an APK from an untrusted source. Installation guidance can be added after an APK has been built and verified.

## Security and Android permissions

The manifest currently requests only `android.permission.INTERNET`. It does not request storage, contacts, location, or other unrelated permissions. Android backup is disabled for the app.

Passwords are used for the connection and are not intentionally saved or logged by the app. The source passes password bytes to the SSH library and clears its temporary byte array after connecting; the password field is cleared when connection starts. No SSH cryptography is implemented in this project.

On first contact, the app presents the server's SHA-256 host-key fingerprint and requires the user to approve it before authentication. For meaningful protection against a man-in-the-middle, compare the fingerprint with one received through a separate trusted channel (for example, from the server administrator). Approval on first contact by itself is trust-on-first-use and cannot establish that the initial connection was not intercepted. A changed host key is rejected without an override prompt.

Commands are executed by the remote SSH server with the authenticated account's privileges. Review a command before running it. This initial version runs one command rather than providing an interactive shell. Command output is limited to 64 KiB per stream (128 KiB combined), and command reads time out after 60 seconds. Disconnecting or timing out closes the SSH channel, but does not guarantee that the remote process has stopped.

## Third-party dependencies and licenses

The SSH implementation is `com.github.mwiede:jsch:2.28.8`. Its JSch, JZlib, and jBCrypt license texts are bundled in the app assets and described in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The SSH-Forge project itself remains under Apache License 2.0; see [LICENSE](LICENSE).

## F-Droid readiness

SSH-Forge has not been submitted to or accepted by F-Droid. F-Droid metadata and release information are intentionally not included. Before a future submission, verify the final build, dependency and license notices, host-key behavior, permissions, reproducibility, and release version against the actual source.

## Bug reports and contributions

For bugs or planning discussions, open an issue at [GitHub Issues](https://github.com/socbbcsbcicshiscw-cyber/SSH-Forge/issues). Pull requests are welcome; describe the change and include relevant build/test results. Never submit real passwords, tokens, private keys, or other secrets.
