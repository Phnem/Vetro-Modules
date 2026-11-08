"""
Record the Vetro Modules demo app on a real phone and turn the recordings into the README videos.

  python record_device.py record  <glass|motion> <dark|light> <clips_dir> [--serial XXXX]
  python record_device.py combine <glass|motion> <clips_dir> <out_base> [--webp-width 1000] [--webp-fps 24]

record : launches the demo, screen-records it with `adb shell screenrecord`, finds the loop-start marker the demo
         draws (a magenta dot at the left edge during the first 50 ms of every loop), cuts EXACTLY one loop from
         marker to marker, crops the marker's edge and re-times it to constant 60 fps -> <clips_dir>/<scene>_<theme>.mp4
combine: puts the dark and light clips side by side with a caption strip -> <out_base>.mp4 (master) + <out_base>.webp
         (looping preview for the README; GitHub strips autoplay/loop from <video>).

Needs: adb, Python 3, `pip install imageio-ffmpeg pillow numpy`. The phone must be unlocked and plugged in.
"""
import argparse
import math
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import imageio_ffmpeg
import numpy as np

FF = imageio_ffmpeg.get_ffmpeg_exe()
PKG = "dev.vetro.modules.demo"
LOOP = {"glass": 8.0, "motion": 12.0}
REMOTE = "/sdcard/vetro_modules_rec.mp4"
FPS = 60
MARKER = (2, 1198)           # where the demo draws the marker (device px): left edge, vertical centre, ~11 px square
CROP_LEFT = 12               # px removed from the left edge of every clip (the marker sits there)
W, H = 1080, 2400

CAPTIONS = {
    "glass": [
        (0.0, 8.0, "Frosted glass", "recorded on a real phone · Android 16 · 120 Hz · dark | light"),
    ],
    "motion": [
        (0.2, 4.4, "Window from a dock icon", "scaleToBounds · enter ζ 0.92 T 0.46 s · exit ζ 1.0"),
        (4.5, 7.5, "Popover “…”", "menuPop ζ 0.72 · scale 0.82→1 · exit ζ 1.0"),
        (7.6, 11.9, "Notification stack", "arrive ζ 0.80 · fling ζ 1.0 · reflow ζ 0.72 +8 %"),
    ],
}


def adb(serial: str, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True)


def first_device() -> str:
    out = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout.splitlines()[1:]
    for line in out:
        parts = line.split()
        if len(parts) == 2 and parts[1] == "device":
            return parts[0]
    sys.exit("no adb device")


def marker_runs(video: Path) -> list[float]:
    """Video times (s) of the first frame of every marker run."""
    raw = subprocess.run(
        [FF, "-loglevel", "error", "-i", str(video), "-vf", f"crop=4:4:{MARKER[0]}:{MARKER[1]}", "-f", "rawvideo",
         "-pix_fmt", "rgb24", "-fps_mode", "passthrough", "-"], capture_output=True).stdout
    frames = np.frombuffer(raw, np.uint8).reshape(-1, 4, 4, 3).astype(int)
    mean = frames.reshape(len(frames), -1, 3).mean(axis=1)
    is_marker = (mean[:, 0] > 190) & (mean[:, 1] < 90) & (mean[:, 2] > 190)
    info = subprocess.run([FF, "-i", str(video), "-vf", "showinfo", "-f", "null", "-", "-fps_mode", "passthrough"],
                          capture_output=True, text=True).stderr
    times = {int(m.group(1)): float(m.group(2)) for m in re.finditer(r"n:\s*(\d+)\s+pts:\s*-?\d+\s+pts_time:([\d.]+)", info)}
    starts = [i for i in range(len(is_marker)) if is_marker[i] and (i == 0 or not is_marker[i - 1])]
    return [times[i] for i in starts if i in times]


def record(a: argparse.Namespace) -> None:
    serial = a.serial or first_device()
    loop = LOOP[a.scene]
    out_dir = Path(a.clips_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb(serial, "shell", "wm", "dismiss-keyguard")
    adb(serial, "shell", "am", "force-stop", PKG)
    adb(serial, "shell", "rm", "-f", REMOTE)

    limit = math.ceil(4.0 + 2 * loop) + 3
    rec = subprocess.Popen(["adb", "-s", serial, "shell", "screenrecord", "--size", f"{W}x{H}", "--bit-rate", "50000000",
                            "--time-limit", str(limit), REMOTE], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    adb(serial, "shell", "am", "start", "-n", f"{PKG}/.MainActivity", "--es", "scene", a.scene, "--es", "theme", a.theme)
    rec.wait()
    time.sleep(1.0)
    raw = out_dir / f"{a.scene}_{a.theme}_raw.mp4"
    adb(serial, "pull", REMOTE, str(raw))
    adb(serial, "shell", "rm", "-f", REMOTE)

    starts = marker_runs(raw)
    usable = [t for t in starts if t >= 4.0]
    if not usable:
        sys.exit(f"loop marker not found in {raw} (markers at {starts})")
    t0 = usable[0]
    frames = int(round(loop * FPS))
    clip = out_dir / f"{a.scene}_{a.theme}.mp4"
    subprocess.run([FF, "-y", "-loglevel", "error", "-ss", f"{t0:.4f}", "-i", str(raw),
                    "-vf", f"crop={W - CROP_LEFT}:{H}:{CROP_LEFT}:0,fps={FPS}", "-frames:v", str(frames),
                    "-c:v", "libx264", "-preset", "slow", "-crf", "10", "-pix_fmt", "yuv420p", str(clip)], check=True)
    print(f"{clip}  {clip.stat().st_size / 1048576:.1f} MB  (marker at {t0:.3f}s, markers {starts})")


def combine(a: argparse.Namespace) -> None:
    clips = Path(a.clips_dir)
    out_base = Path(a.out_base)
    out_base.parent.mkdir(parents=True, exist_ok=True)
    dark, light = clips / f"{a.scene}_dark.mp4", clips / f"{a.scene}_light.mp4"
    pw, ph, gap, strip = 810, 1800, 24, 130
    with tempfile.TemporaryDirectory() as tmp:
        tmpd = Path(tmp)
        filters = [
            f"[0:v]scale={pw}:{ph}:flags=lanczos[l]", f"[1:v]scale={pw}:{ph}:flags=lanczos[r]",
            f"[l]pad=iw+{gap}:ih:0:0:color=0x111114[lp]", "[lp][r]hstack=inputs=2[row]",
            f"[row]pad=iw:ih+{strip}:0:{strip}:color=0x111114[v0]",
        ]
        last = "v0"
        for i, (t0, t1, title, sub) in enumerate(CAPTIONS[a.scene]):
            (tmpd / f"t{i}.txt").write_text(title, encoding="utf-8")
            (tmpd / f"s{i}.txt").write_text(sub, encoding="utf-8")
            font = "C\\:/Windows/Fonts/segoeuib.ttf"
            fontr = "C\\:/Windows/Fonts/segoeui.ttf"
            en = f"between(t\\,{t0}\\,{t1})"
            filters.append(f"[{last}]drawtext=fontfile='{font}':textfile=t{i}.txt:expansion=none:fontcolor=white:fontsize=40:x=30:y=26:enable='{en}'[a{i}]")
            filters.append(f"[a{i}]drawtext=fontfile='{fontr}':textfile=s{i}.txt:expansion=none:fontcolor=0xB8B8BE:fontsize=28:x=30:y=76:enable='{en}'[b{i}]")
            last = f"b{i}"
        graph = ";".join(filters)
        mp4 = out_base.with_suffix(".mp4")
        subprocess.run([FF, "-y", "-loglevel", "error", "-i", str(dark.resolve()), "-i", str(light.resolve()),
                        "-filter_complex", graph, "-map", f"[{last}]", "-c:v", "libx264", "-preset", "slow", "-crf", "17",
                        "-pix_fmt", "yuv420p", "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
                        "-movflags", "+faststart", "-an", str(mp4.resolve())], check=True, cwd=tmp)
        print(f"{mp4}  {mp4.stat().st_size / 1048576:.2f} MB")

        webp = out_base.with_suffix(".webp")
        subprocess.run([FF, "-y", "-loglevel", "error", "-i", str(mp4.resolve()),
                        "-vf", f"fps={a.webp_fps},scale={a.webp_width}:-1:flags=lanczos",
                        "-c:v", "libwebp", "-lossless", "0", "-q:v", str(a.webp_quality), "-compression_level", "6",
                        "-loop", "0", "-an", str(webp.resolve())], check=True)
        print(f"{webp}  {webp.stat().st_size / 1048576:.2f} MB")


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("record")
    r.add_argument("scene", choices=list(LOOP))
    r.add_argument("theme", choices=["dark", "light"])
    r.add_argument("clips_dir")
    r.add_argument("--serial")
    c = sub.add_parser("combine")
    c.add_argument("scene", choices=list(LOOP))
    c.add_argument("clips_dir")
    c.add_argument("out_base")
    c.add_argument("--webp-width", type=int, default=860)
    c.add_argument("--webp-fps", type=int, default=20)
    c.add_argument("--webp-quality", type=int, default=66)
    a = ap.parse_args()
    record(a) if a.cmd == "record" else combine(a)


if __name__ == "__main__":
    main()
