#!/usr/bin/env python3
"""Night-city colour grade for the official Minecraft title panoramas.

Usage:
    python3 tools/build_panoramas.py --source <dir>

<dir> holds one sub-folder per Minecraft version (1.13, 1.14, ... 1.21.4),
each with that version's panorama_0..5.png from the client jar
(assets/minecraft/textures/gui/title/background/).

Writes:
  * FancyMenu panoramas to fancymenu-setup/config/fancymenu/panoramas/
    (picked at random on the title screen by the FancyMenu layouts);
  * the graded 1.21.4 panorama into the resource pack, so the title screen
    gets the same look even without mods.
"""

import argparse
import os
import shutil

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FM_PANORAMAS = os.path.join(ROOT, "fancymenu-setup", "config", "fancymenu", "panoramas")
PACK_PANORAMA = os.path.join(ROOT, "assets", "minecraft", "textures", "gui", "title", "background")

# version -> (folder id, rotation speed)
PANORAMAS = {
    "1.13": ("ocean", 0.8),
    "1.14": ("village", 1.0),
    "1.15": ("bees", 1.0),
    "1.16": ("nether", 1.0),
    "1.17": ("caves", 0.9),
    "1.18": ("peaks", 1.0),
    "1.19": ("swamp", 1.0),
    "1.20": ("cherry", 1.0),
    "1.21": ("trial_chambers", 0.9),
    "1.21.4": ("pale_garden", 0.9),
}
PACK_VERSION = "1.21.4"


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

    overlay = os.path.join(PACK_PANORAMA, "panorama_overlay.png")
    if os.path.isdir(FM_PANORAMAS):
        shutil.rmtree(FM_PANORAMAS)
    for version, (key, speed) in PANORAMAS.items():
        name = "cyber_" + key
        out_dir = os.path.join(FM_PANORAMAS, name)
        img_dir = os.path.join(out_dir, "panorama")
        os.makedirs(img_dir)
        for i in range(6):
            src = os.path.join(args.source, version, "panorama_%d.png" % i)
            g = grade(Image.open(src))
            g.save(os.path.join(img_dir, "panorama_%d.png" % i), optimize=True)
            if version == PACK_VERSION:
                g.save(os.path.join(PACK_PANORAMA, "panorama_%d.png" % i), optimize=True)
        if os.path.isfile(overlay):
            shutil.copy(overlay, os.path.join(out_dir, "overlay.png"))
        with open(os.path.join(out_dir, "properties.txt"), "w") as f:
            f.write("type = panorama\n\npanorama-meta {\n  name = %s\n  speed = %s\n  fov = 85.0\n"
                    "  angle = 25.0\n}\n" % (name, speed))
        print(name)


if __name__ == "__main__":
    main()
