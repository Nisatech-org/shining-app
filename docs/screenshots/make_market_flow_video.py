"""Puts MarketFlowTest's two screen recordings side by side as docs/screenshots/market-flow.mp4.

Usage: python3 make_market_flow_video.py SELLER.mp4 BUYER.mp4 OUT.mp4
Each recording starts with a few black seconds; the script starts each one where the black ends,
which is time 0 of both scripts, so the two phones stay in step. Buyer on the left, seller on the
right, each named above its phone (in the app's font, Vazirmatn). Needs Pillow (with raqm, for
Persian text) and ffmpeg.
"""
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

LENGTH = 27.5  # seconds: the scripts end at 27.0
# Full size: the phones as the emulator records them (1080x2400), so text stays sharp.
PHONE_W, PHONE_H, GAP, MARGIN = 1080, 2400, 96, 112
LABEL_H = 168  # room above the phones for their names
BACKGROUND, BORDER, LABEL = (24, 24, 27), (58, 58, 64), (236, 236, 240)
FONT = Path(__file__).resolve().parents[2] / "app/src/main/res/font/vazirmatn_bold.ttf"
# Left to right: which recording, and its name.
PHONES = [("buyer", "خریدار"), ("seller", "فروشنده")]


def black_end(video: Path) -> float:
    """When the black lead-in ends, judged on the middle of the screen (the status bar isn't black)."""
    out = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", str(video), "-vf",
         "crop=600:600:240:900,blackdetect=d=0.3:pix_th=0.05", "-an", "-f", "null", "-"],
        capture_output=True, text=True,
    ).stderr
    found = re.search(r"black_end:([0-9.]+)", out)
    if not found:
        raise SystemExit(f"No black lead-in in {video}")
    end = float(found.group(1))
    # The tests show 3 s of black; much more means the emulator stalled and the take is off.
    if end > 5:
        raise SystemExit(f"{video}: the black lead-in lasts {end:.1f} s; record it again")
    # A freeze in the middle pushes the last steps later, so the take runs past the script.
    duration = float(subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(video)],
        capture_output=True, text=True, check=True,
    ).stdout)
    if duration - end > LENGTH + 4:
        raise SystemExit(f"{video}: the take runs {duration - end:.1f} s, past the script; record it again")
    return end


def frame_images(work: Path) -> tuple[Path, Path]:
    """The background with the two phone outlines and their names, and the rounded mask for a screen."""
    width, height = MARGIN * 2 + PHONE_W * 2 + GAP, MARGIN * 2 + LABEL_H + PHONE_H
    background = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(background)
    font = ImageFont.truetype(str(FONT), 92, layout_engine=ImageFont.Layout.RAQM)
    top = MARGIN + LABEL_H
    for column, (_, name) in enumerate(PHONES):
        x = MARGIN + column * (PHONE_W + GAP)
        draw.text((x + PHONE_W / 2, MARGIN + LABEL_H / 2 - 16), name, font=font, fill=LABEL, anchor="mm", direction="rtl")
        draw.rounded_rectangle((x - 6, top - 6, x + PHONE_W + 5, top + PHONE_H + 5), radius=94, fill=BORDER)
    mask = Image.new("L", (PHONE_W, PHONE_H), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, PHONE_W - 1, PHONE_H - 1), radius=88, fill=255)
    background.save(work / "background.png")
    mask.save(work / "mask.png")
    return work / "background.png", work / "mask.png"


def main(seller: Path, buyer: Path, out: Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        background, mask = frame_images(Path(tmp))
        def phone(start: float) -> str:
            # Trimmed in the filter, not with -ss: the recordings have a frame only when the
            # screen changes, and seeking in them isn't exact. Start a moment early so the first
            # frame isn't lost to rounding, and shift by the exact start so both clocks agree.
            # That puts the first frame a moment after 0, where plain fps=30 would start, and the
            # video's first frame (its thumbnail) would show empty phones: start_time=0 repeats
            # the first frame back to 0.
            start -= 0.02
            return (
                f"trim=start={start:.3f},setpts=PTS-{start:.3f}/TB,fps=30:start_time=0,scale={PHONE_W}:{PHONE_H}:flags=lanczos,"
                f"tpad=stop_mode=clone:stop_duration={LENGTH},trim=duration={LENGTH},format=rgba"
            )

        recordings = {"seller": seller, "buyer": buyer}
        left, right = (recordings[role] for role, _ in PHONES)
        top = MARGIN + LABEL_H
        filters = ";".join([
            f"[0:v]{phone(black_end(left))}[l]",
            f"[1:v]{phone(black_end(right))}[r]",
            "[3:v]format=gray,split[m1][m2]",
            "[l][m1]alphamerge[lr]",
            "[r][m2]alphamerge[rr]",
            f"[2:v][lr]overlay={MARGIN}:{top}[t]",
            f"[t][rr]overlay={MARGIN + PHONE_W + GAP}:{top},format=yuv420p[out]",
        ])
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error",
             "-i", str(left),
             "-i", str(right),
             "-loop", "1", "-i", str(background),
             "-loop", "1", "-i", str(mask),
             "-filter_complex", filters, "-map", "[out]", "-t", str(LENGTH),
             "-r", "30", "-c:v", "libx264", "-crf", "18", "-preset", "slow", "-tune", "animation", "-movflags", "+faststart", str(out)],
            check=True,
        )


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
