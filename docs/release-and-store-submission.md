# GitHub release, Google Play, and F-Droid preparation

This is a future-facing checklist, not a release or a claim of store approval. At the time of writing (2026-10-25), SSH-Forge has no published release, no actual device screenshots, and no public stable privacy-policy URL. The last successful CI run predates the current uncommitted audit changes. Do not submit a build until those changes pass CI and the release owner completes the checks below.

## Before any distribution

1. Review the full branch diff and license notices; keep the root Apache-2.0 `LICENSE` unchanged.
2. Run `./gradlew testDebugUnitTest assembleDebug` with the documented JDK and Android SDK, or wait for the corresponding GitHub Actions checks to pass. Test host-key approval/rejection, auth failures, network loss, command stdout/stderr/exit status, timeouts, disconnect, retry, and password clearing on an actual device/emulator.
3. Review the built manifest and dependencies, confirm that no secrets or debug-only features are packaged, and inspect the final icon on Android launcher shapes. Validate the release signing and versioning plan. CI's unsigned release APK is not a Play-ready AAB and the CI debug APK is not a production release.
4. Capture genuine screenshots from that tested build. Save unchanged originals in `screenshots/original/`, store exports in `screenshots/store/`, and record device/API/version and any resizing. Never use the concept PNGs as evidence of actual behavior.
5. Re-read `docs/privacy-policy.md` against the final build and all bundled libraries. Arrange a stable, public HTTPS URL under a domain/account controlled by the release owner. No such URL or privacy contact is currently configured. Do not submit to Google Play until the final policy is public and linked in-app and in Play Console.
6. Confirm the package ID is available in the intended store, choose the release signing key and backup procedure, review current target-API/store policies, and have the release owner approve the exact binary and listing. No package-ID global-uniqueness or store acceptance claim is made here.

## GitHub source release (manual future procedure)

1. Ensure work is on the session's existing branch and do not rewrite branch history. Push only that branch and open/review a pull request if the project's maintainers require it.
2. Wait for all required checks on the final merge/release commit; inspect the action logs and retain the commit SHA and artifact/checksum for provenance.
3. Update app version/version code and `fastlane/metadata/android/en-US/changelogs/` in a separate reviewed change before cutting a release. Confirm the source archive builds from a clean checkout.
4. Only after explicit release-owner approval, create a version tag on the approved commit and publish source notes via GitHub Releases. Do not attach an unsigned CI APK as a production-ready binary. Do not publish or sign an APK/AAB as part of this audit task.
5. Use GitHub's documented release/tag workflow and the repository's existing branch protections; do not change repository settings as part of this checklist.

## Google Play preparation and console steps

1. Confirm the developer account, application ID availability, signing configuration, supported countries/devices, content rating, and current Play requirements with the release owner.
2. Build and test a release-signed Android App Bundle from the approved commit. Keep signing credentials outside the repository and CI logs; never put them in chat or commit them.
3. Create the Play Console app and complete the store listing using the truthful text in `fastlane/metadata/android/en-US/` / `store/google-play/listing.md`. Review the 512×512 icon and `graphics/feature/feature-graphic-1024x500.png`; add genuine screenshots only after capture.
4. Host the reviewed final privacy policy at a stable public URL and enter that exact URL both in Play Console and in the app listing/appropriate in-app location. The current local policy is marked **DRAFT** and is not sufficient for submission.
5. Complete the Data safety form from the final build, actual data flows, and current SDK behavior—not by copying an assumption. The app directly sends user-entered SSH credentials/commands to the selected SSH server; describe the developer's collection/sharing and the server endpoint accurately. Complete content rating, target audience, app access, and all other Play Console declarations truthfully.
6. Upload to an internal testing track first. Test install/update, compatibility, privacy/permission disclosures, store screenshots/listing, and crash-free runtime; address Play Console warnings. Promote only after explicit approval and policy review. Store review/acceptance is not guaranteed.

## F-Droid preparation and submission steps

1. Review the current [F-Droid Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/), [submission quick start](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/), and [build metadata reference](https://f-droid.org/docs/Build_Metadata_Reference/) again immediately before submission.
2. Continue active public upstream development. As of 2026-10-25 this repository was created on 2026-10-24, so the policy's usual three-month active-development period has not elapsed. Request an exception only if the maintainers permit and there is a reason; do not imply eligibility or acceptance.
3. Finish independent review of all packaged dependencies, licenses, build reproducibility, Gradle/Android toolchain availability, network behavior, and offline/remote build recipe. Current CI compares two clean builds on one runner and does **not** establish F-Droid reproducibility.
4. Publish a reviewed, tagged source release after the release owner approves it. Keep tag/version code/changelog and source archive consistent. Do not submit a debug artifact.
5. Prepare the app recipe under `metadata/com.sshforge.app.yml` in the separate `fdroiddata` repository, following current metadata and build-server rules. No recipe is included in this upstream app repository. Verify every source, commit, Gradle task, version, and required build output against the chosen tag.
6. Capture authentic app screenshots if the current F-Droid listing guidance requires them. Use only screenshots from real device/emulator sessions; concepts are not screenshots. Use the project icon and provide accurate upstream metadata/changelog.
7. Submit the metadata change through F-Droid's current contribution process, respond to build/test/reviewer requests, and wait for an explicit decision. Submission, inclusion, and availability are not guaranteed.

## Assets and source metadata

- Google Play icon: `graphics/icon/ssh-forge-icon-512.png` (also `fastlane/metadata/android/en-US/images/icon.png`).
- Google Play feature graphic: `graphics/feature/feature-graphic-1024x500.png` (1024×500 RGB, no alpha).
- English listing source: `fastlane/metadata/android/en-US/`; a review note is in `store/google-play/listing.md`.
- F-Droid listing/submission notes: `store/f-droid/README.md`; the eventual build recipe belongs in `fdroiddata`, not here.
- Visual inventory and screenshot restrictions: `docs/graphics-inventory.md`.
- Privacy text: `docs/privacy-policy.md` (draft only; no public URL).
