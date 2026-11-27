#!/usr/bin/env python3
"""Lossless PNG recompression for the pack and the mod (pip install pyoxipng).

Every pixel stays exactly the same (checked after each file); only the
encoding gets smaller, so the game reads less from disk while loading.
Transparent pixels keep their colour, so mipmapped textures look identical.

Usage:
    python3 tools/optimize_png.py [paths...]   (default: pack assets + mod assets)
"""

import os
import sys

from PIL import Image, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT = [os.path.join(ROOT, "assets"), os.path.join(ROOT, "mod", "src", "main", "resources"),
           os.path.join(ROOT, "pack.png")]


def pngs(paths):
    for p in paths:
        if os.path.isfile(p):
            yield p
        for base, _, files in os.walk(p):
            for f in sorted(files):
                if f.endswith(".png"):
                    yield os.path.join(base, f)


def same_pixels(a, b):
    a = a.convert("RGBA")
    b = b.convert("RGBA")
    return a.size == b.size and ImageChops.difference(a, b).getbbox() is None


def main():
    try:
        import oxipng
    except ImportError:
        print("pyoxipng not installed, skipping PNG optimisation (pip install pyoxipng)")
        return
    before = after = 0
    for path in pngs(sys.argv[1:] or DEFAULT):
        with open(path, "rb") as f:
            data = f.read()
        out = oxipng.optimize_from_memory(data, level=4, strip=oxipng.StripChunks.safe())
        before += len(data)
        if len(out) < len(data):
            import io
            if same_pixels(Image.open(io.BytesIO(data)), Image.open(io.BytesIO(out))):
                with open(path, "wb") as f:
                    f.write(out)
                data = out
            else:
                print("pixel mismatch, kept original:", path)
        after += len(data)
    print("PNG: %d KB -> %d KB" % (before // 1024, after // 1024))


if __name__ == "__main__":
    main()
