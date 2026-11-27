#!/usr/bin/env python3
"""Downloads the vanilla title panorama of a Minecraft version.

Since 26.x the panorama faces are served through the asset index instead of
the client jar (the jar only holds 1x1 placeholders).

Usage:
    python3 tools/fetch_panorama.py --version 26.3 --out <dir>

Writes <dir>/<version>/panorama_0..5.png.
"""

import argparse
import json
import os
import urllib.request

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
OBJECTS = "https://resources.download.minecraft.net"


def get_json(url):
    with urllib.request.urlopen(url) as r:
        return json.load(r)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default="26.3")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    entry = next(v for v in get_json(MANIFEST)["versions"] if v["id"] == args.version)
    index = get_json(get_json(entry["url"])["assetIndex"]["url"])["objects"]
    out = os.path.join(args.out, args.version)
    os.makedirs(out, exist_ok=True)
    for i in range(6):
        h = index["minecraft/textures/gui/title/background/panorama_%d.png" % i]["hash"]
        urllib.request.urlretrieve("%s/%s/%s" % (OBJECTS, h[:2], h), os.path.join(out, "panorama_%d.png" % i))
    print("panorama", args.version, "->", out)


if __name__ == "__main__":
    main()
