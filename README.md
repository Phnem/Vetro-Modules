<div align="center">

# Vetro Modules

**Three solutions from the [Vetro](https://github.com/Phnem/Vetro) Android app, packaged as recipes you can lift into your own project.**

Frosted glass · A single table of springs · A chunked, resumable downloader

</div>

---

## What this is (and isn't)

These are **not libraries**. They depend on a particular UI stack, a navigation structure and a lot of taste, so a
Maven artifact would be either useless or a leaky abstraction. Instead every component ships as an archive with
exactly two files:

| File | For whom | What is inside |
| --- | --- | --- |
| `SKILL.md` | people who build with an AI coding agent | a detailed implementation prompt: exact numbers, hard rules, what to build, an acceptance checklist |
| `GUIDE.txt` | people who write the code by hand | the methodology, pinned versions, the full reference code, usage, tuning, troubleshooting |

Every number in both files comes from the shipping app. Nothing is "approximately like".

| Module | Archive | In one line |
| --- | --- | --- |
| [Frosted glass](#1-frosted-glass) | `frostedglass.zip` | 8-layer matte glass for Compose: blur, tint, grain, 1 px rim, highlight, shadow |
| [Motion](#2-motion) | `animation.zip` | one spring table + a window out of a dock icon, a "…" popover, a notification stack |
| [Chunked download](#3-chunked-download) | `download.zip` | multi-connection, resumable, verified downloads — 4.7× faster than one stream on the real release |

Download the archives from the **Releases** page of this repository.

---

## 1. Frosted glass

A matte, diffuse material (as opposed to "liquid" lens glass): it *diffuses* what is under it, so text on top stays
readable over moving covers. Eight layers in a fixed order — live backdrop → local blur → saturation/contrast →
neutral tint (after the blur) → micro-grain → one-physical-pixel rim → inner highlight → wide soft shadow — with
per-role material values for dark and light themes, and a deliberate fallback below Android 12.

<p align="center">
  <img src="assets/video/frostedglass.webp" width="720" alt="Frosted glass: dark theme on the left, light theme on the right">
  <br><sub>dark · light — <a href="assets/video/frostedglass.mp4">full-quality MP4 (60 fps)</a></sub>
</p>

Shown: a dock capsule, round buttons, floating bubbles, a "…" dropdown panel and a notification plate over
scrolling colourful content. Inside the archive: the material table, the structural rules that prevent the glass
from collapsing into a flat fill, and the full `FrostedGlass.kt`.

Depends on `io.github.kyant0:backdrop:1.0.0` (check its licence) · Compose BOM 2025.12.00 · Kotlin 2.4.21 ·
blur needs Android 12+, below that a denser fallback material is used.

## 2. Motion

One table of `(ζ, T)` springs is the only source of truth; UI code never writes `spring(...)` by hand. The three
patterns are compositions of those tokens: a **window growing out of a dock icon** (`sharedBounds` +
`scaleToBounds`, separate enter/exit springs, content revealed after the shell has gained area), a **"…" popover**
(pop from the corner with a controlled overshoot, critically damped close), and a **notification stack**
(arrive / fling / reflow with a cascade that needs no timers).

<p align="center">
  <img src="assets/video/animation.webp" width="720" alt="Motion: dark theme on the left, light theme on the right">
  <br><sub>dark · light — <a href="assets/video/animation.mp4">full-quality MP4 (60 fps)</a></sub>
</p>

The caption on top of the video names the token running at that moment. Inside the archive: the token table with
periods and stiffness, the conversion math, and the code of all three patterns.

## 3. Chunked download

Several connections pull 1 MB chunks from **one shared queue**, so a slow connection owns a chunk, not a share of the
file. Interrupted downloads resume from the first missing chunk, slow connections are replaced mid-chunk, expired
CDN links are re-resolved, a server that ignores `Range` falls back to a single stream, and the file gets its final
name only when size and SHA-256 match.

<p align="center">
  <img src="assets/video/download_benchmark.webp" width="720" alt="Download benchmark: single stream vs split vs chunk queue">
  <br><sub><a href="assets/video/download_benchmark.mp4">full-quality MP4</a></sub>
</p>

Measured on the real v3.4.9-Stable APK (38.4 MB, GitHub CDN), 5 runs per method, methods interleaved, SHA-256
checked every run:

| Method | median | MB/s |
| --- | --- | --- |
| single stream | 47.3 s | 0.77 |
| 6 equal parts (classic split) | 9.8 s | 3.74 |
| **6 connections, chunk queue** | **10.2 s** | **3.61** |

~4.7× faster than one stream. The classic equal split is within noise of the chunk queue on this network — the
speed-up here comes from parallelism; the queue's value is resilience (resume, slow-connection replacement, no
stitching pass). Your network will differ: the harness is in [`tools/bench`](tools/bench), raw numbers in
[`results/download_benchmark_raw.json`](results/download_benchmark_raw.json).

Needs OkHttp 5.4.0 (HTTP/1.1 client — important) and kotlinx-coroutines 1.10.2. The portable version was run
against the real release: interrupted at 12 MB, resumed from 12 MB, verified, no leftovers.

---

## About the videos

The glass and motion videos are **real screen recordings**: the [`demo/`](demo) Android app (Jetpack Compose) uses
exactly the code from the guides (`FrostedGlass.kt`, `MotionTokens.kt`) and plays each scene in a loop by itself;
[`tools/record_device.py`](tools/record_device.py) records it with `adb screenrecord` on a connected phone
(here: Android 16, 1080×2400, 120 Hz), finds the loop-start marker the app draws, cuts exactly one loop and puts
the dark and light recordings side by side. Building the demo is also how the guides were checked: the code in them
compiles and runs as published.

The download graphic is a rendered chart (not a screen recording) that replays the measured byte timelines of the
median runs.

The inline previews are looping animated WebP (full colour, infinite loop; GitHub strips `autoplay`/`loop` from
`<video>` tags, so an MP4 would need a click). The MP4s next to them are the full-quality masters. Every scene ends
exactly where it starts, so it loops without a seam.

## Repository layout

```
frostedglass/   SKILL.md · GUIDE.txt
animation/      SKILL.md · GUIDE.txt
download/       SKILL.md · GUIDE.txt
demo/           Android demo app the glass and motion videos are recorded from
assets/video/   *.webp (README previews) · *.mp4 (full quality)
results/        raw benchmark data
tools/          device recorder, chart renderer, benchmark harness, release packer
dist/           frostedglass.zip · animation.zip · download.zip   (generated; attached to each Release)
```

Rebuild everything:

```bash
# glass + motion: build the demo, install it, record one loop per theme on a connected phone, combine
(cd demo && ./gradlew assembleDebug) && adb install -r demo/app/build/outputs/apk/debug/app-debug.apk
python tools/record_device.py record glass  dark  clips      # then: glass light, motion dark, motion light
python tools/record_device.py combine glass  clips assets/video/frostedglass
python tools/record_device.py combine motion clips assets/video/animation

# download chart (rendered from the measured data)
python tools/render_video.py capture tools/download.html frames/dl --query dpr=2 --fps 30 --viewport 800x450 --scale 2
python tools/render_video.py encode assets/video/download_benchmark frames/dl --fps 30 --webp-fps 15 --webp-width 800 --webp-quality 75

# the three archives
python tools/build_release.py
```

Needs: Android SDK + JDK 21 and a phone with Android 12+ (for the demo), Python 3 with
`pip install imageio-ffmpeg pillow numpy playwright` (+ `playwright install chromium` for the chart only).
The demo's `local.properties` (your SDK path) is not committed.

## Licence

GPL-3.0 — see [LICENSE](LICENSE). You may use, change and share these recipes and code under the terms of the GPL-3.0;
work you distribute that is derived from them must be GPL-3.0 too. If you ship the glass, also respect the licence
of the Kyant backdrop library it builds on.
