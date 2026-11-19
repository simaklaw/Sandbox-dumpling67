#!/usr/bin/env python3
"""Контрольный лист: вырезанные PNG на цветных подложках. qa.py <out.png> <cut1.png> ..."""
import sys
from PIL import Image
out, files = sys.argv[1], sys.argv[2:]
cols = [(255, 60, 160), (40, 200, 90), (250, 215, 60), (70, 110, 240)]
t = 300
W = Image.new("RGB", (t * 4, t * ((len(files) + 3) // 4)), (255, 255, 255))
for i, f in enumerate(files):
    im = Image.open(f).convert("RGBA").resize((t, t), Image.LANCZOS)
    b = Image.new("RGBA", (t, t), cols[i % 4] + (255,))
    b.alpha_composite(im)
    W.paste(b.convert("RGB"), ((i % 4) * t, (i // 4) * t))
W.save(out)
