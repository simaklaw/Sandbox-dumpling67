#!/usr/bin/env python3
"""Звуки комнаты «Поваренок» (индекс 51): мягкие шаги, шипение сковороды, звонок заказа, «ням» гостя.

Правила те же, что у generate_polish_sounds.py: без белого шума на частых путях, мягкая атака,
пик около -3 дБ, каждый файл короче 0,6 с. Шипение — это не шум, а стайка тихих «пузырьков»
(короткие низкие капельки), поэтому не режет слух.

Запуск:  python3 generate_cook_sounds.py       (нужны numpy, scipy, ffmpeg)
"""
import os
import sys

import numpy as np

import generate_busyboard_sounds as g
from generate_busyboard_sounds import SR, glass, mallet, mtof, place, reverb, tt


def finish(x, peak=0.6, fade_ms=14.0, fade_in_ms=3.0):
    return g.finish(x, peak, fade_ms, fade_in_ms)


def s_step(f0):
    """Мягкий шаг поварёнка: глухой «топ» (≈f0→f0/2 Гц) и тёплая гармоника, 0,11 с, без верхов."""
    sec = 0.11
    t = tt(sec)
    f = f0 * (1.0 - 0.45 * np.minimum(1.0, t / 0.05))
    ph = 2 * np.pi * np.cumsum(f) / SR
    body = (np.sin(ph) + 0.35 * np.sin(2 * ph)) * np.minimum(1.0, t / 0.004) * np.exp(-t * 42)
    return finish(body, 0.34, 20.0, 3.0)


def s_sizzle():
    """Шипение сковородки: 9 тихих низких «пузырьков» разной высоты за 0,5 с."""
    sec = 0.5
    buf = np.zeros(int(sec * SR))
    rng = np.random.default_rng(7)
    for k in range(9):
        at = 0.02 + 0.5 * k / 9 + rng.uniform(0, 0.025)
        f = rng.uniform(380, 900)
        d = 0.05
        t = tt(d)
        ph = 2 * np.pi * np.cumsum(f * (0.7 + 0.5 * np.minimum(1.0, t / 0.02))) / SR
        b = np.sin(ph) * np.minimum(1.0, t / 0.004) * np.exp(-t * 60)
        place(buf, b, at, rng.uniform(0.25, 0.5))
    return finish(buf, 0.4, 40.0, 4.0)


def s_order():
    """Звонок нового заказа: два тёплых «динь-дон» стеклянного колокольчика."""
    buf = np.zeros(int(0.55 * SR))
    place(buf, glass(mtof(88), 0.4, 6.0), 0.0, 0.8)
    place(buf, glass(mtof(84), 0.4, 6.0), 0.14, 0.9)
    return finish(reverb(buf, 0.1), 0.6, 60.0, 3.0)


def s_serve():
    """Тарелка встала на прилавок: мягкий «клик» фарфора и коротенький «блинь»."""
    buf = np.zeros(int(0.35 * SR))
    place(buf, mallet(520, 0.12, 30.0), 0.0, 0.7)
    place(buf, glass(mtof(91), 0.25, 9.0), 0.05, 0.5)
    return finish(reverb(buf, 0.06), 0.55, 40.0, 3.0)


def s_pick():
    """Взял продукт с ленты: короткий «плип» вверх."""
    sec = 0.14
    t = tt(sec)
    f = 420 + 380 * np.minimum(1.0, t / 0.07)
    ph = 2 * np.pi * np.cumsum(f) / SR
    b = (np.sin(ph) + 0.2 * np.sin(2 * ph)) * np.minimum(1.0, t / 0.005) * np.exp(-t * 24)
    return finish(b, 0.5, 30.0, 3.0)


def s_cheer():
    """Смена окончена: быстрая мажорная «лесенка» колокольчиков (0,6 с)."""
    buf = np.zeros(int(0.9 * SR))
    for i, m in enumerate((79, 83, 86, 91)):
        place(buf, glass(mtof(m), 0.5, 5.0), 0.1 * i, 0.8)
    return finish(reverb(buf, 0.12), 0.62, 80.0, 3.0)


def jobs():
    return {
        "ck_step1": lambda: s_step(210.0),
        "ck_step2": lambda: s_step(185.0),
        "ck_sizzle": s_sizzle,
        "ck_order": s_order,
        "ck_serve": s_serve,
        "ck_pick": s_pick,
        "ck_cheer": s_cheer,
    }


def main():
    total = 0
    for name, fn in jobs().items():
        out = g.encode(fn(), name)
        total += os.path.getsize(out)
    print(f"{len(jobs())} файлов, {total / 1024:.0f} КБ -> {g.OUT_DIR}")


if __name__ == "__main__":
    sys.exit(main())
