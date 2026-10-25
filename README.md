# SSH-Forge

SSH-Forge is an early-stage Android **SSH client** for connecting to SSH servers from an Android device.

> **Development status: early MVP.** GitHub Actions runs unit and local-SSH integration tests, checks two clean unsigned release builds for byte identity, builds and inspects a signed debug APK, and stores the APK as a temporary artifact. A successful CI run does not verify runtime behavior on an Android device. There is no published release.

## Implemented in source (CI tests/build verified; device behavior not verified)

- Connection form for a server host/IP, port, username, and password.
- Password-based SSH connection using the maintained `com.github.mwiede:jsch` library.
- An explicit SHA-256 host-key fingerprint confirmation before SSH authentication on first contact. Accepted host keys are stored in the app's private data directory; a changed key is rejected.
- Execution of one user-entered remote command, with stdout, stderr, exit status, bounded output, and an explicit Copy output action.
- Connection status, an explicit retry action after failure, disconnect, connection/command timeouts, and readable error messages.
- A bundled third-party license viewer and an in-app privacy/data notice.
- A square master icon and Android adaptive/round launcher icon resources.

Interactive terminal sessions, SSH-key authentication, saved connection profiles, and SFTP are **not implemented**.

## Development requirements

- JDK 17
- Android SDK Platform 36 and Android build tools 36.0.0
- Android Gradle Plugin 9.4.0 and Gradle 9.6.0 (the included Gradle Wrapper selects Gradle 9.6.0)
- Android Studio 2026.1.4 (Quail 4) or another version compatible with AGP 9.4

The app uses `applicationId`/namespace `com.sshforge.app`, `minSdk` 26, and `targetSdk` 36. The SSH dependency is pinned to `com.github.mwiede:jsch:2.28.8`; JUnit 4.13.2 and Apache MINA SSHD 2.20.0 (test-only, embedded local server) support automated tests. AGP 9.4 provides built-in Kotlin support.

### Build and test

From the repository root, with JDK 17 and the Android SDK installed and configured:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written by Gradle under `app/build/outputs/apk/debug/`. The Gradle Wrapper distribution checksum is pinned in `gradle/wrapper/gradle-wrapper.properties`. This local development workspace does not have Java or an Android SDK, so Gradle was run in GitHub Actions. The [workflow](https://github.com/socbbcsbcicshiscw-cyber/SSH-Forge/actions/workflows/android-build.yml) runs `testDebugUnitTest`, builds the unsigned release APK twice from clean outputs and compares the files, then runs `assembleDebug`; it checks the debug APK's package ID, version, SDK levels, size, and signature before uploading it as a 14-day artifact. CI success does not constitute an Android device/emulator test or an independent F-Droid build.

## Installation

Successful GitHub Actions runs provide a temporary debug APK artifact, not a published or device-verified release. Do not install an APK from an untrusted source.

## Security and Android permissions

The manifest currently requests only `android.permission.INTERNET`. It does not request storage, contacts, location, or other unrelated permissions. Android backup is disabled for the app.

Passwords are used for the connection and are not intentionally saved or logged by the app. The source passes password bytes to the SSH library and clears its temporary byte array after connecting; the password field is cleared when connection starts. No SSH cryptography is implemented in this project.

On first contact, the app presents the server's SHA-256 host-key fingerprint and requires the user to approve it before authentication. For meaningful protection against a man-in-the-middle, compare the fingerprint with one received through a separate trusted channel (for example, from the server administrator). Approval on first contact by itself is trust-on-first-use and cannot establish that the initial connection was not intercepted. A changed host key is rejected without an override prompt.

Commands are executed by the remote SSH server with the authenticated account's privileges. Review a command before running it. This initial version runs one command rather than providing an interactive shell. Command output is limited to 64 KiB per stream (128 KiB combined), and command reads time out after 60 seconds. Disconnecting or timing out closes the SSH channel, but does not guarantee that the remote process has stopped.

## Third-party dependencies and licenses

The SSH implementation is `com.github.mwiede:jsch:2.28.8`. Its JSch, JZlib, and jBCrypt license texts are bundled in the app assets and described in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The SSH-Forge project itself remains under Apache License 2.0; see [LICENSE](LICENSE).

## F-Droid readiness

SSH-Forge has not been submitted to or accepted by F-Droid, and no release APK has been published. English upstream metadata (title, descriptions, version-code changelog, and a project icon) is present under `fastlane/metadata/android/en-US/`. The feature graphic is at `graphics/feature/feature-graphic-1024x500.png`. No genuine device/emulator screenshots are available: the labeled concepts in `screenshots/concepts/` are not screenshots and must not be submitted as such. Check current F-Droid graphics requirements; authentic captures are still outstanding.

There is no release tag yet. F-Droid's submission guide expects a tag for each official upstream release, and the current repository was created on 2026-10-24—less than three months ago as of 2026-10-25. The main-repository inclusion policy asks for at least three months of active development unless an exception applies. A final build recipe belongs in the separate `fdroiddata` repository; it was not added here or submitted because no tagged release exists. The CI comparison is only two clean builds on the same GitHub runner; an isolated F-Droid build, toolchain/dependency review, screenshot capture, and maintainers' review remain outstanding.

## Bug reports and contributions

For bugs or planning discussions, open an issue at [GitHub Issues](https://github.com/socbbcsbcicshiscw-cyber/SSH-Forge/issues). Pull requests are welcome; describe the change and include relevant build/test results. Never submit real passwords, tokens, private keys, or other secrets.
