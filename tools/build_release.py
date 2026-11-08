"""
Pack every module folder into dist/<name>.zip containing exactly two files at the archive root:
SKILL.md and GUIDE.txt.

  python tools/build_release.py
"""
import hashlib
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
MODULES = ["frostedglass", "animation", "download"]
FILES = ["SKILL.md", "GUIDE.txt"]
FIXED_TIME = (2026, 11, 8, 0, 0, 0)  # reproducible archives


def main() -> int:
    DIST.mkdir(exist_ok=True)
    for name in MODULES:
        folder = ROOT / name
        missing = [f for f in FILES if not (folder / f).is_file()]
        if missing:
            print(f"{name}: missing {missing}", file=sys.stderr)
            return 1
        out = DIST / f"{name}.zip"
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for f in FILES:
                info = zipfile.ZipInfo(f, FIXED_TIME)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                z.writestr(info, (folder / f).read_bytes())
        digest = hashlib.sha256(out.read_bytes()).hexdigest()
        print(f"{out.name:20s} {out.stat().st_size / 1024:7.1f} KB  sha256 {digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
