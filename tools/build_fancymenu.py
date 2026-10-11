#!/usr/bin/env python3
"""Builds the CYBER//UI FancyMenu title screen (FancyMenu 3.7+, MC 1.21.4).

Writes animated assets and the layout file into
    fancymenu-setup/config/fancymenu/
which the player copies into their .minecraft folder.

Run tools/build_pack.py first: the animated logo is made from the pack's
re-coloured title logo.
"""

import json
import math
import os
import random

from PIL import Image

import build_pack as bp
from build_pack import BLACK, BG, YELLOW, RED, CYAN, WHITE, lerp, rgba, new, rect, frame_rect

ROOT = bp.ROOT
OUT = os.path.join(ROOT, "fancymenu-setup", "config", "fancymenu")
ASSETS = os.path.join(OUT, "assets", "cyber_ui")
ASSET_REF = "[source:local]/config/fancymenu/assets/cyber_ui/"

# --------------------------------------------------------------------------
# Tiny 3x5 pixel font for HUD decorations
# --------------------------------------------------------------------------
FONT = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110",
    "E": "111100110100111", "F": "111100110100100", "G": "011100101101011", "H": "101101111101101",
    "I": "111010010010111", "J": "001001001101010", "K": "101101110101101", "L": "100100100100111", "M": "101111111101101",
    "N": "110101101101101", "O": "010101101101010", "P": "110101110100100", "R": "110101110101101",
    "S": "011100010001110", "T": "111010010010010", "U": "101101101101111", "V": "101101101101010",
    "W": "101101111111101", "Y": "101101010010010", "X": "101101010101101",
    "0": "111101101101111", "1": "010110010010111", "2": "110001010100111", "3": "110001010001110",
    "4": "101101111001001", "5": "111100110001110", "6": "011100111101111", "7": "111001010010010",
    "8": "111101111101111", "9": "111101111001110", "/": "001001010100100", ":": "000010000010000",
    ".": "000000000000010", "%": "101001010100101", "-": "000000111000000", ">": "100010001010100",
    "_": "000000000000111", "|": "010010010010010", " ": "000000000000000", "#": "101111101111101",
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


def save_apng(frames, durations, name):
    os.makedirs(ASSETS, exist_ok=True)
    path = os.path.join(ASSETS, name)
    frames[0].save(path, format="PNG", save_all=True, append_images=frames[1:],
                   duration=durations, loop=0, disposal=0, blend=0, optimize=True)
    return path


def save_gif(frames, durations, name):
    os.makedirs(ASSETS, exist_ok=True)
    path = os.path.join(ASSETS, name)
    rgb = [f.convert("RGB") for f in frames]
    pal = rgb[0].quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    q = [f.quantize(palette=pal, dither=Image.Dither.NONE) for f in rgb]
    q[0].save(path, save_all=True, append_images=q[1:], duration=durations, loop=0,
              optimize=False, disposal=1)
    return path


# --------------------------------------------------------------------------
# Background: rainy neon city at night
# --------------------------------------------------------------------------
def build_background():
    W, H, N = 480, 270, 24
    rng = random.Random(2077)

    sky = Image.new("RGB", (W, H))
    for y in range(H):
        t = y / H
        c = lerp((4, 4, 8), (60, 0, 22), max(0.0, (t - 0.25) / 0.6) ** 1.6)
        for x in range(W):
            sky.putpixel((x, y), c)

    # giant faint ring billboard behind the skyline
    for a in range(720):
        ang = a / 720 * 2 * math.pi
        for r in (58, 59):
            x, y = int(330 + r * math.cos(ang)), int(120 + r * math.sin(ang))
            if 0 <= x < W and 0 <= y < H:
                sky.putpixel((x, y), lerp(sky.getpixel((x, y)), YELLOW, 0.35))

    layers = []  # (buildings, colour, window chance)
    for depth, (col, hmin, hmax, wmin, wmax, wchance) in enumerate((
            ((24, 17, 34), 90, 190, 18, 40, 0.10),
            ((14, 11, 21), 60, 150, 22, 50, 0.16),
            ((3, 3, 5), 30, 100, 26, 60, 0.22))):
        x = -rng.randint(0, 20)
        bl = []
        while x < W:
            bw = rng.randint(wmin, wmax)
            bh = rng.randint(hmin, hmax)
            bl.append((x, H - bh, bw, bh))
            x += bw + rng.randint(0, 6)
        layers.append((bl, col, wchance, depth))

    windows = []
    signs = []
    for bl, col, wchance, depth in layers:
        for (bx, by, bw, bh) in bl:
            for wy in range(by + 4, H - 4, 5 if depth < 2 else 6):
                for wx in range(bx + 3, bx + bw - 3, 4 if depth < 2 else 5):
                    if rng.random() < wchance:
                        c = rng.choice([YELLOW, YELLOW, (255, 170, 60), CYAN, RED])
                        windows.append((wx, wy, depth, c, rng.random()))
            if depth == 2 and rng.random() < 0.5:
                sx = bx + rng.randint(2, max(3, bw - 6))
                sy = by + rng.randint(4, max(5, bh // 2))
                signs.append((sx, sy, rng.randint(14, 34), rng.choice([RED, YELLOW, CYAN]), rng.random()))

    drops = [(rng.randrange(W), rng.randrange(H), rng.randint(1, 3), rng.randint(5, 10)) for _ in range(260)]

    frames = []
    for i in range(N):
        img = sky.copy()
        px = img.load()
        for bl, col, wchance, depth in layers:
            for (bx, by, bw, bh) in bl:
                for y in range(max(0, by), H):
                    for x in range(max(0, bx), min(W, bx + bw)):
                        px[x, y] = col
                # roof antenna
                if bw > 30 and by - 8 >= 0:
                    ax = bx + bw // 2
                    for y in range(by - 8, by):
                        if 0 <= ax < W:
                            px[ax, y] = col
                    if (i // 6) % 2 == 0 and 0 <= ax < W:
                        px[ax, by - 9] = RED
        for (wx, wy, depth, c, phase) in windows:
            if wx < 0 or wx >= W - 1:
                continue
            on = not (phase < 0.08 and (i + int(phase * 100)) % 8 < 3)
            if on:
                k = (0.25, 0.45, 0.8)[depth]
                cc = lerp((0, 0, 0), c, k)
                px[wx, wy] = cc
                px[wx + 1, wy] = cc
        for (sx, sy, sh, c, phase) in signs:
            flick = phase < 0.4 and (i % 12) in (3, 5)
            cc = lerp(c, BLACK, 0.7) if flick else c
            for y in range(sy, min(H, sy + sh)):
                for x in (sx, sx + 1):
                    if 0 <= x < W:
                        px[x, y] = cc
                for x in (sx - 1, sx + 2):
                    if 0 <= x < W:
                        px[x, y] = lerp(px[x, y], cc, 0.35)
        # rain: every drop travels exactly k screen heights per loop
        for (dx, dy, k, ln) in drops:
            y0 = (dy + i * H * k / N) % H
            x0 = (dx - i * k * 6 / N * 4) % W
            for j in range(ln):
                x, y = int(x0 - j * 0.4) % W, int(y0 - j)
                if 0 <= y < H:
                    px[x, y] = lerp(px[x, y], (150, 160, 190), 0.35)
        # scanlines
        for y in range(0, H, 2):
            for x in range(W):
                px[x, y] = lerp(px[x, y], (0, 0, 0), 0.22)
        if i in (9, 10):
            img = bp.glitch(img.convert("RGBA"), random.Random(i), bands=4, max_shift=10).convert("RGB")
        frames.append(img)
    save_gif(frames, [70] * N, "bg_city.gif")


def scanlines(img, k=0.22):
    px = img.load()
    for y in range(0, img.height, 2):
        for x in range(img.width):
            px[x, y] = lerp(px[x, y], (0, 0, 0), k)


# --------------------------------------------------------------------------
# Background: retro neon grid with a striped sunset
# --------------------------------------------------------------------------
def build_bg_synthwave():
    W, H, N = 480, 270, 20
    hor = 150
    rng = random.Random(84)
    stars = [(rng.randrange(W), rng.randrange(hor - 20), rng.random()) for _ in range(90)]
    sky = Image.new("RGB", (W, H))
    for y in range(H):
        t = min(1.0, y / hor)
        c = lerp((6, 2, 12), (80, 0, 40), t ** 2) if y < hor else (6, 3, 10)
        for x in range(W):
            sky.putpixel((x, y), c)
    # sun with horizontal cut stripes
    cx, cy, r = W // 2, hor - 8, 62
    for y in range(cy - r, hor):
        for x in range(cx - r, cx + r):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                t = (y - (cy - r)) / (hor - (cy - r))
                band = y > cy - 18 and ((y - (cy - 18)) // 4) % 2 == 1 and (y - cy + 18) // 4 >= 0
                gap = band and ((y - (cy - 18)) % 4) < 1 + int(t * 3)
                if not gap:
                    sky.putpixel((x, y), lerp(YELLOW, RED, t))
    # mountains
    for x in range(W):
        h1 = int(18 + 14 * math.sin(x * 0.031) + 9 * math.sin(x * 0.083 + 1.3))
        for y in range(hor - max(0, h1), hor):
            sky.putpixel((x, y), (14, 4, 20) if y > hor - h1 + 1 else (120, 0, 50))

    frames = []
    for i in range(N):
        img = sky.copy()
        px = img.load()
        for (sx, sy, ph) in stars:
            if (i + int(ph * N)) % 7:
                px[sx, sy] = lerp((0, 0, 0), (220, 220, 255), 0.4 + 0.6 * ph)
        # floor grid: horizontal lines move towards the viewer, one spacing per loop
        for k in range(40):
            z = k + 1 - i / N  # distance; shifts by exactly one line per loop
            if z <= 0:
                continue
            y = hor + int(round(110 / z))
            if hor < y < H:
                depth = min(1.0, 1.4 / z)
                c = lerp((90, 0, 40), YELLOW, depth)
                for x in range(W):
                    px[x, y] = c
        for k in range(-24, 25):
            for y in range(hor + 1, H):
                t = (y - hor) / (H - hor)
                x = int(W / 2 + k * 6 + k * 40 * t)
                if 0 <= x < W:
                    px[x, y] = lerp((90, 0, 40), (255, 60, 90), t)
        scanlines(img)
        if i == 13:
            img = bp.glitch(img.convert("RGBA"), random.Random(i), bands=3, max_shift=8).convert("RGB")
        frames.append(img)
    save_gif(frames, [70] * N, "bg_synthwave.gif")


# --------------------------------------------------------------------------
# Background: falling data glyphs
# --------------------------------------------------------------------------
def build_bg_datastream():
    W, H, N = 480, 270, 24
    rng = random.Random(1337)
    glyphs = "0123456789ABCDEFKMNRSXY#/%"
    cols = []
    for cx in range(0, W, 5):
        speed = rng.choice([1, 1, 2])  # screen heights per loop
        cols.append((cx, rng.randrange(H), speed, rng.randint(6, 22),
                     [rng.choice(glyphs) for _ in range(60)], rng.random() < 0.12))
    frames = []
    for i in range(N):
        img = Image.new("RGBA", (W, H), rgba(BG))
        for (cx, y0, speed, ln, chars, red) in cols:
            head = (y0 + i * H * speed / N) % H
            for j in range(ln):
                y = int(head - j * 7) % H
                t = j / ln
                base = RED if red else YELLOW
                c = WHITE if j == 0 else lerp(base, (20, 10, 0), t)
                ch = chars[(j + (i if j < 2 else 0)) % len(chars)]
                text(img, cx, y, ch, rgba(c))
        img = img.convert("RGB")
        scanlines(img, 0.3)
        if i in (6, 18):
            img = bp.glitch(img.convert("RGBA"), random.Random(i), bands=3, max_shift=12).convert("RGB")
        frames.append(img)
    save_gif(frames, [80] * N, "bg_datastream.gif")


# --------------------------------------------------------------------------
# Background: neon hex tunnel rushing towards the viewer
# --------------------------------------------------------------------------
def build_bg_tunnel():
    W, H, N = 480, 270, 24
    cx, cy = W / 2, H / 2
    frames = []
    rings = 14
    for i in range(N):
        img = Image.new("RGB", (W, H), BG)
        px = img.load()
        # spokes
        for s in range(6):
            ang = s * math.pi / 3 + math.pi / 6
            for r in range(6, 320):
                x, y = int(cx + r * math.cos(ang)), int(cy + r * math.sin(ang))
                if 0 <= x < W and 0 <= y < H:
                    px[x, y] = lerp(BG, (90, 0, 30), min(1.0, r / 200))
        for k in range(rings):
            # exponential spacing; one ring step per loop keeps it seamless
            d = (k + i / N) / rings
            r = 4 * math.exp(d * 4.4)
            col = YELLOW if k % 3 else RED
            bright = min(1.0, d * 1.6)
            c = lerp(BG, col, bright)
            for e in range(6):
                a0 = e * math.pi / 3 + (k % 2) * 0.0
                a1 = a0 + math.pi / 3
                p0 = (cx + r * math.cos(a0), cy + r * math.sin(a0))
                p1 = (cx + r * math.cos(a1), cy + r * math.sin(a1))
                steps = int(r * 1.2) + 2
                for t in range(steps):
                    x = int(p0[0] + (p1[0] - p0[0]) * t / steps)
                    y = int(p0[1] + (p1[1] - p0[1]) * t / steps)
                    if 0 <= x < W and 0 <= y < H:
                        px[x, y] = c
                        if r > 60 and y + 1 < H:
                            px[x, y + 1] = c
        # core glow
        for y in range(int(cy) - 10, int(cy) + 10):
            for x in range(int(cx) - 10, int(cx) + 10):
                d = math.hypot(x - cx, y - cy)
                if d < 10:
                    px[x, y] = lerp(px[x, y], WHITE, (1 - d / 10) * (0.6 + 0.4 * math.sin(i / N * 2 * math.pi) ** 2))
        scanlines(img)
        if i == 17:
            img = bp.glitch(img.convert("RGBA"), random.Random(i), bands=4, max_shift=10).convert("RGB")
        frames.append(img)
    save_gif(frames, [60] * N, "bg_tunnel.gif")


# --------------------------------------------------------------------------
# Loading screen background: dark grid with a sweeping scan bar
# --------------------------------------------------------------------------
def build_bg_boot():
    W, H, N = 480, 270, 30
    frames = []
    for i in range(N):
        img = Image.new("RGB", (W, H), BG)
        px = img.load()
        for y in range(H):
            for x in range(W):
                if x % 24 == 0 or y % 24 == 0:
                    px[x, y] = (24, 22, 14)
        sweep = int(H * i / N)
        for d in range(40):
            y = sweep - d
            if 0 <= y < H:
                k = (1 - d / 40) ** 2 * 0.35
                for x in range(W):
                    px[x, y] = lerp(px[x, y], YELLOW, k)
        for x in range(W):
            if 0 <= sweep < H:
                px[x, sweep] = lerp(px[x, sweep], YELLOW, 0.6)
        scanlines(img, 0.3)
        frames.append(img)
    save_gif(frames, [60] * N, "bg_boot.gif")


# --------------------------------------------------------------------------
# Spinner and screen transition parts
# --------------------------------------------------------------------------
def build_spinner():
    S, N = 16, 12
    frames = []
    for i in range(N):
        img = new(S, S)
        for seg in range(12):
            ang = seg / 12 * 2 * math.pi
            age = (i - seg) % 12
            if age > 6:
                continue
            c = rgba(lerp(YELLOW, RED, age / 6), int(255 * (1 - age / 7)))
            for r in (5, 6, 7):
                x, y = int(round(7.5 + r * math.cos(ang))), int(round(7.5 + r * math.sin(ang)))
                img.putpixel((x, y), c)
        frames.append(img)
    save_apng(frames, [50] * N, "spinner.apng")


def build_transition_parts():
    body = Image.new("RGBA", (16, 16), rgba(BG))
    for y in range(0, 16, 2):
        for x in range(16):
            body.putpixel((x, y), rgba(BLACK))
    body.putpixel((0, 1), rgba(lerp(BG, YELLOW, 0.3)))
    body.save(os.path.join(ASSETS, "shutter_body.png"), optimize=True)

    frames = []
    rng = random.Random(3)
    for i in range(6):
        img = new(64, 6)
        for x in range(64):
            img.putpixel((x, 0), rgba(RED, 120))
            img.putpixel((x, 1), rgba(RED))
            img.putpixel((x, 2), rgba(YELLOW))
            img.putpixel((x, 3), rgba(YELLOW))
            img.putpixel((x, 4), rgba(YELLOW, 150))
            img.putpixel((x, 5), rgba(YELLOW, 60))
        for _ in range(5):
            x = rng.randrange(60)
            for d in range(rng.randint(2, 6)):
                img.putpixel(((x + d) % 64, 2), rgba(CYAN if i % 2 else WHITE))
        frames.append(img)
    save_apng(frames, [40] * 6, "shutter_edge.apng")


# --------------------------------------------------------------------------
# Logo: re-coloured title logo + edition, with glitch bursts
# --------------------------------------------------------------------------
def build_logo():
    gui = bp.GUI
    logo = Image.open(os.path.join(gui, "title", "minecraft.png")).convert("RGBA")
    logo = logo.crop(logo.getbbox())
    ed = Image.open(os.path.join(gui, "title", "edition.png")).convert("RGBA")
    ed = ed.crop(ed.getbbox())
    w = 512
    logo = logo.resize((w, int(logo.height * w / logo.width)), Image.LANCZOS)
    ew = int(w * 0.5)
    ed = ed.resize((ew, int(ed.height * ew / ed.width)), Image.LANCZOS)
    base = new(w, logo.height + ed.height - 6)
    base.alpha_composite(logo, (0, 0))
    base.alpha_composite(ed, ((w - ew) // 2, logo.height - 8))

    rng = random.Random(11)
    dim = base.copy()
    dim.putalpha(base.getchannel("A").point(lambda v: int(v * 0.55)))
    frames = [base, bp.glitch(base, rng, 3, 14), bp.glitch(base, rng, 4, 20), base, dim, base,
              bp.glitch(base, rng, 2, 8)]
    durations = [2400, 60, 60, 900, 50, 1600, 70]
    save_apng(frames, durations, "logo.apng")
    return base.size


# --------------------------------------------------------------------------
# Status panel: HUD box with live-looking data
# --------------------------------------------------------------------------
def build_status_panel():
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
        # corner brackets
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

        # animated load bar
        y = 54
        frame_rect(img, 6, y, 104, y + 6, rgba(YELLOW))
        fill = int(96 * ((i + 1) / N))
        for x in range(8, 8 + fill):
            if (x // 3) % 2 == 0:
                rect(img, x, y + 2, x, y + 4, rgba(YELLOW))
        text(img, 108, y + 1, "%d%%" % int(100 * (i + 1) / N), rgba(YELLOW))

        # scrolling hex column
        for r in range(4):
            text(img, 110, 16 + r * 9, hexes[(i + r) % len(hexes)], rgba(CYAN if r == 0 else (0, 110, 120)))

        # prompt with blinking cursor
        x = text(img, 6, 70, "> JACK_IN", rgba(YELLOW))
        if i % 4 < 2:
            rect(img, x, 70, x + 2, 74, rgba(YELLOW))
        text(img, 6, 80, "V1.21.4 // CYBER-UI", rgba((90, 90, 100)))
        frames.append(img)
    save_apng(frames, [120] * N, "status_panel.apng")
    return W, H


def build_hazard_strip():
    frames = []
    for i in range(8):
        img = new(64, 4)
        for y in range(4):
            for x in range(64):
                img.putpixel((x, y), rgba(YELLOW if ((x + y + i) // 4) % 2 == 0 else BLACK))
        frames.append(img)
    save_apng(frames, [60] * 8, "hazard_strip.apng")


# --------------------------------------------------------------------------
# Layout file
# --------------------------------------------------------------------------
def container(kind, props):
    lines = ["%s {" % kind]
    for k, v in props.items():
        lines.append("  %s = %s" % (k, v))
    lines.append("}")
    return "\n".join(lines)


def base_element(element_type, ident, anchor, x, y, w, h, **extra):
    p = {
        "element_type": element_type,
        "instance_identifier": ident,
        "appearance_delay": "no_delay",
        "appearance_delay_seconds": "1.0",
        "fade_in_v2": "no_fading",
        "fade_in_speed": "1.0",
        "fade_out": "no_fading",
        "fade_out_speed": "1.0",
        "base_opacity": "1.0",
        "anchor_point": anchor,
        "x": str(x),
        "y": str(y),
        "width": str(w),
        "height": str(h),
        "stretch_x": "false",
        "stretch_y": "false",
        "stay_on_screen": "false",
        "element_loading_requirement_container_identifier": ident + "_req",
        "enable_parallax": "false",
        "parallax_intensity_v2": "0.5",
        "invert_parallax": "false",
        "load_once_per_session": "false",
        "layer_hidden_in_editor": "false",
    }
    p.update({k: str(v) for k, v in extra.items()})
    return p


def slide_in(ident, target, delay_ms, distance=-240, duration_ms=520, steps=10):
    """Animation controller that slides the target in with an ease-out curve."""
    keys = [{"timestamp": 0, "posOffsetX": distance, "posOffsetY": 0, "baseWidth": 0, "baseHeight": 0,
             "anchorPoint": {"name": "top-left"}, "stickyAnchor": False, "uniqueIdentifier": ident + "_k0"}]
    if delay_ms:
        keys.append(dict(keys[0], timestamp=delay_ms, uniqueIdentifier=ident + "_kd"))
    for s in range(1, steps + 1):
        t = s / steps
        ease = 1 - (1 - t) ** 3
        keys.append({"timestamp": delay_ms + int(duration_ms * t), "posOffsetX": int(round(distance * (1 - ease))),
                     "posOffsetY": 0, "baseWidth": 0, "baseHeight": 0, "anchorPoint": {"name": "top-left"},
                     "stickyAnchor": False, "uniqueIdentifier": "%s_k%d" % (ident, s)})
    p = base_element("animation_controller", ident, "top-left", 0, 0, 1, 1)
    p.update({
        "target_element_ids": json.dumps([target]),
        "keyframes": json.dumps(keys, separators=(",", ":")),
        "loop": "false",
        "offset_mode": "true",
        "ignore_size": "true",
        "ignore_position": "false",
    })
    return p


def layout_meta(identifier, random_group=None, extra=None):
    m = {
        "identifier": identifier,
        "render_custom_elements_behind_vanilla": "false",
        "last_edited_time": "0",
        "is_enabled": "true",
        "randommode": "true" if random_group else "false",
        "randomgroup": random_group or "1",
        "randomonlyfirsttime": "false",
        "layout_index": "0",
    }
    m.update(extra or {})
    return container("layout-meta", m)


TITLE_BACKGROUNDS = ["bg_city.gif", "bg_synthwave.gif", "bg_datastream.gif", "bg_tunnel.gif"]


def build_background_layouts():
    """One small layout per background; FancyMenu picks one of the random
    group at random every time the title screen opens."""
    paths = []
    for name in TITLE_BACKGROUNDS:
        key = name[:-4]
        parts = ["type = fancymenu_layout", "", layout_meta("title_screen", random_group="cyber_ui_backgrounds")]
        parts.append(container("menu_background", {
            "background_type": "image",
            "instance_identifier": "cyber_ui_" + key,
            "image_path": ASSET_REF + name,
            "slide": "false",
            "repeat_texture": "false",
            "parallax": "true",
            "parallax_intensity": "0.015",
            "invert_parallax": "false",
            "restart_animated_on_menu_load": "false",
        }))
        parts.append(container("customization", {"action": "backgroundoptions", "keepaspectratio": "true"}))
        paths.append(write_layout("cyber_ui_title_%s.txt" % key, parts))
    return paths


def keyframe(ident, n, t, x, y):
    return {"timestamp": int(t), "posOffsetX": int(round(x)), "posOffsetY": int(round(y)), "baseWidth": 0,
            "baseHeight": 0, "anchorPoint": {"name": "top-left"}, "stickyAnchor": False,
            "uniqueIdentifier": "%s_k%d" % (ident, n)}


def controller(ident, target, keys):
    p = base_element("animation_controller", ident, "top-left", 0, 0, 1, 1)
    p.update({
        "target_element_ids": json.dumps([target]),
        "keyframes": json.dumps(keys, separators=(",", ":")),
        "loop": "false",
        "offset_mode": "true",
        "ignore_size": "true",
        "ignore_position": "false",
    })
    return p


# Menu screens that get the shutter transition (never in-game containers)
TRANSITION_SCREENS = [
    "title_screen", "options_screen", "options_sub_screen", "video_settings_screen", "sound_options_screen",
    "controls_screen", "key_binds_screen", "mouse_settings_screen", "language_select_screen",
    "pack_selection_screen", "skin_customization_screen", "online_options_screen", "chat_options_screen",
    "accessibility_options_screen", "telemetry_info_screen", "credits_and_attribution_screen",
    "select_world_screen", "create_world_screen", "edit_world_screen", "edit_game_rules_screen",
    "experiments_screen", "join_multiplayer_screen", "direct_join_server_screen", "edit_server_screen",
    "realms_main_screen", "pause_screen", "stats_screen", "advancements_screen", "share_to_lan_screen",
    "social_interactions_screen",
]


def build_transition_layout():
    """Universal layout: a scanline shutter covers the screen when it opens and
    slides up, its glowing edge sweeping across the menu."""
    parts = ["type = fancymenu_layout", "", layout_meta(
        "%fancymenu:universal_layout%",
        extra={"universal_layout_whitelist": ";".join(TRANSITION_SCREENS) + ";",
               "universal_layout_blacklist": "", "layout_index": "100"})]
    # Elements rest far off-screen (where they snap back once the animation
    # ends) and are moved over the screen only by the animation keyframes.
    rest_y, body_h, cover_y, travel = -9000, 1200, -100, 1220
    hold, dur, steps = 90, 620, 14
    for ident, src, h, repeat, dy0 in (("cyber_ui_shutter_body", "shutter_body.png", body_h, "true", 0),
                                      ("cyber_ui_shutter_edge", "shutter_edge.apng", 6, "true", body_h)):
        parts.append(container("element", base_element(
            "image", ident, "top-left", -1000, rest_y + dy0, 4000, h,
            source=ASSET_REF + src, repeat_texture=repeat, nine_slice_texture="false",
            nine_slice_texture_border_x="5", nine_slice_texture_border_y="5", image_tint="#FFFFFF",
            restart_animated_on_menu_load="true")))
        cover = cover_y - rest_y
        keys = [keyframe(ident, 0, 0, 0, cover), keyframe(ident, 1, hold, 0, cover)]
        for s in range(1, steps + 1):
            t = s / steps
            ease = 1 - (1 - t) ** 3
            keys.append(keyframe(ident, s + 1, hold + dur * t, 0, cover - travel * ease))
        parts.append(container("element", controller(ident + "_anim", ident, keys)))
    return write_layout("cyber_ui_transition.txt", parts)


def build_layout(logo_size, panel_size):
    parts = ["type = fancymenu_layout", "", layout_meta("title_screen")]

    lw = 230
    lh = int(logo_size[1] * lw / logo_size[0])
    parts.append(container("element", base_element(
        "image", "cyber_ui_logo", "top-left", 18, 14, lw, lh,
        source=ASSET_REF + "logo.apng", repeat_texture="false", nine_slice_texture="false",
        nine_slice_texture_border_x="5", nine_slice_texture_border_y="5", image_tint="#FFFFFF",
        restart_animated_on_menu_load="true", fade_in_v2="every_time", fade_in_speed="1.6")))

    pw, ph = panel_size
    parts.append(container("element", base_element(
        "image", "cyber_ui_status", "mid-right", -pw - 16, -ph // 2, pw, ph,
        source=ASSET_REF + "status_panel.apng", repeat_texture="false", nine_slice_texture="false",
        nine_slice_texture_border_x="5", nine_slice_texture_border_y="5", image_tint="#FFFFFF",
        restart_animated_on_menu_load="true", enable_parallax="true", parallax_intensity_v2="0.6",
        fade_in_v2="every_time", fade_in_speed="1.2")))
    parts.append(container("element", slide_in("cyber_ui_status_anim", "cyber_ui_status", 350, distance=220)))

    parts.append(container("element", base_element(
        "image", "cyber_ui_hazard", "bottom-left", 0, -3, "%guiwidth%", 3,
        source=ASSET_REF + "hazard_strip.apng", repeat_texture="true", nine_slice_texture="false",
        nine_slice_texture_border_x="5", nine_slice_texture_border_y="5", image_tint="#FFFFFF",
        restart_animated_on_menu_load="false")))

    # vanilla buttons in a left-aligned column, sliding in one after another
    column = [
        ("mc_titlescreen_singleplayer_button", -34),
        ("mc_titlescreen_multiplayer_button", -10),
        ("mc_titlescreen_realms_button", 14),
        ("mc_titlescreen_options_button", 44),
        ("mc_titlescreen_quit_button", 68),
    ]
    for n, (ident, y) in enumerate(column):
        parts.append(container("vanilla_button", base_element(
            "vanilla_button", ident, "mid-left", 18, y, 160, 20,
            is_hidden="false", automated_button_clicks="0")))
        parts.append(container("element", slide_in(ident + "_anim", ident, 120 + n * 90)))
    for ident, y in (("mc_titlescreen_language_button", 44), ("mc_titlescreen_accessibility_button", 68)):
        parts.append(container("vanilla_button", base_element(
            "vanilla_button", ident, "mid-left", 182, y, 20, 20,
            is_hidden="false", automated_button_clicks="0")))
        parts.append(container("element", slide_in(ident + "_anim", ident, 120 + 5 * 90)))
    # demo button (only exists in demo mode) takes the singleplayer slot
    parts.append(container("vanilla_button", base_element(
        "vanilla_button", "mc_titlescreen_playdemo_button", "mid-left", 18, -34, 160, 20,
        is_hidden="false", automated_button_clicks="0")))

    # vanilla logo and splash are replaced by the animated logo
    for ident in ("minecraft_logo_widget", "minecraft_splash_widget"):
        parts.append(container("vanilla_button", {
            "element_type": "vanilla_button", "instance_identifier": ident, "is_hidden": "true"}))

    return write_layout("cyber_ui_title_screen.txt", parts)


def write_layout(name, parts):
    os.makedirs(os.path.join(OUT, "customization"), exist_ok=True)
    path = os.path.join(OUT, "customization", name)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n\n".join(parts) + "\n")
    return path


# --------------------------------------------------------------------------
# Loading screen (Drippy Loading Screen 3.x)
# --------------------------------------------------------------------------
def build_boot_text():
    W, H, N = 200, 22, 16
    rng = random.Random(42)
    tasks = ["LOADING TEXTURES", "LINKING SHADERS", "SYNCING SOUNDS", "DECRYPTING DATA"]
    spinner = ["|", "/", "-", "/"]
    frames = []
    for i in range(N):
        img = new(W, H)
        label = tasks[(i // 4) % len(tasks)] + "." * (i % 4)
        x = text(img, 0, 2, "> " + label, rgba(YELLOW))
        if i % 2 == 0:
            rect(img, x + 1, 2, x + 3, 6, rgba(YELLOW))
        text(img, W - 8, 2, spinner[i % 4], rgba(RED))
        hexline = " ".join("".join(rng.choice("0123456789ABCDEF") for _ in range(4)) for _ in range(9))
        text(img, 0, 13, hexline[:49], rgba((0, 120, 130)))
        frames.append(img)
    save_apng(frames, [110] * N, "boot_text.apng")
    return W, H


def build_progress_textures():
    w, h = 200, 8
    bg = new(w, h)
    rect(bg, 0, 0, w - 1, h - 1, (8, 8, 12, 230))
    frame_rect(bg, 0, 0, w - 1, h - 1, rgba(YELLOW))
    rect(bg, 0, 0, 3, 1, rgba(RED))
    bg.save(os.path.join(ASSETS, "progress_background.png"), optimize=True)

    bar = new(w, h)
    for y in range(h):
        for x in range(w):
            on = ((x + y) // 3) % 2 == 0
            bar.putpixel((x, y), rgba(YELLOW if on else (200, 150, 0)))
    frame_rect(bar, 0, 0, w - 1, h - 1, rgba(YELLOW))
    bar.save(os.path.join(ASSETS, "progress_bar.png"), optimize=True)


def build_loading_layout(logo_size, boot_size):
    parts = ["type = fancymenu_layout", ""]
    parts.append(container("layout-meta", {
        "identifier": "drippy_loading_overlay",
        "render_custom_elements_behind_vanilla": "false",
        "last_edited_time": "0",
        "is_enabled": "true",
        "randommode": "false",
        "randomgroup": "1",
        "randomonlyfirsttime": "false",
        "layout_index": "0",
    }))
    parts.append(container("menu_background", {
        "background_type": "drippy_color_background",
        "instance_identifier": "cyber_ui_loading_background",
        "color": "#0A0A0F",
    }))
    # animated grid with a sweeping scan bar, stretched over the whole screen
    parts.append(container("element", base_element(
        "image", "cyber_ui_loading_grid", "top-left", 0, 0, "%guiwidth%", "%guiheight%",
        source=ASSET_REF + "bg_boot.gif", repeat_texture="false", nine_slice_texture="false",
        nine_slice_texture_border_x="5", nine_slice_texture_border_y="5", image_tint="#FFFFFF",
        restart_animated_on_menu_load="false")))

    lw = 260
    lh = int(logo_size[1] * lw / logo_size[0])
    parts.append(container("element", base_element(
        "image", "cyber_ui_loading_logo", "mid-centered", 0, -lh // 2 - 18, lw, lh,
        sticky_anchor="true", source=ASSET_REF + "logo.apng", repeat_texture="false",
        nine_slice_texture="false", nine_slice_texture_border_x="5", nine_slice_texture_border_y="5",
        image_tint="#FFFFFF", restart_animated_on_menu_load="true")))
    # the logo drops in from above with an ease-out
    keys = [keyframe("cyber_ui_loading_logo_anim", 0, 0, 0, -160)]
    for n in range(1, 13):
        t = n / 12
        keys.append(keyframe("cyber_ui_loading_logo_anim", n, 700 * t, 0, -160 * (1 - t) ** 3))
    parts.append(container("element", controller("cyber_ui_loading_logo_anim", "cyber_ui_loading_logo", keys)))

    parts.append(container("element", base_element(
        "image", "cyber_ui_loading_spinner", "mid-centered", -112, 22, 16, 16,
        sticky_anchor="true", source=ASSET_REF + "spinner.apng", repeat_texture="false",
        nine_slice_texture="false", nine_slice_texture_border_x="5", nine_slice_texture_border_y="5",
        image_tint="#FFFFFF", restart_animated_on_menu_load="false")))
    parts.append(container("element", base_element(
        "text_v2", "cyber_ui_loading_percent", "mid-centered", 104, 18, 40, 10,
        source='{"placeholder":"game_loading_progress"}%', source_mode="direct", shadow="false",
        scale="1.0", base_color="#FCEE0A", text_border="0", line_spacing="1", enable_scrolling="false",
        auto_line_wrapping="false", interactable="false")))

    parts.append(container("element", base_element(
        "progress_bar", "cyber_ui_loading_bar", "mid-centered", 0, 22, 200, 8,
        sticky_anchor="true", bar_color="#FCEE0A", background_color="#0A0A0F",
        bar_texture=ASSET_REF + "progress_bar.png", background_texture=ASSET_REF + "progress_background.png",
        direction="right", value_mode="percentage",
        progress_source='{"placeholder":"game_loading_progress"}',
        smooth_filling_animation="true", progress_for_element_anchor="false")))

    bw, bh = boot_size
    parts.append(container("element", base_element(
        "image", "cyber_ui_loading_text", "mid-centered", 0, 36 + bh // 2, bw, bh,
        sticky_anchor="true", source=ASSET_REF + "boot_text.apng", repeat_texture="false",
        nine_slice_texture="false", nine_slice_texture_border_x="5", nine_slice_texture_border_y="5",
        image_tint="#FFFFFF", restart_animated_on_menu_load="false")))

    for anchor, y in (("bottom-left", -3), ("top-left", 0)):
        parts.append(container("element", base_element(
            "image", "cyber_ui_loading_hazard_" + anchor, anchor, 0, y, "%guiwidth%", 3,
            source=ASSET_REF + "hazard_strip.apng", repeat_texture="true", nine_slice_texture="false",
            nine_slice_texture_border_x="5", nine_slice_texture_border_y="5", image_tint="#FFFFFF",
            restart_animated_on_menu_load="false")))

    # the Mojang Studios logo and the vanilla bar are replaced
    for ident in ("mojang_logo", "progress_bar"):
        parts.append(container("vanilla_button", {
            "element_type": "vanilla_button", "instance_identifier": ident, "is_hidden": "true"}))
    return write_layout("cyber_ui_loading_screen.txt", parts)


def main():
    build_background()
    build_bg_synthwave()
    build_bg_datastream()
    build_bg_tunnel()
    build_bg_boot()
    logo_size = build_logo()
    panel_size = build_status_panel()
    build_hazard_strip()
    build_spinner()
    build_transition_parts()
    print(build_layout(logo_size, panel_size))
    for path in build_background_layouts():
        print(path)
    print(build_transition_layout())
    boot_size = build_boot_text()
    build_progress_textures()
    print(build_loading_layout(logo_size, boot_size))


if __name__ == "__main__":
    main()
