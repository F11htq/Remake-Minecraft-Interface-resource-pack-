#!/usr/bin/env python3
"""Builds the "CYBER//UI" resource pack textures for Minecraft 1.21.4.

Cyberpunk restyle of menus and screens only: buttons, sliders, text fields,
checkboxes, tabs, scroll bars, tooltips, slot highlights, menu backgrounds,
every container / inventory screen, the title logo and panorama overlay.
The in-game HUD and the world stay vanilla.

Usage:
    python3 tools/build_pack.py --vanilla <path to an extracted 1.21.4 client
    'assets/minecraft/textures/gui' folder>

The vanilla folder is used only as a layout reference: container screens and
the logo are re-coloured so every slot, arrow and letter stays exactly where
the game expects it.
"""

import argparse
import json
import math
import os
import random
import shutil

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI = os.path.join(ROOT, "assets", "minecraft", "textures", "gui")
SPR = os.path.join(GUI, "sprites")

# --------------------------------------------------------------------------
# Palette
# --------------------------------------------------------------------------
BLACK = (5, 5, 8)
BG = (10, 10, 15)
PANEL_TOP = (24, 24, 32)
PANEL_BOT = (15, 15, 21)
BEVEL_HI = (52, 50, 36)
BEVEL_LO = (9, 9, 13)
SLOT = (11, 11, 16)
SLOT_SH = (4, 4, 7)
FIELD = (8, 8, 12)
EDGE = (60, 58, 44)
DIM = (34, 34, 42)

YELLOW = (252, 238, 10)
RED = (255, 0, 60)
CYAN = (0, 240, 255)
WHITE = (255, 255, 255)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def pulse(t):
    """0..1..0 eased pulse for t in [0, 1)."""
    return 0.5 - 0.5 * math.cos(t * 2 * math.pi)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def new(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def save(img, rel, meta=None):
    base = GUI if rel.startswith("@") else SPR
    path = os.path.join(base, rel.lstrip("@"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    if meta is not None:
        with open(path + ".mcmeta", "w") as f:
            json.dump(meta, f, indent=2)
            f.write("\n")


def nine(w, h, border, stretch_inner=False):
    s = {"type": "nine_slice", "width": w, "height": h, "border": border}
    if stretch_inner:
        s["stretch_inner"] = True
    return {"gui": {"scaling": s}}


def animated(frames, frametime=2, interpolate=False, timing=None, extra=None):
    """Stacks frames vertically and returns (image, mcmeta).

    timing: optional list of per-frame durations in ticks.
    """
    w, h = frames[0].size
    strip = new(w, h * len(frames))
    for i, fr in enumerate(frames):
        strip.paste(fr, (0, i * h))
    anim = {"frametime": frametime}
    if interpolate:
        anim["interpolate"] = True
    if timing:
        anim["frames"] = [{"index": i, "time": t} for i, t in enumerate(timing)]
    if w != h:
        anim["width"] = w
        anim["height"] = h
    meta = {"animation": anim}
    if extra:
        meta.update(extra)
    return strip, meta


def rect(img, x0, y0, x1, y1, c):
    for y in range(max(0, y0), min(img.height - 1, y1) + 1):
        for x in range(max(0, x0), min(img.width - 1, x1) + 1):
            img.putpixel((x, y), c)


def frame_rect(img, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        img.putpixel((x, y0), c)
        img.putpixel((x, y1), c)
    for y in range(y0, y1 + 1):
        img.putpixel((x0, y), c)
        img.putpixel((x1, y), c)


def vgrad(img, x0, y0, x1, y1, top, bot, a=255):
    for y in range(y0, y1 + 1):
        t = 0 if y1 == y0 else (y - y0) / (y1 - y0)
        c = rgba(lerp(top, bot, t), a)
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), c)


def chamfer(img, size, corners=("br",)):
    """Cuts 45 degree corners off an image (transparent)."""
    w, h = img.size
    for i in range(size):
        for j in range(size - i):
            if "tl" in corners:
                img.putpixel((j, i), (0, 0, 0, 0))
            if "tr" in corners:
                img.putpixel((w - 1 - j, i), (0, 0, 0, 0))
            if "bl" in corners:
                img.putpixel((j, h - 1 - i), (0, 0, 0, 0))
            if "br" in corners:
                img.putpixel((w - 1 - j, h - 1 - i), (0, 0, 0, 0))


def chamfer_edge(img, size, c, corners=("br",)):
    """Draws the diagonal outline along chamfered corners."""
    w, h = img.size
    for i in range(size + 1):
        j = size - i
        if "tl" in corners:
            img.putpixel((j, i), c)
        if "tr" in corners:
            img.putpixel((w - 1 - j, i), c)
        if "bl" in corners:
            img.putpixel((j, h - 1 - i), c)
        if "br" in corners:
            img.putpixel((w - 1 - j, h - 1 - i), c)


def glitch(img, rng, bands=2, max_shift=4, tint=True):
    """Returns a copy with a few horizontal slices shifted and colour-split."""
    w, h = img.size
    out = img.copy()
    for _ in range(bands):
        y0 = rng.randrange(0, h)
        bh = rng.randint(1, max(1, h // 6))
        shift = rng.choice([-1, 1]) * rng.randint(1, max_shift)
        col = rng.choice([RED, CYAN])
        for y in range(y0, min(h, y0 + bh)):
            row = [img.getpixel((x, y)) for x in range(w)]
            for x in range(w):
                p = row[(x - shift) % w]
                if p[3] and tint:
                    p = rgba(lerp(p[:3], col, 0.55), p[3])
                if img.getpixel((x, y))[3] or p[3]:
                    out.putpixel((x, y), p if img.getpixel((x, y))[3] else (0, 0, 0, 0))
    return out


def glitch_cycle(base, seed, glitch_frames=3, hold=60, bands=2, max_shift=4):
    """A mostly-still animation with a short glitch burst."""
    rng = random.Random(seed)
    frames = [base] + [glitch(base, rng, bands, max_shift) for _ in range(glitch_frames)]
    timing = [hold] + [1] * glitch_frames
    return frames, timing


# --------------------------------------------------------------------------
# Buttons: dark slab, chamfered bottom-right corner, yellow marker on the left
# --------------------------------------------------------------------------
def button(w, h, fill_top, fill_bot, edge, marker, cut=4, scan=False):
    img = new(w, h)
    vgrad(img, 1, 1, w - 2, h - 2, fill_top, fill_bot)
    if scan:
        for y in range(2, h - 2, 2):
            for x in range(1, w - 1):
                p = img.getpixel((x, y))
                img.putpixel((x, y), rgba(lerp(p[:3], (0, 0, 0), 0.18)))
    frame_rect(img, 0, 0, w - 1, h - 1, rgba(edge))
    if marker:
        rect(img, 1, 3, 2, h - 4, rgba(marker))
    chamfer(img, cut, ("br",))
    chamfer_edge(img, cut, rgba(edge), ("br",))
    return img


def build_buttons():
    w, h = 200, 20
    border = 5
    save(button(w, h, (26, 26, 34), (16, 16, 22), EDGE, YELLOW), "widget/button.png", nine(w, h, border))

    base = button(w, h, (70, 12, 28), (34, 6, 16), YELLOW, RED, scan=True)
    frames, timing = glitch_cycle(base, 1, glitch_frames=3, hold=50, bands=2, max_shift=5)
    strip, meta = animated(frames, timing=timing, extra=nine(w, h, border))
    save(strip, "widget/button_highlighted.png", meta)

    save(button(w, h, (16, 16, 20), (12, 12, 16), DIM, None), "widget/button_disabled.png", nine(w, h, border))


# --------------------------------------------------------------------------
# Sliders: track with tick marks, hazard-striped handle
# --------------------------------------------------------------------------
def slider_track(edge, tick):
    w, h = 200, 20
    img = new(w, h)
    rect(img, 1, 1, w - 2, h - 2, rgba(FIELD))
    for x in range(4, w - 4, 8):
        img.putpixel((x, h - 3), rgba(tick))
        img.putpixel((x, h - 4), rgba(tick))
    frame_rect(img, 0, 0, w - 1, h - 1, rgba(edge))
    return img


def slider_handle(a, b, edge):
    w, h = 8, 20
    img = new(w, h)
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            img.putpixel((x, y), rgba(a if ((x + y) // 3) % 2 == 0 else b))
    frame_rect(img, 0, 0, w - 1, h - 1, rgba(edge))
    return img


def build_sliders():
    handle_border = {"left": 2, "top": 2, "right": 2, "bottom": 3}
    save(slider_track(EDGE, DIM), "widget/slider.png", nine(200, 20, 1))
    save(slider_track(YELLOW, (110, 104, 20)), "widget/slider_highlighted.png", nine(200, 20, 1))
    save(slider_handle(YELLOW, (40, 38, 6), BLACK), "widget/slider_handle.png", nine(8, 20, handle_border))

    # stripes crawl down the handle while hovered
    frames = []
    for i in range(6):
        img = new(8, 20)
        for y in range(1, 19):
            for x in range(1, 7):
                img.putpixel((x, y), rgba(YELLOW if ((x + y + i) // 3) % 2 == 0 else RED))
        frame_rect(img, 0, 0, 7, 19, rgba(BLACK))
        frames.append(img)
    strip, meta = animated(frames, frametime=2, extra=nine(8, 20, handle_border))
    save(strip, "widget/slider_handle_highlighted.png", meta)


# --------------------------------------------------------------------------
# Text fields: focused field gets yellow frame and blinking corner markers
# --------------------------------------------------------------------------
def text_field(edge, corners=None):
    w, h = 200, 20
    img = new(w, h)
    rect(img, 1, 1, w - 2, h - 2, rgba(FIELD, 245))
    frame_rect(img, 0, 0, w - 1, h - 1, rgba(edge))
    if corners:
        for x, y in ((0, 0), (1, 0), (0, 1), (w - 1, h - 1), (w - 2, h - 1), (w - 1, h - 2)):
            img.putpixel((x, y), rgba(corners))
    return img


def build_text_fields():
    save(text_field(EDGE), "widget/text_field.png", nine(200, 20, 1))
    frames = [text_field(YELLOW, RED), text_field(YELLOW)]
    strip, meta = animated(frames, timing=[12, 12], extra=nine(200, 20, 1))
    save(strip, "widget/text_field_highlighted.png", meta)


# --------------------------------------------------------------------------
# Checkboxes
# --------------------------------------------------------------------------
def checkbox(edge, selected, fill=YELLOW):
    img = new(20, 20)
    rect(img, 1, 1, 18, 18, rgba(FIELD))
    frame_rect(img, 0, 0, 19, 19, rgba(edge))
    if selected:
        rect(img, 5, 5, 14, 14, rgba(fill))
        chamfer_box = new(10, 10)
        rect(chamfer_box, 0, 0, 9, 9, rgba(fill))
        chamfer(chamfer_box, 3, ("br",))
        for y in range(10):
            for x in range(10):
                img.putpixel((5 + x, 5 + y), chamfer_box.getpixel((x, y)) if chamfer_box.getpixel((x, y))[3]
                             else rgba(FIELD))
    chamfer(img, 3, ("br",))
    chamfer_edge(img, 3, rgba(edge), ("br",))
    return img


def build_checkboxes():
    save(checkbox(EDGE, False), "widget/checkbox.png")
    save(checkbox(EDGE, True), "widget/checkbox_selected.png")
    for name, sel in (("checkbox_highlighted", False), ("checkbox_selected_highlighted", True)):
        frames, timing = glitch_cycle(checkbox(YELLOW, sel, RED), 7, glitch_frames=2, hold=40,
                                      bands=1, max_shift=2)
        strip, meta = animated(frames, timing=timing)
        save(strip, "widget/%s.png" % name, meta)


# --------------------------------------------------------------------------
# Tabs (create world screen)
# --------------------------------------------------------------------------
def tab(edge, top_line, fill_alpha):
    w, h = 130, 24
    img = new(w, h)
    if fill_alpha:
        vgrad(img, 1, 2, w - 2, h - 1, PANEL_TOP, PANEL_BOT, fill_alpha)
    for x in range(0, w):
        img.putpixel((x, 0), rgba(edge))
        img.putpixel((x, 1), rgba(top_line))
    for y in range(0, h):
        img.putpixel((0, y), rgba(edge))
        img.putpixel((w - 1, y), rgba(edge))
    return img


def build_tabs():
    border = {"left": 2, "top": 2, "right": 2, "bottom": 0}
    save(tab(BLACK, DIM, 0), "widget/tab.png", nine(130, 24, border))
    save(tab(BLACK, YELLOW, 170), "widget/tab_selected.png", nine(130, 24, border))
    save(tab(BLACK, RED, 70), "widget/tab_highlighted.png", nine(130, 24, border))
    save(tab(BLACK, lerp(YELLOW, WHITE, 0.4), 190), "widget/tab_selected_highlighted.png", nine(130, 24, border))


# --------------------------------------------------------------------------
# Scroll bars
# --------------------------------------------------------------------------
def build_scrollers():
    w, h = 6, 32
    bg = new(w, h)
    rect(bg, 0, 0, w - 1, h - 1, rgba(FIELD, 230))
    frame_rect(bg, 0, 0, w - 1, h - 1, rgba(BLACK))
    save(bg, "widget/scroller_background.png", nine(w, h, 1))

    th = new(w, h)
    rect(th, 0, 0, w - 1, h - 1, rgba(YELLOW))
    for y in range(h):
        th.putpixel((w - 1, y), rgba((150, 140, 6)))
    for x in range(w):
        th.putpixel((x, h - 1), rgba((150, 140, 6)))
    save(th, "widget/scroller.png", nine(w, h, 1))


# --------------------------------------------------------------------------
# Tooltip: black glass, yellow frame with a red marker, occasional glitch
# --------------------------------------------------------------------------
def build_tooltip():
    bg = new(100, 100)
    rect(bg, 8, 8, 91, 91, (8, 8, 12, 242))
    for y in range(9, 91, 2):
        for x in range(9, 91):
            bg.putpixel((x, y), (14, 14, 20, 242))
    save(bg, "tooltip/background.png", nine(100, 100, 9))

    def frame(col_top, col_side, marker):
        fr = new(100, 100)
        for x in range(9, 91):
            fr.putpixel((x, 9), rgba(col_top))
            fr.putpixel((x, 90), rgba(col_side, 200))
        for y in range(9, 91):
            fr.putpixel((9, y), rgba(col_side, 200))
            fr.putpixel((90, y), rgba(col_side, 200))
        # red marker block at the top-left and a cut bottom-right corner
        rect(fr, 9, 9, 9 + 3, 9 + 1, rgba(marker))
        for i in range(4):
            fr.putpixel((90 - i, 90 - (3 - i)), rgba(col_side, 200))
        return fr

    base = frame(YELLOW, (150, 140, 20), RED)
    alt = frame(CYAN, (0, 130, 140), YELLOW)
    alt2 = frame(RED, (140, 0, 30), CYAN)
    strip, meta = animated([base, alt, base, alt2], timing=[70, 1, 2, 1],
                           extra=nine(100, 100, 10, stretch_inner=True))
    save(strip, "tooltip/frame.png", meta)


# --------------------------------------------------------------------------
# Slot hover: targeting brackets that lock onto the slot
# --------------------------------------------------------------------------
def brackets(img, inset, length, c):
    x0, y0, x1, y1 = inset, inset, 23 - inset, 23 - inset
    for i in range(length):
        for (x, y) in ((x0 + i, y0), (x0, y0 + i), (x1 - i, y0), (x1, y0 + i),
                       (x0 + i, y1), (x0, y1 - i), (x1 - i, y1), (x1, y1 - i)):
            img.putpixel((x, y), c)


def build_slot_highlight():
    back, front = [], []
    insets = [0, 1, 2, 3, 3, 3, 3, 3, 3, 3]
    for i, inset in enumerate(insets):
        locked = i >= 3
        b = new(24, 24)
        rect(b, 4, 4, 19, 19, rgba(YELLOW, 40 if locked else 20))
        back.append(b)

        f = new(24, 24)
        rect(f, 4, 4, 19, 19, (255, 255, 255, 18))
        col = YELLOW if locked else RED
        brackets(f, inset, 4, rgba(col))
        if locked and i % 2 == 0:
            brackets(f, inset, 2, rgba(WHITE))
        front.append(f)
    timing = [1, 1, 1, 6, 6, 6, 6, 6, 6, 6]
    for name, frames in (("slot_highlight_back", back), ("slot_highlight_front", front)):
        strip, meta = animated(frames, timing=timing, extra=nine(24, 24, 4))
        save(strip, "container/%s.png" % name, meta)


# --------------------------------------------------------------------------
# Menu backgrounds and separators (plain textures)
# --------------------------------------------------------------------------
def build_menu_backgrounds():
    def tile(alpha):
        img = Image.new("RGBA", (16, 16), rgba(BG, alpha))
        for y in range(0, 16, 2):
            for x in range(16):
                img.putpixel((x, y), rgba(BLACK, min(255, alpha + 30)))
        img.putpixel((0, 1), rgba(lerp(BG, YELLOW, 0.35), min(255, alpha + 20)))
        return img

    save(tile(175), "@menu_background.png")
    save(tile(130), "@inworld_menu_background.png")
    save(Image.new("RGBA", (16, 16), rgba(BLACK, 160)), "@menu_list_background.png")
    save(Image.new("RGBA", (16, 16), rgba(BLACK, 140)), "@inworld_menu_list_background.png")

    for prefix in ("", "inworld_"):
        hdr = new(32, 2)
        ftr = new(32, 2)
        for x in range(32):
            hdr.putpixel((x, 0), rgba(YELLOW, 220) if x % 16 < 13 else rgba(RED, 220))
            hdr.putpixel((x, 1), (0, 0, 0, 180))
            ftr.putpixel((x, 0), (0, 0, 0, 180))
            ftr.putpixel((x, 1), rgba(RED, 220) if x % 16 < 13 else rgba(YELLOW, 220))
        save(hdr, "@%sheader_separator.png" % prefix)
        save(ftr, "@%sfooter_separator.png" % prefix)


# --------------------------------------------------------------------------
# Container screens: palette re-map of the vanilla layout
# --------------------------------------------------------------------------
GREY_KEYS = [
    (0, BLACK),
    (55, SLOT_SH),
    (85, BEVEL_LO),
    (139, SLOT),
    (198, None),  # panel, filled with a vertical gradient
    (255, BEVEL_HI),
]


def map_grey(v, panel):
    keys = [(k, c if c is not None else panel) for k, c in GREY_KEYS]
    for k, c in keys:
        if v == k:
            return c
    for (k0, c0), (k1, c1) in zip(keys, keys[1:]):
        if k0 < v < k1:
            return lerp(c0, c1, (v - k0) / (k1 - k0))
    return keys[-1][1]


def remap(src, dst, accent=True):
    img = Image.open(src).convert("RGBA")
    w, h = img.size
    bbox = img.getbbox()
    if bbox is None:
        return
    y_min, y_max = bbox[1], bbox[3] - 1
    px = img.load()
    out = img.copy()
    po = out.load()
    for y in range(h):
        t = 0 if y_max == y_min else (y - y_min) / (y_max - y_min)
        panel = lerp(PANEL_TOP, PANEL_BOT, max(0.0, min(1.0, t)))
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if r == g == b:
                po[x, y] = rgba(map_grey(r, panel), a)
            elif max(r, g, b) - min(r, g, b) < 12:
                lum = int(0.299 * r + 0.587 * g + 0.114 * b)
                po[x, y] = rgba(map_grey(lum, panel), a)

    if accent:
        # yellow line with a red tail on the topmost bevel of the panel
        xs = [x for x in range(w) if any(px[x, y][3] for y in range(h))]
        x0, x1 = (min(xs), max(xs)) if xs else (0, 0)
        for x in range(w):
            for y in range(h):
                if px[x, y][3]:
                    if y <= y_min + 1 and y + 1 < h and px[x, y + 1][:3] == (255, 255, 255):
                        t = 0 if x1 == x0 else (x - x0) / (x1 - x0)
                        po[x, y + 1] = rgba(RED if t > 0.86 else YELLOW)
                    break
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    out.save(dst, optimize=True)


def build_containers(vanilla):
    count = 0
    for d in ("container", "container/creative_inventory"):
        sd = os.path.join(vanilla, d)
        for f in sorted(os.listdir(sd)):
            if f.endswith(".png"):
                remap(os.path.join(sd, f), os.path.join(GUI, d, f))
                count += 1
    for f in ("recipe_book.png", "advancements/window.png"):
        remap(os.path.join(vanilla, f), os.path.join(GUI, f))
        count += 1

    sprite_dirs = {
        "sprites/container/creative_inventory": lambda f: f.startswith("tab_"),
        "sprites/container/inventory": lambda f: f.startswith("effect_background"),
        "sprites/recipe_book": lambda f: f.startswith("tab"),
        "sprites/advancements": lambda f: f.startswith("tab_"),
        "sprites/container": lambda f: f == "slot.png",
    }
    for d, keep in sprite_dirs.items():
        sd = os.path.join(vanilla, d)
        for f in sorted(os.listdir(sd)):
            if f.endswith(".png") and keep(f):
                remap(os.path.join(sd, f), os.path.join(GUI, d, f), accent=False)
                count += 1
    return count


# --------------------------------------------------------------------------
# Title screen: glitch logo and scanline overlay
# --------------------------------------------------------------------------
def recolor_logo(img, face_top, face_bot, side):
    w, h = img.size
    bbox = img.getbbox()
    y0, y1 = bbox[1], bbox[3] - 1
    px = img.load()
    for y in range(h):
        grad = lerp(face_top, face_bot, (y - y0) / max(1, y1 - y0))
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            if lum > 0.45:
                k = 0.7 + 0.3 * min(1.0, (lum - 0.45) / 0.4)
                c = lerp((0, 0, 0), grad, k)
            elif lum > 0.12:
                c = lerp((12, 0, 4), side, 0.45 + 0.4 * (lum - 0.12) / 0.33)
            else:
                c = BLACK
            px[x, y] = rgba(c, a)
    return img


def chroma_split(img, shift):
    """Adds red/cyan ghost copies behind the image (chromatic aberration)."""
    w, h = img.size
    out = new(w, h)
    for col, dx in ((CYAN, -shift), (RED, shift)):
        ghost = new(w, h)
        a = img.getchannel("A")
        solid = Image.new("RGBA", (w, h), rgba(col))
        ghost.paste(solid, (0, 0), a.point(lambda v: v * 0.55))
        out.alpha_composite(ghost, (dx, 0))
    out.alpha_composite(img)
    return out


def write_blur_meta(path):
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True}}, f, indent=2)
        f.write("\n")


def build_title(vanilla):
    src = os.path.join(vanilla, "title")
    dst = os.path.join(GUI, "title")
    os.makedirs(dst, exist_ok=True)
    for name, top, bot, side, shift in (("minecraft", YELLOW, (255, 190, 0), RED, 6),
                                        ("minceraft", YELLOW, (255, 190, 0), RED, 6),
                                        ("edition", RED, (200, 0, 50), (90, 0, 20), 3)):
        img = recolor_logo(Image.open(os.path.join(src, name + ".png")).convert("RGBA"), top, bot, side)
        img = chroma_split(img, shift)
        path = os.path.join(dst, name + ".png")
        img.save(path, optimize=True)
        write_blur_meta(path)

    # Panorama overlay, stretched over the title screen: scanlines, dark
    # vignette and a red glow rising from the bottom edge.
    s = 512
    ov = new(s, s)
    for y in range(s):
        for x in range(s):
            u, v = x / (s - 1), y / (s - 1)
            d = math.hypot((u - 0.5) * 1.2, (v - 0.42) * 1.1)
            vig = max(0.0, min(1.0, (d - 0.22) / 0.55))
            vig = vig * vig * (3 - 2 * vig)
            glow = max(0.0, (v - 0.7) / 0.3) ** 2
            c = lerp(lerp(BG, RED, glow * 0.5), BLACK, vig * 0.6)
            a = 60 + 160 * vig + 40 * glow
            if y % 2 == 0:
                a += 28
            ov.putpixel((x, y), rgba(c, int(min(235, a))))
    path = os.path.join(dst, "background", "panorama_overlay.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    ov.save(path, optimize=True)


# --------------------------------------------------------------------------
# Pack icon
# --------------------------------------------------------------------------
def build_icon():
    s = 64
    img = Image.new("RGBA", (s, s), rgba(BG))
    for y in range(0, s, 2):
        for x in range(s):
            img.putpixel((x, y), rgba(BLACK))
    # hazard stripe band at the bottom
    for y in range(50, 58):
        for x in range(s):
            img.putpixel((x, y), rgba(YELLOW if ((x + y) // 4) % 2 == 0 else BLACK))
    # chamfered yellow frame
    frame_rect(img, 4, 4, 59, 45, rgba(YELLOW))
    rect(img, 5, 5, 8, 7, rgba(RED))
    # "C//" glyph
    rect(img, 14, 14, 17, 35, rgba(YELLOW))
    rect(img, 14, 14, 28, 17, rgba(YELLOW))
    rect(img, 14, 32, 28, 35, rgba(YELLOW))
    for i in range(22):
        for t in range(3):
            img.putpixel((34 + i // 2 + t, 35 - i), rgba(RED))
            img.putpixel((42 + i // 2 + t, 35 - i), rgba(RED))
    chamfer(img, 8, ("br", "tl"))
    img = chroma_split(img, 1)
    img = img.resize((128, 128), Image.NEAREST)
    img.save(os.path.join(ROOT, "pack.png"), optimize=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--vanilla", required=True, help="vanilla 1.21.4 assets/minecraft/textures/gui")
    args = ap.parse_args()

    if os.path.isdir(GUI):
        shutil.rmtree(GUI)
    build_buttons()
    build_sliders()
    build_text_fields()
    build_checkboxes()
    build_tabs()
    build_scrollers()
    build_tooltip()
    build_slot_highlight()
    build_menu_backgrounds()
    n = build_containers(args.vanilla)
    build_title(args.vanilla)
    build_icon()
    print("built widgets + %d container textures" % n)


if __name__ == "__main__":
    main()
