#!/usr/bin/env bash
set -euo pipefail
VERSION="9.6.0"
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/coinforensics-bootstrap"
HOME_DIR="$BASE/gradle-$VERSION"
ZIP="$BASE/gradle-$VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"

if [[ ! -x "$HOME_DIR/bin/gradle" ]]; then
  mkdir -p "$BASE"
  echo "Gradle $VERSION not found locally; downloading official distribution…" >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "$URL"
  else
    echo "Install Android Studio or provide curl/wget, then retry." >&2
    exit 1
  fi
  rm -rf "$HOME_DIR"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ZIP" -d "$BASE"
  else
    echo "unzip is required for this lightweight Gradle bootstrap." >&2
    exit 1
  fi
fi

exec "$HOME_DIR/bin/gradle" "$@"
