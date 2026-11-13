#!/usr/bin/env python3
"""Подставляет в res/raw записи из CC0-паков Kenney (kenney.nl) вместо синтетических «заглушек».

Запуск: python3 import_kenney_sounds.py <папка_с_распакованными_паками>
Ожидает подпапки kenney_interface-sounds, kenney_rpg-audio, kenney_impact-sounds,
kenney_sci-fi-sounds, kenney_voiceover-pack (имя может иметь любой префикс).
Каждый звук: моно, 44,1 кГц, срезана тишина, длина не больше заданной, плавный хвост, пик −3 дБ, OGG Vorbis.
Лицензия Kenney — CC0 (см. SOUND_CREDITS.md). Паки Softline и Essential UI SFX не используются:
их условия не позволяют класть файлы в репозиторий / не указаны.
"""
import glob, os, subprocess, sys
import numpy as np

SR = 44100
RAW = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app/src/main/res/raw")

# имя в res/raw: (пак, файл без .ogg, макс. длина с, усиление, сдвиг высоты (множитель скорости))
MAP = {
    "tap": ("interface", "click_001", 0.30, 1.0, 1.0),
    "soft_no": ("interface", "back_003", 0.50, 0.9, 1.0),
    "spawn": ("interface", "pluck_001", 0.40, 1.0, 1.0),
    "open": ("interface", "open_002", 0.50, 0.9, 1.0),
    "pickup": ("interface", "pluck_002", 0.30, 1.0, 1.0),
    "lever_clack": ("rpg", "metalClick", 0.30, 1.0, 1.0),
    "switch_on": ("interface", "switch_004", 0.30, 1.0, 1.0),
    "switch_off": ("interface", "switch_006", 0.30, 0.9, 1.0),
    "door_creak": ("rpg", "creak2", 0.90, 0.9, 1.0),
    "door_close": ("rpg", "doorClose_2", 0.60, 1.0, 1.0),
    "bell_ding": ("impact", "impactBell_heavy_001", 1.00, 0.9, 1.0),
    "clink": ("impact", "impactGlass_light_002", 0.50, 0.9, 1.0),
    "stamp": ("impact", "impactPlate_light_001", 0.40, 1.0, 1.0),
    "thud": ("impact", "impactSoft_medium_001", 0.40, 1.0, 1.0),
    "wall": ("impact", "impactGeneric_light_002", 0.25, 0.55, 1.0),
    "bumper": ("impact", "impactMetal_light_001", 0.40, 0.9, 1.0),
    "sling": ("impact", "impactMetal_light_003", 0.40, 0.9, 1.0),
    "flip": ("impact", "impactPlank_medium_002", 0.30, 0.8, 1.0),
    "snap_in": ("impact", "impactWood_light_001", 0.35, 1.0, 1.0),
    "box_pop": ("impact", "impactTin_medium_001", 0.40, 0.9, 1.0),
    "box_shake": ("rpg", "handleCoins2", 0.70, 0.9, 1.0),
    "arrow_hit": ("impact", "impactWood_medium_001", 0.50, 1.0, 1.0),
    "carrot_hit": ("impact", "impactSoft_heavy_001", 0.45, 1.0, 1.0),
    "star_hit": ("interface", "glass_003", 0.60, 0.9, 1.0),
    "poke": ("impact", "impactSoft_medium_003", 0.30, 1.0, 1.0),
    "squish_1": ("scifi", "slime_000", 0.55, 0.9, 1.0),
    "squish_2": ("scifi", "slime_001", 0.55, 0.9, 1.0),
    "squish_3": ("scifi", "slime_000", 0.55, 0.9, 0.88),
    "wear": ("rpg", "cloth2", 0.50, 1.0, 1.0),
    "gift": ("rpg", "handleSmallLeather", 0.50, 1.0, 1.0),
    "star_collect": ("rpg", "handleCoins", 0.60, 0.9, 1.0),
    "robot_beep": ("scifi", "computerNoise_001", 0.50, 0.8, 1.0),
    "ufo_hello": ("scifi", "forceField_002", 0.80, 0.8, 1.0),
    "stir": ("rpg", "metalPot2", 0.50, 0.8, 1.0),
    "chime": ("interface", "confirmation_002", 1.00, 0.9, 1.0),
    "fanfare_short": ("interface", "confirmation_004", 1.30, 0.9, 1.0),
    "fx_it_inv_7": ("interface", "drop_003", 0.50, 0.9, 1.0),  # прежний файл был повреждён (обрыв)
    # английские слова-похвалы (_en); русские остаются музыкальными знаками до живой записи
    "praise_1_en": ("voice", "congratulations", 1.40, 0.9, 1.0),
    "praise_2_en": ("voice", "correct", 1.00, 0.9, 1.0),
    "praise_3_en": ("voice", "mission_completed", 1.50, 0.9, 1.0),
}
PACK_DIRS = {"interface": "interface-sounds", "rpg": "rpg-audio", "impact": "impact-sounds",
             "scifi": "sci-fi-sounds", "voice": "voiceover-pack"}


def find(root, pack, name):
    d = glob.glob(os.path.join(root, "*" + PACK_DIRS[pack]))[0]
    pat = os.path.join(d, "**", name + ".ogg")
    hits = glob.glob(pat, recursive=True)
    if pack == "voice":  # женский голос
        hits = [h for h in hits if os.sep + "Female" + os.sep in h] or hits
    if not hits:
        raise SystemExit(f"нет {pack}/{name}")
    return hits[0]


def load(path, speed):
    af = "aresample=44100" if speed == 1.0 else f"asetrate={int(SR * speed)},aresample=44100"
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-af", af, "-f", "f32le", "-ar", str(SR), "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).copy()


def process(x, max_sec, gain):
    a = np.abs(x)
    idx = np.where(a > 0.01 * max(a.max(), 1e-6))[0]
    if len(idx) == 0:
        raise SystemExit("пустой файл")
    x = x[max(0, idx[0] - int(0.002 * SR)):]
    x = x[: int(max_sec * SR)]
    n = min(len(x), int(0.06 * SR))
    x[-n:] *= np.linspace(1, 0, n) ** 1.5  # хвост без щелчка
    x[:int(0.002 * SR)] *= np.linspace(0, 1, int(0.002 * SR))
    pk = np.abs(x).max()
    x = x / pk * (10 ** (-3 / 20)) * gain
    return x


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    root = sys.argv[1]
    for out, (pack, name, mx, gain, speed) in MAP.items():
        x = process(load(find(root, pack, name), speed), mx, gain)
        pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
        dst = os.path.join(RAW, out + ".ogg")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-",
                        "-c:a", "libvorbis", "-q:a", "4", dst], input=pcm, check=True)
        print(f"{out:16s} <- {pack}/{name}  {len(x) / SR:.2f}s")


if __name__ == "__main__":
    main()
