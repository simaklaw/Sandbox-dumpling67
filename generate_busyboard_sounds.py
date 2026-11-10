#!/usr/bin/env python3
"""Звуки «Бизиборда» (комната 10): короткие, тёплые, без голых синусоид и без зацикливания.

Правила, которым подчиняются все звуки этого набора:
  * каждый файл — одноразовый (до 2 с), никаких петель: пока ребёнок не двигает деталь, не звучит ничего;
  * «непрерывные» вещи (молния, шестерёнки, колесо) звучат серией крошечных щелчков — по одному на шаг движения;
  * деревянные и стеклянные тембры (маримба, колокольчик, косточки счётов) вместо пищалок;
  * мягкая атака и плавный хвост — щелчков по краям нет, пик не выше -3 дБ.

Файлы называются bb2_*.ogg и лежат в app/src/main/res/raw (список — в sounds_checklist.csv).
Ноты ксилофона bb2_xylo_1..8 — до-мажор от C5 до C6, играются как есть (без сдвига высоты).

Запуск:  python3 generate_busyboard_sounds.py        (нужны numpy, scipy, ffmpeg)
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter

SR = 44100
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app", "src", "main", "res", "raw")
RNG = np.random.default_rng(67)


def mtof(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


def tt(sec):
    return np.arange(int(sec * SR)) / SR


def lowpass(x, hz, order=2):
    b, a = butter(order, hz / (SR / 2), btype="low")
    return lfilter(b, a, x)


def highpass(x, hz, order=2):
    b, a = butter(order, hz / (SR / 2), btype="high")
    return lfilter(b, a, x)


def bandpass(x, lo, hi, order=2):
    b, a = butter(order, [lo / (SR / 2), hi / (SR / 2)], btype="band")
    return lfilter(b, a, x)


def noise(sec):
    return RNG.uniform(-1.0, 1.0, int(sec * SR))


def attack(n_ms=2.0):
    return lambda t: np.minimum(1.0, t / (n_ms / 1000.0))


def mallet(f, sec, decay, partials=((1.0, 1.0, 1.0), (3.9, 0.30, 3.0), (9.2, 0.10, 6.0))):
    """Деревянная пластина: основной тон + быстро гаснущие верхние обертоны."""
    t = tt(sec)
    s = np.zeros_like(t)
    for mult, amp, dmul in partials:
        s += amp * np.sin(2 * np.pi * f * mult * t) * np.exp(-t * decay * dmul)
    return s * attack(1.5)(t)


def glass(f, sec, decay=3.2):
    """Колокольчик: неточные (колокольные) обертоны, долгий хвост."""
    t = tt(sec)
    s = (np.sin(2 * np.pi * f * t)
         + 0.55 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 2.0)
         + 0.30 * np.sin(2 * np.pi * f * 5.40 * t) * np.exp(-t * 5.0)
         + 0.12 * np.sin(2 * np.pi * f * 8.93 * t) * np.exp(-t * 9.0))
    return s * np.exp(-t * decay) * attack(2.0)(t)


def reverb(x, wet=0.14):
    out = np.zeros_like(x)
    for ms, g in ((29.7, 0.72), (37.1, 0.70), (41.1, 0.68), (43.7, 0.66)):
        d = int(ms / 1000 * SR)
        a = np.zeros(d + 1)
        a[0] = 1.0
        a[d] = -g
        out += lfilter([1.0], a, x)
    return x + wet * out / 4.0


def place(buf, sig, at_s, gain=1.0):
    i = int(at_s * SR)
    j = min(len(buf), i + len(sig))
    if 0 <= i < len(buf):
        buf[i:j] += sig[: j - i] * gain


def finish(x, peak=0.7, fade_ms=6.0, fade_in_ms=None):
    """Нормализация, срез постоянной составляющей и плавные края (без щелчков).

    У ударных звуков (щелчок, тик) пик стоит в самом начале, поэтому вход у них короче выхода.
    Нормализуем уже ПОСЛЕ краёв, чтобы пик получился ровно заданным.
    """
    x = np.asarray(x, dtype=np.float64)
    x = x - np.mean(x)
    n_out = int(fade_ms / 1000 * SR)
    n_in = int((fade_ms if fade_in_ms is None else fade_in_ms) / 1000 * SR)
    if n_in > 0 and len(x) > n_in:
        x[:n_in] *= np.linspace(0.0, 1.0, n_in)
    if n_out > 0 and len(x) > n_out:
        x[-n_out:] *= np.linspace(1.0, 0.0, n_out)
    m = np.max(np.abs(x))
    if m > 0:
        x = x / m * peak
    return x


# ───────────────────────── сами звуки ─────────────────────────

def s_click():
    """Выключатель: сухой щелчок пластмассы + короткий деревянный резонанс."""
    buf = np.zeros(int(0.09 * SR))
    place(buf, highpass(noise(0.004), 2500) * np.exp(-tt(0.004) * 900), 0.0, 0.9)
    t = tt(0.06)
    place(buf, np.sin(2 * np.pi * 1700 * t) * np.exp(-t * 90) + 0.4 * np.sin(2 * np.pi * 3100 * t) * np.exp(-t * 140), 0.0, 0.55)
    return finish(buf, 0.65, 3.0, 0.2)


def s_clack():
    """Задвижка встала на место: деревянный «клак»."""
    buf = np.zeros(int(0.18 * SR))
    place(buf, bandpass(noise(0.006), 800, 5000) * np.exp(-tt(0.006) * 700), 0.0, 0.8)
    t = tt(0.16)
    place(buf, (np.sin(2 * np.pi * 520 * t) + 0.6 * np.sin(2 * np.pi * 1250 * t) + 0.3 * np.sin(2 * np.pi * 2300 * t)) * np.exp(-t * 38), 0.0, 0.7)
    return finish(buf, 0.75)


def s_tick():
    """Зубец храповика: крошечный мягкий тик (играется очень часто, поэтому тихий)."""
    buf = np.zeros(int(0.05 * SR))
    place(buf, bandpass(noise(0.003), 1500, 6000) * np.exp(-tt(0.003) * 1100), 0.0, 0.8)
    t = tt(0.04)
    place(buf, np.sin(2 * np.pi * 2300 * t) * np.exp(-t * 130), 0.0, 0.45)
    return finish(buf, 0.5, 3.0, 0.2)


def s_zip():
    """Зубчик молнии: «цк» — шипящий щелчок с тонким звоном."""
    buf = np.zeros(int(0.04 * SR))
    place(buf, highpass(noise(0.018), 3500) * np.exp(-tt(0.018) * 260), 0.0, 0.8)
    t = tt(0.03)
    place(buf, np.sin(2 * np.pi * 3600 * t) * np.exp(-t * 170), 0.0, 0.30)
    return finish(buf, 0.45, 3.0, 0.2)


def s_bead():
    """Косточка счётов: «тук» деревом. Высота меняется скоростью (rate) при игре."""
    return finish(mallet(784.0, 0.22, 16.0, partials=((1.0, 1.0, 1.0), (2.76, 0.35, 2.2), (5.4, 0.12, 4.0))), 0.7)


def s_keyturn():
    """Замок открылся: «кла-чик» и мягкий удар дверцы."""
    buf = np.zeros(int(0.36 * SR))
    for at, f in ((0.0, 1900.0), (0.085, 1450.0)):
        t = tt(0.05)
        place(buf, (bandpass(noise(0.05), 1200, 6000) * 0.5 + np.sin(2 * np.pi * f * t)) * np.exp(-t * 110), at, 0.7)
    t = tt(0.2)
    place(buf, np.sin(2 * np.pi * 140 * t) * np.exp(-t * 22), 0.17, 0.8)
    return finish(buf, 0.75, 4.0, 0.2)


def s_thunk():
    """Рычаг дошёл до упора: тёплый низкий «бум» с щелчком."""
    buf = np.zeros(int(0.26 * SR))
    t = tt(0.24)
    place(buf, np.sin(2 * np.pi * (115 - 25 * t / 0.24) * t) * np.exp(-t * 17), 0.0, 1.0)
    place(buf, lowpass(noise(0.01), 3500) * np.exp(-tt(0.01) * 300), 0.0, 0.5)
    return finish(buf, 0.8, 6.0, 0.4)


def s_ding():
    """Заработана звёздочка: чистый колокольчик и искорка октавой выше."""
    buf = np.zeros(int(1.1 * SR))
    place(buf, glass(1318.5, 1.1, 3.0), 0.0, 1.0)
    place(buf, glass(2637.0, 0.6, 6.0), 0.05, 0.35)
    return finish(reverb(buf, 0.18), 0.7)


def s_fanfare():
    """Все 9 звёзд: радостный подъём до-мажора, маримба и колокольчики."""
    buf = np.zeros(int(2.2 * SR))
    notes = ((0.00, 72), (0.14, 76), (0.28, 79), (0.42, 84), (0.62, 79), (0.76, 84))
    for at, m in notes:
        place(buf, mallet(mtof(m), 0.7, 5.5), at, 0.75)
        place(buf, glass(mtof(m + 12), 0.5, 7.0), at, 0.18)
    for m in (72, 76, 79, 84):
        place(buf, glass(mtof(m), 1.4, 2.6), 0.95, 0.38)
        place(buf, mallet(mtof(m - 12), 1.0, 3.5), 0.95, 0.22)
    return finish(reverb(buf, 0.22), 0.75, 10.0)


def s_xylo(midi):
    return finish(reverb(mallet(mtof(midi), 0.75, 6.0), 0.08), 0.72, 4.0)


def s_whoosh():
    """Тихий «вжих»: полоса шума плавно ползёт вверх."""
    n = int(0.32 * SR)
    t = np.arange(n) / SR
    x = noise(0.32)
    out = np.zeros(n)
    k = 8
    for i in range(k):
        lo = 350 + i * 170
        seg = slice(i * n // k, (i + 1) * n // k)
        out[seg] = bandpass(x, lo, lo * 1.8)[seg]
    env = np.sin(np.pi * np.minimum(1.0, t / 0.32)) ** 1.5
    return finish(out * env, 0.45, 20.0)


def s_pop():
    """Мягкий «поп» пузырька."""
    n = int(0.13 * SR)
    t = np.arange(n) / SR
    f = 760 - 420 * np.minimum(1.0, t / 0.07)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) * np.exp(-t * 38) + 0.2 * lowpass(noise(0.13), 2500) * np.exp(-t * 90)
    return finish(x, 0.65)


def s_spark():
    """Весёлая искорка: два тоненьких «пинь» разной высоты (не шипение!)."""
    buf = np.zeros(int(0.16 * SR))
    for at, f in ((0.0, 3000.0), (0.045, 4200.0)):
        t = tt(0.1)
        place(buf, np.sin(2 * np.pi * f * t) * np.exp(-t * 45), at, 0.8)
    place(buf, highpass(noise(0.02), 4000) * np.exp(-tt(0.02) * 200), 0.0, 0.2)
    return finish(lowpass(buf, 9000), 0.45, 4.0, 0.2)


def s_zap():
    """Короткое замыкание по-мультяшному: «дзынь-бульк» — смешно, а не страшно."""
    n = int(0.5 * SR)
    t = np.arange(n) / SR
    wob = 1.0 + 0.5 * np.sin(2 * np.pi * 22 * t)
    f = 260 + 520 * np.minimum(1.0, t / 0.22) - 380 * np.clip((t - 0.26) / 0.24, 0.0, 1.0)
    ph = 2 * np.pi * np.cumsum(f * (1 + 0.04 * np.sin(2 * np.pi * 40 * t))) / SR
    x = (np.sin(ph) + 0.3 * np.sin(2 * ph)) * (0.55 + 0.45 * wob / 1.5)
    x *= np.minimum(1.0, t / 0.02) * np.exp(-t * 3.5)
    return finish(lowpass(x, 4500), 0.6, 12.0)


def s_relief():
    """«Фух!» — выдох и две спокойные ноты вниз: порядок наведён."""
    buf = np.zeros(int(1.0 * SR))
    t = tt(0.4)
    breath = bandpass(noise(0.4), 700, 2200) * np.sin(np.pi * np.minimum(1.0, t / 0.4)) ** 2
    place(buf, breath, 0.0, 0.35)
    place(buf, mallet(mtof(79), 0.6, 5.0), 0.22, 0.7)
    place(buf, mallet(mtof(76), 0.7, 4.5), 0.42, 0.8)
    return finish(reverb(buf, 0.16), 0.7, 10.0)


def s_lamp():
    """Лампочка зажглась: тёплый «пинь» с лёгким хвостом."""
    buf = np.zeros(int(0.5 * SR))
    place(buf, glass(1568.0, 0.5, 6.5), 0.0, 1.0)
    place(buf, mallet(mtof(67), 0.3, 12.0), 0.0, 0.3)
    return finish(reverb(buf, 0.1), 0.6)


SOUNDS = {
    "bb2_click": s_click,
    "bb2_clack": s_clack,
    "bb2_tick": s_tick,
    "bb2_zip": s_zip,
    "bb2_bead": s_bead,
    "bb2_keyturn": s_keyturn,
    "bb2_thunk": s_thunk,
    "bb2_ding": s_ding,
    "bb2_fanfare": s_fanfare,
    "bb2_whoosh": s_whoosh,
    "bb2_pop": s_pop,
    "bb2_spark": s_spark,
    "bb2_zap": s_zap,
    "bb2_relief": s_relief,
    "bb2_lamp": s_lamp,
}
XYLO_MIDI = (72, 74, 76, 77, 79, 81, 83, 84)  # C5 D5 E5 F5 G5 A5 B5 C6


def encode(x, name):
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, f"{name}.ogg")
    with tempfile.TemporaryDirectory() as td:
        wav = os.path.join(td, "s.wav")
        wavfile.write(wav, SR, (np.clip(x, -1.0, 1.0) * 32767).astype(np.int16))
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", wav,
                        "-c:a", "libvorbis", "-q:a", "4", "-ac", "1", out], check=True)
    return out


def main():
    total = 0
    jobs = list(SOUNDS.items()) + [(f"bb2_xylo_{i + 1}", (lambda m=m: s_xylo(m))) for i, m in enumerate(XYLO_MIDI)]
    for name, fn in jobs:
        x = fn()
        out = encode(x, name)
        size = os.path.getsize(out)
        total += size
        print(f"{name:14s} {len(x) / SR:5.2f} c  {size / 1024:5.1f} КБ")
    print(f"всего {len(jobs)} файлов, {total / 1024:.0f} КБ -> {OUT_DIR}")


if __name__ == "__main__":
    sys.exit(main())
