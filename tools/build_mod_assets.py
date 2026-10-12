#!/usr/bin/env python3
"""Builds the textures bundled in the CYBER//UI Fabric mod.

Usage:
    python3 tools/build_mod_assets.py --panoramas <dir>

<dir> holds a sub-folder "1.21.4" with panorama_0..5.png from the 1.21.4
client jar. Run tools/build_pack.py
first: the loading-screen logo is made from the pack's re-coloured logo.

Writes into mod/src/main/resources/assets/cyberui/.
"""

import argparse
import os
import random

from PIL import Image

import build_pack as bp
import build_panoramas as bpano
from build_pack import BLACK, YELLOW, RED, CYAN, rgba, new, rect, frame_rect

ROOT = bp.ROOT
OUT = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "cyberui")
TEX = os.path.join(OUT, "textures", "gui")

# version -> folder name of the panorama bundled in the mod
PANORAMAS = {"1.21.4": "pale_garden"}

FONT = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110",
    "E": "111100110100111", "F": "111100110100100", "G": "011100101101011", "H": "101101111101101",
    "I": "111010010010111", "J": "001001001101010", "K": "101101110101101", "L": "100100100100111",
    "M": "101111111101101", "N": "110101101101101", "O": "010101101101010", "P": "110101110100100",
    "R": "110101110101101", "S": "011100010001110", "T": "111010010010010", "U": "101101101101111",
    "V": "101101101101010", "W": "101101111111101", "Y": "101101010010010", "X": "101101010101101",
    "0": "111101101101111", "1": "010110010010111", "2": "110001010100111", "3": "110001010001110",
    "4": "101101111001001", "5": "111100110001110", "6": "011100111101111", "7": "111001010010010",
    "8": "111101111101111", "9": "111101111001110", "/": "001001010100100", ":": "000010000010000",
    ".": "000000000000010", "%": "101001010100101", "-": "000000111000000", ">": "100010001010100",
    "_": "000000000000111", " ": "000000000000000",
}


def text(img, x, y, s, c):
    for ch in s.upper():
        g = FONT.get(ch, FONT[" "])
        for i, bit in enumerate(g):
            if bit == "1":
                px, py = x + i % 3, y + i // 3
                if 0 <= px < img.width and 0 <= py < img.height:
                    img.putpixel((px, py), c)
        x += 4
    return x


def strip(frames):
    w, h = frames[0].size
    out = new(w, h * len(frames))
    for i, f in enumerate(frames):
        out.paste(f, (0, i * h))
    return out


def save(img, name):
    os.makedirs(TEX, exist_ok=True)
    img.save(os.path.join(TEX, name), optimize=True)


def build_status_panel():
    """140x92 HUD panel, 16 frames stacked vertically."""
    W, H, N = 140, 92, 16
    rng = random.Random(5)
    hexes = ["".join(rng.choice("0123456789ABCDEF") for _ in range(4)) for _ in range(40)]
    frames = []
    for i in range(N):
        img = new(W, H)
        rect(img, 0, 0, W - 1, H - 1, (8, 8, 12, 200))
        for y in range(1, H - 1, 2):
            for x in range(1, W - 1):
                img.putpixel((x, y), (12, 12, 18, 200))
        for (x, y, dx, dy) in ((0, 0, 1, 1), (W - 1, 0, -1, 1), (0, H - 1, 1, -1), (W - 1, H - 1, -1, -1)):
            for k in range(8):
                img.putpixel((x + dx * k, y), rgba(YELLOW))
                img.putpixel((x, y + dy * k), rgba(YELLOW))
        rect(img, 4, 4, 52, 10, rgba(YELLOW))
        text(img, 6, 5, "SYS//STAT", rgba(BLACK))
        rect(img, W - 12, 5, W - 6, 9, rgba(RED if i % 4 < 2 else (90, 0, 20)))
        lines = [("NET", "ONLINE", YELLOW), ("UPLINK", "%d%%" % (92 + (i * 3) % 8), YELLOW),
                 ("TEMP", "%d.%dC" % (36 + i % 3, (i * 7) % 10), YELLOW), ("ICE", "BYPASS", RED)]
        for n, (k, v, c) in enumerate(lines):
            y = 16 + n * 9
            text(img, 6, y, k, rgba((150, 150, 160)))
            for d in range(30, 70, 4):
                img.putpixel((d, y + 4), rgba((70, 70, 80)))
            text(img, 72, y, v, rgba(c))
        y = 54
        frame_rect(img, 6, y, 104, y + 6, rgba(YELLOW))
        for x in range(8, 8 + int(96 * ((i + 1) / N))):
            if (x // 3) % 2 == 0:
                rect(img, x, y + 2, x, y + 4, rgba(YELLOW))
        text(img, 108, y + 1, "%d%%" % int(100 * (i + 1) / N), rgba(YELLOW))
        for r in range(4):
            text(img, 110, 16 + r * 9, hexes[(i + r) % len(hexes)], rgba(CYAN if r == 0 else (0, 110, 120)))
        x = text(img, 6, 70, "> JACK_IN", rgba(YELLOW))
        if i % 4 < 2:
            rect(img, x, 70, x + 2, 74, rgba(YELLOW))
        text(img, 6, 80, "V1.21.4 // CYBER-UI", rgba((90, 90, 100)))
        frames.append(img)
    save(strip(frames), "status_panel.png")


def build_hazard():
    """64x4 hazard stripes, 8 frames stacked vertically (stripes crawl)."""
    frames = []
    for i in range(8):
        img = new(64, 4)
        for y in range(4):
            for x in range(64):
                img.putpixel((x, y), rgba(YELLOW if ((x + y + i) // 4) % 2 == 0 else BLACK))
        frames.append(img)
    save(strip(frames), "hazard.png")


def build_loading_logo():
    gui = bp.GUI
    logo = Image.open(os.path.join(gui, "title", "minecraft.png")).convert("RGBA")
    logo = logo.crop(logo.getbbox())
    ed = Image.open(os.path.join(gui, "title", "edition.png")).convert("RGBA")
    ed = ed.crop(ed.getbbox())
    w = 512
    logo = logo.resize((w, int(logo.height * w / logo.width)), Image.LANCZOS)
    ew = w // 2
    ed = ed.resize((ew, int(ed.height * ew / ed.width)), Image.LANCZOS)
    out = new(w, logo.height + ed.height - 6)
    out.alpha_composite(logo, (0, 0))
    out.alpha_composite(ed, ((w - ew) // 2, logo.height - 8))
    save(out, "loading_logo.png")


def build_panoramas(source):
    for version, name in PANORAMAS.items():
        d = os.path.join(TEX, "panorama", name)
        os.makedirs(d, exist_ok=True)
        for i in range(6):
            g = bpano.grade(Image.open(os.path.join(source, version, "panorama_%d.png" % i)))
            g.save(os.path.join(d, "panorama_%d.png" % i), optimize=True)
        print("panorama", name)


def build_mod_icon():
    """Pack icon with a red "MOD" plate in the bottom-right corner (128x128)."""
    icon = Image.open(os.path.join(ROOT, "pack.png")).convert("RGBA").resize((128, 128), Image.NEAREST)
    small = new(64, 64)
    small.alpha_composite(icon.resize((64, 64), Image.NEAREST))
    # plate: black outline, red fill, yellow "MOD" in the 3x5 font at 2x
    x0, y0, x1, y1 = 33, 48, 62, 61
    rect(small, x0, y0, x1, y1, rgba(BLACK))
    rect(small, x0 + 1, y0 + 1, x1 - 1, y1 - 1, rgba(RED))
    label = new(12, 5)
    text(label, 0, 0, "MOD", rgba(YELLOW))
    label = label.resize((24, 10), Image.NEAREST)
    small.alpha_composite(label, (x0 + 3, y0 + 2))
    out = small.resize((128, 128), Image.NEAREST)
    out.save(os.path.join(OUT, "icon.png"), optimize=True)
    os.makedirs(os.path.join(ROOT, "modrinth"), exist_ok=True)
    out.resize((512, 512), Image.NEAREST).save(os.path.join(ROOT, "modrinth", "mod-icon.png"), optimize=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--panoramas", required=True)
    args = ap.parse_args()
    build_status_panel()
    build_hazard()
    build_loading_logo()
    build_panoramas(args.panoramas)
    build_mod_icon()


if __name__ == "__main__":
    main()
