#!/usr/bin/env python3
"""Night-city colour grade for the Minecraft 26.3 title panorama.

Usage:
    python3 tools/build_panoramas.py --source <dir>

<dir> holds the vanilla panorama_0..5.png of 26.3. They are not in the client
jar any more (only 1x1 placeholders): tools/fetch_panorama.py downloads them
from the asset index. The graded faces are written into the resource pack.
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


def grade(img, face=None):
    """Turns the daylight panorama into a city at night: the blue sky becomes
    a deep navy-violet with dim clouds, the sun a neon-yellow glow, and the
    scenery is pulled down in exposure and split-toned (teal-navy shadows,
    crimson mids, neon-yellow highlights)."""
    a = np.asarray(img.convert("RGB")).astype(np.float32) / 255
    r, g, b = a[..., 0:1], a[..., 1:2], a[..., 2:3]
    lum = (a @ np.array([0.299, 0.587, 0.114], np.float32))[..., None]
    mx = a.max(axis=-1, keepdims=True)
    mn = a.min(axis=-1, keepdims=True)
    chroma = mx - mn

    # --- scenery: lower exposure, split-tone -------------------------------
    dark = np.power(a, 1.35) * 0.72
    dl = (dark @ np.array([0.299, 0.587, 0.114], np.float32))[..., None]
    sat = dl + (dark - dl) * 1.05
    shadow = np.array([0.03, 0.08, 0.20], np.float32)
    mid = np.array([0.60, 0.08, 0.26], np.float32)
    high = np.array([1.0, 0.85, 0.22], np.float32)
    t1 = smooth(0.0, 0.35, dl)
    t2 = smooth(0.35, 0.80, dl)
    tint = shadow * (1 - t1) + mid * t1
    tint = tint * (1 - t2) + high * t2
    soft = np.where(tint < 0.5, sat - (1 - 2 * tint) * sat * (1 - sat),
                    sat + (2 * tint - 1) * (np.sqrt(np.clip(sat, 0, 1)) - sat))
    scene = sat * 0.45 + soft * 0.55

    # --- sky: blue and bright, or white-grey clouds ------------------------
    blue = smooth(0.04, 0.16, b - r) * smooth(0.35, 0.55, b)
    cloud = smooth(0.80, 0.92, mn) * smooth(0.12, 0.04, chroma)
    sun = smooth(0.97, 1.0, mn)
    # pale haze along the horizon becomes the glow of a distant city
    # (side faces 0-3 only, in a band around the horizon, bluish pixels only,
    # so white blocks and cloud edges are left alone)
    haze = np.zeros_like(lum)
    if face is not None and face < 4:
        rows = np.linspace(0.0, 1.0, a.shape[0], dtype=np.float32)[:, None, None]
        band = np.exp(-((rows - 0.51) / 0.035) ** 2)
        haze = (smooth(0.55, 0.72, mn) * smooth(0.16, 0.06, chroma) * smooth(0.01, 0.05, b - r)
                * band * (1 - cloud) * (1 - sun) * 0.85)
    sky_col = np.array([0.035, 0.04, 0.12], np.float32) + (b - 0.55) * np.array([0.10, 0.02, 0.22], np.float32)
    cloud_col = np.array([0.20, 0.12, 0.26], np.float32) * (0.7 + 0.3 * lum)
    sun_col = np.array([1.0, 0.92, 0.35], np.float32)
    out = scene
    out = out * (1 - blue) + sky_col * blue
    out = out * (1 - cloud) + cloud_col * cloud
    haze_col = np.array([0.62, 0.10, 0.36], np.float32) * (0.55 + 0.45 * lum)
    out = out * (1 - haze) + haze_col * haze
    out = out * (1 - sun) + sun_col * sun
    out = smooth(0.0, 1.0, np.clip(out, 0, 1)) * 0.3 + out * 0.7
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", required=True)
    args = ap.parse_args()

    os.makedirs(PACK_PANORAMA, exist_ok=True)
    for i in range(6):
        g = grade(Image.open(os.path.join(args.source, "panorama_%d.png" % i)), i)
        g.save(os.path.join(PACK_PANORAMA, "panorama_%d.png" % i), optimize=True)
    print("graded panorama written to", PACK_PANORAMA)


if __name__ == "__main__":
    main()
