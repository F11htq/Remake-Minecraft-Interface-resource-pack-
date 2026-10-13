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
import math
import os
import random

from PIL import Image

import build_pack as bp
import build_panoramas as bpano
from build_pack import BLACK, YELLOW, RED, CYAN, WHITE, rgba, new, rect, frame_rect, lerp

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


# --------------------------------------------------------------------------
# Boss bar frames: ornate per-boss compositions (256x64, bar slot at x 37..218, y 30..34)
# --------------------------------------------------------------------------
from PIL import ImageDraw

FRAME_W, FRAME_H = 256, 64
BAR_X, BAR_Y, BAR_W, BAR_H = 37, 30, 182, 5


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def poly(img, pts, fill, outline=None):
    layer = new(img.width, img.height)
    d = ImageDraw.Draw(layer)
    d.polygon(pts, fill=fill, outline=outline)
    img.alpha_composite(layer)


def line(img, pts, color, width=1):
    layer = new(img.width, img.height)
    ImageDraw.Draw(layer).line(pts, fill=color, width=width)
    img.alpha_composite(layer)


def shade_noise(img, box, color, seed, density=0.18):
    """Speckle texture inside the opaque pixels of a box."""
    rng = random.Random(seed)
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < img.width and 0 <= y < img.height and img.getpixel((x, y))[3] > 0:
                if rng.random() < density:
                    img.putpixel((x, y), color)


def casing(img, rim, body, accent, light):
    """Thick bevelled casing around the bar slot with a neon rim."""
    x0, y0, x1, y1 = BAR_X - 5, BAR_Y - 4, BAR_X + BAR_W + 4, BAR_Y + BAR_H + 3
    poly(img, [(x0 + 3, y0), (x1, y0), (x1, y1 - 3), (x1 - 3, y1), (x0, y1), (x0, y0 + 3)], rgba(body, 245))
    line(img, [(x0 + 3, y0), (x1, y0)], rgba(light))
    line(img, [(x0, y0 + 3), (x0, y1)], rgba(light))
    line(img, [(x0, y1), (x1 - 3, y1)], rgba(lerp(body, BLACK, 0.6)))
    line(img, [(x1, y0), (x1, y1 - 3)], rgba(lerp(body, BLACK, 0.6)))
    line(img, [(x0 + 3, y0), (x0, y0 + 3)], rgba(rim))
    line(img, [(x1, y1 - 3), (x1 - 3, y1)], rgba(rim))
    # inner neon rim + slot
    rect(img, BAR_X - 2, BAR_Y - 2, BAR_X + BAR_W + 1, BAR_Y + BAR_H + 1, rgba(rim))
    rect(img, BAR_X - 1, BAR_Y - 1, BAR_X + BAR_W, BAR_Y + BAR_H, rgba((4, 4, 7)))
    # rivets
    for x in range(x0 + 10, x1 - 6, 22):
        px(img, x, y0 + 1, rgba(light))
        px(img, x, y1 - 1, rgba(lerp(light, BLACK, 0.4)))
    for y in range(BAR_Y, BAR_Y + BAR_H):
        px(img, x0 + 2, y, rgba(accent))


def cyber_tag(img, code, neon):
    """Cyberpunk detail: circuit trace and a red ID tag under the casing."""
    y = BAR_Y + BAR_H + 4
    for t in range(16):
        px(img, BAR_X + 4 + t, y + 1, rgba(neon, 210))
    for t in range(4):
        px(img, BAR_X + 19, y + 1 + t, rgba(neon, 210))
    px(img, BAR_X + 19, y + 5, rgba(YELLOW))
    tx = BAR_X + BAR_W - 24
    rect(img, tx, y + 1, tx + 21, y + 7, rgba(RED))
    line(img, [(tx, y + 1), (tx + 21, y + 1)], rgba((255, 130, 150)))
    text(img, tx + 2, y + 2, code, rgba(BLACK))


def mirror(img, draw):
    left = new(FRAME_W, FRAME_H)
    draw(left)
    img.alpha_composite(left)
    img.alpha_composite(left.transpose(Image.FLIP_LEFT_RIGHT))


# ---------------------------------------------------------------- dragon
def frame_dragon():
    img = new(FRAME_W, FRAME_H)
    mem_dark, mem_light, bone, rim = (34, 8, 52), (92, 22, 128), (196, 150, 230), (220, 70, 255)

    def wing(im):
        shoulder = (44, 30)
        tips = [(2, 4), (12, 1), (24, 2), (33, 9)]
        lows = [(3, 22), (13, 30), (23, 34), (32, 38)]
        # membrane between fingers, scalloped trailing edge
        pts = [shoulder] + tips + [(40, 40)]
        pts = [shoulder, tips[0]]
        for k in range(len(tips)):
            pts.append(lows[k])
            if k + 1 < len(tips):
                pts.append(tips[k + 1])
        pts.append((42, 40))
        poly(im, pts, rgba(mem_dark, 240))
        # lighter membrane near the bones
        for k, tip in enumerate(tips):
            poly(im, [shoulder, tip, ((tip[0] + lows[k][0]) // 2 + 2, (tip[1] + lows[k][1]) // 2)],
                 rgba(mem_light, 200))
        for tip in tips:
            line(im, [shoulder, tip], rgba(bone), 1)
            px(im, tip[0], tip[1], rgba((255, 240, 255)))
        # claw at the wrist
        poly(im, [(30, 8), (36, 2), (34, 10)], rgba(bone))
        # scalloped edge glow
        for k in range(len(lows)):
            a = tips[k]
            b = lows[k]
            line(im, [a, b], rgba(rim, 150))
        # end crystal floating near the tip
        cx, cy = 8, 44
        poly(im, [(cx, cy - 5), (cx + 5, cy), (cx, cy + 5), (cx - 5, cy)], rgba((255, 120, 230), 230), rgba((255, 230, 255)))
        px(im, cx, cy, rgba(WHITE))
        rect(im, cx - 1, cy + 7, cx + 1, cy + 12, rgba((60, 30, 70)))
    mirror(img, wing)

    casing(img, rim, (22, 10, 30), (255, 80, 220), (120, 60, 160))
    # spikes along the top of the casing
    for x in range(BAR_X + 4, BAR_X + BAR_W - 2, 10):
        poly(img, [(x, BAR_Y - 4), (x + 3, BAR_Y - 9), (x + 6, BAR_Y - 4)], rgba((60, 22, 80)), rgba((150, 60, 200)))

    # dragon head facing the player, resting on top of the casing
    cx = FRAME_W // 2
    head = [(cx, 27), (cx - 5, 25), (cx - 8, 19), (cx - 11, 12), (cx - 10, 6), (cx - 5, 3),
            (cx + 5, 3), (cx + 10, 6), (cx + 11, 12), (cx + 8, 19), (cx + 5, 25)]
    # horns
    poly(img, [(cx - 8, 5), (cx - 24, 0), (cx - 5, 3)], rgba((70, 50, 80)), rgba((180, 150, 200)))
    poly(img, [(cx + 8, 5), (cx + 24, 0), (cx + 5, 3)], rgba((70, 50, 80)), rgba((180, 150, 200)))
    poly(img, head, rgba((24, 14, 32)), rgba((120, 70, 160)))
    # brow ridge and snout plates
    line(img, [(cx - 10, 8), (cx - 3, 11)], rgba((90, 60, 110)))
    line(img, [(cx + 10, 8), (cx + 3, 11)], rgba((90, 60, 110)))
    for y in range(15, 25, 3):
        w = max(1, 6 - (y - 15) // 3)
        line(img, [(cx - w, y), (cx + w, y)], rgba((40, 26, 52)))
    shade_noise(img, (cx - 11, 3, cx + 12, 27), rgba((34, 22, 44)), 3, 0.2)
    # glowing eyes with a neon trail
    for ex in (cx - 7, cx + 4):
        rect(img, ex, 11, ex + 2, 12, rgba((255, 60, 230)))
        px(img, ex + 1, 11, rgba(WHITE))
        for d in range(1, 5):
            px(img, ex - d if ex < cx else ex + 2 + d, 11, rgba((255, 60, 230), 200 - d * 40))
    # nostrils + fangs hanging over the casing
    px(img, cx - 2, 23, rgba((255, 60, 230)))
    px(img, cx + 2, 23, rgba((255, 60, 230)))
    for x in (cx - 4, cx + 4):
        line(img, [(x, 24), (x, 27)], rgba((230, 220, 240)))
    cyber_tag(img, "END", rim)
    return img


# ---------------------------------------------------------------- wither
SKULL = [
    "..######..",
    ".########.",
    "##########",
    "##..##..##",
    "#...##...#",
    "##########",
    ".##.##.##.",
    ".#.#..#.#.",
]


def skull(img, x0, y0, s, bone, eye):
    for j, row in enumerate(SKULL):
        for i, ch in enumerate(row):
            if ch == "#":
                c = bone if j < 5 else lerp(bone, BLACK, 0.3)
                rect(img, x0 + i * s, y0 + j * s, x0 + i * s + s - 1, y0 + j * s + s - 1, rgba(c))
            elif j in (3, 4) and 1 <= i <= 8:
                rect(img, x0 + i * s, y0 + j * s, x0 + i * s + s - 1, y0 + j * s + s - 1, rgba(eye))


def rose(img, x, y, rng):
    petals = [(0, -2), (-2, 0), (2, 0), (0, 1), (-1, -1), (1, -1)]
    for dx, dy in petals:
        px(img, x + dx, y + dy, rgba((24, 6, 10)))
    px(img, x, y - 1, rgba((120, 10, 30)))
    px(img, x, y, rgba((70, 6, 16)))
    px(img, x + rng.choice((-1, 1)), y - 1, rgba((150, 20, 40)))


def frame_wither():
    img = new(FRAME_W, FRAME_H)
    rim = (150, 90, 255)
    rng = random.Random(9)

    def side(im):
        # bone spikes fanning out from the casing end
        for k, (tx, ty) in enumerate([(2, 18), (6, 8), (16, 3), (4, 46), (12, 54)]):
            poly(im, [(34, 28 + k % 2 * 6), (tx, ty), (36, 34 + k % 2 * 2)], rgba((70, 64, 76)), rgba((150, 140, 160)))
        # soul sand clump
        poly(im, [(14, 40), (34, 28), (38, 40), (26, 48)], rgba((52, 38, 30)))
        shade_noise(im, (12, 26, 40, 50), rgba((80, 60, 45)), 5, 0.25)
        skull(im, 16, 30, 1, (90, 84, 96), (150, 90, 255))
    mirror(img, side)

    casing(img, rim, (14, 10, 18), (150, 90, 255), (70, 60, 84))
    # rose vines along the top and bottom of the casing
    for edge_y, dirn in ((BAR_Y - 5, -1), (BAR_Y + BAR_H + 4, 1)):
        for x in range(BAR_X - 4, BAR_X + BAR_W + 4):
            y = edge_y + int(round(1.5 * math.sin(x * 0.35))) * dirn
            px(img, x, y, rgba((30, 50, 26)))
            if x % 7 == 0:
                px(img, x, y + dirn, rgba((40, 70, 34)))
                px(img, x + 1, y + 2 * dirn, rgba((40, 70, 34)))
        for x in range(BAR_X + 6, BAR_X + BAR_W - 4, 13):
            if abs(x - FRAME_W // 2) > 26:
                rose(img, x, edge_y + dirn * 2, rng)
    # skull cluster over the centre
    cx = FRAME_W // 2
    skull(img, cx - 30, 14, 2, (64, 58, 70), (170, 110, 255))
    skull(img, cx + 10, 14, 2, (64, 58, 70), (170, 110, 255))
    skull(img, cx - 15, 2, 3, (88, 80, 96), (200, 150, 255))
    # dark aura behind the cluster
    for r in range(3):
        line(img, [(cx - 34 - r * 2, 34 + r), (cx + 34 + r * 2, 34 + r)], rgba((20, 10, 30), 120 - r * 35))
    cyber_tag(img, "WTR", rim)
    return img


# ---------------------------------------------------------------- raid
def illager_banner(img, x0, y0, w, h):
    rect(img, x0, y0, x0 + w - 1, y0 + h - 1, rgba((214, 208, 198)))
    for x in range(x0, x0 + w):
        if (x - x0) % 4 in (1, 2):
            px(img, x, y0 + h, rgba((214, 208, 198)))
    # ominous banner pattern
    rect(img, x0 + 2, y0 + h // 4, x0 + w - 3, y0 + h // 4 + h // 3, rgba((44, 44, 44)))
    rect(img, x0 + 3, y0 + h // 4 + 3, x0 + w - 4, y0 + h // 4 + 4, rgba((20, 150, 150)))
    rect(img, x0 + w // 2 - 1, y0 + h // 4 + h // 3, x0 + w // 2, y0 + h - 4, rgba((120, 120, 120)))
    line(img, [(x0 + 1, y0 + h - 3), (x0 + w - 2, y0 + h - 3)], rgba((160, 150, 140)))
    rect(img, x0 - 2, y0 - 2, x0 + w + 1, y0 - 1, rgba((110, 74, 40)))


def frame_raid():
    img = new(FRAME_W, FRAME_H)
    rim = (255, 120, 40)

    def side(im):
        # chain from the banner pole to the casing
        for t in range(0, 20, 3):
            rect(im, 16 + t, 7 + t // 2, 17 + t, 8 + t // 2, rgba((120, 120, 130)))
        illager_banner(im, 4, 10, 14, 34)
        # torch glow
        for r in range(4, 0, -1):
            rect(im, 26 - r, 44 - r, 26 + r, 44 + r, rgba((255, 140, 40), 30 + (4 - r) * 25))
        rect(im, 25, 44, 27, 52, rgba((110, 74, 40)))
        px(im, 26, 43, rgba((255, 230, 120)))
    mirror(img, side)

    casing(img, rim, (26, 12, 8), (255, 160, 60), (110, 70, 40))
    cx = FRAME_W // 2
    # crossed axes behind the centre banner
    for sgn in (-1, 1):
        line(img, [(cx - 22 * sgn, 2), (cx + 10 * sgn, 26)], rgba((110, 74, 40)), 2)
        poly(img, [(cx - 22 * sgn, 2), (cx - 30 * sgn, 6), (cx - 28 * sgn, 14), (cx - 18 * sgn, 10)],
             rgba((200, 200, 210)), rgba((240, 240, 250)))
    illager_banner(img, cx - 8, 3, 16, 20)
    # wave plaque edges
    for x in range(BAR_X, BAR_X + BAR_W, 30):
        px(img, x, BAR_Y - 6, rgba(rim))
    cyber_tag(img, "RAID", rim)
    return img


# ---------------------------------------------------------------- generic
def frame_generic():
    img = new(FRAME_W, FRAME_H)
    rim = YELLOW

    def side(im):
        # armoured tech plate with hazard stripes and a WARNING panel
        poly(im, [(30, 18), (6, 18), (2, 24), (2, 40), (6, 46), (30, 46), (36, 40), (36, 24)], rgba((16, 16, 22)),
             rgba((90, 86, 40)))
        for y in range(20, 45):
            for x in range(5, 12):
                if ((x + y) // 3) % 2 == 0:
                    px(im, x, y, rgba(YELLOW))
        rect(im, 14, 22, 32, 28, rgba(RED))
        for k in range(4):
            rect(im, 15 + k * 4, 32, 16 + k * 4, 41, rgba((0, 200, 220) if k % 2 else (0, 120, 140)))
        for (bx, by) in ((8, 20), (8, 43), (32, 20), (32, 43)):
            px(im, bx, by, rgba((200, 200, 210)))
        # antenna
        line(im, [(20, 18), (20, 6)], rgba((120, 120, 130)))
        px(im, 20, 5, rgba(RED))
    mirror(img, side)
    for wx in (15, FRAME_W - 32):
        text(img, wx, 23, "WARN", rgba(BLACK))
    casing(img, rim, (14, 14, 20), YELLOW, (90, 86, 40))
    cx = FRAME_W // 2
    # holographic target reticle in the centre
    poly(img, [(cx - 18, 24), (cx - 12, 14), (cx + 12, 14), (cx + 18, 24)], rgba((16, 16, 22)), rgba(YELLOW))
    rect(img, cx - 8, 17, cx + 7, 22, rgba((0, 40, 50)))
    text(img, cx - 7, 18, "BOSS", rgba((0, 230, 255)))
    for d in (-24, 20):
        line(img, [(cx + d, 10), (cx + d + 4, 10)], rgba(YELLOW))
    cyber_tag(img, "LVL", YELLOW)
    return img


def build_boss_frames():
    for name, fn in (("generic", frame_generic), ("dragon", frame_dragon),
                     ("wither", frame_wither), ("raid", frame_raid)):
        save(fn(), "boss_%s.png" % name)


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
    build_boss_frames()
    build_panoramas(args.panoramas)
    build_mod_icon()


if __name__ == "__main__":
    main()
