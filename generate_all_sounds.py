#!/usr/bin/env python3
import math
import os
import random
import struct
import subprocess

SAMPLE_RATE = 44100

def write_wav(filename, samples, sample_rate=SAMPLE_RATE):
    # samples: list of floats in [-1.0, 1.0]
    num_samples = len(samples)
    data = bytearray()
    for s in samples:
        s_clamped = max(-1.0, min(1.0, s))
        val = int(s_clamped * 32767)
        data.extend(struct.pack('<h', val))
    
    # 44 byte header
    header = bytearray()
    header.extend(b'RIFF')
    header.extend(struct.pack('<I', 36 + len(data)))
    header.extend(b'WAVEfmt ')
    header.extend(struct.pack('<I', 16)) # Subchunk1Size (16 for PCM)
    header.extend(struct.pack('<H', 1))  # AudioFormat (1 = PCM)
    header.extend(struct.pack('<H', 1))  # NumChannels (1 = Mono)
    header.extend(struct.pack('<I', sample_rate))
    header.extend(struct.pack('<I', sample_rate * 2)) # ByteRate
    header.extend(struct.pack('<H', 2))  # BlockAlign
    header.extend(struct.pack('<H', 16)) # BitsPerSample
    header.extend(b'data')
    header.extend(struct.pack('<I', len(data)))
    
    with open(filename, 'wb') as f:
        f.write(header + data)

def gen_tone(freq, dur_s, amp=0.5, fade_in=0.01, fade_out=0.01):
    num_samples = int(dur_s * SAMPLE_RATE)
    out = []
    fade_in_n = int(fade_in * SAMPLE_RATE)
    fade_out_n = int(fade_out * SAMPLE_RATE)
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        val = math.sin(2 * math.pi * freq * t) * amp
        if i < fade_in_n:
            val *= (i / max(1, fade_in_n))
        elif i > num_samples - fade_out_n:
            val *= ((num_samples - i) / max(1, fade_out_n))
        out.append(val)
    return out

def gen_pop(dur_s=0.08, start_f=600, end_f=180, amp=0.7):
    n = int(dur_s * SAMPLE_RATE)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        prog = i / n
        freq = start_f + (end_f - start_f) * (prog ** 0.5)
        env = (1.0 - prog) ** 2
        val = math.sin(2 * math.pi * freq * t) * env * amp
        out.append(val)
    return out

def gen_chime(notes, dur_s=0.6, amp=0.4):
    n = int(dur_s * SAMPLE_RATE)
    out = [0.0] * n
    for note_f, offset_s in notes:
        offset_n = int(offset_s * SAMPLE_RATE)
        for i in range(n - offset_n):
            idx = offset_n + i
            t = i / SAMPLE_RATE
            env = math.exp(-t * 6.0)
            harmonics = (math.sin(2 * math.pi * note_f * t) +
                         0.4 * math.sin(2 * math.pi * note_f * 2 * t) +
                         0.15 * math.sin(2 * math.pi * note_f * 3 * t))
            out[idx] += harmonics * env * amp
    return out

def gen_noise(dur_s, amp=0.3, fade_in=0.02, fade_out=0.02):
    n = int(dur_s * SAMPLE_RATE)
    out = []
    fade_in_n = int(fade_in * SAMPLE_RATE)
    fade_out_n = int(fade_out * SAMPLE_RATE)
    for i in range(n):
        val = (random.random() * 2.0 - 1.0) * amp
        if i < fade_in_n:
            val *= (i / max(1, fade_in_n))
        elif i > n - fade_out_n:
            val *= ((n - i) / max(1, fade_out_n))
        out.append(val)
    return out

def gen_squish(dur_s=0.25, base_f=220):
    n = int(dur_s * SAMPLE_RATE)
    out = []
    for i in range(n):
        prog = i / n
        t = i / SAMPLE_RATE
        f = base_f + 140 * math.sin(prog * math.pi * 3)
        env = math.sin(prog * math.pi)
        # FM synthesis squish
        mod = math.sin(2 * math.pi * 45 * t) * 0.4
        val = math.sin(2 * math.pi * f * t + mod) * env * 0.6
        out.append(val)
    return out

def gen_boing(dur_s=0.4, f0=180, f1=420):
    n = int(dur_s * SAMPLE_RATE)
    out = []
    for i in range(n):
        prog = i / n
        t = i / SAMPLE_RATE
        f = f0 + (f1 - f0) * prog + 30 * math.sin(prog * 20 * math.pi)
        env = (1.0 - prog) ** 1.5
        val = math.sin(2 * math.pi * f * t) * env * 0.6
        out.append(val)
    return out

def gen_whistle(dur_s=0.8, f_start=600, f_end=1200):
    n = int(dur_s * SAMPLE_RATE)
    out = []
    for i in range(n):
        prog = i / n
        t = i / SAMPLE_RATE
        f = f_start + (f_end - f_start) * (prog ** 1.2)
        env = math.sin(prog * math.pi)
        val = math.sin(2 * math.pi * f * t) * env * 0.5
        out.append(val)
    return out

def gen_animal_sound(kind):
    if kind == "dog":
        # 2 short barks
        b1 = gen_tone(240, 0.15, amp=0.7, fade_in=0.01, fade_out=0.03)
        sil = [0.0] * int(0.08 * SAMPLE_RATE)
        b2 = gen_tone(280, 0.18, amp=0.7, fade_in=0.01, fade_out=0.04)
        return b1 + sil + b2
    elif kind == "cat":
        # mew sweep: 500 -> 750 -> 400
        n = int(0.6 * SAMPLE_RATE)
        out = []
        for i in range(n):
            prog = i / n
            t = i / SAMPLE_RATE
            f = 500 + 350 * math.sin(prog * math.pi)
            env = math.sin(prog * math.pi)
            val = (math.sin(2 * math.pi * f * t) + 0.3 * math.sin(4 * math.pi * f * t)) * env * 0.5
            out.append(val)
        return out
    elif kind == "cow":
        # moo: 130 Hz sustained
        n = int(1.1 * SAMPLE_RATE)
        out = []
        for i in range(n):
            prog = i / n
            t = i / SAMPLE_RATE
            f = 135 - 15 * prog
            env = math.sin(prog * math.pi)
            val = (math.sin(2 * math.pi * f * t) + 0.5 * math.sin(4 * math.pi * f * t) + 0.2 * math.sin(6 * math.pi * f * t)) * env * 0.6
            out.append(val)
        return out
    elif kind == "pig":
        # oink oink: 2 guttural grunts
        g1 = gen_noise(0.12, amp=0.5)
        sil = [0.0] * int(0.06 * SAMPLE_RATE)
        g2 = gen_noise(0.15, amp=0.5)
        return g1 + sil + g2
    elif kind == "frog":
        # ribbit: rapid clicks
        n = int(0.35 * SAMPLE_RATE)
        out = []
        for i in range(n):
            prog = i / n
            t = i / SAMPLE_RATE
            click = math.sin(2 * math.pi * 320 * t) * (1.0 if (int(t * 40) % 2 == 0) else 0.1)
            env = math.sin(prog * math.pi)
            out.append(click * env * 0.6)
        return out
    elif kind == "duck":
        # quack: 380 Hz formant nasal
        n = int(0.3 * SAMPLE_RATE)
        out = []
        for i in range(n):
            prog = i / n
            t = i / SAMPLE_RATE
            f = 420 - 100 * prog
            env = math.sin(prog * math.pi)
            val = (math.sin(2 * math.pi * f * t) + 0.6 * math.sin(4 * math.pi * f * t)) * env * 0.6
            out.append(val)
        return out
    elif kind == "sheep":
        # baaa: vibrato 260 Hz
        n = int(0.8 * SAMPLE_RATE)
        out = []
        for i in range(n):
            prog = i / n
            t = i / SAMPLE_RATE
            f = 260 + 20 * math.sin(2 * math.pi * 9 * t)
            env = math.sin(prog * math.pi)
            val = math.sin(2 * math.pi * f * t) * env * 0.6
            out.append(val)
        return out
    elif kind == "rooster":
        # short crow: 600 -> 800 -> 900 -> 700
        n = int(1.1 * SAMPLE_RATE)
        out = []
        for i in range(n):
            prog = i / n
            t = i / SAMPLE_RATE
            f = 500 + 400 * prog
            env = math.sin(prog * math.pi)
            val = (math.sin(2 * math.pi * f * t) + 0.4 * math.sin(4 * math.pi * f * t)) * env * 0.6
            out.append(val)
        return out
    elif kind == "goose":
        n = int(0.4 * SAMPLE_RATE)
        out = []
        for i in range(n):
            prog = i / n
            t = i / SAMPLE_RATE
            f = 550 - 50 * prog
            env = math.sin(prog * math.pi)
            val = (math.sin(2 * math.pi * f * t) + 0.7 * math.sin(4 * math.pi * f * t)) * env * 0.6
            out.append(val)
        return out
    elif kind == "horse":
        n = int(0.8 * SAMPLE_RATE)
        out = []
        for i in range(n):
            prog = i / n
            t = i / SAMPLE_RATE
            f = 800 - 300 * prog + 80 * math.sin(2 * math.pi * 14 * t)
            env = math.sin(prog * math.pi)
            val = (math.sin(2 * math.pi * f * t) + 0.3 * math.sin(4 * math.pi * f * t)) * env * 0.5
            out.append(val)
        return out
    elif kind == "crow":
        # caw: 700 Hz rough
        n = int(0.5 * SAMPLE_RATE)
        out = []
        for i in range(n):
            prog = i / n
            t = i / SAMPLE_RATE
            f = 750 - 100 * prog
            noise = (random.random() * 2 - 1) * 0.2
            env = math.sin(prog * math.pi)
            val = (math.sin(2 * math.pi * f * t) + noise) * env * 0.6
            out.append(val)
        return out
    elif kind == "bunny":
        # soft squeak: 1200 Hz tiny
        return gen_tone(1200, 0.2, amp=0.3, fade_in=0.02, fade_out=0.05)
    return gen_tone(440, 0.3)

os.makedirs("input", exist_ok=True)

# Generate Voice sounds using espeak-ng with warm toddler/female-friendly pitch
voice_dict = {
    "voice_arrow_normal": "Стрела!",
    "voice_arrow_carrot": "Морковка!",
    "voice_arrow_star": "Звёздочка!",
    "praise_1": "Молодец!",
    "praise_2": "Умничка!",
    "praise_3": "Ура!",
    "peekaboo_1": "Ку-ку!",
    "peekaboo_2": "Ку-ку! А вот и я!",
    "yum_voice": "Ням-ням!",
    "voice_hint_tap": "Нажми!",
    "voice_hint_drag": "Потяни сюда!",
    "voice_hint_look": "Смотри!",
    "voice_name_cow": "Корова",
    "voice_name_dog": "Собака",
    "voice_name_cat": "Кошка",
    "voice_name_duck": "Утка",
    "voice_name_pig": "Свинка",
    "voice_name_frog": "Лягушка",
    "voice_shape_circle": "Круг",
    "voice_shape_square": "Квадрат",
    "voice_shape_triangle": "Треугольник",
    "voice_shape_star": "Звезда",
    "voice_shape_heart": "Сердце",
    "voice_shape_oval": "Овал",
}

for name, phrase in voice_dict.items():
    wav_path = f"input/{name}.wav"
    subprocess.run(["espeak-ng", "-v", "ru+f3", "-s", "135", "-p", "70", phrase, "-w", wav_path], check=True)
    print(f"Generated voice: {wav_path}")

# Generate Sound Effects
sfx_recipes = {
    # Animals
    "animal_dog_1": lambda: gen_animal_sound("dog"),
    "animal_dog_2": lambda: gen_tone(320, 0.15, amp=0.6),
    "animal_cat_1": lambda: gen_animal_sound("cat"),
    "animal_cat_2": lambda: gen_tone(650, 0.4, amp=0.4),
    "animal_cow_1": lambda: gen_animal_sound("cow"),
    "animal_cow_2": lambda: gen_tone(120, 0.9, amp=0.5),
    "animal_horse_1": lambda: gen_animal_sound("horse"),
    "animal_horse_2": lambda: gen_animal_sound("horse"),
    "animal_pig_1": lambda: gen_animal_sound("pig"),
    "animal_pig_2": lambda: gen_animal_sound("pig"),
    "animal_sheep_1": lambda: gen_animal_sound("sheep"),
    "animal_sheep_2": lambda: gen_animal_sound("sheep"),
    "animal_goose_1": lambda: gen_animal_sound("goose"),
    "animal_goose_2": lambda: gen_animal_sound("goose"),
    "animal_rooster_1": lambda: gen_animal_sound("rooster"),
    "animal_rooster_2": lambda: gen_animal_sound("rooster"),
    "animal_frog_1": lambda: gen_animal_sound("frog"),
    "animal_frog_2": lambda: gen_animal_sound("frog"),
    "animal_crow_1": lambda: gen_animal_sound("crow"),
    "animal_crow_2": lambda: gen_animal_sound("crow"),
    "animal_duck_1": lambda: gen_animal_sound("duck"),
    "animal_duck_2": lambda: gen_animal_sound("duck"),
    "animal_bunny_1": lambda: gen_animal_sound("bunny"),
    "animal_bunny_2": lambda: gen_animal_sound("bunny"),
    "animal_dog_happy": lambda: gen_tone(450, 0.3, amp=0.4),
    "animal_cat_happy": lambda: gen_tone(220, 0.5, amp=0.3),
    "animal_bunny_happy": lambda: gen_tone(950, 0.25, amp=0.3),
    # Feeding
    "pickup": lambda: gen_pop(dur_s=0.08, start_f=450, end_f=750, amp=0.7),
    "eat_in": lambda: gen_pop(dur_s=0.15, start_f=600, end_f=250, amp=0.7),
    "chew_1": lambda: gen_squish(dur_s=0.25, base_f=280),
    "chew_2": lambda: gen_squish(dur_s=0.22, base_f=310),
    "yum": lambda: gen_chime([(440, 0.0), (554, 0.15), (659, 0.3)], dur_s=0.6, amp=0.5),
    # Common
    "tap": lambda: gen_pop(dur_s=0.04, start_f=800, end_f=400, amp=0.5),
    "pop": lambda: gen_pop(dur_s=0.08, start_f=750, end_f=250, amp=0.7),
    "whoosh": lambda: gen_noise(dur_s=0.35, amp=0.4),
    "whoosh_soft": lambda: gen_noise(dur_s=0.25, amp=0.25),
    "chime": lambda: gen_chime([(523, 0.0), (659, 0.1), (784, 0.2), (1046, 0.3)], dur_s=0.8),
    "fanfare_short": lambda: gen_chime([(523, 0.0), (659, 0.12), (784, 0.24), (1046, 0.38)], dur_s=1.1, amp=0.6),
    "soft_no": lambda: gen_tone(280, 0.3, amp=0.3),
    "spawn": lambda: gen_pop(dur_s=0.12, start_f=300, end_f=650, amp=0.6),
    "open": lambda: gen_pop(dur_s=0.1, start_f=400, end_f=700, amp=0.5),
    # Busyboard
    "lever_clack": lambda: gen_pop(dur_s=0.1, start_f=900, end_f=200, amp=0.8),
    "lever_hum_loop": lambda: gen_tone(110, 1.0, amp=0.2, fade_in=0.0, fade_out=0.0),
    "confetti_rain": lambda: gen_chime([(659, 0.0), (784, 0.1), (880, 0.2), (1046, 0.3), (1318, 0.4)], dur_s=1.2),
    "train_toot": lambda: gen_whistle(dur_s=0.8, f_start=440, f_end=580),
    "star_burst": lambda: gen_chime([(880, 0.0), (1046, 0.08), (1318, 0.16), (1760, 0.24)], dur_s=1.0),
    "quack_1": lambda: gen_animal_sound("duck"),
    "quack_2": lambda: gen_animal_sound("duck"),
    "quack_3": lambda: gen_animal_sound("duck"),
    "frog_boing": lambda: gen_boing(dur_s=0.4, f0=150, f1=400),
    "bubble": lambda: gen_pop(dur_s=0.12, start_f=400, end_f=850, amp=0.6),
    "firework": lambda: gen_chime([(587, 0.0), (740, 0.1), (880, 0.2), (1174, 0.3)], dur_s=1.2),
    "switch_on": lambda: gen_pop(dur_s=0.05, start_f=900, end_f=500, amp=0.6),
    "switch_off": lambda: gen_pop(dur_s=0.05, start_f=500, end_f=250, amp=0.6),
    "jingle_lights": lambda: gen_chime([(523, 0.0), (587, 0.15), (659, 0.3), (784, 0.45)], dur_s=1.2),
    "knob_tick": lambda: gen_pop(dur_s=0.03, start_f=1200, end_f=600, amp=0.4),
    "door_creak": lambda: gen_squish(dur_s=0.4, base_f=180),
    "door_close": lambda: gen_pop(dur_s=0.12, start_f=350, end_f=150, amp=0.7),
    "bell_ding": lambda: gen_chime([(1046, 0.0)], dur_s=0.9, amp=0.6),
    # Rain
    "rain_loop": lambda: gen_noise(2.0, amp=0.15, fade_in=0.0, fade_out=0.0),
    "drop_plip_1": lambda: gen_pop(dur_s=0.08, start_f=900, end_f=1400, amp=0.5),
    "drop_plip_2": lambda: gen_pop(dur_s=0.08, start_f=1100, end_f=1600, amp=0.5),
    "drop_plip_3": lambda: gen_pop(dur_s=0.08, start_f=850, end_f=1300, amp=0.5),
    "rainbow_chime": lambda: gen_chime([(523, 0.0), (659, 0.12), (784, 0.24), (1046, 0.36), (1318, 0.48)], dur_s=1.1),
    # Catapult
    "sling_fire": lambda: gen_boing(dur_s=0.3, f0=400, f1=150),
    "parachute_open": lambda: gen_noise(dur_s=0.35, amp=0.35),
    "land_bounce": lambda: gen_boing(dur_s=0.25, f0=140, f1=240),
    "star_collect": lambda: gen_chime([(880, 0.0), (1318, 0.1)], dur_s=0.4),
    "cloud_bounce": lambda: gen_boing(dur_s=0.35, f0=200, f1=420),
    # Boxes
    "box_shake": lambda: gen_squish(dur_s=0.4, base_f=160),
    "box_pop": lambda: gen_pop(dur_s=0.14, start_f=280, end_f=650, amp=0.7),
    "chick_peep": lambda: gen_tone(1600, 0.18, amp=0.4),
    "elephant_toot": lambda: gen_whistle(dur_s=0.6, f_start=240, f_end=480),
    "robot_beep": lambda: gen_chime([(800, 0.0), (1200, 0.12)], dur_s=0.35),
    "dino_soft": lambda: gen_tone(110, 0.5, amp=0.5),
    # Archery
    "bow_pull_loop": lambda: gen_noise(1.0, amp=0.15, fade_in=0.0, fade_out=0.0),
    "shoot": lambda: gen_noise(0.2, amp=0.5),
    "arrow_hit": lambda: gen_pop(dur_s=0.1, start_f=350, end_f=120, amp=0.7),
    "carrot_hit": lambda: gen_pop(dur_s=0.12, start_f=450, end_f=200, amp=0.6),
    "star_hit": lambda: gen_chime([(1046, 0.0), (1318, 0.08)], dur_s=0.4),
    "scarecrow_giggle": lambda: gen_chime([(587, 0.0), (740, 0.12), (587, 0.24)], dur_s=0.5),
    "wobble": lambda: gen_boing(dur_s=0.4, f0=220, f1=180),
    "thud": lambda: gen_pop(dur_s=0.12, start_f=180, end_f=90, amp=0.6),
    # Squishy
    "squish_1": lambda: gen_squish(dur_s=0.25, base_f=200),
    "squish_2": lambda: gen_squish(dur_s=0.22, base_f=240),
    "squish_3": lambda: gen_squish(dur_s=0.28, base_f=180),
    "stretch": lambda: gen_squish(dur_s=0.45, base_f=260),
    "release_boing": lambda: gen_boing(dur_s=0.35, f0=180, f1=360),
    "fly": lambda: gen_whistle(dur_s=0.6, f_start=350, f_end=850),
    "bounce": lambda: gen_boing(dur_s=0.25, f0=220, f1=380),
    "poke": lambda: gen_pop(dur_s=0.08, start_f=500, end_f=280, amp=0.6),
    "giggle": lambda: gen_chime([(659, 0.0), (784, 0.1), (659, 0.2), (784, 0.3)], dur_s=0.6),
    "dizzy": lambda: gen_whistle(dur_s=0.7, f_start=600, f_end=300),
    "yawn": lambda: gen_whistle(dur_s=0.8, f_start=400, f_end=220),
    "wear": lambda: gen_chime([(784, 0.0), (1046, 0.1)], dur_s=0.35),
    "gift": lambda: gen_chime([(523, 0.0), (659, 0.1), (784, 0.2), (1046, 0.3)], dur_s=0.9),
    # Pinball
    "bumper": lambda: gen_boing(dur_s=0.22, f0=280, f1=520),
    "sling": lambda: gen_boing(dur_s=0.18, f0=320, f1=600),
    "flip": lambda: gen_pop(dur_s=0.06, start_f=650, end_f=280, amp=0.5),
    "wall": lambda: gen_pop(dur_s=0.05, start_f=450, end_f=220, amp=0.4),
    "blub": lambda: gen_pop(dur_s=0.25, start_f=350, end_f=150, amp=0.6),
    # Figures
    "snap_in": lambda: gen_pop(dur_s=0.14, start_f=400, end_f=180, amp=0.7),
    # Tea
    "splash_small": lambda: gen_pop(dur_s=0.18, start_f=600, end_f=1200, amp=0.5),
    "stir": lambda: gen_squish(dur_s=0.6, base_f=320),
    "steam": lambda: gen_noise(dur_s=0.8, amp=0.2),
    "glug": lambda: gen_pop(dur_s=0.2, start_f=250, end_f=180, amp=0.6),
    "clink": lambda: gen_chime([(1480, 0.0)], dur_s=0.25, amp=0.5),
    # Decor
    "decor_sun": lambda: gen_chime([(659, 0.0), (880, 0.12)], dur_s=0.4),
    "decor_cloud": lambda: gen_noise(dur_s=0.35, amp=0.25),
    "decor_butterfly": lambda: gen_chime([(987, 0.0), (1318, 0.1)], dur_s=0.35),
    "decor_bird": lambda: gen_tone(1500, 0.2, amp=0.4),
    "decor_bee": lambda: gen_tone(240, 0.35, amp=0.3),
    "decor_flower": lambda: gen_chime([(784, 0.0), (1046, 0.12)], dur_s=0.35),
    "decor_tree": lambda: gen_tone(220, 0.3, amp=0.4),
    "decor_mushroom": lambda: gen_boing(dur_s=0.25, f0=220, f1=340),
    "decor_snail": lambda: gen_squish(dur_s=0.3, base_f=180),
    "decor_star": lambda: gen_chime([(1046, 0.0), (1568, 0.1)], dur_s=0.4),
    "decor_fish": lambda: gen_pop(dur_s=0.15, start_f=450, end_f=900, amp=0.5),
    "decor_ball": lambda: gen_boing(dur_s=0.25, f0=240, f1=420),
    "decor_yarn": lambda: gen_squish(dur_s=0.25, base_f=260),
    "decor_kite": lambda: gen_noise(dur_s=0.3, amp=0.3),
    "decor_rainbow": lambda: gen_chime([(523, 0.0), (659, 0.1), (784, 0.2), (1046, 0.3)], dur_s=0.7),
    "decor_balloon_fly": lambda: gen_whistle(dur_s=0.4, f_start=300, f_end=700),
    "decor_plant": lambda: gen_pop(dur_s=0.15, start_f=350, end_f=750, amp=0.5),
    "decor_pumpkin": lambda: gen_boing(dur_s=0.3, f0=180, f1=320),
    "decor_sunflower": lambda: gen_chime([(880, 0.0), (1174, 0.1)], dur_s=0.4),
    "decor_gear": lambda: gen_pop(dur_s=0.08, start_f=950, end_f=500, amp=0.5),
    "decor_guest": lambda: gen_chime([(587, 0.0), (880, 0.12)], dur_s=0.45),
    "decor_teapot": lambda: gen_whistle(dur_s=0.5, f_start=800, f_end=1200),
    "decor_cake": lambda: gen_boing(dur_s=0.25, f0=260, f1=400),
    "decor_mouse": lambda: gen_tone(1800, 0.15, amp=0.3),
    "decor_bulb": lambda: gen_chime([(1318, 0.0)], dur_s=0.25, amp=0.5),
    "decor_crayon": lambda: gen_noise(dur_s=0.2, amp=0.3),
    "splash_soft": lambda: gen_pop(dur_s=0.14, start_f=450, end_f=900, amp=0.4),
    "sling_pull_loop": lambda: gen_squish(dur_s=1.0, base_f=160),
    "balloon_boost": lambda: gen_boing(dur_s=0.3, f0=280, f1=580),
    "bird_tweet": lambda: gen_tone(1700, 0.18, amp=0.35),
    "kite_swoosh": lambda: gen_noise(dur_s=0.35, amp=0.3),
    "rainbow_slide": lambda: gen_whistle(dur_s=0.6, f_start=400, f_end=1100),
    "ufo_hello": lambda: gen_chime([(600, 0.0), (900, 0.1), (1200, 0.2)], dur_s=0.4),
    "wind_loop": lambda: gen_noise(2.0, amp=0.15, fade_in=0.0, fade_out=0.0),
    # Rainbow buttons (Xylophone scale notes)
    "rb_red": lambda: gen_chime([(523, 0.0)], dur_s=0.35),      # C5
    "rb_orange": lambda: gen_chime([(587, 0.0)], dur_s=0.35),   # D5
    "rb_yellow": lambda: gen_chime([(659, 0.0)], dur_s=0.35),   # E5
    "rb_green": lambda: gen_chime([(698, 0.0)], dur_s=0.35),    # F5
    "rb_blue": lambda: gen_chime([(784, 0.0)], dur_s=0.35),     # G5
    "rb_purple": lambda: gen_chime([(880, 0.0)], dur_s=0.35),   # A5
    # Rest mode lullaby
    "lullaby_short": lambda: gen_chime([(523, 0.0), (659, 0.6), (784, 1.2), (1046, 1.8), (784, 2.4), (659, 3.0), (523, 3.6)], dur_s=5.0, amp=0.35),
    # Drawing & Grains
    "draw_loop": lambda: gen_noise(1.0, amp=0.2, fade_in=0.0, fade_out=0.0),
    "grain_pour_loop": lambda: gen_noise(1.5, amp=0.25, fade_in=0.0, fade_out=0.0),
    "grain_scoop_loop": lambda: gen_noise(1.0, amp=0.25, fade_in=0.0, fade_out=0.0),
    "stamp": lambda: gen_pop(dur_s=0.1, start_f=350, end_f=120, amp=0.7),
    # Balloons
    "balloon_pop_1": lambda: gen_pop(dur_s=0.1, start_f=650, end_f=150, amp=0.8),
    "balloon_pop_2": lambda: gen_pop(dur_s=0.1, start_f=750, end_f=180, amp=0.8),
    "balloon_pop_3": lambda: gen_pop(dur_s=0.1, start_f=550, end_f=120, amp=0.8),
    "rainbow_salute": lambda: gen_chime([(523, 0.0), (659, 0.1), (784, 0.2), (1046, 0.3), (1318, 0.4)], dur_s=1.2, amp=0.6),
}

for name, recipe in sfx_recipes.items():
    wav_path = f"input/{name}.wav"
    samples = recipe()
    write_wav(wav_path, samples)
    print(f"Generated sfx: {wav_path}")

print("All input WAV files generated successfully!")
