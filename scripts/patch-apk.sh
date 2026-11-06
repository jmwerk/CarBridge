#!/usr/bin/env bash
# Approach-B spike: hand-patch a single (non-split) APK so Android Auto lists it, then re-sign it.
# Only use on apps whose license allows modification (e.g. GPL apps like VLC). Never redistribute output.
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <input.apk> <output.apk> [new.package.name]" >&2
  echo "  new.package.name installs the patched copy next to the original" >&2
  exit 1
fi

in_apk=$1
out_apk=$2
rename_args=()
[[ $# -eq 3 ]] && rename_args=(--rename "$3")
here=$(cd "$(dirname "$0")" && pwd)
sdk=${ANDROID_HOME:-$HOME/Library/Android/sdk}
bt=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)
keystore=$HOME/.android/debug.keystore
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

command -v apktool >/dev/null || { echo "apktool missing: brew install apktool" >&2; exit 1; }
[[ -f $keystore ]] || { echo "missing $keystore (build any app in Android Studio once)" >&2; exit 1; }

apktool d -f -q -o "$work/src" "$in_apk"
python3 -I "$here/inject_car_manifest.py" "$work/src/AndroidManifest.xml" "${rename_args[@]}"

mkdir -p "$work/src/res/xml"
if [[ ! -f $work/src/res/xml/automotive_app_desc.xml ]]; then
  cat > "$work/src/res/xml/automotive_app_desc.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="media" />
</automotiveApp>
EOF
fi

apktool b -q -o "$work/unsigned.apk" "$work/src"
"$bt/zipalign" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
# Standard Android debug keystore; its credentials are the public SDK defaults
"$bt/apksigner" sign --ks "$keystore" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$out_apk" "$work/aligned.apk"
"$bt/apksigner" verify "$out_apk"
echo "patched: $out_apk"
if [[ $# -eq 3 ]]; then
  echo "install: adb install \"$out_apk\" (installs next to the original)"
else
  echo "install: uninstall the original first (signature differs), then adb install \"$out_apk\""
fi
