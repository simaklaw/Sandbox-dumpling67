#!/usr/bin/env python3
"""Мягкая мелодия меню -> res/raw/menu_music.ogg (бесшовный луп ~29 с). numpy + ffmpeg."""
import os
import struct
import subprocess
import numpy as np

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "app/src/main/res/raw")


def tt(d):
    return np.arange(int(d * SR)) / SR


def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def box(m, dur, decay=4., bright=.25, vol=1.):
    f = mtof(m)
    t = tt(dur)
    n = len(t)
    env = np.exp(-decay * t)
    a = min(int(.01 * SR), n // 3)
    env[:a] *= np.linspace(0, 1, a)
    r = min(int(.08 * SR), n // 3)
    env[-r:] *= np.linspace(1, 0, r)
    return ((np.sin(2 * np.pi * f * t) + bright * np.sin(4 * np.pi * f * t) + .18 * np.sin(np.pi * f * t)) * env * vol).astype(np.float32)


def place(o, s, at):
    st = int(at * SR)
    en = min(st + len(s), len(o))
    o[st:en] += s[:en - st]


beat = 60 / 66.0
mel = [(76, 1), (79, 1), (81, 2), (79, 1), (76, 1), (74, 2), (72, 1), (76, 1), (79, 2), (76, 1), (74, 1), (72, 2),
       (74, 1), (76, 1), (79, 2), (81, 1), (79, 1), (76, 2), (79, 1), (76, 1), (74, 2), (72, 2), (0, 2)]
o = np.zeros(int((sum(b for _, b in mel) * beat + 0.5) * SR), dtype=np.float32)
t = 0.0
for m, b in mel:
    if m > 0:
        place(o, box(m, b * beat * .92, 3.5, .25, .5), t)
    t += b * beat
for i, m in enumerate([48, 48, 43, 43, 45, 45, 43, 48]):
    place(o, box(m, 3.6 * beat, 1.1, .1, .3), i * 4 * beat)
n = int(.06 * SR)
k = np.arange(n) / n
o[:n] = o[:n] * np.sin(k * np.pi / 2) + o[-n:] * np.cos(k * np.pi / 2)  # бесшовный луп: хвост вливается в начало
o = o[:-n]
rms = 20 * np.log10(np.sqrt(np.mean(o * o)) + 1e-9)
o *= 10 ** ((-20 - rms) / 20)
pk = np.abs(o).max()
if pk > 0.6:
    o *= 0.6 / pk
pcm = (np.clip(o, -1, 1) * 32767).astype("<i2").tobytes()
wav = (b"RIFF" + struct.pack("<I", 36 + len(pcm)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 1, SR, SR * 2, 2, 16)
       + b"data" + struct.pack("<I", len(pcm)) + pcm)
os.makedirs(RAW, exist_ok=True)
subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "wav", "-i", "pipe:0",
                "-c:a", "libvorbis", "-q:a", "4", "-ac", "1", os.path.join(RAW, "menu_music.ogg")], input=wav, check=True)
print("Готово: menu_music.ogg ~", round(len(o) / SR, 1), "с")
