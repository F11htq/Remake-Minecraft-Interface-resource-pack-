"""Boss bar frames bundled in the CYBER//UI mod.

Raid and generic frames, drawn in code (the other bosses come from
tools/art/boss_bars_source.png via boss_art.py). Each style is a 256x64 back layer (drawn under the health fill) and a front
layer (drawn over it). The bar slot is at x 37..218, y 30..34.
"""

import math
import random

from PIL import Image, ImageDraw

from build_pack import BLACK, YELLOW, RED, CYAN, WHITE, rgba, new, rect, lerp
from build_mod_assets import text

# --------------------------------------------------------------------------
# Boss bar frames: ornate per-boss compositions (256x64, bar slot at x 37..218, y 30..34)
# --------------------------------------------------------------------------

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


def build():
    """Hand-drawn frames for raids and for every boss without its own artwork."""
    return {"raid": frame_raid(), "generic": frame_generic()}
