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

verify_native_abi() {
  local apk="$1"
  local expected="$2"
  local name
  name="$(basename "$apk")"

  mapfile -t native_abis < <(
    unzip -Z1 "$apk" \
      | awk -F/ '/^lib\/[^/]+\/.*\.so$/ { print $2 }' \
      | sort -u
  )

  if [[ ${#native_abis[@]} -ne 1 || "${native_abis[0]}" != "$expected" ]]; then
    echo "Native ABI verification failed for $name. Expected only '$expected', found:" >&2
    printf '  %s\n' "${native_abis[@]:-<none>}" >&2
    exit 1
  fi

  if ! unzip -Z1 "$apk" | grep -q "^lib/${expected}/libopencv_java4\\.so$"; then
    echo "OpenCV native runtime missing from $name for ABI $expected" >&2
    exit 1
  fi

  echo "Verified $name contains only native ABI $expected and includes libopencv_java4.so"
}

verify_native_abi "$ARM64" "arm64-v8a"
verify_native_abi "$ARMV7" "armeabi-v7a"

rm -rf "$DEST_DIR"
mkdir -p "$DEST_DIR"
cp "$ARM64" "$DEST_DIR/CoinForensics-arm64-v8a.apk"
cp "$ARMV7" "$DEST_DIR/CoinForensics-armeabi-v7a.apk"

(
  cd "$DEST_DIR"
  sha256sum CoinForensics-arm64-v8a.apk CoinForensics-armeabi-v7a.apk > SHA256SUMS.txt
)

if find "$DEST_DIR" -maxdepth 1 -type f -name '*.apk' \
  \( -name '*x86*' -o -name '*universal*' \) | grep -q .; then
  echo "Forbidden x86/universal APK found in $DEST_DIR" >&2
  exit 1
fi

echo "Verified ARM-only OpenCV APK set:"
ls -lh "$DEST_DIR"/*.apk "$DEST_DIR/SHA256SUMS.txt"
