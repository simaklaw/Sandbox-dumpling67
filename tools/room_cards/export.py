#!/usr/bin/env python3
"""export.py <dir_cut> <dir_res>: NN.png (вырезанные) -> room_<pageIndex>.webp в drawable-nodpi.
Обрезка по видимому содержимому, поля 3%, квадрат с прозрачными полями, 512x512, WebP q=92."""
import os
import sys
from PIL import Image

# порядок имён исходных JPG -> pageIndex комнаты (37-я картинка — логотип, не комната)
PAGES = [49, 1, 35, 32, 36, 51, 6, 42, 39, 7, 9, 37, 40, 46, 38, 41, 47, 21, 22, 20, 23, 0,
         30, 24, 43, 31, 12, 17, 48, 34, 16, 50, 44, 4, 5, 28, 27]
SIZE = 512

src, dst = sys.argv[1], sys.argv[2]
for i, page in enumerate(PAGES):
    im = Image.open(os.path.join(src, f"{i:02d}.png")).convert("RGBA")
    box = im.getchannel("A").point(lambda v: 255 if v > 10 else 0).getbbox()
    im = im.crop(box)
    side = int(max(im.size) * 1.06)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
    sq = sq.resize((SIZE, SIZE), Image.LANCZOS)
    sq.save(os.path.join(dst, f"room_{page}.webp"), "WEBP", quality=92, alpha_quality=100, method=6)
print("ok", len(PAGES))
