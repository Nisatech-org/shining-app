#!/usr/bin/env bash
# Makes docs/screenshots again: every screen in light and dark, overview.png, and market-flow.mp4.
#
#   bash docs/screenshots/refresh.sh               # everything (about 6 minutes)
#   SKIP_VIDEO=1 bash docs/screenshots/refresh.sh  # just the screenshots
#   SKIP_SHOTS=1 bash docs/screenshots/refresh.sh  # just the video
#
# Needs an emulator (or starts the AVD named in $AVD), adb, Python 3 with Pillow (and raqm for
# the Persian labels) and ffmpeg. The emulator must render on the GPU (-gpu host): with software
# rendering it lags several seconds behind the video's script. Everything shown is sample data
# from the tests in app/src/androidTest/java/app/shining/screenshots/; nothing reaches a server.
# It leaves the results in the working tree for you to look at and commit.
set -euo pipefail

cd "$(dirname "$0")/../.."
ROOT=$(pwd)
OUT=docs/screenshots
WORK=$(mktemp -d)
RUNNER=app.shining.test/androidx.test.runner.AndroidJUnitRunner
AVD=${AVD:-foodie_api34}
SDK=$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)
SDK=${SDK:-${ANDROID_HOME:-}}

say() { printf '\n== %s\n' "$*"; }

# --- the emulator ---------------------------------------------------------------------------
if ! adb get-state >/dev/null 2>&1; then
  [ -x "$SDK/emulator/emulator" ] || { echo "No device, and no emulator under \$SDK ($SDK)." >&2; exit 1; }
  say "Starting the $AVD emulator (GPU on, 4 GB)"
  nohup "$SDK/emulator/emulator" -avd "$AVD" -gpu host -memory 4096 -cores 6 -no-snapshot-save -no-audio -no-boot-anim \
    >"$WORK/emulator.log" 2>&1 &
  adb wait-for-device
fi
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
sleep 3

NIGHT_BEFORE=$(adb shell cmd uimode night | tr -d '\r' | awk '{print $3}')
restore() {
  adb shell cmd uimode night "${NIGHT_BEFORE:-no}" >/dev/null 2>&1 || true
  adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap restore EXIT

# A tidy status bar: 12:00, full battery, no notifications.
adb shell settings put global sysui_demo_allowed 1
for c in "enter" "clock -e hhmm 1200" "battery -e level 100 -e plugged false" \
         "network -e wifi show -e level 4 -e mobile hide" "notifications -e visible false"; do
  adb shell am broadcast -a com.android.systemui.demo -e command $c >/dev/null
done

# --- build and install (Gradle's connectedAndroidTest would uninstall the app, and the pictures with it)
say "Building and installing"
./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null

# Runs one test method or class; fails loudly if it didn't pass.
run() {
  local result
  result=$(adb shell am instrument -w -e class "$1" "$RUNNER" 2>&1 | tr -d '\r')
  if ! grep -q '^OK (' <<<"$result"; then
    grep -m1 -A4 -E 'Error in|crashed|Exception' <<<"$result" >&2 || true
    return 1
  fi
}

night() {
  adb shell cmd uimode night "$1" >/dev/null
  sleep 3
}

# --- screenshots ----------------------------------------------------------------------------
if [ -z "${SKIP_SHOTS:-}" ]; then
  say "Screenshots, light then dark"
  adb shell run-as app.shining rm -rf files/screenshots
  # Sheets follow the system's light or dark setting, so each mode is a pass of its own.
  night no
  run app.shining.screenshots.ScreenshotTest
  night yes
  run app.shining.screenshots.ScreenshotTest
  adb exec-out run-as app.shining tar -C files -cf - screenshots | tar -xf - -C "$WORK"

  say "Scaling them to 540x1200 and making overview.png"
  python3 - "$WORK/screenshots" "$OUT" <<'PY'
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

shots, out = Path(sys.argv[1]), Path(sys.argv[2])


def save(image: Image.Image, target: Path) -> bool:
    """Writes the picture only if it looks different, so a refresh doesn't churn files over scaling noise."""
    if target.exists():
        old = Image.open(target).convert("RGB")
        if old.size == image.size:
            diff = ImageChops.difference(old, image).convert("L").point(lambda v: 255 if v > 16 else 0)
            if diff.histogram()[255] < 200:
                return False
    image.save(target, optimize=True)
    return True


changed = 0
for path in sorted(shots.glob("*.png")):
    changed += save(Image.open(path).convert("RGB").resize((540, 1200), Image.LANCZOS), out / path.name)

# The strip at the top of the readme: five screens in a row.
names = ["home", "reserve", "market", "profile", "home-dark"]
pw, ph, gap = 461, 1024, 24
overview = Image.new("RGB", (pw * 5 + gap * 6, ph + gap * 2), (243, 243, 245))
mask = Image.new("L", (pw, ph), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, pw - 1, ph - 1), radius=36, fill=255)
for i, name in enumerate(names):
    screen = Image.open(shots / f"{name}.png").convert("RGB").resize((pw, ph), Image.LANCZOS)
    overview.paste(screen, (gap + i * (pw + gap), gap), mask)
changed += save(overview, out / "overview.png")
print(f"{len(list(shots.glob('*.png')))} screenshots, {changed} pictures changed")
PY
fi

# --- the video ------------------------------------------------------------------------------
if [ -z "${SKIP_VIDEO:-}" ]; then
  say "Recording the market video (dark), one phone at a time"
  night yes
  # The emulator now and then stalls mid-take; the video script refuses such a take, and then
  # both sides are recorded again.
  for attempt in 1 2 3; do
    sleep 5
    run "app.shining.screenshots.MarketFlowTest#recordSeller"
    sleep 5
    run "app.shining.screenshots.MarketFlowTest#recordBuyer"
    adb pull /sdcard/Download/market-seller.mp4 "$WORK/" >/dev/null
    adb pull /sdcard/Download/market-buyer.mp4 "$WORK/" >/dev/null
    if python3 "$OUT/make_market_flow_video.py" "$WORK/market-seller.mp4" "$WORK/market-buyer.mp4" "$OUT/market-flow.mp4"; then
      break
    fi
    [ "$attempt" = 3 ] && { echo "Three takes in a row stalled; is the emulator on the GPU?" >&2; exit 1; }
    echo "That take stalled; recording again ($attempt of 3)."
  done
fi

say "Done. What changed:"
git -C "$ROOT" status --short -- "$OUT"
