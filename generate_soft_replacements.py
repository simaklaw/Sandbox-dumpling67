#!/usr/bin/env python3
"""Чистка звука: Смешинки/Зелик/раннер/успех + все клиппующие семьи (fx_it_*, lw2_*, cw2_*, sw_*, btn_*).
Имена НЕ меняются. Погоду/музыку/лупы/bb2_/животных/голоса/_en НЕ трогаем.
Стандарт: моно 44.1k OGG q4, пик<=0.72, RMS -22..-13.
Запуск: --dry | (боевой) | --verify | --only=zb_
Нужны numpy, scipy, ffmpeg (libvorbis)."""
import os, sys, shutil, struct, subprocess
import numpy as np
from scipy.signal import butter, sosfilt
np.random.seed(67)
SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "app/src/main/res/raw")
BAK = os.path.join(HERE, "app/src/main/raw_backup")
TARGET_RMS = -18.0
PEAK_ENC = 0.65
PEAK_MAX = 0.72
RMS_BAND = (-25.0, -13.0)  # ТЗ: -22..-13; -25 — допуск для коротких острых звуков после лимитера
DRY = "--dry" in sys.argv
VERIFY = "--verify" in sys.argv
ONLY = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--only=")), "")


def tt(d):
    return np.arange(int(d * SR)) / SR


def mix(*s):
    n = max(len(x) for x in s)
    o = np.zeros(n, dtype=np.float32)
    for x in s:
        o[:len(x)] += x
    return o


def mallet(f, dur=.3, decay=6., bright=.3):
    t = tt(dur)
    n = len(t)
    env = np.exp(-decay * t)
    a = min(int(.006 * SR), n // 3)
    env[:a] *= np.linspace(0, 1, a)
    r = min(int(.04 * SR), n // 3)
    env[-r:] *= np.linspace(1, 0, r)
    return ((np.sin(2 * np.pi * f * t) + bright * np.sin(4 * np.pi * f * t) + .1 * np.sin(6 * np.pi * f * t)) * env).astype(np.float32)


def glide(f0, f1, dur=.4, decay=3.):
    t = tt(dur)
    n = len(t)
    ph = 2 * np.pi * np.cumsum(f0 + (f1 - f0) * t / dur) / SR
    env = np.exp(-decay * t)
    a = min(int(.01 * SR), n // 3)
    env[:a] *= np.linspace(0, 1, a)
    r = min(int(.06 * SR), n // 3)
    env[-r:] *= np.linspace(1, 0, r)
    return ((np.sin(ph) + .25 * np.sin(2 * ph)) * env).astype(np.float32)


def seq(notes, spacing=.1, dur=.3):
    o = np.zeros(int((spacing * (len(notes) - 1) + dur) * SR) + 1, dtype=np.float32)
    for i, f in enumerate(notes):
        s = mallet(f, dur)
        st = int(i * spacing * SR)
        o[st:st + len(s)] += s
    return o


def thud(dur=.4, f=90):
    t = tt(dur)
    w = np.sin(2 * np.pi * f * t) * np.exp(-9 * t)
    c = np.random.randn(int(.01 * SR)) * np.exp(-np.arange(int(.01 * SR)) / (.002 * SR)) * .3
    w[:len(c)] += c
    return w.astype(np.float32)


def puff(dur=.25, cut=1500):
    n = int(dur * SR)
    x = sosfilt(butter(3, cut, fs=SR, output="sos"), np.random.randn(n)).astype(np.float32)
    env = np.exp(-6 * np.arange(n) / SR)
    a = int(.005 * SR)
    env[:a] *= np.linspace(0, 1, a)
    r = int(.05 * SR)
    env[-r:] *= np.linspace(1, 0, r)
    return (x * env).astype(np.float32)


def swell():
    t = tt(1.0)
    env = np.exp(-1.6 * t)
    a = int(.12 * SR)
    env[:a] *= np.linspace(0, 1, a)
    r = int(.15 * SR)
    env[-r:] *= np.linspace(1, 0, r)
    w = np.sin(2 * np.pi * 131 * t) + .45 * np.sin(2 * np.pi * 196 * t) + .25 * np.sin(2 * np.pi * 262 * t)
    x = (w * env).astype(np.float32)
    th = thud(.3, 65)
    x[:len(th)] += th * .6
    return x


def fanfare():
    a = seq([C5, E5, G5, C6], .11, .3)
    ch = mix(mallet(E5, .55, 4.), mallet(G5, .55, 4.), mallet(C6, .55, 4.))
    o = np.zeros(int(1.15 * SR), dtype=np.float32)
    o[:len(a)] += a
    st = int(.5 * SR)
    o[st:st + len(ch)] += ch
    return o


def limit(x, ceil):
    """Мягкий лимитер: выше порога сжимаем плавно (tanh), пик не превышает ceil. RMS почти не страдает."""
    th = ceil * 0.6
    ax = np.abs(x)
    over = ax > th
    if not over.any():
        return x
    y = x.copy()
    y[over] = np.sign(x[over]) * (th + (ceil - th) * np.tanh((ax[over] - th) / (ceil - th)))
    return y


def norm(x, max_boost=None):
    x = x.astype(np.float32)
    cur = 20 * np.log10(np.sqrt(np.mean(x * x)) + 1e-9)
    g = TARGET_RMS - cur
    if max_boost is not None:
        g = min(g, max_boost)
    x = limit(x * 10 ** (g / 20), PEAK_ENC)
    pk = np.abs(x).max()
    if pk > PEAK_ENC:
        x *= PEAK_ENC / pk
    return x.astype(np.float32)


C4, E4, G4, A4 = 261.63, 329.63, 392., 440.
C5, E5, G5, A5, C6 = 523.25, 659.25, 783.99, 880., 1046.5
G3 = 196.
RECIPES = {
    "sm_tickle": seq([E5, G5], .07, .12), "sm_giggle": seq([G5, E5, C5], .1, .16), "sm_wee": glide(A5, E5, .4),
    "sm_oops": seq([E5, C5], .12, .2), "sm_tada": seq([C5, E5, G5, C6], .09, .3), "sm_honk": mallet(G3, .3, 5., .5),
    "sm_yawn": glide(E5, C5, .7, 2.), "sm_hic": mallet(C5, .1, 9.), "sm_sneeze": mix(puff(.22, 1400), mallet(A4, .2)),
    "sm_party": seq([C5, E5, G5, A5, G5, E5], .1, .25), "sm_blah": seq([G4, E4], .12, .18), "sm_boing": glide(C4, G4, .35, 4.),
    "sm_gasp": glide(E5, A5, .2, 5.), "sm_inflate": glide(C5, G5, .5, 3.), "sm_whirl": glide(G5, E5, .6, 3.),
    "sm_smooch": mix(puff(.12, 2000), mallet(E5, .15)), "sm_note1": mallet(C5, .3), "sm_note2": mallet(E5, .3), "sm_note3": mallet(G5, .3),
    "zb_coin": seq([G5, C6], .06, .16), "zb_roar": swell(), "zb_summon": seq([C4, E4, G4, C5], .09, .4),
    "zb_victory": fanfare(), "zb_down": mix(glide(C5, C4, .7, 2.5), thud(.35, 60)), "zb_charge": glide(C4, C5, .7, 2.),
    "zb_dash": glide(G5, C5, .25, 4.), "zb_fire": glide(E5, A4, .3, 4.), "zb_boom": mix(thud(.5, 80), mallet(C4, .4)),
    "zb_coinrain": seq([C6, A5, G5, E5, C5, G4], .09, .25),
    "cw2_reward": seq([C5, E5, G5, C6], .1, .35), "lw2_reward": seq([G4, C5, E5, G5], .1, .35),
    "lw2_success": mix(seq([C5, E5, G5], .1, .3), mallet(C6, .4)), "btn_tada": seq([C5, E5, G5, C6], .09, .3),
    "star_collect": mallet(A5, .25, 7.), "pet_mouse_1": glide(A5, C6, .12, 9.), "pet_mouse_2": glide(G5, A5, .14, 9.),
    "pet_mouse_3": glide(A5, E5, .12, 9.), "btn_cymbal": mix(puff(.5, 2200), mallet(E5, .4, 5.)),
}
NORMALIZE = {
    "sm_bubble", "sm_pop", "sm_snore", "sm_thud",
    "zb_hurt", "zb_jump", "zb_jump2", "zb_land", "zb_ouch", "zb_phase", "zb_shock", "zb_slam", "zb_stomp", "zb_rocket",
    "release_boing", "frog_boing", "bounce", "cloud_bounce", "land_bounce", "wobble", "bubble", "dizzy", "rainbow_slide",
    "squish_1", "squish_2", "squish_3", "drop_plip_1", "drop_plip_2",
    "fanfare_short", "rainbow_salute", "confetti_rain", "firework", "fx_fanfare", "fx_legendary_fanfare",
    "fx_magic_sparkle", "fx_sparkle_chime", "fx_chime", "star_burst", "rainbow_chime", "chime", "pickup",
    "sw_rage", "sw_warn", "sw_hit",
}
# Шаг 0 (аудит): всё, что реально клиппует (пик > 1.0) вне исключений: fx_it_* (награды/удары
# монстриков и Зелика), lw2_*, cw2_*, sw_*, btn_*, sg_shot, fx_bd_flap, decor_*, шумовые.
NORMALIZE |= {
 "btn_cymbal", "btn_magic", "btn_rocket", "btn_zip", "cw2_arrive", "cw2_foam", "cw2_honk",
 "cw2_reward", "cw2_rinse", "cw2_scrub", "cw2_sparkle", "decor_cloud", "decor_crayon", "decor_kite",
 "fx_bd_flap", "fx_it_drg_10", "fx_it_drg_11", "fx_it_drg_19", "fx_it_drg_20", "fx_it_drg_21",
 "fx_it_drp_1", "fx_it_drp_11", "fx_it_drp_14", "fx_it_drp_17", "fx_it_drp_18", "fx_it_drp_19",
 "fx_it_drp_2", "fx_it_drp_22", "fx_it_drp_27", "fx_it_drp_28", "fx_it_drp_3", "fx_it_drp_30",
 "fx_it_drp_31", "fx_it_drp_32", "fx_it_drp_4", "fx_it_drp_5", "fx_it_drp_6", "fx_it_drp_7",
 "fx_it_drp_8", "fx_it_dst_11", "fx_it_dst_18", "fx_it_dst_21", "fx_it_dst_6", "fx_it_dst_7",
 "fx_it_dst_9", "fx_it_eqp_10", "fx_it_eqp_11", "fx_it_eqp_12", "fx_it_eqp_13", "fx_it_eqp_6",
 "fx_it_eqp_8", "fx_it_eqp_9", "fx_it_inv_1", "fx_it_inv_10", "fx_it_inv_12", "fx_it_inv_15",
 "fx_it_inv_3", "fx_it_inv_4", "fx_it_inv_5", "fx_it_pck_11", "fx_it_pck_17", "fx_it_pck_18",
 "fx_it_pck_19", "fx_it_pck_2", "fx_it_pck_20", "fx_it_pck_22", "fx_it_pck_24", "fx_it_pck_25",
 "fx_it_pck_6", "fx_it_pck_9", "fx_ui_click", "kite_swoosh", "lw2_drop", "lw2_open", "lw2_pin",
 "lw2_pop", "lw2_powder", "lw2_reward", "lw2_stain", "lw2_start", "lw2_success", "parachute_open",
 "sg_shot", "shoot", "sm_giggle", "sm_hic", "sm_party", "sm_smooch", "sm_sneeze", "sm_tickle",
 "sm_yawn", "steam", "sw_boom", "sw_chomp", "sw_crunch", "sw_dash", "sw_miss", "sw_start",
 "zb_boom", "zb_charge", "zb_dash", "zb_down", "zb_fire"
}
# Шаг 0 (аудит): очень громкие файлы (RMS > -8 дБ при норме около -17), не клиппующие.
NORMALIZE |= {
 "bird_tweet", "btn_honk", "btn_kazoo", "btn_rocket", "btn_siren", "btn_whistle", "cw2_honk",
 "decor_bee", "decor_bird", "decor_mouse", "decor_tree", "dino_soft", "fx_cw_beep", "fx_sh_pick",
 "fx_squeak", "lw2_start", "sm_hic", "sm_honk", "sm_oops", "sm_sneeze", "sm_tada", "sm_tickle",
 "sm_wee", "sm_yawn", "sw_dash", "zb_charge", "zb_dash"
}
# Пискливые «писки» птенцов (chick_peep, ar_peeps) — это звери: по правилам проекта их не трогаем.
LOWPASS = {"squish_1": 2500, "squish_3": 2500, "zb_rocket": 3000}
# опционально (шумовые вне погоды) — раскомментировать при желании:
# LOWPASS.update({k: 3000 for k in ["shoot", "steam", "kc_whoosh", "kc_stir", "fx_sb_water", "lever_clack",
#   "decor_crayon", "decor_cloud", "decor_kite", "kite_swoosh", "parachute_open", "wl_powder", "fx_popper"]})


def decode(p):
    r = subprocess.run(["ffmpeg", "-v", "error", "-i", p, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                       capture_output=True, check=True)
    return np.frombuffer(r.stdout, dtype=np.float32).copy()


def wav_bytes(x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    return (b"RIFF" + struct.pack("<I", 36 + len(pcm)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 1, SR, SR * 2, 2, 16)
            + b"data" + struct.pack("<I", len(pcm)) + pcm)


def encode(x, p):
    r = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "wav", "-i", "pipe:0",
                        "-c:a", "libvorbis", "-q:a", "4", "-ac", "1", p], input=wav_bytes(x), capture_output=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.decode()[:200])


def stats(x):
    return (round(float(np.abs(x).max()), 3), round(float(20 * np.log10(np.sqrt(np.mean(x * x)) + 1e-9)), 1),
            round(len(x) / SR, 3))


def declick(x):
    n = len(x)
    a = min(int(.004 * SR), n // 3)
    r = min(int(.03 * SR), n // 3)
    x = x.copy()
    x[:a] *= np.linspace(0, 1, a)
    x[-r:] *= np.cos(np.linspace(0, np.pi / 2, r))
    return x


def process(name):
    p = os.path.join(RAW, name + ".ogg")
    if not os.path.exists(p):
        print("  пропуск:", name)
        return None
    # Исходник — оригинал из бэкапа (если он есть), чтобы повторный запуск не обрабатывал файл дважды.
    src = os.path.join(BAK, name + ".ogg")
    if not os.path.exists(src) or VERIFY:
        src = p
    before = stats(decode(src))
    if name in RECIPES:
        y = norm(RECIPES[name])
    else:
        x = decode(src)
        if name in LOWPASS:
            x = sosfilt(butter(3, LOWPASS[name], fs=SR, output="sos"), x).astype(np.float32)
        y = norm(declick(x), max_boost=16.0)
    return p, before, y


def main():
    names = sorted(set(RECIPES) | NORMALIZE)
    if ONLY:
        names = [n for n in names if n.startswith(ONLY)]
    if not VERIFY and not DRY:
        os.makedirs(BAK, exist_ok=True)
    ok = fail = 0
    print(f"{'файл':20}{'ДО пик/RMS/с':>22}{'ПОСЛЕ пик/RMS/с':>22}")
    for name in names:
        res = process(name)
        if res is None:
            continue
        p, before, y = res
        if VERIFY:
            pk, rms, dur = stats(decode(p))
            good = pk <= PEAK_MAX and RMS_BAND[0] <= rms <= RMS_BAND[1] and dur <= 3.0
            print(f"{name:20}{pk:5}/{rms:6}/{dur:5}  {'OK' if good else 'FAIL'}")
            ok += good
            fail += (not good)
            continue
        if DRY:
            print(f"{name:20}{str(before):>22}{str(stats(y)):>22}  (dry)")
            ok += 1
            continue
        b = os.path.join(BAK, name + ".ogg")
        if not os.path.exists(b):
            shutil.copy2(p, b)
        for _ in range(6):  # vorbis даёт овершут на щелчках: пережимаем, пока декодированный пик не уложится
            encode(y, p)
            d = decode(p)
            pk = float(np.abs(d).max())
            if pk <= PEAK_MAX:
                break
            y = (y * (0.96 * PEAK_MAX / pk)).astype(np.float32)
        after = stats(decode(p))
        good = after[0] <= PEAK_MAX and RMS_BAND[0] <= after[1] <= RMS_BAND[1]
        print(f"{name:20}{str(before):>22}{str(after):>22}  {'OK' if good else 'CHECK'}")
        ok += good
        fail += (not good)
    gi = os.path.join(HERE, ".gitignore")
    if not VERIFY and not DRY and os.path.exists(gi):
        t = open(gi, encoding="utf-8").read()
        if "raw_backup" not in t:
            with open(gi, "a", encoding="utf-8") as f:
                f.write("\napp/src/main/raw_backup/\n")
    print(f"\nИтого: {ok} ok, {fail} attention. Бэкап: {BAK}")
    if VERIFY and fail:
        sys.exit(1)


if __name__ == "__main__":
    main()
