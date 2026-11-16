#!/usr/bin/env python3
"""Рисует иконку приложения (пельмешек на солнечном фоне) векторно, с 4-кратным сглаживанием.
Пишет adaptive-слои (bg/fg/mono) и устаревшие mipmap-png. Запуск: python3 tools_gen_icon.py"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RES = "app/src/main/res/"
S = 4            # суперсэмплинг
N = 432 * S      # холст 108dp * 4
def P(v): return int(round(v * S))

def radial(size, cx, cy, r, c0, c1):
    y, x = np.mgrid[0:size, 0:size]
    d = np.clip(np.hypot(x - cx, y - cy) / r, 0, 1)[..., None]
    a = np.array(c0, float); b = np.array(c1, float)
    return Image.fromarray((a * (1 - d) + b * d).astype(np.uint8), "RGB")

# ---------------- фон ----------------
def make_bg():
    bg = radial(N, N * 0.5, N * 0.46, N * 0.78, (170, 232, 255), (52, 140, 232)).convert("RGBA")
    ov = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    cx, cy = N / 2, N * 0.52
    k = 20
    for i in range(k):
        if i % 2: continue
        a0 = i / k * 2 * math.pi; a1 = (i + 1) / k * 2 * math.pi
        d.polygon([(cx, cy), (cx + math.cos(a0) * N, cy + math.sin(a0) * N), (cx + math.cos(a1) * N, cy + math.sin(a1) * N)], fill=(255, 255, 255, 52))
    bg.alpha_composite(ov)
    # холмы
    d = ImageDraw.Draw(bg)
    d.ellipse([P(-90), P(330), P(250), P(560)], fill=(112, 201, 95, 255))
    d.ellipse([P(150), P(345), P(540), P(580)], fill=(84, 180, 82, 255))
    d.ellipse([P(-90), P(330), P(250), P(350)], fill=(150, 225, 120, 120))
    # конфетти и звёздочки
    rnd = [(60, 70, (255, 255, 255), 7), (370, 90, (255, 255, 255), 6), (45, 200, (255, 214, 79), 8), (392, 215, (124, 214, 255), 8),
           (96, 120, (186, 156, 255), 6), (340, 150, (255, 105, 150), 6), (70, 300, (255, 255, 255), 5), (380, 320, (255, 255, 255), 5)]
    for x, y, c, r in rnd:
        d.ellipse([P(x - r), P(y - r), P(x + r), P(y + r)], fill=c + (255,))
    return bg.convert("RGB").resize((432, 432), Image.LANCZOS)

# ---------------- пельмешек ----------------
OUT = (122, 72, 38, 255)
def blob(d, box, fill, w=6):
    x0, y0, x1, y1 = box
    d.ellipse([P(x0 - w), P(y0 - w), P(x1 + w), P(y1 + w)], fill=OUT)
    d.ellipse([P(x0), P(y0), P(x1), P(y1)], fill=fill)

def star(d, cx, cy, r, col):
    pts = []
    for i in range(8):
        a = i / 8 * 2 * math.pi - math.pi / 2
        rr = r if i % 2 == 0 else r * 0.38
        pts.append((P(cx + math.cos(a) * rr), P(cy + math.sin(a) * rr)))
    d.polygon(pts, fill=col)

def make_char(mono=False):
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx = 216
    cream = (255, 228, 165, 255); cream_d = (236, 184, 112, 255)
    if mono:
        cream = cream_d = (255, 255, 255, 255)
    # тень под героем
    if not mono:
        sh = Image.new("RGBA", (N, N), (60, 40, 20, 0))
        ImageDraw.Draw(sh).ellipse([P(120), P(318), P(312), P(346)], fill=(60, 40, 20, 90))
        im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(P(5))))
        d = ImageDraw.Draw(im)
    # ручки (поднятые) и ножки
    blob(d, (160, 304, 212, 340), cream_d)
    blob(d, (220, 304, 272, 340), cream_d)
    # тело
    blob(d, (cx - 112, 132, cx + 112, 330), cream, 7)
    # объём: светлый блик слева сверху и тёплая тень справа снизу
    if not mono:
        hl = Image.new("RGBA", (N, N), (255, 255, 255, 0)); h = ImageDraw.Draw(hl)
        h.ellipse([P(132), P(168), P(176), P(190)], fill=(255, 255, 255, 170))
        mask = Image.new("L", (N, N), 0); ImageDraw.Draw(mask).ellipse([P(cx - 112), P(132), P(cx + 112), P(330)], fill=255)
        sh2 = Image.new("RGBA", (N, N), (214, 150, 80, 0)); s = ImageDraw.Draw(sh2)
        s.ellipse([P(150), P(190), P(350), P(380)], fill=(214, 150, 80, 120))
        sh2 = sh2.filter(ImageFilter.GaussianBlur(P(14)))
        body = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        # тень только у нижнего-правого края: вычитаем центральный круг
        cut = Image.new("L", (N, N), 255); ImageDraw.Draw(cut).ellipse([P(100), P(120), P(330), P(300)], fill=0)
        ca = sh2.split()[3].point(lambda v: v)
        sh2.putalpha(Image.composite(ca, Image.new("L", (N, N), 0), cut))
        body.alpha_composite(sh2); body.alpha_composite(hl.filter(ImageFilter.GaussianBlur(P(6))))
        body.putalpha(Image.composite(body.split()[3], Image.new("L", (N, N), 0), mask))
        im.alpha_composite(body); d = ImageDraw.Draw(im)
    # поднятые ручки поверх тела
    for sgn in (-1, 1):
        sx, sy = cx + sgn * 94, 256
        hx, hy = cx + sgn * 118, 192
        d.line([(P(sx), P(sy)), (P(hx), P(hy))], fill=OUT, width=P(40), joint="curve")
        d.ellipse([P(sx - 20), P(sy - 20), P(sx + 20), P(sy + 20)], fill=OUT)
        d.ellipse([P(hx - 20), P(hy - 20), P(hx + 20), P(hy + 20)], fill=OUT)
        d.line([(P(sx), P(sy)), (P(hx), P(hy))], fill=cream, width=P(28), joint="curve")
        d.ellipse([P(sx - 14), P(sy - 14), P(sx + 14), P(sy + 14)], fill=cream)
        d.ellipse([P(hx - 14), P(hy - 14), P(hx + 14), P(hy + 14)], fill=cream)
    # защипы сверху: лучики от узелка
    kx, ky = cx, 152
    for a in range(-150, -29, 20):
        r0, r1 = 20, 66
        t = math.radians(a)
        x0, y0 = kx + math.cos(t) * r0, ky + 6 + math.sin(t) * r0 * 0.55
        x1, y1 = kx + math.cos(t) * r1 * 1.25, ky + 6 + math.sin(t) * r1 * 0.9 + 22
        d.line([(P(x0), P(y0)), (P((x0 + x1) / 2 + math.cos(t + 1.57) * 6), P((y0 + y1) / 2 + math.sin(t + 1.57) * 6)), (P(x1), P(y1))], fill=(196, 140, 84, 255) if not mono else (0, 0, 0, 0), width=P(5), joint="curve")
    blob(d, (cx - 24, ky - 13, cx + 24, ky + 15), cream_d)
    d.ellipse([P(cx - 8), P(ky - 6), P(cx + 8), P(ky + 6)], fill=(255, 220, 160, 255) if not mono else (0, 0, 0, 0))
    # лицо
    dark = (59, 36, 24, 255)
    eye = (0, 0, 0, 0) if mono else dark
    for ex in (cx - 40, cx + 40):
        d.ellipse([P(ex - 17), P(222), P(ex + 17), P(262)], fill=eye)
        if not mono:
            d.ellipse([P(ex - 8), P(228), P(ex + 3), P(240)], fill=(255, 255, 255, 255))
            d.ellipse([P(ex + 3), P(246), P(ex + 9), P(252)], fill=(255, 255, 255, 220))
    if not mono:
        for ex in (cx - 68, cx + 68):
            ch = Image.new("RGBA", (N, N), (0, 0, 0, 0))
            ImageDraw.Draw(ch).ellipse([P(ex - 20), P(258), P(ex + 20), P(282)], fill=(255, 128, 140, 170))
            im.alpha_composite(ch.filter(ImageFilter.GaussianBlur(P(3)))); d = ImageDraw.Draw(im)
    mouth = (0, 0, 0, 0) if mono else (198, 40, 40, 255)
    d.pieslice([P(cx - 24), P(254), P(cx + 24), P(300)], 0, 180, fill=mouth)
    if not mono:
        d.pieslice([P(cx - 13), P(276), P(cx + 13), P(298)], 0, 180, fill=(255, 123, 138, 255))
        d.arc([P(cx - 24), P(254), P(cx + 24), P(300)], 0, 180, fill=OUT, width=P(4))
        # звёздочки-искры вокруг
        for x, y, r in ((74, 128, 15), (360, 120, 13), (52, 262, 9), (378, 270, 10)):
            star(d, x, y, r, (255, 255, 255, 255))
        star(d, 332, 92, 11, (255, 214, 79, 255)); star(d, 92, 92, 9, (255, 214, 79, 255))
    return im.resize((432, 432), Image.LANCZOS)

bg = make_bg()
fg = make_char()
mono = make_char(True)
bg.save(RES + "drawable-nodpi/ic_launcher_bg.png", optimize=True)
fg.save(RES + "drawable-nodpi/ic_launcher_fg.png", optimize=True)
# монохромный слой: только силуэт (альфа), без звёздочек
mono.save(RES + "drawable-nodpi/ic_launcher_mono.png", optimize=True)

# устаревшие значки (до API 26)
full = bg.convert("RGBA"); full.alpha_composite(fg)
for dens, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    big = full.resize((px * 4, px * 4), Image.LANCZOS)
    for name, kind in (("ic_launcher", "sq"), ("ic_launcher_round", "round")):
        m = Image.new("L", big.size, 0); md = ImageDraw.Draw(m)
        if kind == "round": md.ellipse([0, 0, big.size[0] - 1, big.size[1] - 1], fill=255)
        else: md.rounded_rectangle([0, 0, big.size[0] - 1, big.size[1] - 1], radius=int(big.size[0] * 0.22), fill=255)
        o = big.copy(); o.putalpha(m)
        o.resize((px, px), Image.LANCZOS).save(RES + f"mipmap-{dens}/{name}.png", optimize=True)
full.resize((512, 512), Image.LANCZOS).save("/tmp/claude-0/-home-claude/a225bbe0-499a-5ee0-9029-6fa81a894ba5/scratchpad/icon_new.png")
print("ok")
