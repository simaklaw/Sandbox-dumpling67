#!/usr/bin/env python3
"""Приятные звуки вместо неприятных: переходы, шарики, «кастрюля чудес», голоса-без-слов.

Что здесь заменено (файлы перезаписываются на месте, имена прежние, код менять не надо):
  * whoosh, whoosh_soft, fx_zone_transition — были белым шумом («помехи телевизора») на переходах между
    меню и играми. Теперь это мягкая «капелька» с колокольчиком, без единого шумового звука.
  * bp_pop1..7 — лопание шариков. Раньше шесть разных по характеру звуков (часть — шумовые, один тянулся
    полсекунды). Теперь это настоящий «хлоп»: короткий треск + глухой «бум» резины + выдох воздуха
    (0,24 с), у каждого цвета тембр чуть свой. Мелодии нот больше нет — лопание должно звучать как лопание.
  * rain_drop1..4, rain_splash — «Дождик»: мягкие водяные капельки и «плюх» вместо частого `drop_plip`/`splash_soft`
    (их играли до 12 раз в секунду, это раздражало). Теперь редкие, тихие, в одной тональности.
  * kc_munch1, kc_munch2 — жевание мишки. Были острыми шумовыми щелчками (центр спектра 7 кГц), которые
    4 раза подряд в одном ритме звучали как автоматная очередь. Теперь это округлое «ням» без верхов.
  * все «говорящие» файлы (praise_*, peekaboo_*, yum_voice, voice_hint_*, voice_arrow_*, voice_name_*,
    voice_shape_*, phone_hi_*) — были роботом espeak-ng. Живой голос в этом окружении сделать нельзя,
    поэтому слова заменены короткими музыкальными знаками (кукушка вместо «Ку-ку!», колокольчики
    вместо «Молодец!», у каждого зверька свой мотив). Настоящие записи кладутся поверх файлов с теми же
    именами — см. VOICES.md. Копии *_en получают тот же звук (слов нет, язык не важен).

Правила набора: никаких голых синусоид и белого шума, мягкая атака (кроме ударных), пик около -3 дБ,
каждый файл короче 1,2 с, у низких нот усилены гармоники (динамик телефона слаб ниже ~300 Гц).

Запуск:  python3 generate_polish_sounds.py       (нужны numpy, scipy, ffmpeg)
"""
import os
import sys

import numpy as np

import generate_busyboard_sounds as g
from generate_busyboard_sounds import SR, glass, mallet, mtof, noise, place, reverb, tt

NOTE = {"C5": 72, "D5": 74, "E5": 76, "F5": 77, "G5": 79, "A5": 81, "B5": 83, "C6": 84}


def finish(x, peak=0.7, fade_ms=12.0, fade_in_ms=3.0):
    return g.finish(x, peak, fade_ms, fade_in_ms)


# ───────────────────────── строительные блоки ─────────────────────────

def drop(f0, f1, sec, glide_s, decay, harm2=0.22):
    """Мягкая «капелька»: тон скользит от f0 к f1 и гаснет; чуть второй гармоники для тепла."""
    t = tt(sec)
    f = f0 + (f1 - f0) * np.minimum(1.0, t / glide_s)
    ph = 2 * np.pi * np.cumsum(f) / SR
    env = np.minimum(1.0, t / 0.008) * np.exp(-t * decay)
    return (np.sin(ph) + harm2 * np.sin(2 * ph)) * env


def flute(f, sec, gain=1.0, vibrato=0.0):
    """Тёплый «свистковый» тон кукушки: основной тон и две слабые гармоники, мягкие вход и выход."""
    t = tt(sec)
    fm = f * (1.0 + vibrato * np.sin(2 * np.pi * 5.5 * t))
    ph = 2 * np.pi * np.cumsum(fm) / SR
    s = np.sin(ph) + 0.22 * np.sin(2 * ph) + 0.07 * np.sin(3 * ph)
    env = np.minimum(1.0, t / 0.02) * np.minimum(1.0, (sec - t) / 0.06)
    return s * env * gain


def pluck(midi, sec=0.4, decay=7.0, gain=1.0, sparkle=0.12):
    """Деревянная нота с едва заметной искоркой октавой выше."""
    if midi < 64:  # динамик телефона слаб ниже ~300 Гц: у низких нот усиливаем 2-ю и 3-ю гармоники
        partials = ((1.0, 1.0, 1.0), (2.0, 0.75, 1.4), (3.0, 0.45, 1.9), (3.9, 0.30, 3.0), (9.2, 0.10, 6.0))
        out = mallet(mtof(midi), sec, decay, partials) * gain
    else:
        out = mallet(mtof(midi), sec, decay) * gain
    if sparkle > 0:
        place(out, glass(mtof(midi + 12), min(sec, 0.25), 12.0), 0.0, sparkle * gain)
    return out


def seq(buf, notes, step, **kw):
    for i, m in enumerate(notes):
        place(buf, pluck(m, **kw), i * step, 1.0)


# ───────────────────────── переходы (без шума) ─────────────────────────

def s_whoosh_soft():
    """Листание, мягкий переход: короткая «капелька» вверх и колокольчик."""
    buf = np.zeros(int(0.26 * SR))
    place(buf, drop(330, 620, 0.18, 0.10, 16), 0.0, 0.8)
    place(buf, glass(mtof(NOTE["G5"]), 0.22, 11.0), 0.06, 0.34)
    return finish(reverb(buf, 0.10), 0.55, 20.0, 6.0)


def s_whoosh():
    """Чуть бодрее: «капелька» повыше и два колокольчика (квинта)."""
    buf = np.zeros(int(0.36 * SR))
    place(buf, drop(260, 700, 0.22, 0.13, 12), 0.0, 0.8)
    place(buf, glass(mtof(NOTE["G5"]), 0.28, 9.0), 0.08, 0.30)
    place(buf, glass(mtof(NOTE["C6"]), 0.26, 10.0), 0.12, 0.30)
    return finish(reverb(buf, 0.12), 0.58, 22.0, 6.0)


def s_zone_transition():
    """Выход из игры и вход в меню: тёплый подъём до-ми-соль с колокольчиком на конце."""
    buf = np.zeros(int(0.62 * SR))
    place(buf, drop(300, 520, 0.12, 0.06, 20), 0.0, 0.5)
    for at, m in ((0.02, 72), (0.09, 76), (0.16, 79)):
        place(buf, pluck(m, 0.38, 7.0, 0.7, 0.10), at, 1.0)
    place(buf, glass(mtof(84), 0.42, 5.5), 0.22, 0.34)
    return finish(reverb(buf, 0.16), 0.62, 30.0, 4.0)


# ───────────────────────── шарики ─────────────────────────

BALLOON_NOTES = (72, 74, 76, 79, 81, 84, 86)  # до-ре-ми-соль-ля-до-ре: любая очередь звучит согласно


def s_balloon_pop(midi):
    """Настоящее «хлоп!» лопнувшего шарика: короткий треск (3–6 кГц, ~8 мс), глухой «бум» резины
    (≈190→80 Гц) и быстрый воздушный выдох. Всё в 0,24 с. Высота «бума» и треска чуть своя у каждого
    цвета (midi задаёт сдвиг), чтобы очередь не звучала одинаково; верхи срезаны, чтобы не резало ухо."""
    sec = 0.24
    n = int(sec * SR)
    t = np.arange(n) / SR
    k = 2.0 ** ((midi - 72) / 36.0)  # ±1 октава по всему ряду нот → ±~1/3 тона разброса тембра
    crack = g.lowpass(g.bandpass(noise(sec), 2200 * k, 6200 * k), 6500)
    crack = crack * np.exp(-t * 420) * np.minimum(1.0, t / 0.0004)
    f = (210 - 130 * np.minimum(1.0, t / 0.035)) * k
    ph = 2 * np.pi * np.cumsum(f) / SR
    boom = (np.sin(ph) + 0.35 * np.sin(2 * ph)) * np.exp(-t * 38) * np.minimum(1.0, t / 0.001)
    puff = g.lowpass(g.bandpass(noise(sec), 700, 2600), 2800)
    puff = puff * np.minimum(1.0, t / 0.006) * np.exp(-t * 26) * 0.25
    buf = crack * 1.1 + boom * 0.95 + puff
    return finish(reverb(buf, 0.04), 0.78, 40.0, 0.3)


# ───────────────────────── дождик ─────────────────────────

RAIN_DROP_HZ = (784, 880, 1047, 1175)  # соль-ля-до-ре: капельки ложатся в одну тональность


def s_rain_drop(f_end):
    """Мягкая капелька воды: тон слегка взлетает (как «блюп» в луже), без шума и острых краёв."""
    sec = 0.17
    t = tt(sec)
    f = f_end * (0.62 + 0.38 * np.minimum(1.0, t / 0.05))
    ph = 2 * np.pi * np.cumsum(f) / SR
    env = np.minimum(1.0, t / 0.005) * np.exp(-t * 30)
    buf = (np.sin(ph) + 0.18 * np.sin(2 * ph)) * env
    return finish(reverb(buf, 0.08), 0.42, 30.0, 4.0)


def s_rain_splash():
    """Тихий «плюх» в лужу: округлый низкий тон 320→170 Гц и мягкий всплеск, верхов нет."""
    sec = 0.22
    t = tt(sec)
    f = 320 - 150 * np.minimum(1.0, t / 0.09)
    ph = 2 * np.pi * np.cumsum(f) / SR
    body = (np.sin(ph) + 0.4 * np.sin(2 * ph)) * np.minimum(1.0, t / 0.006) * np.exp(-t * 20)
    sprinkle = g.lowpass(g.bandpass(noise(sec), 500, 1800), 2000)
    sprinkle = sprinkle * np.minimum(1.0, t / 0.01) * np.exp(-t * 22) * 0.22
    return finish(reverb(body + sprinkle, 0.08), 0.5, 40.0, 5.0)


# ───────────────────────── кастрюля чудес ─────────────────────────

def s_munch(pitch, phase):
    """Мягкое «ням»: округлый стук (≈400→190 Гц) + тёплая «каша» 500–2200 Гц. Верхов нет, атака ≈6 мс."""
    sec = 0.30
    t = tt(sec)
    f = (420 - 230 * np.minimum(1.0, t / 0.07)) * pitch
    ph = 2 * np.pi * np.cumsum(f) / SR
    thump = np.sin(ph) * (1 - np.exp(-t / 0.006)) * np.exp(-t * 24)
    nasal = np.sin(2 * ph) * (1 - np.exp(-t / 0.006)) * np.exp(-t * 30) * 0.45
    mush = g.lowpass(g.bandpass(noise(sec), 500, 2200), 2400)
    mush = mush * (1 - np.exp(-t / 0.012)) * np.exp(-t * 19) * (0.65 + 0.35 * np.sin(2 * np.pi * 24 * t + phase))
    return finish(thump * 0.8 + nasal + mush * 0.7, 0.7, 25.0, 5.0)


# ───────────────────────── голоса без слов ─────────────────────────

def s_praise_1():  # «Молодец!» — быстрый подъём и звон
    buf = np.zeros(int(0.95 * SR))
    seq(buf, (72, 76, 79, 84), 0.07, sec=0.4, decay=6.0, gain=0.75)
    for m in (84, 88, 91):
        place(buf, glass(mtof(m), 0.6, 4.5), 0.29, 0.26)
    return finish(reverb(buf, 0.18), 0.72, 40.0)


def s_praise_2():  # «Умничка!» — «та-да»
    buf = np.zeros(int(0.85 * SR))
    place(buf, pluck(79, 0.25, 11.0, 0.7), 0.0, 1.0)
    place(buf, pluck(84, 0.6, 5.0, 0.9), 0.16, 1.0)
    for m in (84, 88):
        place(buf, glass(mtof(m), 0.55, 4.5), 0.18, 0.26)
    place(buf, glass(mtof(96), 0.3, 12.0), 0.2, 0.14)
    return finish(reverb(buf, 0.18), 0.72, 40.0)


def s_praise_3():  # «Ура!» — «та-та-та-ТАМ!»
    buf = np.zeros(int(0.9 * SR))
    for at, m in ((0.0, 72), (0.09, 72), (0.18, 79)):
        place(buf, pluck(m, 0.2, 12.0, 0.7), at, 1.0)
    place(buf, pluck(84, 0.6, 5.0, 0.95), 0.30, 1.0)
    for m in (84, 88, 91):
        place(buf, glass(mtof(m), 0.55, 4.5), 0.31, 0.22)
    return finish(reverb(buf, 0.18), 0.72, 40.0)


def cuckoo(buf, at):
    place(buf, flute(784.0, 0.17, 0.9), at, 1.0)          # «ку»
    place(buf, flute(659.3, 0.22, 0.85, 0.008), at + 0.22, 1.0)  # «ку» — малая терция вниз


def s_peekaboo_1():  # «Ку-ку!» — кукушка
    buf = np.zeros(int(0.62 * SR))
    cuckoo(buf, 0.0)
    return finish(reverb(buf, 0.10), 0.7, 40.0)


def s_peekaboo_2():  # «Ку-ку! А вот и я!» — кукушка и весёлый подскок
    buf = np.zeros(int(1.05 * SR))
    cuckoo(buf, 0.0)
    place(buf, pluck(76, 0.3, 9.0, 0.7), 0.58, 1.0)
    place(buf, pluck(81, 0.45, 6.0, 0.9), 0.70, 1.0)
    place(buf, glass(mtof(93), 0.3, 10.0), 0.72, 0.16)
    return finish(reverb(buf, 0.14), 0.7, 50.0)


def s_yum_voice():  # «Ням-ням!» — два довольных «ням» и улыбка
    buf = np.zeros(int(0.65 * SR))
    for at, f0, f1 in ((0.0, 330, 220), (0.20, 370, 247)):
        place(buf, drop(f0, f1, 0.22, 0.09, 13, 0.35), at, 0.9)
    place(buf, pluck(81, 0.3, 8.0, 0.6), 0.42, 1.0)
    return finish(reverb(buf, 0.10), 0.68, 35.0)


def s_hint_tap():  # «Нажми!» — два лёгких «тип-тип»
    buf = np.zeros(int(0.38 * SR))
    place(buf, mallet(mtof(88), 0.2, 14.0), 0.0, 0.8)
    place(buf, mallet(mtof(88), 0.2, 14.0), 0.14, 0.65)
    return finish(reverb(buf, 0.08), 0.68, 25.0, 1.0)


def s_hint_drag():  # «Потяни сюда!» — плавный подъём и «дзинь» на конце
    buf = np.zeros(int(0.8 * SR))
    place(buf, drop(330, 760, 0.42, 0.34, 4.0, 0.3), 0.0, 0.6)
    place(buf, glass(mtof(88), 0.4, 6.0), 0.34, 0.5)
    return finish(reverb(buf, 0.14), 0.62, 60.0, 8.0)


def s_hint_look():  # «Смотри!» — «динь-дон»
    buf = np.zeros(int(0.85 * SR))
    place(buf, glass(mtof(76), 0.6, 4.5), 0.0, 0.8)
    place(buf, glass(mtof(72), 0.7, 4.0), 0.24, 0.85)
    return finish(reverb(buf, 0.16), 0.66, 50.0)


def s_arrow_normal():  # «Стрела!» — чистый деревянный «ток»
    buf = np.zeros(int(0.32 * SR))
    place(buf, pluck(67, 0.28, 13.0, 0.9, 0.05), 0.0, 1.0)
    place(buf, pluck(55, 0.2, 18.0, 0.5, 0.0), 0.0, 1.0)
    return finish(reverb(buf, 0.06), 0.7, 25.0, 1.0)


def s_arrow_carrot():  # «Морковка!» — два упругих «бон-бон»
    buf = np.zeros(int(0.45 * SR))
    place(buf, pluck(67, 0.25, 11.0, 0.85), 0.0, 1.0)
    place(buf, pluck(72, 0.3, 9.0, 0.9), 0.14, 1.0)
    return finish(reverb(buf, 0.08), 0.7, 30.0)


def s_arrow_star():  # «Звёздочка!» — мерцающая тройка
    buf = np.zeros(int(0.75 * SR))
    for at, m in ((0.0, 76), (0.06, 79), (0.12, 84)):  # E5 G5 C6: мерцание без «режущего» блеска
        place(buf, glass(mtof(m), 0.55, 6.0), at, 0.7)
    return finish(reverb(buf, 0.18), 0.64, 50.0)


# имя → (нота MIDI, характер): крупные зверьки ниже, мелкие выше
ANIMALS = {
    "elephant": (53, "note"), "cow": (57, "note"), "bear": (59, "note"), "dino": (55, "slide"),
    "horse": (62, "note"), "dog": (64, "note"), "sheep": (66, "note"), "pig": (67, "note"),
    "piglet": (71, "note"), "cat": (74, "note"), "duck": (69, "note"), "frog": (65, "bloop"),
    "chick": (84, "note"), "robot": (76, "robot"),
}


def s_animal_name(midi, kind):
    buf = np.zeros(int(0.42 * SR))
    f = mtof(midi)
    if kind == "bloop":      # лягушка: «бульк» снизу вверх
        place(buf, drop(f * 0.7, f * 1.25, 0.3, 0.09, 9.0, 0.3), 0.0, 0.9)
    elif kind == "slide":    # динозавр: тяжёлая нота с лёгким «оседанием»
        place(buf, drop(f * 1.12, f, 0.38, 0.12, 6.0, 0.4), 0.0, 0.9)
        place(buf, pluck(midi + 7, 0.25, 12.0, 0.35, 0.0), 0.0, 1.0)
    elif kind == "robot":    # робот: «бип-буп»
        for at, m in ((0.0, midi), (0.15, midi + 5)):
            t = tt(0.14)
            sq = np.sin(2 * np.pi * mtof(m) * t) + 0.3 * np.sin(2 * np.pi * mtof(m) * 3 * t)
            place(buf, sq * np.minimum(1, t / 0.006) * np.exp(-t * 12), at, 0.7)
    else:
        place(buf, pluck(midi, 0.4, 7.0, 0.9, 0.10), 0.0, 1.0)
    return finish(reverb(buf, 0.08), 0.68, 35.0)


def s_shape(kind):
    buf = np.zeros(int(0.5 * SR))
    if kind == "circle":     # круг: гладкая «капелька»
        place(buf, drop(520, 590, 0.4, 0.15, 7.0, 0.2), 0.0, 0.9)
    elif kind == "square":   # квадрат: чёткий «ток»
        place(buf, pluck(72, 0.3, 13.0, 0.9, 0.04), 0.0, 1.0)
    elif kind == "triangle": # треугольник: звонкий «дзинь»
        place(buf, glass(mtof(79), 0.45, 5.5), 0.0, 0.8)
    elif kind == "star":     # звезда: мерцание
        place(buf, glass(mtof(79), 0.4, 6.0), 0.0, 0.7)
        place(buf, glass(mtof(83), 0.4, 6.0), 0.07, 0.6)
    elif kind == "heart":    # сердце: два тёплых мягких тона
        place(buf, pluck(69, 0.35, 6.0, 0.8, 0.05), 0.0, 1.0)
        place(buf, pluck(76, 0.4, 5.5, 0.85, 0.05), 0.2, 1.0)
    else:                    # овал: мягкая средняя капля
        place(buf, drop(430, 480, 0.4, 0.2, 7.0, 0.15), 0.0, 0.9)
    return finish(reverb(buf, 0.08), 0.66, 35.0)


PHONE_FRIENDS = {  # друг по телефону: имя → (нота, «петушиный» мотив)
    "cow": (57, False), "dog": (64, False), "cat": (74, False), "duck": (69, False), "frog": (65, False),
    "pig": (67, False), "horse": (62, False), "sheep": (66, False), "chick": (84, False),
    "rooster": (79, True), "bunny": (72, False),
}


def s_phone_hi(midi, rooster):
    """«Привет!» от друга: тёплый мотив из трёх нот (у петуха — четыре, повыше) и искорка."""
    buf = np.zeros(int(1.0 * SR))
    notes = (midi, midi + 4, midi + 7) if not rooster else (midi, midi + 5, midi + 9, midi + 12)
    for i, m in enumerate(notes):
        place(buf, pluck(m, 0.4, 7.0, 0.85, 0.08), i * 0.13, 1.0)
    place(buf, glass(mtof(notes[-1] + 12), 0.5, 7.0), len(notes) * 0.13, 0.22)
    return finish(reverb(buf, 0.14), 0.68, 50.0)


# ───────────────────────── сборка ─────────────────────────

def jobs():
    j = {
        "whoosh_soft": s_whoosh_soft,
        "whoosh": s_whoosh,
        "fx_zone_transition": s_zone_transition,
        "kc_munch1": lambda: s_munch(1.0, 0.0),
        "kc_munch2": lambda: s_munch(1.13, 1.7),
    }
    for i, hz in enumerate(RAIN_DROP_HZ):
        j[f"rain_drop{i + 1}"] = (lambda hz=hz: s_rain_drop(hz))
    j["rain_splash"] = s_rain_splash
    for i, m in enumerate(BALLOON_NOTES):
        j[f"bp_pop{i + 1}"] = (lambda m=m: s_balloon_pop(m))
    voices = {
        "praise_1": s_praise_1, "praise_2": s_praise_2, "praise_3": s_praise_3,
        "peekaboo_1": s_peekaboo_1, "peekaboo_2": s_peekaboo_2, "yum_voice": s_yum_voice,
        "voice_hint_tap": s_hint_tap, "voice_hint_drag": s_hint_drag, "voice_hint_look": s_hint_look,
        "voice_arrow_normal": s_arrow_normal, "voice_arrow_carrot": s_arrow_carrot, "voice_arrow_star": s_arrow_star,
    }
    for name, (midi, kind) in ANIMALS.items():
        voices[f"voice_name_{name}"] = (lambda midi=midi, kind=kind: s_animal_name(midi, kind))
    for kind in ("circle", "square", "triangle", "star", "heart", "oval"):
        voices[f"voice_shape_{kind}"] = (lambda kind=kind: s_shape(kind))
    for name, (midi, rooster) in PHONE_FRIENDS.items():
        voices[f"phone_hi_{name}"] = (lambda midi=midi, rooster=rooster: s_phone_hi(midi, rooster))
    for name, fn in voices.items():
        j[name] = fn
        j[name + "_en"] = fn  # слов нет — английская копия звучит так же
    return j


def main():
    total = 0
    cache = {}
    for name, fn in jobs().items():
        base = name[:-3] if name.endswith("_en") else name
        if name.endswith("_en") and base in cache:
            x = cache[base]
        else:
            x = fn()
            cache[name] = x
        out = g.encode(x, name)
        total += os.path.getsize(out)
    print(f"{len(jobs())} файлов, {total / 1024:.0f} КБ -> {g.OUT_DIR}")


if __name__ == "__main__":
    sys.exit(main())
