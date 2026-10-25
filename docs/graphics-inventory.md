# Graphics and screenshot inventory

All media below is in the repository; file paths are relative to its root. Generated mockups are explicitly concepts and must not be uploaded as screenshots of the running app.

| Asset | File | Dimensions / format | Intended use and status |
|---|---|---|---|
| Master app icon | `graphics/icon/ssh-forge-icon.svg` | SVG source | Editable vector source for the SSH-Forge `>_` / SSH motif. |
| Large icon raster | `graphics/icon/ssh-forge-icon-1024.png` | 1024×1024, PNG | High-resolution square master. |
| Store / Fastlane icon | `graphics/icon/ssh-forge-icon-512.png` | 512×512, PNG | Google Play listing icon-sized raster; also copied to `fastlane/metadata/android/en-US/images/icon.png`. |
| Google Play feature graphic | `graphics/feature/feature-graphic-1024x500.png` | 1024×500, RGB PNG, no alpha | Store listing feature graphic. It is an original promotional graphic, not a screenshot. |
| Feature graphic source | `graphics/feature/feature-graphic-1024x500.svg` | SVG source | Editable vector source for the banner. |
| Concept 01 | `screenshots/concepts/01-connect-concept.png` | 1080×1920, RGB PNG | Illustrative connection form; not captured from an app/device. |
| Concept 02 | `screenshots/concepts/02-fingerprint-concept.png` | 1080×1920, RGB PNG | Illustrative first-use host-key confirmation; placeholder fingerprint. |
| Concept 03 | `screenshots/concepts/03-command-output-concept.png` | 1080×1920, RGB PNG | Illustrative one-command flow; no command was run and no server output is shown. |
| Concept 04 | `screenshots/concepts/04-auth-error-retry-concept.png` | 1080×1920, RGB PNG | Illustrative source-defined authentication error; no failed login occurred. |
| Concept 05 | `screenshots/concepts/05-timeout-disconnect-concept.png` | 1080×1920, RGB PNG | Illustrative timeout/disconnect guidance; no network timeout was generated. |
| Concept 06 | `screenshots/concepts/06-privacy-license-concept.png` | 1080×1920, RGB PNG | Illustrative privacy and license links; not a captured device screen. |

Every concept contains a prominent “CONCEPT • NOT A REAL SCREENSHOT” label and a footer explaining that its state was not captured from a live app. They are placed separately from the store screenshot directory on purpose. Do not remove those labels or represent concepts as real screenshots.

## Real screenshots

There are currently **no real Android-device or emulator captures**. `screenshots/original/` is reserved for unchanged captures; `screenshots/store/` is reserved for truthful, unaltered or transparently resized store-ready exports. Both directories contain README markers only. Populate them only after actually installing and using the build on a device/emulator, and record device/API/build details alongside the originals. Never fabricate command results or SSH sessions.

The raster dimensions and color mode were checked locally after generation. Android rendering, adaptive-icon appearance on launchers, and in-store rendering still need device/store-console review.
