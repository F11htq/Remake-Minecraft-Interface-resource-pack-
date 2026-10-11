#!/usr/bin/env python3
"""Night-city colour grade for the Minecraft 1.21.4 title panorama.

Usage:
    python3 tools/build_panoramas.py --source <dir>

<dir> holds the vanilla panorama_0..5.png from the 1.21.4 client jar
(assets/minecraft/textures/gui/title/background/). The graded faces are
written into the resource pack.
"""

import argparse
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK_PANORAMA = os.path.join(ROOT, "assets", "minecraft", "textures", "gui", "title", "background")


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def grade(img):
    """Split-tone: teal-navy shadows, crimson mids, neon-yellow highlights,
    slightly desaturated, like a city at night."""
    a = np.asarray(img.convert("RGB")).astype(np.float32) / 255
    lum = (a @ np.array([0.299, 0.587, 0.114], np.float32))[..., None]
    sat = lum + (a - lum) * 0.85
    shadow = np.array([0.04, 0.10, 0.22], np.float32)
    mid = np.array([0.55, 0.10, 0.28], np.float32)
    high = np.array([1.0, 0.83, 0.25], np.float32)
    t1 = smooth(0.0, 0.45, lum)
    t2 = smooth(0.45, 0.95, lum)
    tint = shadow * (1 - t1) + mid * t1
    tint = tint * (1 - t2) + high * t2
    soft = np.where(tint < 0.5, sat - (1 - 2 * tint) * sat * (1 - sat),
                    sat + (2 * tint - 1) * (np.sqrt(sat) - sat))
    out = sat * 0.4 + soft * 0.6
    out = smooth(0.0, 1.0, out) * 0.35 + out * 0.65
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", required=True)
    args = ap.parse_args()

    os.makedirs(PACK_PANORAMA, exist_ok=True)
    for i in range(6):
        g = grade(Image.open(os.path.join(args.source, "panorama_%d.png" % i)))
        g.save(os.path.join(PACK_PANORAMA, "panorama_%d.png" % i), optimize=True)
    print("graded panorama written to", PACK_PANORAMA)


if __name__ == "__main__":
    main()
