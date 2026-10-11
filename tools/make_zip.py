#!/usr/bin/env python3
"""Packs the resource pack into a ready-to-use zip (CYBER-UI-1.21.4.zip)."""

import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "CYBER-UI-1.21.4.zip")


def main():
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        for name in ("pack.mcmeta", "pack.png"):
            z.write(os.path.join(ROOT, name), name)
        for base, _, files in os.walk(os.path.join(ROOT, "assets")):
            for f in sorted(files):
                path = os.path.join(base, f)
                # forward slashes inside the zip, as Minecraft expects
                z.write(path, os.path.relpath(path, ROOT).replace(os.sep, "/"))
    print(OUT, os.path.getsize(OUT) // 1024, "KB")


if __name__ == "__main__":
    main()
