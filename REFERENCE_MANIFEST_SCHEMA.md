# Coin Forensics reference-manifest format — v0.3

A curated manifest is the scalable mechanism for adding exact coin/date/die knowledge without publishing a new APK.

The app accepts an HTTPS JSON URL containing either a single pack object or:

```json
{ "packs": [ ... ] }
```

## Pack fields

| Field | Purpose |
|---|---|
| `id` | Stable unique pack ID. Required. |
| `display_name` | User-visible issue name. |
| `country` | Issuer/country. |
| `denomination` | Denomination/type. |
| `year` | Exact year/date string. |
| `mint_or_variety` | Mint, overdate, variety or die family. |
| `catalog_references` | Array such as `PCGS #...`, `KM#...`, `Prid-...`. |
| `source_url` | Optional pack-level research URL. |
| `physical` | Physical specifications. |
| `sources` | Provenance records. |
| `regions` | Normalized diagnostic regions. |
| `markers` | Named diagnostic tests. |
| `images` | Reference image assets tied to sources. |
| `notes` | Curator notes and scope limits. |

## Physical specification

```json
"physical": {
  "expected_weight_g": 26.96,
  "weight_tolerance_g": 0.25,
  "expected_diameter_mm": 39.0,
  "diameter_tolerance_mm": 0.25,
  "expected_thickness_mm": 2.6,
  "thickness_tolerance_mm": 0.20,
  "composition_note": ".900 silver; verify issue-specific source",
  "source_ids": ["spec-source"]
}
```

Only measurements actually entered by the user are tested. The app does not derive real grams or millimeters from an uncalibrated photograph.

## Source records

Every diagnostic and every downloadable image should point back to a source.

```json
{
  "id": "cert-genuine-001",
  "title": "Certified exact-date control specimen",
  "authority": "PCGS",
  "kind": "CERTIFIED_GENUINE",
  "scope": "EXACT_DATE_VARIETY",
  "url": "https://example.org/source-page",
  "certification_number": "12345678",
  "grade_or_status": "AU58",
  "notes": "Exact issue control",
  "image_usage_note": "Image supplied under separate permission"
}
```

Supported `kind` values:

- `CERTIFIED_GENUINE`
- `GENUINE_AUCTION`
- `CATALOG_REFERENCE`
- `COUNTERFEIT_DIAGNOSTIC`
- `AUTHENTICATION_GUIDANCE`
- `SPECIFICATION`
- `COMMUNITY_LEAD`

Supported `scope` values:

- `EXACT_DATE_VARIETY`
- `SAME_TYPE_OTHER_DATE`
- `SERIES_WIDE`
- `GENERAL_AUTHENTICATION`

## Regions

Coordinates are normalized to `0.0..1.0` after coin-face normalization.

Rectangle:

```json
{
  "id": "date",
  "label": "Date digits",
  "side": "OBVERSE",
  "shape": "RECT",
  "left": 0.37,
  "top": 0.74,
  "right": 0.63,
  "bottom": 0.89,
  "description": "Exact date geometry"
}
```

Rim / denticle annulus:

```json
{
  "id": "obverse-rim",
  "label": "Obverse rim / denticles",
  "side": "OBVERSE",
  "shape": "ANNULUS",
  "inner_radius": 0.78,
  "outer_radius": 0.98
}
```

Whole face:

```json
{
  "id": "reverse-full",
  "label": "Whole reverse",
  "side": "REVERSE",
  "shape": "FULL_COIN"
}
```

## Diagnostic markers

Example exact geometry marker:

```json
{
  "id": "date-geometry",
  "title": "Date digit geometry",
  "category": "DATE_GEOMETRY",
  "side": "OBVERSE",
  "region_id": "date",
  "scope": "EXACT_DATE_VARIETY",
  "automated_test": "REGION_REFERENCE_MATCH",
  "description": "Compare digit placement and boundary geometry against exact certified controls.",
  "source_ids": ["cert-genuine-001", "cert-genuine-002"]
}
```

Other `automated_test` values:

- `WEIGHT_TOLERANCE`
- `DIAMETER_TOLERANCE`
- `SURFACE_REFERENCE_DELTA`
- `MANUAL_ONLY`

Known counterfeit markers can cite a `COUNTERFEIT_DIAGNOSTIC` source. Their images remain in the counterfeit comparison pool and never enter the genuine median.

## Image assets

Every image must cite a valid `source_id`.

Open/reusable licensed asset:

```json
{
  "id": "genuine-obverse-001",
  "source_id": "cert-genuine-001",
  "title": "Certified control obverse",
  "side": "OBVERSE",
  "source_page_url": "https://example.org/source-page",
  "image_url": "https://example.org/reference/obverse.jpg",
  "license_name": "CC BY 4.0",
  "license_url": "https://creativecommons.org/licenses/by/4.0/",
  "creator": "Example photographer",
  "can_auto_download": true,
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

Separately authorized asset:

```json
{
  "id": "counterfeit-reverse-001",
  "source_id": "documented-fake-001",
  "side": "REVERSE",
  "image_url": "https://example.org/reference/fake-reverse.jpg",
  "permission_basis": "authorized",
  "can_auto_download": true
}
```

`can_auto_download: true` does **not** override rights validation. The app requires either:

- a recognized reusable license; or
- `permission_basis: "authorized"`.

When `sha256` is supplied, the downloaded bytes must match that 64-hex-character SHA-256 value.

## Manifest validation

The parser rejects the whole affected pack when it finds:

- duplicate source IDs
- duplicate region IDs
- duplicate marker IDs
- physical specs citing missing sources
- diagnostics citing missing sources
- markers pointing to missing regions
- marker-side / region-side mismatch
- a region outside normalized bounds
- invalid rectangle or annulus geometry
- negative physical tolerances
- an image citing a missing provenance source

This strict behavior is deliberate: an invalid authentication pack should fail closed rather than quietly alter an authenticity assessment.

## Curation rule

Do not label a source `CERTIFIED_GENUINE` or `COUNTERFEIT_DIAGNOSTIC` merely because a web listing says so. The manifest should record why the specimen is considered documented and keep exact-date, same-type-other-date, series-wide and general guidance separate.

## Queryable feed URLs

The Android app accepts either a static manifest URL or an HTTPS URL template. The following placeholders are replaced with URL-encoded case metadata before the request:

- `{country}`
- `{denomination}`
- `{year}`
- `{mint}`
- `{query}` — the non-empty fields joined as a search phrase

Example:

```text
https://references.example/api/coin?country={country}&denomination={denomination}&year={year}&mint={mint}
```

A feed may return one pack or a top-level `packs` array. The client still validates each returned pack and filters it against the active coin metadata.
