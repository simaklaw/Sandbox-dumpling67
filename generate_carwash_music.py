#!/usr/bin/env python3
"""Три оригинальные танцевальные мелодии для «Автомойки для игрушек».

  cw2_dance_car.ogg    — машинка:  до мажор, 130 уд/мин, полька «ум-па», маримба
  cw2_dance_bus.ogg    — автобус:  соль мажор, 120 уд/мин, свинг, укулеле и свист
  cw2_dance_train.ogg  — паровоз:  ре мажор, 126 уд/мин, «чух-чух», банджо и гудок

Все мелодии сочинены для проекта и синтезируются кодом (numpy), чужих записей и чужих мотивов в них нет.
Каждая — ровно 6 тактов по 4 доли; анимация в CarWashRoomScreen привязана к этому темпу
(CW_BPM, 16 долей танца на месте и 8 долей отъезда), поэтому темп менять нельзя без правки кода.

Запуск:  python3 generate_carwash_music.py        (нужны numpy, scipy, ffmpeg)
Файлы кладутся в app/src/main/res/raw.
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


def mtof(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


def tt(n):
    return np.arange(n) / SR


# ───────────────────────── инструменты ─────────────────────────

def marimba(f, vel=1.0):
    t = tt(int(0.9 * SR))
    s = (np.sin(2 * np.pi * f * t) * np.exp(-t * 7.0)
         + 0.45 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-t * 20.0)
         + 0.15 * np.sin(2 * np.pi * f * 10 * t) * np.exp(-t * 40.0))
    return s * np.minimum(1.0, t / 0.003) * vel


def pluck(f, vel=1.0, bright=1.0, decay=5.0):
    """Щипок струны (укулеле, банджо): гармоники, верхние затухают быстрее."""
    t = tt(int(1.2 * SR))
    s = np.zeros_like(t)
    for k in range(1, 9):
        if f * k > 9000:
            break
        s += (1.0 / k ** (1.35 / bright)) * np.sin(2 * np.pi * f * k * t + k) * np.exp(-t * (decay + 2.2 * k))
    return s * np.minimum(1.0, t / 0.002) * vel


def whistle(f, dur, vel=1.0, seed=0):
    """Свист/флейта: мягкая атака, лёгкое вибрато, чуть дыхания."""
    n = int((dur + 0.18) * SR)
    t = tt(n)
    vib = 1.0 + 0.004 * np.sin(2 * np.pi * 5.6 * t) * np.minimum(1.0, t / 0.25)
    ph = 2 * np.pi * np.cumsum(f * vib) / SR
    s = np.sin(ph) + 0.12 * np.sin(2 * ph)
    rng = np.random.default_rng(seed)
    s += 0.02 * rng.standard_normal(n)
    env = np.minimum(1.0, t / 0.04) * np.where(t < dur, 1.0, np.exp(-(t - dur) * 22.0))
    return s * env * vel * 0.8


def bass(f, dur, vel=1.0):
    n = int((dur + 0.1) * SR)
    t = tt(n)
    s = np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 2 * t) + 0.18 * np.sin(2 * np.pi * f * 3 * t)
    env = np.minimum(1.0, t / 0.006) * np.exp(-t * 4.5) * np.where(t < dur, 1.0, np.exp(-(t - dur) * 40.0))
    return s * env * vel


def kick(vel=1.0):
    t = tt(int(0.25 * SR))
    f = 50 + 90 * np.exp(-t * 38)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * 16) * vel


def snare(rng, vel=1.0):
    t = tt(int(0.16 * SR))
    noise = rng.standard_normal(len(t))
    b, a = butter(2, [1500 / (SR / 2), 7000 / (SR / 2)], "band")
    return (lfilter(b, a, noise) * np.exp(-t * 28) * 0.9 + np.sin(2 * np.pi * 190 * t) * np.exp(-t * 30) * 0.4) * vel


def hat(rng, vel=1.0, open_=False):
    t = tt(int((0.18 if open_ else 0.05) * SR))
    noise = rng.standard_normal(len(t))
    b, a = butter(2, 6500 / (SR / 2), "high")
    return lfilter(b, a, noise) * np.exp(-t * (12 if open_ else 70)) * vel


def bell(f, vel=1.0):
    t = tt(int(1.4 * SR))
    s = (np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 3)
         + 0.3 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 6))
    return s * np.exp(-t * 3.2) * np.minimum(1.0, t / 0.002) * vel


def toot(vel=1.0):
    """Гудок паровозика: две ноты вместе, лёгкое «дрожание»."""
    n = int(0.95 * SR)
    t = tt(n)
    s = np.zeros(n)
    for f in (587.3, 740.0):
        vib = 1.0 + 0.006 * np.sin(2 * np.pi * 6 * t)
        ph = 2 * np.pi * np.cumsum(f * vib) / SR
        s += np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)
    env = np.minimum(1.0, t / 0.03) * np.where(t < 0.6, 1.0, np.exp(-(t - 0.6) * 12))
    return s * env * 0.35 * vel


# ───────────────────────── сборка дорожки ─────────────────────────

class Mix:
    def __init__(self, seconds):
        self.buf = np.zeros(int(seconds * SR))

    def add(self, sig, at_s, gain=1.0):
        i = int(at_s * SR)
        if i < 0 or i >= len(self.buf):
            return
        j = min(len(self.buf), i + len(sig))
        self.buf[i:j] += sig[: j - i] * gain


def reverb(x, wet=0.16):
    out = np.zeros_like(x)
    for ms, g in ((29.7, 0.78), (37.1, 0.76), (41.1, 0.74), (43.7, 0.72)):
        d = int(ms / 1000 * SR)
        a = np.zeros(d + 1)
        a[0] = 1.0
        a[d] = -g
        out += lfilter([1.0], a, x)
    return x + wet * out / 4.0


def render(name, bpm, melody, chords, bass_roots, style, lead, swing=False, seed=1):
    """melody: [(midi, beats)], chords: [(доля начала, [midi...])] по 4 доли в такте, bass_roots: [(доля, midi)]."""
    rng = np.random.default_rng(seed)
    spb = 60.0 / bpm
    beats = 24
    mix = Mix(beats * spb + 1.6)

    def tm(beat):
        """время в секундах; при свинге вторая восьмая сдвигается к трети доли"""
        if swing:
            fl = np.floor(beat + 1e-6)
            fr = beat - fl
            if abs(fr - 0.5) < 1e-6:
                beat = fl + 0.667
        return beat * spb

    # мелодия
    b = 0.0
    for midi, dur in melody:
        f = mtof(midi)
        at = tm(b)
        if lead == "marimba":
            mix.add(marimba(f), at, 0.95)
            if dur > 1.0:                       # длинную ноту «раскатываем» тремоло
                k = 1
                while k * 0.125 < dur * spb - 0.05:
                    mix.add(marimba(f, 0.6), at + k * 0.125, 0.8)
                    k += 1
        elif lead == "whistle":
            mix.add(whistle(f, dur * spb * 0.97, seed=int(b * 10)), at, 0.8)
        else:  # banjo
            mix.add(pluck(f, 1.0, bright=1.6, decay=7.0), at, 0.95)
            if dur > 1.0:
                k = 1
                while k * 0.11 < dur * spb - 0.05:
                    mix.add(pluck(f, 0.55, bright=1.6, decay=7.0), at + k * 0.11, 0.8)
                    k += 1
        b += dur

    # аккорды: style задаёт рисунок
    for bar in range(6):
        for (beat0, notes) in chords[bar]:
            if style == "polka":
                hits = [beat0 + 1.0, beat0 + 3.0] if len(chords[bar]) == 1 else [beat0 + 1.0]
                if bar == 5:
                    hits = [0.0]
                for h in hits:
                    for i, n in enumerate(notes):
                        mix.add(pluck(mtof(n), 0.7, bright=0.9, decay=7.0), tm(bar * 4 + h) + i * 0.006, 0.34)
            elif style == "uke":
                hits = [beat0 + 1.0, beat0 + 3.0] if len(chords[bar]) == 1 else [beat0 + 1.0]
                if bar == 5:
                    hits = [0.0]
                for h in hits:
                    for i, n in enumerate(notes):
                        mix.add(pluck(mtof(n), 0.75, bright=1.0, decay=6.0), tm(bar * 4 + h) + i * 0.008, 0.36)
            else:  # chug: короткие аккорды на каждой «и»
                if bar == 5:
                    hits = [0.0]
                else:
                    span = 4.0 if len(chords[bar]) == 1 else 2.0
                    hits = [beat0 + 0.5 + k for k in range(int(span))]
                for h in hits:
                    for i, n in enumerate(notes):
                        mix.add(pluck(mtof(n), 0.6, bright=1.3, decay=11.0), tm(bar * 4 + h) + i * 0.005, 0.30)

    # бас
    for bar in range(6):
        for beat, midi in bass_roots[bar]:
            if bar == 5 and beat > 0:
                continue
            mix.add(bass(mtof(midi), 0.34 * spb * 2, 0.9), tm(bar * 4 + beat), 0.62)

    # ударные
    for bar in range(6):
        last = bar == 5
        for beat in range(4):
            at = tm(bar * 4 + beat)
            if beat % 2 == 0 and (not last or beat == 0):
                mix.add(kick(), at, 0.8)
            if beat % 2 == 1 and not last:
                mix.add(snare(rng), at, 0.30)
            if last:
                continue
            if style == "chug":
                for k in range(4):
                    mix.add(hat(rng, 1.0), tm(bar * 4 + beat + k * 0.25), 0.10 if k % 2 else 0.17)
            else:
                mix.add(hat(rng), tm(bar * 4 + beat), 0.10)
                mix.add(hat(rng), tm(bar * 4 + beat + 0.5), 0.17)
        if bar == 4:  # подводка к финалу
            for k in range(4):
                mix.add(snare(rng), tm(bar * 4 + 3 + k * 0.25), 0.16 + 0.06 * k)

    # финал: колокольчики вверх и гудок у паровоза
    root = {"car": 84, "bus": 79, "train": 86}[name]
    for i, step in enumerate((0, 4, 7, 12)):
        mix.add(bell(mtof(root + step)), tm(20 + 1.0) + i * 0.09, 0.30)
    if name == "train":
        mix.add(toot(), tm(20), 0.7)

    x = reverb(mix.buf)
    b_, a_ = butter(3, 9500 / (SR / 2), "low")
    x = lfilter(b_, a_, x)
    end = int((beats * spb + 0.75) * SR)
    x = x[:end]
    fade = int(0.18 * SR)
    x[-fade:] *= np.linspace(1.0, 0.0, fade)
    x = np.tanh(x * 1.15)
    x = x / max(1e-6, np.max(np.abs(x))) * 0.5
    return x


# ───────────────────────── ноты ─────────────────────────

C, G, F = [60, 64, 67], [59, 62, 67], [60, 65, 69]
CAR = dict(
    bpm=130, style="polka", lead="marimba",
    melody=[
        (76, .5), (76, .5), (79, .5), (76, .5), (72, 1), (76, 1),
        (74, .5), (74, .5), (77, .5), (74, .5), (71, 1), (74, 1),
        (72, .5), (76, .5), (79, .5), (84, .5), (83, .5), (79, .5), (76, 1),
        (74, .5), (77, .5), (81, .5), (79, .5), (77, 1), (74, 1),
        (81, .5), (79, .5), (77, .5), (76, .5), (74, .5), (76, .5), (77, .5), (74, .5),
        (76, .5), (79, .5), (84, 3),
    ],
    chords=[[(0, C)], [(0, G)], [(0, C)], [(0, G)], [(0, F), (2, G)], [(0, C)]],
    bass_roots=[[(0, 48), (2, 55)], [(0, 43), (2, 50)], [(0, 48), (2, 55)], [(0, 43), (2, 50)],
                [(0, 41), (2, 43)], [(0, 48)]],
)

G2c, D2c, C2c = [59, 62, 67], [62, 66, 69], [60, 64, 67]
BUS = dict(
    bpm=120, style="uke", lead="whistle", swing=True,
    melody=[
        (71, .5), (74, .5), (79, 1), (79, .5), (78, .5), (76, 1),
        (74, .5), (76, .5), (74, .5), (71, .5), (69, 2),
        (71, .5), (74, .5), (79, 1), (81, .5), (79, .5), (76, 1),
        (74, .5), (72, .5), (71, .5), (69, .5), (67, 2),
        (76, .5), (76, .5), (76, .5), (79, .5), (74, .5), (74, .5), (74, .5), (78, .5),
        (74, .5), (71, .5), (79, 3),
    ],
    chords=[[(0, G2c)], [(0, D2c)], [(0, G2c)], [(0, C2c), (2, G2c)], [(0, C2c), (2, D2c)], [(0, G2c)]],
    bass_roots=[[(0, 43), (2, 50)], [(0, 50), (2, 45)], [(0, 43), (2, 50)], [(0, 48), (2, 43)],
                [(0, 48), (2, 50)], [(0, 43)]],
)

Dd, Gd, Ad = [62, 66, 69], [62, 67, 71], [61, 64, 69]
TRAIN = dict(
    bpm=126, style="chug", lead="banjo",
    melody=[
        (69, .5), (74, .5), (78, .5), (74, .5), (81, 1), (78, 1),
        (79, .5), (76, .5), (79, .5), (83, .5), (81, 2),
        (69, .5), (74, .5), (78, .5), (74, .5), (81, 1), (83, 1),
        (81, .5), (79, .5), (78, .5), (76, .5), (74, 2),
        (83, .5), (83, .5), (81, .5), (81, .5), (79, .5), (79, .5), (78, .5), (76, .5),
        (78, .5), (81, .5), (86, 3),
    ],
    chords=[[(0, Dd)], [(0, Gd), (2, Ad)], [(0, Dd)], [(0, Ad), (2, Dd)], [(0, Gd), (2, Dd)], [(0, Dd)]],
    bass_roots=[[(0, 50), (1, 57), (2, 50), (3, 57)], [(0, 43), (1, 50), (2, 45), (3, 52)],
                [(0, 50), (1, 57), (2, 50), (3, 57)], [(0, 45), (1, 52), (2, 50), (3, 57)],
                [(0, 43), (1, 50), (2, 50), (3, 57)], [(0, 50)]],
)


def encode(x, name):
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, f"cw2_dance_{name}.ogg")
    with tempfile.TemporaryDirectory() as td:
        wav = os.path.join(td, "m.wav")
        wavfile.write(wav, SR, (x * 32767).astype(np.int16))
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", wav,
                        "-c:a", "libvorbis", "-q:a", "3", "-ac", "1", out], check=True)
    return out


def main():
    for name, spec in (("car", CAR), ("bus", BUS), ("train", TRAIN)):
        spec = dict(spec)
        bpm = spec.pop("bpm")
        sw = spec.pop("swing", False)
        x = render(name, bpm, spec["melody"], spec["chords"], spec["bass_roots"], spec["style"], spec["lead"], sw,
                   seed={"car": 1, "bus": 2, "train": 3}[name])
        out = encode(x, name)
        print(f"{name}: {len(x) / SR:.2f} c, {os.path.getsize(out) / 1024:.0f} КБ -> {out}")


if __name__ == "__main__":
    sys.exit(main())
