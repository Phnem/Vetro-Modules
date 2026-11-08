"""
Render an HTML scene that exposes window.renderAt(t) into lossless frames, then encode:
  - MP4  (H.264, CRF 16, bt709, +faststart)      -> full quality, 60 fps
  - WebP (animated, lossy q~82, loops forever)   -> what the README shows (GitHub strips autoplay/loop on <video>)

  python render_video.py capture <page.html> <frames_dir> [--query theme=dark] [--seconds 8] [--fps 60]
                                 [--viewport 390x760] [--scale 2]
  python render_video.py encode  <out_base> <frames_dir> [<frames_dir2>] [--fps 60] [--webp-fps 30]
                                 [--webp-width 960] [--webp-quality 82] [--gap 24]

Two frame dirs are put side by side (first | second). The scene is a pure function of time and
renderAt(T) == renderAt(0), so the file loops without a visible seam.
"""
import argparse
import subprocess
import sys
from pathlib import Path

import imageio_ffmpeg


def capture(a: argparse.Namespace) -> None:
    from playwright.sync_api import sync_playwright

    vw, vh = (int(x) for x in a.viewport.split("x"))
    url = Path(a.page).resolve().as_uri() + (("?" + a.query) if a.query else "")
    out = Path(a.frames_dir)
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("f*.png"):
        old.unlink()
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": vw, "height": vh}, device_scale_factor=a.scale)
        page.goto(url)
        page.evaluate("document.fonts.ready")
        # --seconds 0 (default): use the exact loop length the scene reports, so the file loops without a seam
        seconds = a.seconds or float(page.evaluate("window.LOOP_SECONDS"))
        frames = int(round(seconds * a.fps))
        for i in range(frames):
            page.evaluate(f"renderAt({i / a.fps})")
            page.screenshot(path=str(out / f"f{i:05d}.png"))
        browser.close()
    print(f"{out}: {frames} frames")


def encode(a: argparse.Namespace) -> None:
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    dirs = [Path(d) for d in a.frames_dirs]
    inputs: list[str] = []
    for d in dirs:
        inputs += ["-framerate", str(a.fps), "-i", str(d / "f%05d.png")]
    if len(dirs) == 2:
        graph = f"[0:v]pad=iw+{a.gap}:ih:0:0:color=0x111114[l];[l][1:v]hstack=inputs=2[v]"
    else:
        graph = "[0:v]null[v]"

    base = Path(a.out_base)
    base.parent.mkdir(parents=True, exist_ok=True)
    mp4 = base.with_suffix(".mp4")
    subprocess.run([ff, "-y", "-loglevel", "error", *inputs, "-filter_complex",
                    graph + ";[v]scale=out_color_matrix=bt709:out_range=tv,format=yuv420p[o]", "-map", "[o]",
                    "-c:v", "libx264", "-preset", "slow", "-crf", "16",
                    "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
                    "-movflags", "+faststart", "-an", str(mp4)], check=True)
    print(f"{mp4}  {mp4.stat().st_size / 1048576:.2f} MB")

    webp = base.with_suffix(".webp")
    step = max(1, round(a.fps / a.webp_fps))
    subprocess.run([ff, "-y", "-loglevel", "error", *inputs, "-filter_complex",
                    graph + f";[v]select=not(mod(n\\,{step})),setpts=N/({a.fps}/{step})/TB,"
                            f"scale={a.webp_width}:-1:flags=lanczos[o]", "-map", "[o]",
                    "-r", str(a.fps // step), "-c:v", "libwebp", "-lossless", "0", "-q:v", str(a.webp_quality),
                    "-compression_level", "6", "-loop", "0", "-an", str(webp)], check=True)
    print(f"{webp}  {webp.stat().st_size / 1048576:.2f} MB")


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("capture")
    c.add_argument("page")
    c.add_argument("frames_dir")
    c.add_argument("--query", default="")
    c.add_argument("--seconds", type=float, default=0.0, help="0 = window.LOOP_SECONDS of the scene")
    c.add_argument("--fps", type=int, default=60)
    c.add_argument("--viewport", default="390x760")
    c.add_argument("--scale", type=float, default=2.0)
    e = sub.add_parser("encode")
    e.add_argument("out_base")
    e.add_argument("frames_dirs", nargs="+")
    e.add_argument("--fps", type=int, default=60)
    e.add_argument("--webp-fps", type=int, default=30)
    e.add_argument("--webp-width", type=int, default=960)
    e.add_argument("--webp-quality", type=int, default=82)
    e.add_argument("--gap", type=int, default=24)
    a = ap.parse_args()
    capture(a) if a.cmd == "capture" else encode(a)


if __name__ == "__main__":
    sys.exit(main())
