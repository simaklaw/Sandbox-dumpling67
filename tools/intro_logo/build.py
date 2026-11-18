#!/usr/bin/env python3
"""Картинки заставки и иконка приложения из рендеров «Детской площадки».

Запуск: python3 tools/intro_logo/build.py <папка с исходниками>
В папке: cradle.jpg (колыбелька), assembly.jpg (сборка), final.jpg (финал с пельмешком),
logo_cutout.jpg (логотип с пельмешком на «шахматке»: из него вырезается пельмешек для иконки).
Нужны Pillow, numpy, scipy.
"""
import os, sys
import numpy as np
from PIL import Image, ImageFilter, ImageDraw
from scipy import ndimage as ndi

SRC = sys.argv[1]
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../app/src/main/res")
NODPI = os.path.join(RES, "drawable-nodpi")


def load(n):
    return Image.open(os.path.join(SRC, n)).convert("RGB")


# --- заставка: три кадра как webp (кадры уже с небом, края растушёвывает код) ---
for name, out, size in (("cradle.jpg", "intro_cradle", 1024), ("assembly.jpg", "intro_assembly", 1024), ("final.jpg", "intro_final", 1024)):
    im = load(name)
    if im.width != size:
        im = im.resize((size, round(im.height * size / im.width)), Image.LANCZOS)
    im.save(os.path.join(NODPI, out + ".webp"), "WEBP", quality=82, method=6)
    print(out, im.size)

# --- иконка: пельмешек вырезается по цвету (жёлто-оранжевый), фон «шахматка» и тёмно-синий отсекаются ---
im = load("logo_cutout.jpg")
box = (330, 610, 700, 930)
cr = im.crop(box)
hsv = np.asarray(cr.convert("HSV")).astype(np.float32)
h, s, v = hsv[..., 0] * 360 / 255, hsv[..., 1] / 255, hsv[..., 2] / 255
body = (h > 28) & (h < 58) & (s > 0.3) & (v > 0.72)
body = ndi.binary_closing(body, iterations=6)
lab, n = ndi.label(body)
sizes = ndi.sum(body, lab, range(1, n + 1))
body = lab == (1 + int(np.argmax(sizes)))
body = ndi.binary_fill_holes(body)
core = ndi.binary_opening(body, iterations=12)  # убирает тонкие «хвостики» чужих деталей (рука медведя и т.п.)
body = body & ndi.binary_dilation(core, iterations=14)
body = ndi.binary_opening(body, iterations=3)
body = ndi.binary_dilation(body, iterations=1)
body = ndi.binary_fill_holes(body)
# срезаем узкий «пенёк» сверху: строки, где ширина маски меньше 40 px
widths = body.sum(axis=1)
top = int(np.argmax(widths >= 40))
body[:top, :] = False
alpha = Image.fromarray((body * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.6))
ys, xs = np.nonzero(body)
bb = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
print("пельмешек", bb, "размер", bb[2] - bb[0], "x", bb[3] - bb[1])
rgba = cr.convert("RGBA")
rgba.putalpha(alpha)
dump = rgba.crop(bb)

C = 432
SAFE = 0.60  # адаптивная иконка: значимая часть в круге 66dp из 108dp
tw = int(C * SAFE)
dh = int(dump.height * tw / dump.width)
dump_s = dump.resize((tw, dh), Image.LANCZOS)

# фон: небо
bg = Image.new("RGB", (C, C))
px = bg.load()
for y in range(C):
    t = y / (C - 1)
    r = int(0x7E + (0xCF - 0x7E) * t)
    g = int(0xC4 + (0xEC - 0xC4) * t)
    b = int(0xF5 + (0xFF - 0xF5) * t)
    for x in range(C):
        px[x, y] = (r, g, b)
cl = Image.new("L", (C, C), 0)
dl = ImageDraw.Draw(cl)
for cx, cy, rr in ((90, 120, 46), (340, 90, 38), (110, 360, 30)):  # облачка одним слоем, без видимых швов
    for dx, dy, k in ((0, 0, 1.0), (rr * .8, -rr * .2, .8), (rr * 1.5, 0, .9)):
        dl.ellipse((cx + dx - rr * k, cy + dy - rr * k, cx + dx + rr * k, cy + dy + rr * k), fill=255)
cl = cl.filter(ImageFilter.GaussianBlur(2)).point(lambda v: int(v * 0.55))
bg.paste(Image.new("RGB", (C, C), (255, 255, 255)), (0, 0), cl)
bg.save(os.path.join(NODPI, "ic_launcher_bg.png"))

fg = Image.new("RGBA", (C, C), (0, 0, 0, 0))
pos = ((C - tw) // 2, (C - dh) // 2 + 6)
# мягкая тень под пельмешком
sh = Image.new("RGBA", (C, C), (0, 0, 0, 0))
ImageDraw.Draw(sh).ellipse((pos[0] + tw * .1, pos[1] + dh * .9, pos[0] + tw * .9, pos[1] + dh * 1.02), fill=(20, 40, 90, 90))
fg = Image.alpha_composite(fg, sh.filter(ImageFilter.GaussianBlur(8)))
fg.alpha_composite(dump_s, pos)
fg.save(os.path.join(NODPI, "ic_launcher_fg.png"))

# монохромная иконка (Android 13): силуэт без глаз/рта/щёк
m = np.asarray(dump_s).copy()
hh = np.asarray(dump_s.convert("HSV")).astype(np.float32)
face = (hh[..., 2] / 255 < 0.62) | ((hh[..., 0] * 360 / 255 > 340) | (hh[..., 0] * 360 / 255 < 12)) & (hh[..., 1] / 255 > 0.35)
a = m[..., 3].astype(np.float32)
a[face & (a > 200)] *= 0.0
mono = Image.new("RGBA", (C, C), (0, 0, 0, 0))
ms = Image.fromarray(np.dstack([np.full(a.shape, 255, np.uint8)] * 3 + [a.astype(np.uint8)]), "RGBA")
mono.alpha_composite(ms, pos)
mono.save(os.path.join(NODPI, "ic_launcher_mono.png"))

# старые лаунчеры: квадрат со скруглением и круг
full = bg.convert("RGBA")
full.alpha_composite(fg)
# для старого лаунчера пельмешек крупнее: кадрируем центр
for dens, px_ in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    big = full.crop((int(C * .1), int(C * .1), int(C * .9), int(C * .9))).resize((px_ * 4, px_ * 4), Image.LANCZOS)
    for fn, shape in (("ic_launcher.png", "round_rect"), ("ic_launcher_round.png", "circle")):
        mk = Image.new("L", big.size, 0)
        dd = ImageDraw.Draw(mk)
        if shape == "circle":
            dd.ellipse((0, 0, big.width - 1, big.height - 1), fill=255)
        else:
            dd.rounded_rectangle((0, 0, big.width - 1, big.height - 1), radius=int(big.width * 0.18), fill=255)
        o = big.copy()
        o.putalpha(mk)
        o.resize((px_, px_), Image.LANCZOS).save(os.path.join(RES, f"mipmap-{dens}", fn))
print("иконка готова")
