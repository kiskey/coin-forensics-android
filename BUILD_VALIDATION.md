# Build / validation notes — Coin Forensics v0.4

Validation performed after the general-purpose reference engine plus the v0.4 ARM-only Gradle split and GitHub Actions release work.

## Passed checks

- **GitHub Actions YAML:** `.github/workflows/android-latest.yml` parses successfully as YAML and declares separate read-only verification and write-enabled release jobs.
- **ARM split configuration:** `app/build.gradle.kts` enables ABI splits only for `arm64-v8a` and `armeabi-v7a` with `isUniversalApk = false`.
- **Release guard shell syntax:** `scripts/verify-abi-apks.sh` passes `bash -n`.
- **Release guard smoke test:** a synthetic two-APK directory is accepted and renamed to the two release filenames with SHA-256 output; unexpected/non-ARM APK names cause failure.
- **Git bootstrap script syntax:** the included lightweight `gradlew` bootstrap still passes `bash -n`.

- **Online/reference core compilation:** `Models`, `DiscoveryModels`, `GenericReferencePackFactory`, `ReferenceManifestParser`, `OnlineReferenceService`, and `ReferencePackRepository` compile successfully with `kotlinc` against minimal Android graphics/JSON API stubs.
- **Deterministic forensic/reference engine compilation:** `Models`, `DiscoveryModels`, `ScoreMath`, `ReferenceComparator`, `ForensicEngine`, `DieReferenceAnalyzer`, and `ReferencePackRepository` compile successfully with `kotlinc` against minimal Android graphics API stubs.
- **ViewModel semantic compilation:** the real `AppViewModel.kt` compiles against minimal AndroidX lifecycle/coroutine/engine service stubs. This exercises the new identity-selection, discovery, acquisition, pack-selection, and authentication orchestration code without Android Studio.
- **All-source Kotlin parser scan:** every Kotlin source was passed through `kotlinc`; no parser-style errors (`expecting`, unexpected tokens, missing/unclosed syntax) were found. Normal Android/AndroidX/Compose references remain unresolved in this scan because the Android SDK/classpath is not installed in this creation environment.
- **Android XML:** all manifest/resource XML files parse successfully with a strict XML parser.
- **Manifest security:** the optional online-research build declares `INTERNET`, sets `android:usesCleartextTraffic="false"`, and the reference downloader requires HTTPS. It also rejects a final redirect to non-HTTPS.
- **Reference download safety:** reusable-reference downloads are capped at 25 MiB; API/manifest text responses are capped at 5 MiB; optional SHA-256 image hashes are verified when present.
- **Open-media load control:** Wikimedia Commons metadata requests are intentionally limited to a small result set because `extmetadata` is an expensive API property.
- **Reference manifest example:** `REFERENCE_MANIFEST_EXAMPLE.json` parses as valid JSON.
- **Built-in exact-pack runtime smoke test:** the 1911-B pack still loads with **21 sources, 17 diagnostic markers, and 11 image regions**.
- **Built-in source/region integrity:** the 1911-B source, marker, and region IDs are unique; every marker source/region reference resolves.
- **Dynamic-pack smoke test:** an arbitrary `Egypt / 20 Qirsh / 1917` query with no catalogue identity builds a neutral pack with **14 spatial regions and 16 diagnostics**, and importantly contains **no guessed weight or diameter test**.
- **Selected-identity physical-test smoke test:** once a catalogue identity containing known weight/diameter is supplied, the dynamic pack adds weight and diameter corroboration checks.
- **Ambiguous catalogue protection:** when several identity candidates are returned, v0.3 does not silently apply the first candidate's physical specifications. The user must select an exact identity (unless exactly one candidate or a single curated identity is available).
- **Cross-case protection:** changing identity-defining metadata clears reference controls and derived comparisons so controls from one coin cannot remain attached to another coin case.
- **Reference provenance separation:** certified/documented genuine controls, catalogue/open-reference controls, and known/documented counterfeit controls remain separate evidence pools. Counterfeit similarity can raise caution; failure to match one counterfeit cannot increase authenticity confidence.
- **High-resolution comparison path:** alignment remains computationally inexpensive at the coarse stage, while regional geometry/edge correlation is evaluated at a larger 512 px working scale instead of only the coarse 224 px alignment scale.

## Rights / provenance behavior

v0.3 will automatically acquire an image only when the discovery record has a compatible reusable licence (for example CC0, Public Domain, CC BY, or CC BY-SA) or an exact manifest explicitly records an authorized permission basis. Clear NC/ND restrictions are rejected for automatic processing.

Professional/reference sites that do not grant compatible automated reuse remain **source-linked/manual**. Their pages can still document a diagnostic or certification source, but the app does not pretend it has permission to scrape or redistribute their photographs.

The exact-manifest parser fails closed for duplicate IDs, dangling marker citations, invalid normalized geometry, side mismatches, negative tolerances, or image assets not tied to provenance sources.

## What was not possible here

A full Android `assembleDebug` was **not** possible in this runtime because a complete Android SDK/Gradle/Compose toolchain and dependency cache are not installed locally. The validation above therefore covers the Kotlin core, orchestration layer, parsers, static project structure and resources, but does not substitute for Android Studio device/emulator validation.

Run final platform validation on a machine with Android Studio/JDK 17 and network access for dependencies:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./scripts/verify-abi-apks.sh app/build/outputs/apk/debug dist
```

The expected release-staging files are `dist/CoinForensics-arm64-v8a.apk`, `dist/CoinForensics-armeabi-v7a.apk`, and `dist/SHA256SUMS.txt`.

## Current project size

At validation time the Android source tree contains **16 Kotlin files / approximately 5,206 Kotlin lines**. This count excludes Markdown documentation, JSON examples, Gradle scripts, and Android XML resources.
