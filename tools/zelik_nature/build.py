"""Обрезает рендеры природы (render.js) по прозрачности, уменьшает и кладёт в res/drawable-nodpi как zn_<ключ>.webp.
Модели: Midnight Misfit Studio «Cozy Nature — Free Low Poly Nature Sampler». Сами GLB в репозиторий не кладём (лицензия)."""
import os, sys
from PIL import Image
IN, OUT = sys.argv[1], sys.argv[2]
MAP = {  # ключ: (файл, максимальная сторона)
    'oak': ('Nat_01_OakRound', 480), 'birch': ('Nat_03_Birch', 480), 'spruce': ('Nat_07_Spruce', 480), 'cherry': ('Nat_10_CherryBlossom', 480),
    'bush': ('Nat_13_BushRound', 300), 'berries': ('Nat_15_BushBerries', 300), 'fern': ('Nat_17_FernLarge', 240),
    'rock_s': ('Nat_20_RockSmall', 200), 'rock_l': ('Nat_22_RockLarge', 320), 'rock_t': ('Nat_24_RockTall', 320),
    'rock_arch': ('Nat_25_RockArch', 360), 'rock_moss': ('Nat_28_RockMossy', 260),
    'daisy': ('Nat_30_Daisy', 220), 'tulip': ('Nat_31_Tulip', 220), 'lavender': ('Nat_32_Lavender', 220), 'sunflower': ('Nat_33_Sunflower', 240),
    'tuft': ('Nat_35_GrassTuftA', 200), 'tallgrass': ('Nat_37_TallGrass', 240),
    'agaric': ('Nat_40_FlyAgaric', 180), 'porcini': ('Nat_41_Porcini', 180), 'mushgroup': ('Nat_42_MushroomGroup', 200),
    'stump': ('Nat_45_StumpSmall', 200), 'log': ('Nat_47_LogLying', 340), 'logstack': ('Nat_48_LogStack', 280), 'roots': ('Nat_50_StumpRoots', 260),
}
for key, (name, mx) in MAP.items():
    im = Image.open(os.path.join(IN, f'out_{name}.png')).convert('RGBA')
    im = im.crop(im.getchannel('A').point(lambda a: 255 if a > 8 else 0).getbbox())
    k = mx / max(im.size)
    im = im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS)
    im.save(os.path.join(OUT, f'zn_{key}.webp'), 'WEBP', quality=88, method=6)
    print(key, im.size)
