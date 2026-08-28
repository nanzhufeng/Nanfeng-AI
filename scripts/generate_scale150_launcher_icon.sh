#!/bin/zsh
set -euo pipefail

# The supplied master remains immutable. The active derivative keeps the horse
# subject at the user's final approved 85% linear size on the same orange canvas.
immutable_master='artwork/source/nanfeng_ai_launcher_icon_master_20260828.png'
master='artwork/source/nanfeng_ai_launcher_icon_master_20260828_subject_085.png'
android_foreground='app/src/main/res/drawable-nodpi/nanfeng_ai_icon_foreground_image.png'
desktop_png='desktop/src-tauri/icons/nanfeng_ai_icon_rgba.png'
desktop_icns='desktop/src-tauri/icons/nanfeng_ai_icon.icns'
desktop_runtime_png='desktop/src-tauri/icons/icon.png'

test "$(shasum -a 256 "$immutable_master" | awk '{print $1}')" = 'da5357509c204e8df822b71e1d1495634df7b9c2404d84cc0c01f6c67a35c347'
test "$(shasum -a 256 "$master" | awk '{print $1}')" = 'c3474b35a2f772ba94e56f6ea8bc27a92e29ea1996f28a6d7ba404b2c6b98b63'

mode="${1:-all}"
if [[ "$mode" != 'all' && "$mode" != '--android-only' ]]; then
  print -u2 'usage: generate_scale150_launcher_icon.sh [--android-only]'
  exit 2
fi

derive_square() {
  local destination="$1"
  local pixels="$2"
  mkdir -p "${destination:h}"
  ffmpeg -y -loglevel error -i "$master" \
    -vf "scale=${pixels}:${pixels}:flags=lanczos,format=rgba" \
    -frames:v 1 -pix_fmt rgba "$destination"
}

derive_android_square() {
  local destination="$1"
  local pixels="$2"
  mkdir -p "${destination:h}"
  ffmpeg -y -loglevel error -i "$master" \
    -vf "scale=${pixels}:${pixels}:flags=lanczos,format=rgba" \
    -frames:v 1 -pix_fmt rgba "$destination"
}

# Android adaptive foreground and every legacy density are independent immutable-master exports.
derive_android_square "$android_foreground" 1254
for density pixels in mdpi 48 hdpi 72 xhdpi 96 xxhdpi 144 xxxhdpi 192; do
  derive_android_square "app/src/main/res/mipmap-${density}/ic_launcher.png" "$pixels"
  derive_android_square "app/src/main/res/mipmap-${density}/ic_launcher_round.png" "$pixels"
done

[[ "$mode" == '--android-only' ]] && exit 0

# Tauri consumes an RGBA PNG and macOS receives a real ICNS derived only from master.
derive_square "$desktop_png" 1024
derive_square "$desktop_runtime_png" 1024
iconset_parent="$(mktemp -d /tmp/nanfeng-ai-iconset.XXXXXX)"
iconset="$iconset_parent/nanfeng_ai_icon.iconset"
mkdir -p "$iconset"
trap 'rm -rf "$iconset_parent"' EXIT
for spec in 'icon_16x16.png:16' 'icon_16x16@2x.png:32' 'icon_32x32.png:32' 'icon_32x32@2x.png:64' 'icon_128x128.png:128' 'icon_128x128@2x.png:256' 'icon_256x256.png:256' 'icon_256x256@2x.png:512' 'icon_512x512.png:512' 'icon_512x512@2x.png:1024'; do
  name="${spec%%:*}"
  pixels="${spec##*:}"
  derive_square "$iconset/$name" "$pixels"
done
iconutil -c icns "$iconset" -o "$desktop_icns"
