#!/usr/bin/env python3
"""Builds the "Nebula UI" resource pack textures for Minecraft 1.21.4.

Only menus and screens are touched: buttons, sliders, text fields, checkboxes,
tabs, scroll bars, tooltips, slot highlights, menu backgrounds and every
container / inventory screen. The in-game HUD and the world stay vanilla.

Usage:
    python3 tools/build_pack.py --vanilla <path to an extracted 1.21.4 client
    'assets/minecraft/textures/gui' folder>

The vanilla folder is used only as a layout reference for container screens:
their grey palette is re-mapped to the Nebula palette so that every slot,
arrow and progress bar stays exactly where the game expects it.
"""

import argparse
import json
import math
import os
import shutil

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI = os.path.join(ROOT, "assets", "minecraft", "textures", "gui")
SPR = os.path.join(GUI, "sprites")

# --------------------------------------------------------------------------
# Palette
# --------------------------------------------------------------------------
OUTLINE = (7, 9, 14)
PANEL_TOP = (38, 44, 63)
PANEL_BOT = (26, 30, 45)
BEVEL_HI = (62, 72, 99)
BEVEL_LO = (16, 19, 28)
SLOT = (14, 17, 25)
SLOT_SH = (8, 10, 15)
FIELD = (10, 12, 19)
EDGE = (52, 61, 86)

CYAN = (79, 209, 255)
VIOLET = (155, 107, 255)
PINK = (255, 107, 214)
ACCENT_LOOP = [CYAN, VIOLET, PINK, VIOLET]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def loop_color(t):
    """Smooth colour on the cyan -> violet -> pink -> violet loop, t in [0, 1)."""
    n = len(ACCENT_LOOP)
    p = (t % 1.0) * n
    i = int(p)
    f = p - i
    f = f * f * (3 - 2 * f)  # smoothstep for an eased feel
    return lerp(ACCENT_LOOP[i], ACCENT_LOOP[(i + 1) % n], f)


def pulse(t):
    """0..1..0 eased pulse for t in [0, 1)."""
    return 0.5 - 0.5 * math.cos(t * 2 * math.pi)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def new(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def save(img, rel, meta=None):
    path = os.path.join(SPR if not rel.startswith("@") else GUI, rel.lstrip("@"))
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


def animated(frames, frametime=2, interpolate=True, extra=None):
    """Stacks frames vertically and returns (image, mcmeta)."""
    w, h = frames[0].size
    strip = new(w, h * len(frames))
    for i, fr in enumerate(frames):
        strip.paste(fr, (0, i * h))
    meta = {"animation": {"frametime": frametime, "interpolate": interpolate}}
    if w != h:
        meta["animation"]["width"] = w
        meta["animation"]["height"] = h
    if extra:
        meta.update(extra)
    return strip, meta


def rect(img, x0, y0, x1, y1, c):
    """Fills inclusive rect."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), c)


def frame_rect(img, x0, y0, x1, y1, c, round_corners=True):
    for x in range(x0, x1 + 1):
        img.putpixel((x, y0), c)
        img.putpixel((x, y1), c)
    for y in range(y0, y1 + 1):
        img.putpixel((x0, y), c)
        img.putpixel((x1, y), c)
    if round_corners:
        for p in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
            img.putpixel(p, (0, 0, 0, 0))


def vgrad(img, x0, y0, x1, y1, top, bot, a=255):
    for y in range(y0, y1 + 1):
        t = 0 if y1 == y0 else (y - y0) / (y1 - y0)
        c = rgba(lerp(top, bot, t), a)
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), c)


# --------------------------------------------------------------------------
# Buttons
# --------------------------------------------------------------------------
def button_base(w, h, fill_top, fill_bot, edge, hi, lo):
    img = new(w, h)
    vgrad(img, 1, 1, w - 2, h - 2, fill_top, fill_bot)
    for x in range(2, w - 2):
        img.putpixel((x, 1), rgba(hi))
        img.putpixel((x, h - 2), rgba(lo))
    frame_rect(img, 0, 0, w - 1, h - 1, rgba(edge))
    # soften the inner corners
    for p in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        img.putpixel(p, rgba(lerp(edge, fill_top, 0.5)))
    return img


def build_buttons():
    w, h = 200, 20
    save(button_base(w, h, (44, 52, 74), (30, 35, 52), (12, 15, 22), (66, 77, 106), (22, 26, 38)),
         "widget/button.png", nine(w, h, 3))

    frames = []
    n = 16
    for i in range(n):
        acc = loop_color(i / n)
        img = button_base(w, h, lerp((54, 64, 92), acc, 0.12), lerp((36, 42, 62), acc, 0.18),
                          acc, lerp((86, 98, 132), acc, 0.35), lerp(acc, (0, 0, 0), 0.35))
        frames.append(img)
    strip, meta = animated(frames, frametime=3, extra=nine(w, h, 3))
    save(strip, "widget/button_highlighted.png", meta)

    save(button_base(w, h, (24, 27, 38), (20, 23, 33), (12, 14, 20), (30, 34, 47), (17, 19, 27)),
         "widget/button_disabled.png", nine(w, h, 3))


# --------------------------------------------------------------------------
# Sliders
# --------------------------------------------------------------------------
def slider_track(edge, inner_top):
    w, h = 200, 20
    img = new(w, h)
    vgrad(img, 1, 1, w - 2, h - 2, inner_top, FIELD)
    frame_rect(img, 0, 0, w - 1, h - 1, rgba(edge))
    return img


def slider_handle(top, bot, edge, shine):
    w, h = 8, 20
    img = new(w, h)
    vgrad(img, 1, 1, w - 2, h - 2, top, bot)
    for y in range(2, h - 3):
        img.putpixel((1, y), rgba(shine))
    frame_rect(img, 0, 0, w - 1, h - 1, rgba(edge))
    return img


def build_sliders():
    handle_border = {"left": 2, "top": 2, "right": 2, "bottom": 3}
    save(slider_track(EDGE, (20, 24, 35)), "widget/slider.png", nine(200, 20, 1))

    n = 16
    frames = [slider_track(loop_color(i / n), (24, 29, 42)) for i in range(n)]
    strip, meta = animated(frames, frametime=3, extra=nine(200, 20, 1))
    save(strip, "widget/slider_highlighted.png", meta)

    save(slider_handle(CYAN, VIOLET, OUTLINE, (190, 236, 255)),
         "widget/slider_handle.png", nine(8, 20, handle_border))

    frames = []
    for i in range(n):
        p = pulse(i / n)
        frames.append(slider_handle(lerp(CYAN, (220, 248, 255), 0.25 + 0.45 * p),
                                    lerp(VIOLET, PINK, p), lerp(OUTLINE, (255, 255, 255), 0.15 * p),
                                    (255, 255, 255)))
    strip, meta = animated(frames, frametime=2, extra=nine(8, 20, handle_border))
    save(strip, "widget/slider_handle_highlighted.png", meta)


# --------------------------------------------------------------------------
# Text fields
# --------------------------------------------------------------------------
def text_field(edge):
    w, h = 200, 20
    img = new(w, h)
    rect(img, 1, 1, w - 2, h - 2, rgba(FIELD, 245))
    frame_rect(img, 0, 0, w - 1, h - 1, rgba(edge), round_corners=False)
    return img


def build_text_fields():
    save(text_field(EDGE), "widget/text_field.png", nine(200, 20, 1))
    n = 16
    frames = [text_field(loop_color(i / n)) for i in range(n)]
    strip, meta = animated(frames, frametime=3, extra=nine(200, 20, 1))
    save(strip, "widget/text_field_highlighted.png", meta)


# --------------------------------------------------------------------------
# Checkboxes
# --------------------------------------------------------------------------
CHECK = [(5, 10), (6, 11), (7, 12), (8, 13), (9, 12), (10, 11), (11, 10), (12, 9), (13, 8), (14, 7)]


def checkbox(edge, selected, check_color=None, glow=0.0):
    img = new(20, 20)
    rect(img, 1, 1, 18, 18, rgba(lerp(FIELD, edge, glow * 0.15)))
    frame_rect(img, 0, 0, 19, 19, rgba(edge))
    if selected:
        for x, y in CHECK:
            for dy in (0, 1):
                img.putpixel((x, y + dy), rgba(check_color))
    return img


def build_checkboxes():
    save(checkbox(EDGE, False), "widget/checkbox.png")
    save(checkbox(EDGE, True, CYAN), "widget/checkbox_selected.png")
    n = 16
    for name, sel in (("checkbox_highlighted", False), ("checkbox_selected_highlighted", True)):
        frames = []
        for i in range(n):
            acc = loop_color(i / n)
            frames.append(checkbox(acc, sel, lerp(acc, (255, 255, 255), 0.35), glow=1.0))
        strip, meta = animated(frames, frametime=3)
        save(strip, "widget/%s.png" % name, meta)


# --------------------------------------------------------------------------
# Tabs (create world screen)
# --------------------------------------------------------------------------
def tab(edge, inner, fill_alpha):
    w, h = 130, 24
    img = new(w, h)
    if fill_alpha:
        vgrad(img, 2, 2, w - 3, h - 1, lerp(PANEL_TOP, inner, 0.15), PANEL_BOT, fill_alpha)
    for x in range(1, w - 1):
        img.putpixel((x, 0), rgba(edge))
        img.putpixel((x, 1), rgba(inner))
    for y in range(1, h):
        img.putpixel((0, y), rgba(edge))
        img.putpixel((w - 1, y), rgba(edge))
        img.putpixel((1, y), rgba(inner))
        img.putpixel((w - 2, y), rgba(inner))
    return img


def build_tabs():
    border = {"left": 2, "top": 2, "right": 2, "bottom": 0}
    save(tab(OUTLINE, (40, 47, 66), 0), "widget/tab.png", nine(130, 24, border))
    save(tab(OUTLINE, CYAN, 140), "widget/tab_selected.png", nine(130, 24, border))
    n = 16
    for name, alpha in (("tab_highlighted", 60), ("tab_selected_highlighted", 160)):
        frames = [tab(OUTLINE, loop_color(i / n), alpha) for i in range(n)]
        strip, meta = animated(frames, frametime=3, extra=nine(130, 24, border))
        save(strip, "widget/%s.png" % name, meta)


# --------------------------------------------------------------------------
# Scroll bars
# --------------------------------------------------------------------------
def build_scrollers():
    w, h = 6, 32
    bg = new(w, h)
    rect(bg, 0, 0, w - 1, h - 1, rgba(FIELD, 220))
    frame_rect(bg, 0, 0, w - 1, h - 1, rgba(OUTLINE), round_corners=False)
    save(bg, "widget/scroller_background.png", nine(w, h, 1))

    th = new(w, h)
    rect(th, 0, 0, w - 1, h - 1, rgba((112, 128, 170)))
    for y in range(h):
        th.putpixel((w - 1, y), rgba((70, 82, 115)))
    for x in range(w):
        th.putpixel((x, h - 1), rgba((70, 82, 115)))
        th.putpixel((x, 0), rgba((150, 166, 210)))
    save(th, "widget/scroller.png", nine(w, h, 1))


# --------------------------------------------------------------------------
# Tooltip
# --------------------------------------------------------------------------
def build_tooltip():
    bg = new(100, 100)
    rect(bg, 8, 8, 91, 91, (11, 13, 21, 238))
    for p in ((8, 8), (91, 8), (8, 91), (91, 91)):
        bg.putpixel(p, (0, 0, 0, 0))
    save(bg, "tooltip/background.png", nine(100, 100, 9))

    n = 24
    frames = []
    for i in range(n):
        t = i / n
        top, bot = loop_color(t), loop_color(t + 0.35)
        fr = new(100, 100)
        for y in range(9, 91):
            c = lerp(top, bot, (y - 9) / 81)
            fr.putpixel((9, y), rgba(c, 230))
            fr.putpixel((90, y), rgba(c, 230))
        for x in range(10, 90):
            fr.putpixel((x, 9), rgba(top, 230))
            fr.putpixel((x, 90), rgba(bot, 230))
        for p in ((9, 9), (90, 9), (9, 90), (90, 90)):
            fr.putpixel(p, (0, 0, 0, 0))
        # soft outer glow
        for x in range(10, 90):
            fr.putpixel((x, 8), rgba(top, 60))
            fr.putpixel((x, 91), rgba(bot, 60))
        for y in range(10, 90):
            c = lerp(top, bot, (y - 9) / 81)
            fr.putpixel((8, y), rgba(c, 60))
            fr.putpixel((91, y), rgba(c, 60))
        frames.append(fr)
    strip, meta = animated(frames, frametime=2, extra=nine(100, 100, 10, stretch_inner=True))
    save(strip, "tooltip/frame.png", meta)


# --------------------------------------------------------------------------
# Slot hover highlight
# --------------------------------------------------------------------------
def build_slot_highlight():
    n = 16
    back, front = [], []
    for i in range(n):
        p = pulse(i / n)
        acc = loop_color(i / n)
        b = new(24, 24)
        # glow halo spilling 1-3px outside the 16x16 slot
        for d, a in ((3, 18), (2, 38), (1, 70)):
            frame_rect(b, 4 - d, 4 - d, 19 + d, 19 + d, rgba(acc, int(a * (0.6 + 0.4 * p))))
        rect(b, 4, 4, 19, 19, rgba(acc, int(55 + 35 * p)))
        frame_rect(b, 4, 4, 19, 19, rgba(acc, 200), round_corners=False)
        back.append(b)

        f = new(24, 24)
        rect(f, 4, 4, 19, 19, (255, 255, 255, int(14 + 18 * p)))
        frame_rect(f, 4, 4, 19, 19, rgba(lerp(acc, (255, 255, 255), 0.4), int(140 + 80 * p)),
                   round_corners=False)
        front.append(f)
    for name, frames in (("slot_highlight_back", back), ("slot_highlight_front", front)):
        strip, meta = animated(frames, frametime=2, extra=nine(24, 24, 4))
        save(strip, "container/%s.png" % name, meta)


# --------------------------------------------------------------------------
# Menu backgrounds and separators (plain textures, not sprites)
# --------------------------------------------------------------------------
def build_menu_backgrounds():
    def tile(base, alpha, dot_alpha):
        img = Image.new("RGBA", (16, 16), rgba(base, alpha))
        for (x, y) in ((0, 0), (8, 8)):
            img.putpixel((x, y), rgba(lerp(base, CYAN, 0.35), dot_alpha))
        return img

    save(tile((8, 10, 22), 150, 170), "@menu_background.png")
    save(tile((8, 10, 22), 110, 130), "@inworld_menu_background.png")
    save(Image.new("RGBA", (16, 16), (6, 8, 16, 150)), "@menu_list_background.png")
    save(Image.new("RGBA", (16, 16), (6, 8, 16, 130)), "@inworld_menu_list_background.png")

    for prefix in ("", "inworld_"):
        hdr = new(32, 2)
        ftr = new(32, 2)
        for x in range(32):
            hdr.putpixel((x, 0), rgba(CYAN, 170))
            hdr.putpixel((x, 1), (0, 0, 0, 170))
            ftr.putpixel((x, 0), (0, 0, 0, 170))
            ftr.putpixel((x, 1), rgba(VIOLET, 170))
        save(hdr, "@%sheader_separator.png" % prefix)
        save(ftr, "@%sfooter_separator.png" % prefix)


# --------------------------------------------------------------------------
# Container screens: palette re-map of the vanilla layout
# --------------------------------------------------------------------------
GREY_KEYS = [
    (0, OUTLINE),
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
                # near-grey detail: map by luminance, keep a hint of the hue
                lum = int(0.299 * r + 0.587 * g + 0.114 * b)
                po[x, y] = rgba(map_grey(lum, panel), a)

    if accent:
        # neon line on the topmost bevel of the panel
        xs = [x for x in range(w) if any(px[x, y][3] for y in range(h))]
        x0, x1 = (min(xs), max(xs)) if xs else (0, 0)
        for x in range(w):
            for y in range(h):
                if px[x, y][3]:
                    if y <= y_min + 1 and y + 1 < h and px[x, y + 1][:3] == (255, 255, 255):
                        t = 0 if x1 == x0 else (x - x0) / (x1 - x0)
                        po[x, y + 1] = rgba(lerp(CYAN, VIOLET, t))
                    break
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    out.save(dst, optimize=True)


def build_containers(vanilla):
    count = 0
    plain = ["container", "container/creative_inventory"]
    for d in plain:
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
# Pack icon
# --------------------------------------------------------------------------
def build_icon():
    s = 64
    img = new(s, s)
    for y in range(s):
        for x in range(s):
            dx, dy = x - 31.5, y - 31.5
            # rounded square
            qx, qy = max(abs(dx) - 22, 0), max(abs(dy) - 22, 0)
            d = math.hypot(qx, qy)
            if d > 8:
                continue
            t = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            if d > 6.5:
                img.putpixel((x, y), rgba(loop_color(t)))
            else:
                img.putpixel((x, y), rgba(lerp(PANEL_TOP, PANEL_BOT, y / s)))
    # mini window: title bar + three "buttons"
    rect(img, 14, 14, 49, 17, rgba(CYAN))
    for i, c in enumerate((CYAN, VIOLET, PINK)):
        y = 24 + i * 9
        rect(img, 16, y, 47, y + 5, rgba(lerp(PANEL_TOP, c, 0.25)))
        frame_rect(img, 15, y - 1, 48, y + 6, rgba(c))
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
    build_icon()
    print("built widgets + %d container textures" % n)


if __name__ == "__main__":
    main()
