#!/usr/bin/env bash
# Builds and signs a release APK on the owner's machine (docs/development.md
# › Releasing). The key stays in ~/.config/starbooru-release and never
# reaches GitHub.
#
#   tools/release.sh            # writes dist/StarBooru-<version>-android-arm64.apk and SHA256SUMS
set -euo pipefail

cd "$(dirname "$0")/.."
K="${STARBOORU_RELEASE_DIR:-$HOME/.config/starbooru-release}"
CERT=fb4481c6c698fb00a43085ce002f79baf14f7df0ba0de3e009cf02ebda31de16
TOOLS="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
VERSION=$(sed -n 's/^val appVersion = "\(.*\)"$/\1/p' app/build.gradle.kts)
[ -n "$VERSION" ] || { echo "no appVersion in app/build.gradle.kts" >&2; exit 1; }

./gradlew --console=plain clean assembleRelease -Pstarbooru.abis=arm64-v8a

mkdir -p dist
OUT="dist/StarBooru-$VERSION-android-arm64.apk"
"$TOOLS/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk dist/aligned.apk
"$TOOLS/apksigner" sign --ks "$K/starbooru-release.jks" --ks-pass "file:$K/keystore-password" \
  --ks-key-alias starbooru --out "$OUT" dist/aligned.apk
rm -f dist/aligned.apk dist/*.idsig

# Android refuses an update signed with any other key.
"$TOOLS/apksigner" verify --print-certs "$OUT" | grep -q "SHA-256 digest: $CERT" \
  || { echo "signed with the wrong certificate" >&2; exit 1; }

(cd dist && sha256sum "$(basename "$OUT")" > SHA256SUMS)
echo "$OUT"
