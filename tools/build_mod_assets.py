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


# ---------------------------------------------------------------- shared
CX, CY = FRAME_W // 2, BAR_Y + BAR_H // 2


def mask_fill(img, box, inside, fill, outline=None):
    """Fill every pixel of a box for which inside(x, y) is true; outline its edge."""
    x0, y0, x1, y1 = box
    pts = {(x, y) for y in range(y0, y1) for x in range(x0, x1) if inside(x, y)}
    for (x, y) in pts:
        edge = any((x + dx, y + dy) not in pts for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        px(img, x, y, rgba(outline) if (edge and outline) else rgba(fill))
    return pts


def mirror_x(img):
    """Overlay the horizontally flipped image on itself (symmetric ornaments)."""
    img.alpha_composite(img.transpose(Image.FLIP_LEFT_RIGHT))


def spear(img, dark, edge, glow, seed, thorn=None):
    """Long shaft around the bar slot with arrow tips, thorns and a neon line."""
    rng = random.Random(seed)
    x0, x1 = BAR_X - 3, BAR_X + BAR_W + 2
    y0, y1 = BAR_Y - 3, BAR_Y + BAR_H + 2
    thorn = thorn or dark
    # thorns along both edges
    x = x0 + 2
    while x < x1 - 2:
        h = rng.choice((1, 2, 2, 3))
        poly(img, [(x - 1, y0), (x + rng.choice((-1, 0, 1)), y0 - h), (x + 1, y0)], rgba(thorn))
        h = rng.choice((1, 1, 2))
        poly(img, [(x + 2, y1), (x + 3, y1 + h), (x + 4, y1)], rgba(thorn))
        x += rng.randint(4, 8)
    rect(img, x0, y0, x1, y1, rgba(dark))
    line(img, [(x0, y0), (x1, y0)], rgba(edge))
    line(img, [(x0, y1), (x1, y1)], rgba(lerp(dark, BLACK, 0.5)))
    # arrow tips with a barb
    for sgn, base in ((-1, x0), (1, x1)):
        tip = base + sgn * 17
        poly(img, [(base, CY - 6), (tip, CY), (base, CY + 6)], rgba(dark), rgba(edge))
        poly(img, [(base - sgn * 1, CY - 6), (base + sgn * 5, CY - 9), (base + sgn * 4, CY - 4)], rgba(dark), rgba(edge))
        poly(img, [(base - sgn * 1, CY + 6), (base + sgn * 5, CY + 9), (base + sgn * 4, CY + 4)], rgba(dark), rgba(edge))
        line(img, [(base + sgn * 2, CY), (tip - sgn * 3, CY)], rgba(glow))
        px(img, tip - sgn * 2, CY, rgba(WHITE))
    # recessed slot + neon rails (the cyberpunk part)
    rect(img, BAR_X - 1, BAR_Y - 1, BAR_X + BAR_W, BAR_Y + BAR_H, rgba((4, 4, 7)))
    line(img, [(BAR_X - 1, BAR_Y - 2), (BAR_X + BAR_W, BAR_Y - 2)], rgba(glow, 200))
    line(img, [(BAR_X - 1, BAR_Y + BAR_H + 1), (BAR_X + BAR_W, BAR_Y + BAR_H + 1)], rgba(glow, 120))
    for x in range(BAR_X + 20, BAR_X + BAR_W - 10, 36):
        px(img, x, BAR_Y - 3, rgba(WHITE))


# ---------------------------------------------------------------- dragon
def frame_dragon():
    back, front = new(FRAME_W, FRAME_H), new(FRAME_W, FRAME_H)
    dark, edge, glow = (16, 8, 22), (90, 40, 120), (255, 40, 200)
    rng = random.Random(3)

    # curled bat wings at both ends: a crescent with scalloped spikes and bones
    wing = new(FRAME_W, FRAME_H)
    ax, bx = BAR_X - 6, BAR_X + 1
    mask_fill(wing, (0, 0, BAR_X, FRAME_H),
              lambda x, y: (x - ax) ** 2 + (y - CY) ** 2 * 0.8 <= 22 ** 2
              and (x - bx) ** 2 + (y - CY) ** 2 * 0.8 > 20 ** 2 and x < BAR_X - 4,
              dark, edge)
    for k in range(7):
        a = math.radians(115 + k * 21.5)
        sx, sy = ax + 22 * math.cos(a), CY + 22 / 0.9 * math.sin(a)
        ex, ey = ax + 27 * math.cos(a), CY + 27 / 0.9 * math.sin(a)
        poly(wing, [(sx + 2 * math.sin(a), sy - 2 * math.cos(a)), (ex, ey),
                    (sx - 2 * math.sin(a), sy + 2 * math.cos(a))], rgba(dark), rgba(edge))
        line(wing, [(ax + 4, CY), (sx, sy)], rgba((60, 30, 80)))
    # neon inner edge of the wing
    for y in range(CY - 18, CY + 19):
        dx = int(math.sqrt(max(0, 20 ** 2 - (y - CY) ** 2 * 0.8)))
        px(wing, bx - dx - 1, y, rgba(glow, 170))
    mirror_x(wing)
    back.alpha_composite(wing)

    spear(back, dark, edge, glow, 11, thorn=(30, 14, 40))

    # ender goo dripping under the centre
    for x in range(CX - 52, CX + 53):
        if rng.random() < 0.55:
            n = max(1, int(rng.random() ** 2 * 9 * (1 - abs(x - CX) / 60)) + 1)
            for t in range(n):
                c = lerp((255, 120, 230), (120, 0, 110), t / max(1, n))
                px(back, x, BAR_Y + BAR_H + 3 + t, rgba(c, 255 - t * 12))
    for x in (CX - 40, CX - 23, CX + 21, CX + 39):
        poly(back, [(x - 2, BAR_Y + BAR_H + 3), (x, BAR_Y + BAR_H + 10), (x + 2, BAR_Y + BAR_H + 3)],
             rgba((200, 40, 220)), rgba((255, 170, 250)))

    # dragon head over the bar centre (front layer)
    head = [(CX, 17), (CX - 6, 20), (CX - 9, 27), (CX - 8, 36), (CX - 5, 43), (CX - 2, 48), (CX, 49),
            (CX + 2, 48), (CX + 5, 43), (CX + 8, 36), (CX + 9, 27), (CX + 6, 20)]
    for s in (-1, 1):
        poly(front, [(CX + s * 4, 20), (CX + s * 13, 9), (CX + s * 8, 22)], rgba(dark), rgba(edge))
        poly(front, [(CX + s * 8, 24), (CX + s * 15, 21), (CX + s * 9, 29)], rgba(dark), rgba(edge))
        poly(front, [(CX + s * 7, 38), (CX + s * 12, 44), (CX + s * 5, 42)], rgba(dark), rgba(edge))
    poly(front, head, rgba(dark), rgba(edge))
    shade_noise(front, (CX - 9, 17, CX + 10, 49), rgba((30, 16, 40)), 5, 0.25)
    line(front, [(CX, 21), (CX, 44)], rgba((34, 18, 46)))
    for s in (-1, 1):
        line(front, [(CX + s * 2, 30), (CX + s * 6, 28)], rgba(glow))
        px(front, CX + s * 4, 29, rgba((255, 200, 250)))
        px(front, CX + s * 2, 45, rgba(glow))
    px(front, CX, 24, rgba(RED))
    px(front, CX, 25, rgba(RED, 150))
    cyber_tag(back, "END", glow)
    return back, front


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
                c = bone if j < 5 else lerp(bone, BLACK, 0.35)
                rect(img, x0 + i * s, y0 + j * s, x0 + i * s + s - 1, y0 + j * s + s - 1, rgba(c))
            elif j in (3, 4) and 1 <= i <= 8:
                rect(img, x0 + i * s, y0 + j * s, x0 + i * s + s - 1, y0 + j * s + s - 1, rgba(eye))
    # outline
    w, h = 10 * s, 8 * s
    out = new(img.width, img.height)
    for y in range(y0 - 1, y0 + h + 1):
        for x in range(x0 - 1, x0 + w + 1):
            if 0 <= x < img.width and 0 <= y < img.height and img.getpixel((x, y))[3] == 0:
                if any(0 <= x + dx < img.width and 0 <= y + dy < img.height
                       and x0 <= x + dx < x0 + w and y0 <= y + dy < y0 + h
                       and img.getpixel((x + dx, y + dy))[3] > 0
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out.putpixel((x, y), rgba((6, 4, 8)))
    img.alpha_composite(out)


def big_rose(img, x, y, petal, light, core):
    for dx, dy in ((0, -3), (-2, -2), (2, -2), (-3, 0), (3, 0), (-2, 2), (2, 2), (0, 3)):
        rect(img, x + dx - 1, y + dy - 1, x + dx + 1, y + dy + 1, rgba(petal))
    rect(img, x - 2, y - 2, x + 2, y + 2, rgba(lerp(petal, light, 0.4)))
    for dx, dy in ((-1, -2), (1, -1), (-2, 1), (0, 2)):
        px(img, x + dx, y + dy, rgba(light))
    px(img, x, y, rgba(core))
    px(img, x + 1, y, rgba(lerp(core, BLACK, 0.4)))


def frame_wither():
    back, front = new(FRAME_W, FRAME_H), new(FRAME_W, FRAME_H)
    dark, edge, glow = (22, 18, 26), (70, 60, 84), (150, 90, 255)
    rng = random.Random(7)

    # thorny vines curling along the top, roses on them
    half = new(FRAME_W, FRAME_H)
    for x in range(BAR_X - 6, CX - 14):
        y = BAR_Y - 5 - int(round(2.2 * math.sin((x - BAR_X) * 0.22)))
        px(half, x, y, rgba((24, 30, 22)))
        px(half, x, y + 1, rgba((14, 18, 12)))
        if x % 5 == 0:
            px(half, x + 1, y - 1, rgba((44, 52, 38)))
    for i, x in enumerate(range(CX - 24, BAR_X + 4, -13)):
        big_rose(half, x, BAR_Y - 9 - (i % 2) * 2, (36, 30, 44), (96, 84, 116), glow)
        line(half, [(x, BAR_Y - 6), (x + 2, BAR_Y - 3)], rgba((24, 30, 22)))
    # small vine under the bar
    for x in range(BAR_X + 10, CX - 30):
        y = BAR_Y + BAR_H + 4 + int(round(1.2 * math.sin(x * 0.3)))
        px(half, x, y, rgba((20, 24, 18)))
        if x % 9 == 0:
            px(half, x, y + 1, rgba((44, 52, 38)))
    mirror_x(half)
    back.alpha_composite(half)

    spear(back, dark, edge, glow, 23, thorn=(40, 34, 46))

    # skull trio over the centre, hooded in darkness (front layer)
    poly(front, [(CX - 18, CY + 4), (CX, 12), (CX + 18, CY + 4), (CX, 44)], rgba((8, 6, 12), 200))
    skull(front, CX - 31, 23, 2, (78, 72, 86), (170, 110, 255))
    skull(front, CX + 11, 23, 2, (78, 72, 86), (170, 110, 255))
    skull(front, CX - 15, 14, 3, (104, 96, 114), (220, 170, 255))
    # neon crown pixel + scan line on the middle skull (cyber touch)
    rect(front, CX - 1, 11, CX, 12, rgba(RED))
    line(front, [(CX - 14, 27), (CX + 13, 27)], rgba(glow, 110))
    cyber_tag(back, "WTR", glow)
    return back, front


# ---------------------------------------------------------------- raid
def illager_banner(img, x0, y0, w, h):
    rect(img, x0, y0, x0 + w - 1, y0 + h - 1, rgba((214, 208, 198)))
    for x in range(x0, x0 + w):
        if (x - x0) % 4 in (1, 2):
            px(img, x, y0 + h, rgba((214, 208, 198)))
    rect(img, x0 + 2, y0 + h // 4, x0 + w - 3, y0 + h // 4 + h // 3, rgba((44, 44, 44)))
    rect(img, x0 + 3, y0 + h // 4 + 3, x0 + w - 4, y0 + h // 4 + 4, rgba((20, 150, 150)))
    rect(img, x0 + w // 2 - 1, y0 + h // 4 + h // 3, x0 + w // 2, y0 + h - 4, rgba((120, 120, 120)))
    rect(img, x0 - 2, y0 - 2, x0 + w + 1, y0 - 1, rgba((110, 74, 40)))


def frame_raid():
    back, front = new(FRAME_W, FRAME_H), new(FRAME_W, FRAME_H)
    dark, edge, glow = (40, 32, 32), (110, 90, 86), (255, 40, 60)

    # ominous banner hanging from the right tip
    bxr = BAR_X + BAR_W + 6
    line(back, [(bxr + 4, CY), (bxr + 4, CY + 6)], rgba((110, 74, 40)))
    illager_banner(back, bxr - 1, CY + 8, 11, 20)
    spear(back, dark, edge, glow, 31, thorn=(70, 60, 58))

    # huge double-bit axe on the left end (front layer); blades never cover the slot
    hx = BAR_X + 12
    metal, light, shade = (92, 92, 100), (196, 196, 206), (52, 52, 60)

    def blade(x, y):
        yy = y if y < CY else 2 * CY - y + (BAR_H % 2)
        if not (yy <= BAR_Y - 4):
            return False
        inside = ((x - hx) / 21.0) ** 2 + ((yy - (BAR_Y - 4)) / 26.0) ** 2 <= 1.0
        notch = ((x - (hx - 13)) ** 2 + (yy - 2) ** 2 <= 49) or ((x - (hx + 13)) ** 2 + (yy - 2) ** 2 <= 49)
        return inside and not notch and yy >= 1
    pts = mask_fill(front, (hx - 24, 0, hx + 25, FRAME_H), blade, metal, (20, 20, 26))
    for (x, y) in pts:
        yy = y if y < CY else 2 * CY - y
        r = ((x - hx) / 21.0) ** 2 + ((yy - (BAR_Y - 4)) / 26.0) ** 2
        if 0.72 < r < 0.86:
            px(front, x, y, rgba(light))
        elif r < 0.25:
            px(front, x, y, rgba(shade))
    # glowing red cutting edges
    for (x, y) in pts:
        yy = y if y < CY else 2 * CY - y
        if ((x - hx) / 21.0) ** 2 + ((yy - (BAR_Y - 4)) / 26.0) ** 2 > 0.9 and abs(x - hx) > 14:
            px(front, x, y, rgba(glow))
    # rivet rings and the haft above / below the slot only
    for y in (BAR_Y - 14, BAR_Y + BAR_H + 13):
        rect(front, hx - 3, y - 3, hx + 3, y + 3, rgba((20, 20, 26)))
        rect(front, hx - 2, y - 2, hx + 2, y + 2, rgba(metal))
        rect(front, hx - 1, y - 1, hx + 1, y + 1, rgba((20, 20, 26)))
    for y0, y1 in ((0, BAR_Y - 3), (BAR_Y + BAR_H + 2, FRAME_H - 1)):
        rect(front, hx - 1, y0, hx + 1, y1, rgba((110, 74, 40)))
        line(front, [(hx - 1, y0), (hx - 1, y1)], rgba((150, 104, 60)))
    cyber_tag(back, "RAID", glow)
    return back, front


# ---------------------------------------------------------------- generic
def frame_generic():
    back, front = new(FRAME_W, FRAME_H), new(FRAME_W, FRAME_H)
    dark, edge, glow = (16, 16, 22), (90, 86, 40), YELLOW

    # data cables curling down from under the centre, like tentacles
    half = new(FRAME_W, FRAME_H)
    for k, (reach, drop, phase) in enumerate(((60, 18, 0.0), (36, 26, 1.2), (84, 12, 2.0))):
        prev = None
        for t in range(0, 101):
            u = t / 100.0
            x = CX - 8 - reach * u
            y = BAR_Y + BAR_H + 2 + drop * math.sin(u * math.pi) * (1 - 0.3 * u) + 3 * math.sin(u * 9 + phase) * u
            p = (int(round(x)), int(round(y)))
            if p != prev:
                w = 2 if u < 0.6 else 1
                rect(half, p[0], p[1], p[0] + w - 1, p[1] + w - 1, rgba((10, 10, 14)))
                if t % 9 == k:
                    px(half, p[0], p[1], rgba(CYAN))
                prev = p
    # hazard stripes on the left arrow
    mirror_x(half)
    back.alpha_composite(half)
    spear(back, dark, edge, glow, 41, thorn=(50, 48, 30))
    for x0 in (BAR_X - 3, BAR_X + BAR_W - 12):
        for x in range(x0, x0 + 16):
            for y in (BAR_Y - 3, BAR_Y + BAR_H + 2):
                if ((x + y) // 2) % 2 == 0:
                    px(back, x, y, rgba(YELLOW))

    # hexagonal optic in the centre (front layer)
    hexa = [(CX - 6, 18), (CX + 6, 18), (CX + 12, CY), (CX + 6, 47), (CX - 6, 47), (CX - 12, CY)]
    poly(front, hexa, rgba((14, 14, 20)), rgba(YELLOW))
    poly(front, [(CX - 4, 22), (CX + 4, 22), (CX + 8, CY), (CX + 4, 43), (CX - 4, 43), (CX - 8, CY)],
         rgba((0, 30, 40)), rgba((0, 120, 140)))
    rect(front, CX - 3, CY - 3, CX + 3, CY + 3, rgba(CYAN))
    rect(front, CX - 1, CY - 1, CX + 1, CY + 1, rgba(RED))
    px(front, CX - 2, CY - 2, rgba(WHITE))
    for s in (-1, 1):
        line(front, [(CX + s * 13, CY), (CX + s * 17, CY)], rgba(YELLOW))
        px(front, CX + s * 6, 15, rgba(RED))
    cyber_tag(back, "LVL", YELLOW)
    return back, front


def build_boss_frames():
    for name, fn in (("generic", frame_generic), ("dragon", frame_dragon),
                     ("wither", frame_wither), ("raid", frame_raid)):
        back, front = fn()
        save(back, "boss_%s.png" % name)
        save(front, "boss_%s_front.png" % name)


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
