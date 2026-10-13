#!/usr/bin/env python3
"""Cuts the boss bar artwork (tools/art/boss_bars_source.png) into mod textures.

For every boss three textures are written into the mod:
  boss_<name>.png        256x64 back layer, the bar slot cleared
  boss_<name>_front.png  256x64 front layer: the centre emblem, drawn over the fill
  boss_<name>_fill.png   182x5 fill texture taken from the artwork's own bar

The slot always lands at x 37..218, y 30..34 so the mod can use one layout.
"""

import numpy as np
from PIL import Image

FRAME_W, FRAME_H = 256, 64
BAR_X, BAR_Y, BAR_W, BAR_H = 37, 30, 182, 5

# name -> bounding box of the bar (with its label) in the source sheet
BOXES = {
    "dragon": (30, 27, 747, 184),
    "wither": (779, 27, 1454, 184),
    "warden": (34, 197, 743, 342),
    "golem": (779, 197, 1459, 342),
    "wskeleton": (33, 352, 743, 487),
    "ravager": (779, 352, 1466, 487),
    "evoker": (34, 501, 726, 626),
    "guardian": (784, 501, 1453, 626),
}


def runs(mask):
    out, start = [], None
    for i, v in enumerate(mask):
        if v and start is None:
            start = i
        elif not v and start is not None:
            out.append((start, i))
            start = None
    if start is not None:
        out.append((start, len(mask)))
    return out


# name plates baked into the artwork (x0, y0, x1, y1 in box pixels); the mod draws its own name
LABELS = {
    "dragon": (205, 0, 510, 43),
    "wither": (218, 0, 442, 44),
    "warden": (288, 0, 427, 39),
    "golem": (236, 0, 424, 49),
    "wskeleton": (208, 0, 522, 38),
    "ravager": (232, 0, 428, 35),
    "evoker": (276, 0, 454, 33),
    "guardian": (220, 0, 440, 34),
}

# slot ends (x0, x1 in box pixels): where the fill meets the arrowheads
SLOT_X = {
    "dragon": (75, 640),
    "wither": (80, 600),
    "warden": (78, 625),
    "golem": (75, 605),
    "wskeleton": (100, 620),
    "ravager": (105, 590),
    "evoker": (105, 605),
    "guardian": (90, 598),
}


def remove_label(a, name):
    x0, y0, x1, y1 = LABELS[name]
    a[y0:y1, x0:x1] = 0
    return a


def find_slot(a):
    """Returns (y0, y1, x0, x1, gap0, gap1): the inner bar slot and the emblem gap, in source pixels.

    The shaft is the band of rows that is opaque across the bar's straight parts;
    the slot ends where the shaft widens into the arrowheads and is interrupted
    where it widens into the centre emblem.
    """
    h, w = a.shape[:2]
    op = a[:, :, 3] > 100
    probe = np.r_[int(w * 0.22):int(w * 0.38), int(w * 0.62):int(w * 0.78)]
    full = op[:, probe].mean(axis=1) > 0.97
    s0, s1 = max(runs(full), key=lambda r: r[1] - r[0])
    thick = s1 - s0
    cy = (s0 + s1) // 2

    def extent(x):
        if not op[cy, x]:
            return 0
        top = cy
        while top > 0 and op[top - 1, x]:
            top -= 1
        bot = cy
        while bot < h - 1 and op[bot + 1, x]:
            bot += 1
        return bot - top + 1

    ext = np.array([extent(x) for x in range(w)])
    raw = ext > thick * 1.35
    # ignore particles touching the shaft: only a run of wide columns counts
    wide = np.zeros(w, bool)
    for (s, e) in runs(raw):
        if e - s >= 6:
            wide[s:e] = True
    x0 = int(w * 0.22)
    while x0 > 0 and not wide[x0 - 1]:
        x0 -= 1
    x1 = int(w * 0.78)
    while x1 < w - 1 and not wide[x1 + 1]:
        x1 += 1
    gap0 = int(w * 0.38)
    while gap0 < w // 2 and not wide[gap0 + 1]:
        gap0 += 1
    gap1 = int(w * 0.62)
    while gap1 > w // 2 and not wide[gap1 - 1]:
        gap1 -= 1
    # inner fill: the middle of the shaft
    pad = max(1, round(thick * 0.28))
    y0, y1 = s0 + pad, s1 - pad
    # the fill colour usually runs on into the arrowheads: follow it outwards
    band = a[y0:y1, :, :3].astype(int)
    colour = np.median(band[:, int(w * 0.26):int(w * 0.36)].reshape(-1, 3), axis=0)
    match = ((np.abs(band - colour).sum(axis=2) < 120) & op[y0:y1]).mean(axis=0) > 0.6

    def follow(x, step, limit):
        miss = 0
        while 0 < x < w - 1 and (step < 0 and x > limit or step > 0 and x < limit):
            miss = 0 if match[x + step] else miss + 1
            if miss > 3:
                return x - step * 3
            x += step
        return x
    x0 = min(x0 + 2, follow(int(w * 0.3), -1, int(w * 0.04)))
    x1 = max(x1 - 1, follow(int(w * 0.7), 1, int(w * 0.96)))
    return y0, y1, x0, x1, gap0, gap1


def premul_resize(img, size):
    return img.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def cut(src, name, box):
    a = np.array(src.crop(box)).copy()
    a = remove_label(a, name)
    y0, y1, x0, x1, gap0, gap1 = find_slot(a)
    x0, x1 = SLOT_X[name]
    h, w = a.shape[:2]
    s = BAR_W / float(x1 - x0)
    full = Image.fromarray(a)
    scaled = premul_resize(full, (max(1, round(w * s)), max(1, round(h * s))))
    ox = round(BAR_X - x0 * s)
    oy = round(BAR_Y + BAR_H / 2.0 - (y0 + y1) / 2.0 * s)

    layer = Image.new("RGBA", (FRAME_W, FRAME_H), (0, 0, 0, 0))
    layer.paste(scaled, (ox, oy))
    arr = np.array(layer)
    arr[arr[:, :, 3] < 40] = 0
    arr[:, :, 3] = np.where(arr[:, :, 3] > 0, np.maximum(arr[:, :, 3], 200), 0)

    # front: the emblem columns around the centre gap
    g0 = int(ox + gap0 * s) - 1
    g1 = int(ox + gap1 * s) + 2
    front = np.zeros_like(arr)
    front[:, g0:g1] = arr[:, g0:g1]
    # back: everything, with the slot cleared to a dark recess
    back = arr.copy()
    back[BAR_Y:BAR_Y + BAR_H, BAR_X:BAR_X + BAR_W] = (6, 5, 10, 255)

    # fill texture: the artwork's band, emblem gap patched with a copy of its left side
    band = Image.fromarray(a[y0:y1, x0:x1])
    bw = x1 - x0
    gl, gr = gap0 - x0, gap1 - x0
    patched = band.copy()
    if gr > gl:
        src_w = gr - gl
        piece = band.crop((max(0, gl - src_w), 0, gl, band.height))
        patched.paste(piece.resize((src_w, band.height)), (gl, 0))
    fill = premul_resize(patched, (BAR_W, BAR_H))
    fa = np.array(fill)
    fa[:, :, 3] = 255
    return Image.fromarray(back), Image.fromarray(front), Image.fromarray(fa)


def build(path):
    src = Image.open(path).convert("RGBA")
    return {name: cut(src, name, box) for name, box in BOXES.items()}
