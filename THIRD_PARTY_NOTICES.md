# Third-party notices

The Android app includes `com.github.mwiede:jsch:2.28.8`, a pure-Java SSH2 implementation. The dependency's upstream POM lists its bundled JSch, JZlib, and jBCrypt license notices. Their original license texts are included as APK assets under `app/src/main/assets/licenses/jsch-2.28.8/` and are not replaced by this repository's Apache License 2.0.

- JSch: Revised BSD-style license — [upstream notice](https://github.com/mwiede/jsch/blob/jsch-2.28.8/LICENSE.txt)
- JZlib: Revised BSD-style license — [upstream notice](https://github.com/mwiede/jsch/blob/jsch-2.28.8/LICENSE.JZlib.txt)
- jBCrypt: ISC license — [upstream notice](https://github.com/mwiede/jsch/blob/jsch-2.28.8/LICENSE.jBCrypt.txt)

Tests also use JUnit 4.13.2 (EPL-1.0) and Apache MINA SSHD 2.20.0 (Apache-2.0) to run a local SSH server. These are test-only dependencies and are not packaged in the Android app. See the [JUnit license](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE.txt) and [Apache MINA SSHD license](https://github.com/apache/mina-sshd/blob/sshd-2.20.0/LICENSE.txt).

This file describes the third-party dependencies only. The SSH-Forge source remains under the Apache License, Version 2.0 in [`LICENSE`](LICENSE).
