# F-Droid submission preparation

SSH-Forge has not been submitted to or accepted by F-Droid. There is no tagged upstream release or `fdroiddata` build recipe. The project repository was created on 2026-10-24; as of 2026-10-25 the usual three-month active-development period in the F-Droid Inclusion Policy has not elapsed.

## Eventual submission checklist

1. Recheck the current [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/), [Submitting to F-Droid Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/), and [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/).
2. Continue active upstream development and obtain explicit maintainer approval for a tagged source release.
3. Confirm package ID, version code, source tag, license/third-party notices, build dependencies, network use, and reproducibility. Current CI's same-runner duplicate build is not an F-Droid build-server reproducibility check.
4. Add the recipe as `metadata/com.sshforge.app.yml` in the separate `fdroiddata` repository. It does not belong in this app repository. Validate the recipe against the chosen tag and current F-Droid build tools.
5. Submit the metadata change according to F-Droid's current contribution process, then respond to build-server output and reviewer requests. Do not claim acceptance until it is explicitly granted.
6. Use the project icon `../../graphics/icon/ssh-forge-icon-512.png`. Real screenshots are not available: do not submit the concept images as real app screenshots. Capture only from a genuine, tested build when a device/emulator is available.

See [`../../docs/release-and-store-submission.md`](../../docs/release-and-store-submission.md) and [`../../docs/graphics-inventory.md`](../../docs/graphics-inventory.md). No store URL, package-ID uniqueness guarantee, release artifact, or submission has been fabricated.
