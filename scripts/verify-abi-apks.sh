#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="${1:-app/build/outputs/apk/debug}"
DEST_DIR="${2:-dist}"

if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "APK output directory not found: $SOURCE_DIR" >&2
  exit 1
fi

mapfile -t ALL_APKS < <(find "$SOURCE_DIR" -maxdepth 1 -type f -name '*.apk' -print | sort)

if [[ ${#ALL_APKS[@]} -ne 2 ]]; then
  echo "Expected exactly 2 APKs (arm64-v8a + armeabi-v7a), found ${#ALL_APKS[@]}:" >&2
  printf '  %s\n' "${ALL_APKS[@]:-<none>}" >&2
  exit 1
fi

ARM64=""
ARMV7=""
for apk in "${ALL_APKS[@]}"; do
  base="$(basename "$apk")"
  case "$base" in
    *arm64-v8a*) ARM64="$apk" ;;
    *armeabi-v7a*) ARMV7="$apk" ;;
    *)
      echo "Unexpected APK architecture/name: $base" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$ARM64" || -z "$ARMV7" ]]; then
  echo "Missing required ABI APK. arm64-v8a='$ARM64' armeabi-v7a='$ARMV7'" >&2
  exit 1
fi

rm -rf "$DEST_DIR"
mkdir -p "$DEST_DIR"
cp "$ARM64" "$DEST_DIR/CoinForensics-arm64-v8a.apk"
cp "$ARMV7" "$DEST_DIR/CoinForensics-armeabi-v7a.apk"

(
  cd "$DEST_DIR"
  sha256sum CoinForensics-arm64-v8a.apk CoinForensics-armeabi-v7a.apk > SHA256SUMS.txt
)

# Guard against accidental universal/x86 artifacts entering the release directory.
if find "$DEST_DIR" -maxdepth 1 -type f -name '*.apk' \
  \( -name '*x86*' -o -name '*universal*' \) | grep -q .; then
  echo "Forbidden x86/universal APK found in $DEST_DIR" >&2
  exit 1
fi

echo "Verified ARM-only APK set:"
ls -lh "$DEST_DIR"/*.apk "$DEST_DIR/SHA256SUMS.txt"
