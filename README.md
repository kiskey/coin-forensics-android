# Coin Forensics for Android — v0.4

Coin Forensics is a local-first Android app for evidence-based coin image inspection, reference research, and provenance-aware visual authentication assistance.

v0.4 builds on the v0.3 general-purpose reference engine, which generalized the v0.2 exact 1911-B Trade Dollar engine into a reusable system that can research **arbitrary coin metadata**, acquire reference images where reuse rights are explicit, load exact curated die packs when available, compare high-resolution user photographs against several genuine controls and documented counterfeit controls, and produce an explainable report.

It does **not** claim that an image-only result proves authenticity. It does not fabricate UV/IR information, metal composition, weight, or hidden surface data that was never captured.

## Core workflow

```text
Coin information
(country / issuer, denomination, year, mint or variety)
        │
        ▼
Reference research
        │
        ├── Wikimedia Commons open-media candidates
        ├── Numista API identity/specifications (optional API key)
        └── Curated HTTPS reference-pack manifest (optional)
        │
        ▼
Identity resolution
(user selects the exact catalogue candidate when ambiguous)
        │
        ▼
Reference pack
        ├── exact curated die pack, if available
        ├── built-in exact pack, if available
        └── conservative dynamic type/series pack otherwise
        │
        ▼
Reference acquisition
        ├── auto-download only where rights are explicitly compatible
        └── source-linked/manual import for restricted or uncertain images
        │
        ▼
User high-resolution photos
(obverse / reverse / edge, optional real UV / real IR)
        │
        ▼
Local deterministic forensic engine
        │
        ├── full-design / rim / regional geometry
        ├── lettering / relief / edge maps where curated regions exist
        ├── microtexture and image-quality measurements
        ├── physical measurements entered by the user
        └── documented counterfeit proximity in a separate evidence pool
        │
        ▼
Explainable evidence report
```

## What v0.4 implements

### 1. Arbitrary-coin research

The **Refs** screen can research the current case metadata instead of requiring a hard-coded coin pack.

The online discovery layer currently supports:

- **Wikimedia Commons** via the MediaWiki API for open-media image candidates and per-file license metadata.
- **Numista API** when the user supplies their own API key. It is used for catalogue identity candidates, physical specifications, and image metadata when present.
- **Curated HTTPS manifest** for exact issue/die packs, documented genuine controls, documented counterfeit controls, named diagnostic regions, and permissioned reference assets.

The app does not scrape restricted grading-service websites. PCGS, NGC, auction houses, forums, and other sources can still be represented in a curated pack or manually attached with provenance when the user has a permitted image/source.

### 2. Identity selection instead of silently guessing

Catalogue search can return several plausible matches. v0.3 displays the candidates and lets the user select the intended identity before catalogue physical specifications are applied.

A curated exact pack still outranks a generic catalogue identity.

Changing an identity-defining field (issuer/country, denomination, year, mint/variety) clears old reference controls and reference-derived comparisons. This prevents cross-case contamination.

### 3. Dynamic reference packs

When no exact die pack exists, `GenericReferencePackFactory` generates a conservative pack with spatial regions instead of pretending it knows where a date or mintmark is located on every coin:

- full obverse / reverse
- rim and border zone
- central relief
- upper / lower sectors
- left / right sectors
- surface-microtexture divergence
- weight and diameter checks when catalogue specifications are available
- manual edge/reeding review

These dynamic geometry checks are labelled **series/type-level**. They are not promoted to exact-die diagnostics.

### 4. Exact curated die packs

A remote JSON manifest can override the generic spatial pack with named exact diagnostics such as:

- date digits and overdates
- mintmark position
- lettering / script shapes
- stars, beads and denticles
- relief boundaries
- known die cracks or die polish
- transfer-die repeating depressions
- specific counterfeit defects
- edge/reeding diagnostics
- physical specifications

The existing built-in **1911-B British Trade Dollar — Prid-21 / PCGS #207444** pack remains included as an example of the exact-pack approach.

See `REFERENCE_MANIFEST_SCHEMA.md` and `REFERENCE_MANIFEST_EXAMPLE.json`.

The manifest setting may be either a static HTTPS JSON URL or a **query-template URL**. Supported placeholders are `{country}`, `{denomination}`, `{year}`, `{mint}`, and `{query}`; the app URL-encodes the active case values before requesting the feed. For example:

```text
https://references.example/api/coin?country={country}&denomination={denomination}&year={year}&mint={mint}
```

That allows one remotely maintained reference service to hold thousands of exact date/mint/die packs without shipping the whole database inside the APK.

### 5. Rights-aware automatic image acquisition

Automatic image download is intentionally conservative.

A discovered image is auto-downloadable only when the provider or curated manifest establishes a compatible rights basis. The current implementation recognizes public-domain/CC0 and attribution-style CC licenses and rejects obvious NC/ND restrictions for automatic processing. A curated manifest may alternatively mark an asset as explicitly `authorized`.

Restricted/uncertain images are shown as **source only** and can be opened in the browser. They are not silently scraped or copied.

Downloaded reference assets are limited to 25 MB, must decode as a raster image, must use HTTPS, and can carry an optional SHA-256 hash. When a curated pack supplies a hash, the download is rejected if the content no longer matches.

### 6. High-resolution comparison

The app keeps high-resolution user images up to a bounded working size rather than immediately replacing them with tiny thumbnails.

Reference registration is solved quickly at 224 px using a small rotation/scale/translation search. Once aligned, regional die/device measurements are performed at a **512 px working scale** with edge geometry weighted more heavily than luminance. This preserves more useful die information while keeping on-device computation manageable.

The comparator produces:

- overall visual consistency
- luminance correlation
- edge/device correlation
- best local alignment
- normalized difference map
- region-by-region consistency

The numeric result is a **visual evidence consistency score**, never “X% genuine.”

### 7. Multi-control genuine consensus

Several documented genuine controls can be attached per side. The analyzer computes each target-vs-control regional comparison and then uses the **median regional result**.

That reduces dependence on one coin's:

- wear
- toning
- contact marks
- cleaning
- die state
- lighting
- camera processing

Certified/professionally documented controls receive a much stronger provenance rating than open catalogue images.

### 8. Counterfeit evidence is physically separate

Reference images tagged as:

- `COUNTERFEIT_DIAGNOSTIC`
- `COMMUNITY_LEAD`

never enter the genuine-control median.

The app instead calculates **counterfeit proximity** separately. A close match to a documented fake may raise a caution. Failure to match a known fake never increases the authenticity evidence because another counterfeit die or method may exist.

Community counterfeit examples remain lower-authority manual evidence.

### 9. Provenance strength

The report now separates visual consistency from provenance strength.

A target may visually match an open catalogue photograph extremely well while still lacking a certified genuine baseline. In that case the app reports type/series consistency and explicitly refuses to call it strong die-level authentication.

This avoids a common failure mode where a high image-correlation number looks more authoritative than the reference source actually is.

### 10. Local forensic transforms

The deterministic image engine still provides:

- original pixels
- perceptual luminance
- red / green / blue channels
- local contrast
- high-pass detail
- Sobel edge map
- Laplacian microtexture
- local texture variance
- specular/glare map
- pseudo-relief
- false-color luminance

And measures:

- resolution
- sharpness
- contrast
- highlight / shadow clipping
- glare estimate
- microtexture energy
- local variance
- edge density
- rim circularity estimate
- rim-pattern regularity estimate

## Real UV and IR only

The `UV` and `IR` slots are reserved for actual ultraviolet/infrared captures. A normal RGB photograph is never transformed and then mislabeled as physical UV/IR evidence.

False-color, high-pass and pseudo-relief views are computational visualizations of visible-light pixels only.

## Reference trust ladder

The engine distinguishes reference roles instead of treating every web image equally:

1. **Certified / professionally authenticated genuine control**
2. **Curated specialist source**
3. **Catalogue / open-reference image**
4. **Documented counterfeit diagnostic** — separate comparison pool
5. **Community counterfeit lead** — manual corroboration only
6. **Unknown/unverified** — not suitable for strong conclusions

A high visual match to a lower-trust reference does not magically upgrade that reference's provenance.

## Curated manifest safety rules

Remote exact packs are validated before use. The parser rejects packs with:

- duplicate source, region, or marker IDs
- diagnostic markers citing missing sources
- markers pointing to missing regions
- side/region mismatches
- invalid 0..1 region coordinates
- invalid annulus geometry
- negative measurement tolerances
- image assets that are not bound to a provenance source

A manifest's `can_auto_download` flag is not sufficient by itself. The asset must also have a recognized reusable license or `permission_basis: "authorized"`.

## First exact built-in pack: 1911-B British Trade Dollar

The v0.2 exact pack is retained. It includes:

- exact-date genuine/control provenance
- normal 1911-B vs 1911/00-B variety caution
- date region
- trident / B mintmark
- ship and waves
- shield-adjacent field
- Britannia relief
- central reverse characters
- Jawi/peripheral lettering
- both rim/denticle zones
- weight / diameter specification checks
- documented same-type counterfeit guidance
- separately handled counterfeit controls

See `REFERENCE_SOURCES_1911B.md`.

## Example expansion targets

No engine changes are required to add exact packs for coins such as:

- British Trade Dollar 1898-B / 1909-B
- Burma 1852 Peacock Kyat
- Egypt 1916 / 1917 20 Qirsh
- French Indo-China Piastre
- Chinese Yuan Shikai / “Fat Man” Dollar
- Straits Settlements Dollar
- South African Republic crowns and half-crowns
- British India rupees

A curator supplies the exact sources, regions, physical specifications, documented genuine controls, known counterfeit controls/markers and licensed assets in a manifest. The same Android analyzer consumes them.

## Privacy and networking

v0.3 adds the Android `INTERNET` permission because online reference research is now optional.

- User coin images remain local unless the user explicitly shares/export them.
- The forensic engine itself runs locally.
- The Numista API key is held in app state for the current session and is not written into the PDF report.
- Cleartext HTTP is disabled.
- Automatic reference downloads require HTTPS.
- Online research can fail completely and local image analysis still works.

## Build requirements

Configured for:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- compileSdk 37
- targetSdk 36
- minSdk 28
- Kotlin Compose plugin 2.3.21
- Compose BOM 2026.08.00
- AndroidX Core 1.18.0 / Lifecycle 2.11.0 / Activity Compose 1.13.0
- Java 17+

Open in a current Android Studio installation and run:

```bash
./gradlew test assembleDebug
```

The debug APK will appear under:

```text
app/build/outputs/apk/debug/
```

The creation environment used here does not contain a full Android SDK/Compose dependency cache, so final APK assembly must be done on an Android Studio/Android SDK machine.

## Capture guidance for authentication

For each target coin, use the highest-resolution source image you can obtain without AI reconstruction:

1. straight-on obverse
2. straight-on reverse
3. complete edge/reeding when possible
4. coin fills most of frame
5. camera sensor parallel to the coin
6. neutral non-reflective background
7. avoid clipped highlights
8. preserve original image before editing
9. optionally add several directional-light photographs
10. enter real scale measurements from a calibrated scale/caliper rather than estimating physical size from pixels

Do not sharpen, “restore,” generatively upscale, or reconstruct missing details before authentication. Viewing upscales are acceptable, but measurements should be tied to original captured information.

## Interpretation boundary

Coin Forensics is an **authentication-assistance and evidence-comparison tool**, not a replacement for PCGS/NGC/ANACS or an in-hand numismatist.

Image evidence cannot directly establish alloy, internal structure, exact weight, density, magnetic behavior, edge depth that is not photographed, or microscopic features below the source resolution. A sophisticated transfer-die counterfeit can also reproduce genuine geometry extremely closely.

When value/risk warrants it, combine the app with:

- calibrated weight
- diameter / thickness
- complete edge examination
- specific gravity
- XRF or other alloy testing where appropriate
- known die diagnostics
- provenance
- professional in-hand authentication

## Project layout

```text
app/src/main/java/com/coinforensics/app/
├── MainActivity.kt
├── imaging/
│   ├── ForensicEngine.kt
│   ├── ImageIo.kt
│   ├── ReferenceComparator.kt
│   └── ScoreMath.kt
├── model/
│   ├── DiscoveryModels.kt
│   └── Models.kt
├── reference/
│   ├── DieReferenceAnalyzer.kt
│   ├── GenericReferencePackFactory.kt
│   ├── OnlineReferenceService.kt
│   ├── ReferenceManifestParser.kt
│   └── ReferencePackRepository.kt
├── report/
│   └── ReportExporter.kt
└── ui/
    ├── AppViewModel.kt
    ├── CoinForensicsApp.kt
    └── CoinForensicsTheme.kt
```

## Suggested next technical layers

- OpenCV feature/keypoint registration and explicit perspective correction.
- Automatic coin-boundary detection/cropping with a manual correction UI.
- Directional-light registration / photometric stereo for surface topology.
- Better exact-issue image feeds from rights-cleared professional sources.
- Signed/versioned reference manifests and local reference-pack cache.
- Full edge unwrapping and reeding periodicity comparison.
- Specific-gravity workflow.
- Optional local TFLite/ONNX classifier as a separate evidence channel only after a sufficiently large, correctly labeled training set exists.

## GitHub Actions: ARM-only rolling latest build (v0.4)

The project now includes `.github/workflows/android-latest.yml`. Pull requests and pushes to `main` run unit tests, Android lint, and an ARM-only APK build. Successful pushes to `main` replace one rolling GitHub Release/tag named `latest` with:

- `CoinForensics-arm64-v8a.apk` — ARMv8 64-bit
- `CoinForensics-armeabi-v7a.apk` — ARMv7 32-bit
- `SHA256SUMS.txt`

No x86, x86_64, or universal APK is accepted by the release guard. The release APKs are currently debug-signed CI builds intended for sideloading/testing. See [`GITHUB_SETUP.md`](GITHUB_SETUP.md) for complete unpack, local test, `git init`, first commit, remote creation, and push instructions.
