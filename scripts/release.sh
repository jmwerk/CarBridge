#!/usr/bin/env bash
# Builds a release-signed CarBridge APK and publishes it as a GitHub release for Obtainium.
# Usage: scripts/release.sh   (version comes from patcher/build.gradle.kts)
set -euo pipefail

RELEASE_CERT_SHA256="a4689360a91a49b16fa7b150dcfc6e2b89211acdb963b55f1b15704ada43cc7f"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
BUILD_TOOLS="$(ls -d "$HOME"/Library/Android/sdk/build-tools/* | tail -1)"

cd "$(dirname "$0")/.."
version=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' patcher/build.gradle.kts)
tag="v$version"

[[ -z "$(git status --porcelain)" ]] || { echo "Working tree not clean" >&2; exit 1; }
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && { echo "$tag already exists; bump versionName/versionCode" >&2; exit 1; }

./gradlew -q :patcher:assembleRelease
apk="patcher/build/outputs/apk/release/patcher-release.apk"

# Obtainium can't update across signing keys, so never ship a debug-signed fallback
cert=$("$BUILD_TOOLS/apksigner" verify --print-certs "$apk" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
[[ "$cert" == "$RELEASE_CERT_SHA256" ]] || { echo "APK not signed with the release key ($cert)" >&2; exit 1; }

out="patcher/build/CarBridge-$version.apk"
cp "$apk" "$out"
git tag -a "$tag" -m "CarBridge $version"
git push origin HEAD "$tag"
gh release create "$tag" "$out" --title "CarBridge $version" --generate-notes
