#!/usr/bin/env sh
# Source-only, checksum-verifying Gradle bootstrap. No binary wrapper JAR needed.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$ROOT/gradle/wrapper/gradle-wrapper.properties"
URL=$(sed -n 's/^distributionUrl=//p' "$PROPS" | sed 's/\\:/:/g')
SUM=$(sed -n 's/^distributionSha256Sum=//p' "$PROPS")
VERSION=$(printf '%s' "$URL" | sed -n 's/.*gradle-\([0-9.]*\)-bin.zip/\1/p')
HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/farakhvan-$VERSION"
BIN="$HOME_DIR/gradle-$VERSION/bin/gradle"
if [ ! -x "$BIN" ]; then
    mkdir -p "$HOME_DIR"
    TMP=$(mktemp -d "$HOME_DIR/download.XXXXXX")
    trap 'rm -rf "$TMP"' EXIT HUP INT TERM
    curl --fail --location --retry 3 --connect-timeout 30 "$URL" -o "$TMP/gradle.zip"
    printf '%s  %s\n' "$SUM" "$TMP/gradle.zip" | sha256sum -c -
    unzip -q "$TMP/gradle.zip" -d "$TMP"
    mv "$TMP/gradle-$VERSION" "$HOME_DIR/"
    rm -rf "$TMP"
    trap - EXIT HUP INT TERM
fi
exec "$BIN" -p "$ROOT" "$@"
