#!/usr/bin/env python3
"""Вырезает фон (шахматка, запечённая в JPG) из картинок карточек комнат.

Использование: cutout.py <in.jpg> <out.png>  (PNG с альфой, исходный размер)

Две стадии.
1. Тоны: два тона шахматки берутся по краю кадра, фон = пиксели, близкие к одному
   из них и связанные с краем кадра (быстрая и надёжная грубая маска).
2. Клетки: по грубому фону находится сетка шахматки (grid.py); дальше тона
   оцениваются отдельно для каждой чётности клеток и «ползут» вглубь кадра за
   цветным свечением, а тон привязан к клетке, поэтому предметы случайного цвета
   фон не имитируют. Края получают мягкую альфу, цвет у края берётся из
   ближайшей внутренней точки (без серой каймы).
"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import grid  # noqa: E402

P = 6


def tones(a, border=8):
    """Два тона шахматки (RGB) по краю кадра."""
    lum = a.mean(2)
    m = np.zeros(lum.shape, bool)
    m[:border, :] = m[-border:, :] = m[:, :border] = m[:, -border:] = True
    v, l = a[m], lum[m]
    lo, hi = np.percentile(l, 15), np.percentile(l, 85)
    for _ in range(12):
        mid = (lo + hi) / 2
        lo, hi = l[l < mid].mean(), l[l >= mid].mean()
    mid = (lo + hi) / 2
    return v[l < mid].mean(0), v[l >= mid].mean(0)


def field(a, w, base, sigma):
    """Плавное поле цвета (нормированная свёртка по маске w)."""
    w = w.astype(np.float32)
    den = ndi.gaussian_filter(w, sigma)
    out = np.empty_like(a)
    for c in range(3):
        num = ndi.gaussian_filter(a[..., c] * w, sigma)
        out[..., c] = np.where(den > 1e-3, num / np.maximum(den, 1e-3), base[c])
    return out


def dist(a, f):
    return np.abs(a - f).max(2)


def pad_morph(m, op, it):
    """Морфология с отражением у краёв кадра (иначе фон отрывается от рамки)."""
    return op(np.pad(m, P, mode="edge"), iterations=it)[P:-P, P:-P]


def connect_bg(cand, par_pos, par_neg):
    """Фон = компоненты, связанные с рамкой, + замкнутые «дырки» с обоими тонами."""
    lab, n = ndi.label(cand)
    edge = np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))
    bg = np.isin(lab, edge[edge > 0])
    ids = np.arange(1, n + 1)
    sz = ndi.sum(cand, lab, ids)
    npos = ndi.sum(par_pos & cand, lab, ids)
    nneg = ndi.sum(par_neg & cand, lab, ids)
    # замкнутая дырка = настоящая шахматка, если обе чётности клеток представлены сопоставимо
    # (плоский серый предмет совпадает только с одним тоном и сюда не попадёт)
    balanced = np.minimum(npos, nneg) >= 0.25 * np.maximum(npos, nneg)
    bg |= np.isin(lab, ids[(sz >= 120) & (npos > 20) & (nneg > 20) & balanced])
    return bg


def stage1(a):
    lo_c, hi_c = tones(a)
    flo = np.broadcast_to(lo_c, a.shape).astype(np.float32)
    fhi = np.broadcast_to(hi_c, a.shape).astype(np.float32)
    for tl in (26, 20):
        flo = field(a, dist(a, flo) < tl, lo_c, 40)
        fhi = field(a, dist(a, fhi) < tl, hi_c, 40)
    tight = (dist(a, flo) < 12) | (dist(a, fhi) < 12)
    d = fhi - flo
    t = ((a - flo) * d).sum(2) / np.maximum((d * d).sum(2), 1)
    onseg = (t > -0.1) & (t < 1.1) & (dist(a, flo + t[..., None] * d) < 9)
    dk, lt = tight & (t < 0.5), tight & (t >= 0.5)
    trans = onseg & ndi.binary_dilation(dk, iterations=2) & ndi.binary_dilation(lt, iterations=2)
    cand = pad_morph(tight | trans, ndi.binary_closing, 2)
    return connect_bg(cand, lt, dk)


def stage2(a, bg1, extra=(), cmin=22):
    lum = a.mean(2)
    h, w = lum.shape
    tx, x0, ty, y0, sc = grid.detect(lum, bg1)
    par, line = grid.parity_and_lines(lum.shape, tx, x0, ty, y0)
    pos, neg = par > 0, par < 0
    safe = ~line
    seed = ndi.binary_erosion(bg1, iterations=2) & safe
    sp, sn = seed & pos, seed & neg
    for r in extra:  # ручные «затравки»: прямоугольники (доли кадра) внутри чистой шахматки
        ya, yb, xa, xb = int(r[1] * h), int(r[3] * h), int(r[0] * w), int(r[2] * w)
        sp[ya:yb, xa:xb] |= pos[ya:yb, xa:xb] & safe[ya:yb, xa:xb]
        sn[ya:yb, xa:xb] |= neg[ya:yb, xa:xb] & safe[ya:yb, xa:xb]
    base_p = a[sp].mean(0) if sp.any() else np.array([190.0] * 3)
    base_n = a[sn].mean(0) if sn.any() else np.array([120.0] * 3)
    for it in range(24):
        fp = field(a, sp, base_p, 22)
        fn = field(a, sn, base_n, 22)
        contrast = np.abs(fp - fn).max(2) > cmin
        reach = ndi.binary_dilation(sp | sn, iterations=12)
        sp = pos & safe & reach & contrast & (dist(a, fp) < 16)
        sn = neg & safe & reach & contrast & (dist(a, fn) < 16)
    fp = field(a, sp, base_p, 22)
    fn = field(a, sn, base_n, 22)
    reach = ndi.binary_dilation(sp | sn, iterations=14)
    tight = safe & reach & ((pos & (dist(a, fp) < 11)) | (neg & (dist(a, fn) < 11)))
    # пиксели на границах клеток лежат на отрезке тон-тон
    d = fp - fn
    t = ((a - fn) * d).sum(2) / np.maximum((d * d).sum(2), 1)
    onseg = (t > -0.15) & (t < 1.15) & (dist(a, fn + t[..., None] * d) < 10)
    lineok = line & reach & onseg
    cand = pad_morph(tight | lineok, ndi.binary_closing, 2)
    return connect_bg(cand, pos, neg), (tx, ty, sc), (par, tx, ty)


def cutout(rgb, info=None, extra=(), thin=None, cmin=22, kill=()):
    a = rgb.astype(np.float32)
    h, w = a.shape[:2]
    bg1 = stage1(a)
    bg2, gi, pinfo = stage2(a, bg1, extra, cmin)
    # сетка найдена уверенно -> объединяем стадии; иначе (нестандартная картинка) только тоны
    bg = (bg1 | bg2) if gi[2] >= 12 else bg1
    if info is not None:
        info.update(grid=gi, bg1=float(bg1.mean()), bg2=float(bg2.mean()))
    sat = a.max(2) - a.min(2)
    bgp = pad_morph(bg, ndi.binary_dilation, 1)
    bg = bg | (bgp & (sat < 30))
    fg = ~bg
    lb, nb = ndi.label(~fg)  # мелкие островки фона внутри предмета — это предмет
    if nb:
        sb = ndi.sum(~fg, lb, range(1, nb + 1))
        fg = fg | np.isin(lb, 1 + np.flatnonzero(sb < 150))
    lab2, n2 = ndi.label(fg)  # серые крошки (шум JPEG) убираем, цветные искры оставляем
    if n2:
        ids = 1 + np.arange(n2)
        s2 = ndi.sum(fg, lab2, ids)
        msat = ndi.mean(sat, lab2, ids)
        fg = np.isin(lab2, ids[(s2 >= 1500) | ((s2 >= 200) & (msat > 70))])
    for r in kill:  # принудительно прозрачные прямоугольники (доли кадра)
        fg[int(r[1] * h):int(r[3] * h), int(r[0] * w):int(r[2] * w)] = False
    if thin:  # убрать тонкие «ленты» (стенки стеклянного аквариума): открытие выше доли ymax
        r, ymax = int(thin[0]), thin[1]
        yy, xx = np.ogrid[-r:r + 1, -r:r + 1]
        disk = (yy * yy + xx * xx) <= r * r
        op = ndi.binary_opening(fg, structure=disk)
        top = np.arange(h)[:, None] < ymax * h
        fg = np.where(top, op, fg)
    alpha = ndi.gaussian_filter(fg.astype(np.float32), 0.9)
    alpha = np.clip((alpha - 0.5) * 2.2 + 0.5, 0, 1)
    inner = ndi.binary_erosion(fg, iterations=2)
    idx = ndi.distance_transform_edt(~inner, return_distances=False, return_indices=True)
    col = a[idx[0], idx[1]]
    solid = ndi.binary_erosion(fg, iterations=3)
    out_rgb = np.where(solid[..., None], a, col)
    return np.dstack([out_rgb, alpha * 255]).clip(0, 255).astype(np.uint8)


if __name__ == "__main__":
    im = Image.open(sys.argv[1]).convert("RGB")
    inf = {}
    # необязательные аргументы: x0,y0,x1,y1 (доли кадра) — затравки фона для тонированных мест
    ex = [tuple(float(v) for v in t.split(",")) for t in sys.argv[3:]]
    import json, os
    sf = os.path.join(os.path.dirname(os.path.abspath(__file__)), "seeds.json")
    if os.path.exists(sf):  # ручные затравки по имени выходного файла (NN)
        ex += [tuple(r) for r in json.load(open(sf)).get(os.path.basename(sys.argv[2])[:-4], [])]
    thin = None
    if os.path.exists(sf):
        thin = json.load(open(sf)).get(os.path.basename(sys.argv[2])[:-4] + "_open")
    key = os.path.basename(sys.argv[2])[:-4]
    cfg = json.load(open(sf)) if os.path.exists(sf) else {}
    Image.fromarray(cutout(np.asarray(im), inf, ex, thin, cfg.get(key + "_contrast", 22), cfg.get(key + "_kill", [])), "RGBA").save(sys.argv[2])
    print(sys.argv[1].rsplit("/", 1)[-1], inf)
