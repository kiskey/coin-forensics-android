# Changelog

## v0.3.0 — versatile online reference acquisition and dynamic authentication

- Generalized the exact-die analyzer beyond the built-in 1911-B reference pack.
- Added metadata-driven online research for arbitrary coins.
- Added Wikimedia Commons discovery with per-file license metadata.
- Added optional Numista API catalogue identity/specification discovery.
- Added HTTPS curated reference-manifest support for exact die packs and permissioned image assets.
- Added explicit identity candidate selection instead of silently trusting the first catalogue result.
- Added conservative dynamic packs for coins without an exact curated pack.
- Dynamic packs use whole-side, rim, central, upper/lower and left/right regions and are explicitly scoped as type/series evidence.
- Added rights-aware automatic reference download; uncertain/restricted assets remain source-only/manual.
- Added 25 MB reference download limit, HTTPS-only images, raster decode validation, and optional SHA-256 pin verification.
- Hardened license recognition to reject obvious NC/ND restrictions for automatic processing.
- Added strict manifest validation for duplicate IDs, dangling citations, bad regions, side mismatches, negative tolerances and unbound image assets.
- Increased imported working-image bound from 2400 px to 3200 px.
- Kept coarse alignment at 224 px but moved regional device/die comparison to a 512 px working scale.
- Added reference provenance-strength score separately from visual evidence consistency.
- Prevented catalogue-only matches from being described as strong die-level authentication.
- Maintained complete separation between genuine/reference controls and counterfeit controls.
- Added source-rights and hash provenance to automatically acquired reference samples and PDF reporting.
- Added case contamination guard: changing identity-defining metadata clears old reference controls and comparisons while measurement-only edits preserve the reference set.
- Added Android INTERNET permission only for optional reference research; cleartext traffic remains disabled.

## v0.2.0 — exact coin/die intelligence

- Added structured exact-issue reference packs with provenance, evidence authority, source scope, physical specifications, regions and diagnostic markers.
- Added first built-in pack: **Great Britain 1911-B Trade Dollar — Prid-21 / PCGS #207444**.
- Added multi-control genuine consensus using median region scores.
- Added source-bound counterfeit reference images and a physically separate counterfeit comparison pool.
- Added counterfeit-vs-genuine regional delta reporting; known-fake proximity can raise a caution but non-match never increases authenticity evidence.
- Added exact regional checks for date, trident/B mintmark, ship/waves, shield field, Britannia relief, reverse center, Jawi lettering and both rim/denticle zones.
- Added documented weight and caliper diameter checks.
- Added thickness-source conflict handling instead of a misleading hard threshold.
- Added 1911/00-B overdate caution.
- Added lower-authority exact-year counterfeit community leads as manual-only evidence.
- Updated PDF report with reference roles, source provenance, counterfeit proximity and source list.

## 0.4.0 — GitHub CI + ARM-only rolling release

- Added GitHub Actions workflow for unit tests, Android lint, and APK build verification.
- Restricted Gradle APK splits to `arm64-v8a` and `armeabi-v7a`; universal/x86 APKs are disabled.
- Added a strict release guard that requires exactly two ARM APKs and generates SHA-256 checksums.
- Added a protected rolling `latest` release job that rebuilds the verified commit and replaces the previous `latest` release.
- Added full local unpack, Git initialization, commit, GitHub remote, push, branch, and release instructions in `GITHUB_SETUP.md`.
- Bumped app version to 0.4.0 / versionCode 4.
