"""Сетка шахматки (размер клетки и фаза по осям) по уже найденному фону.

Границы клеток дают гребёнку пиков в проекции градиента на ось; период и сдвиг
подбираются по максимуму суммы пиков. Работает и там, где у края кадра
виньетка или объекты (учитываются только пиксели фона)."""
import numpy as np
from scipy import ndimage as ndi


def _comb_fit(g, lo_t=15.0, hi_t=30.0):
    n = len(g)
    g = g / (g.mean() + 1e-6)
    idx = np.arange(n)

    def score(t, x0):
        k = np.arange(0, int((n - x0) / t) + 1)
        pos = x0 + k * t
        pos = pos[(pos >= 1) & (pos < n - 2)]
        # пик может быть размазан на 1-2 пикселя: берём локальный максимум рядом
        v = 0.0
        for d in (-1, 0, 1):
            v = np.maximum(v, g[np.clip(np.round(pos).astype(int) + d, 0, n - 1)]) if d else g[np.round(pos).astype(int)]
        return float(np.mean(v))

    best = (-1.0, 25.6, 0.0)
    for t in np.arange(lo_t, hi_t, 0.1):
        for x0 in np.arange(0, t, 0.5):
            s = score(t, x0)
            if s > best[0]:
                best = (s, t, x0)
    _, t, x0 = best
    for dt in np.arange(-0.12, 0.121, 0.01):
        for dx in np.arange(-1.0, 1.01, 0.1):
            s = score(t + dt, x0 + dx)
            if s > best[0]:
                best = (s, t + dt, x0 + dx)
    return best[1], best[2] % best[1], best[0]


def detect(lum, bg):
    """lum: яркость; bg: bool-маска надёжного фона. Возвращает tx, x0, ty, y0, score."""
    m = ndi.binary_erosion(bg, iterations=3)
    gx = np.abs(np.diff(lum, axis=1)) * (m[:, 1:] & m[:, :-1])
    gy = np.abs(np.diff(lum, axis=0)) * (m[1:, :] & m[:-1, :])
    px = gx.sum(0)
    py = gy.sum(1)
    tx, x0, sx = _comb_fit(np.append(px, 0.0)[: gx.shape[1] + 1])
    ty, y0, sy = _comb_fit(np.append(py, 0.0)[: gy.shape[0] + 1])
    return tx, x0 + 0.5, ty, y0 + 0.5, min(sx, sy)


def parity_and_lines(shape, tx, x0, ty, y0):
    """Знак клетки (±1) и маска пикселей у границ клеток (±1.5 px)."""
    h, w = shape
    x = np.arange(w, dtype=np.float64)
    y = np.arange(h, dtype=np.float64)
    kx = np.floor((x - x0) / tx).astype(int)
    ky = np.floor((y - y0) / ty).astype(int)
    px = np.where(kx % 2 == 0, 1.0, -1.0)
    py = np.where(ky % 2 == 0, 1.0, -1.0)
    fx = np.abs(((x - x0) / tx) % 1.0 - 0.5) > 0.5 - 1.6 / tx
    fy = np.abs(((y - y0) / ty) % 1.0 - 0.5) > 0.5 - 1.6 / ty
    par = np.outer(py, px).astype(np.float32)
    line = fy[:, None] | fx[None, :]
    return par, line
